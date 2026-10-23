param([string]$Base = "http://127.0.0.1:8080")
$ErrorActionPreference = "Continue"
$script:pass = 0; $script:fail = 0; $script:fails = @()

function C($m, $p, $h, $b) {
    $x = @{ Uri = "$Base$p"; Method = $m; UseBasicParsing = $true; TimeoutSec = 120 }
    if ($h) { $x["Headers"] = $h }
    if ($b -ne $null) { $x["Body"] = [Text.Encoding]::UTF8.GetBytes($b); $x["ContentType"] = "application/json" }
    try { $r = Invoke-WebRequest @x; return @{ s = [int]$r.StatusCode; j = ($r.Content | ConvertFrom-Json) } }
    catch { $resp = $_.Exception.Response; if ($resp) { $sr = New-Object System.IO.StreamReader($resp.GetResponseStream(), [Text.Encoding]::UTF8); $t = $sr.ReadToEnd(); try { return @{ s = [int]$resp.StatusCode; j = ($t | ConvertFrom-Json) } } catch { return @{ s = [int]$resp.StatusCode; j = $null } } }; return @{ s = -1; j = $null } }
}
function D($r) { if ($r.j) { return $r.j.data } else { return $null } }
function Check($name, $cond, $detail) {
    if ($cond) { $script:pass++; Write-Output ("  PASS  " + $name + $(if ($detail) { "  [" + $detail + "]" } else { "" })) }
    else { $script:fail++; $script:fails += $name; Write-Output ("  FAIL  " + $name + "  ->  " + $detail) }
}
function Login($u) { return (D (C "POST" "/api/v1/auth/login" $null ('{"username":"' + $u + '","password":"Dgp@2026"}'))).accessToken }
function Auth($t) { return @{ Authorization = "Bearer $t" } }
function Tables($tok) { return @((D (C "GET" "/api/v1/metadata/tables?pageNum=1&pageSize=200" (Auth $tok) $null)).records).Count }

Write-Output ""
Write-Output "=== 多租户行级隔离 ==="
$admin = Login "admin"; $HA = Auth $admin
$t1 = Login "dev_t1"; $H1 = Auth $t1
$t2 = Login "zhangsan"; $H2 = Auth $t2
$t3 = Login "wangwu"; $H3 = Auth $t3
$nAdmin = Tables $admin; $nT1 = Tables $t1; $nT2 = Tables $t2; $nT3 = Tables $t3
Check "管理员可见全部元数据" ($nAdmin -ge 20) ("tables=" + $nAdmin)
Check "tenant1 用户可见本租户元数据" ($nT1 -eq $nAdmin) ("tables=" + $nT1)
Check "tenant2 用户看不到 tenant1 数据" ($nT2 -eq 0) ("tables=" + $nT2)
Check "tenant3 用户看不到 tenant1 数据" ($nT3 -eq 0) ("tables=" + $nT3)
$one = (D (C "GET" "/api/v1/metadata/tables?pageNum=1&pageSize=1" $HA $null)).records
if ($one -and $one.Count -gt 0) {
    $id = $one[0].id
    $cross = C "GET" ("/api/v1/metadata/tables/" + $id) $H2 $null
    Check "tenant2 访问 tenant1 详情 -> 404" ($cross.s -eq 404) ("HTTP " + $cross.s)
}
$kpi2 = (D (C "GET" "/api/v1/overview/dashboard" $H2 $null)).kpi
$kpi1 = (D (C "GET" "/api/v1/overview/dashboard" $HA $null)).kpi
Check "租户 KPI 不同（不再完全一致）" (($kpi1.tableCount -ne $kpi2.tableCount) -or ($kpi2.tableCount -eq 0)) ("t1=" + $kpi1.tableCount + " t2=" + $kpi2.tableCount)

Write-Output ""
Write-Output "=== 登出 / refreshToken 轮换与重放 ==="
$r1 = D (C "POST" "/api/v1/auth/login" $null '{"username":"admin","password":"Dgp@2026"}')
$at1 = $r1.accessToken; $rt1 = $r1.refreshToken
$rot = C "POST" "/api/v1/auth/refresh" $null ('{"refreshToken":"' + $rt1 + '"}')
$d2 = D $rot
Check "首次刷新 -> 200 且轮换新 refreshToken" (($rot.s -eq 200) -and ($d2.refreshToken) -and ($d2.refreshToken -ne $rt1)) ""
$replay = C "POST" "/api/v1/auth/refresh" $null ('{"refreshToken":"' + $rt1 + '"}')
Check "旧 refreshToken 重放 -> 401" ($replay.s -eq 401) ("HTTP " + $replay.s)
$me = C "GET" "/api/v1/auth/me" (Auth $d2.accessToken) $null
Check "重放检测后该会话 accessToken 失效 -> 401" ($me.s -eq 401) ("HTTP " + $me.s)
$r3 = D (C "POST" "/api/v1/auth/login" $null '{"username":"admin","password":"Dgp@2026"}')
$null = C "POST" "/api/v1/auth/logout" (Auth $r3.accessToken) $null
$after = C "POST" "/api/v1/auth/refresh" $null ('{"refreshToken":"' + $r3.refreshToken + '"}')
Check "登出后 refreshToken -> 401" ($after.s -eq 401) ("HTTP " + $after.s)
$me2 = C "GET" "/api/v1/auth/me" (Auth $r3.accessToken) $null
Check "登出后 accessToken -> 401" ($me2.s -eq 401) ("HTTP " + $me2.s)

Write-Output ""
Write-Output "=== 报告标题 / 概览闭环率 ==="
$rp = D (C "POST" "/api/v1/report/generate" $HA '{"title":"验收专用标题-TENANT","format":"html","formats":["html","csv","md"]}')
$hit = $false
foreach ($f in @($rp.files)) {
    if ($f.fileId) {
        try {
            $resp = Invoke-WebRequest -Uri ("$Base/api/v1/report/files/" + $f.fileId + "/download") -Headers $HA -UseBasicParsing -TimeoutSec 60
            $text = [Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray()); if ($text -match "验收专用标题-TENANT") { $hit = $true }
        } catch { }
    }
}
Check "自定义标题进入产物（HTML/CSV/MD）" ($rp -and $hit) ("reportId=" + $rp.reportId)
$ana = Login "analyst_t1"
$k1 = (D (C "GET" "/api/v1/overview/dashboard" (Auth $ana) $null)).kpi
Check "分析师概览闭环率非空" (($null -ne $k1.closedRate) -and ("$($k1.closedRate)" -ne "")) ("closedRate=" + $k1.closedRate)

Write-Output ""
Write-Output ("===== 租户隔离 + 会话安全 汇总：PASS=" + $script:pass + "  FAIL=" + $script:fail + " =====")
if ($script:fail -gt 0) { Write-Output ("失败项：" + ($script:fails -join "; ")) }
