param([string]$Base = "http://127.0.0.1:8080")
$ErrorActionPreference = "Continue"
$script:pass = 0; $script:fail = 0; $script:fails = @()

function C($m, $p, $h, $b) {
    $x = @{ Uri = "$Base$p"; Method = $m; UseBasicParsing = $true; TimeoutSec = 120 }
    if ($h) { $x["Headers"] = $h }
    if ($b -ne $null) { $x["Body"] = [Text.Encoding]::UTF8.GetBytes($b); $x["ContentType"] = "application/json" }
    try { $r = Invoke-WebRequest @x; return @{ s = [int]$r.StatusCode; j = ($r.Content | ConvertFrom-Json) } }
    catch {
        $resp = $_.Exception.Response
        if ($resp) {
            $status = -1
            try { $status = [int]$resp.StatusCode } catch { try { $status = [int]$resp.StatusCode.value__ } catch { } }
            $t = $_.ErrorDetails.Message
            if (-not $t -and $resp.Content) {
                try { $t = $resp.Content.ReadAsStringAsync().GetAwaiter().GetResult() } catch { }
            }
            if (-not $t -and $resp.GetResponseStream) {
                try { $sr = New-Object System.IO.StreamReader($resp.GetResponseStream(), [Text.Encoding]::UTF8); $t = $sr.ReadToEnd() } catch { }
            }
            if ($t) { try { return @{ s = $status; j = ($t | ConvertFrom-Json) } } catch { } }
            return @{ s = $status; j = $null }
        }
        return @{ s = -1; j = $null }
    }
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
Check "管理员可见全部元数据" ($nAdmin -ge 10) ("tables=" + $nAdmin)
Check "tenant1 用户可见本租户元数据" ($nT1 -eq $nAdmin) ("tables=" + $nT1)
Check "tenant2 用户看不到 tenant1 数据" ($nT2 -eq 0) ("tables=" + $nT2)
Check "tenant3 用户看不到 tenant1 数据" ($nT3 -eq 0) ("tables=" + $nT3)
$one = (D (C "GET" "/api/v1/metadata/tables?pageNum=1&pageSize=1" $HA $null)).records
if ($one -and $one.Count -gt 0) {
    $id = $one[0].id
    $cross = C "GET" ("/api/v1/metadata/tables/" + $id) $H2 $null
    Check "tenant2 访问 tenant1 详情 -> 404" ($cross.s -eq 404) ("HTTP " + $cross.s)
}
$dash2 = D (C "GET" "/api/v1/overview/dashboard" $H2 $null)
$kpi2 = $dash2.kpi
$kpi1 = (D (C "GET" "/api/v1/overview/dashboard" $HA $null)).kpi
Check "租户 KPI 不同（不再完全一致）" (($kpi1.tableCount -ne $kpi2.tableCount) -or ($kpi2.tableCount -eq 0)) ("t1=" + $kpi1.tableCount + " t2=" + $kpi2.tableCount)
Check "tenant2 字段总数清零" ($kpi2.columnCount -eq 0) ("columns=" + $kpi2.columnCount)
Check "tenant2 服务调用量清零" ($kpi2.callCount -eq 0) ("calls=" + $kpi2.callCount)
Check "tenant2 失败作业数清零" ($dash2.alerts.failedJobs -eq 0) ("failedJobs=" + $dash2.alerts.failedJobs)
Check "tenant2 最近采集时间为空" ($null -eq $dash2.collectHealth.lastCollect) ("lastCollect=" + $dash2.collectHealth.lastCollect)
Check "tenant2 最近作业/趋势无跨租户数据" ((@($dash2.recentJobs).Count -eq 0) -and (@($dash2.callTrend).Count -eq 0) -and (@($dash2.qualityTrend).Count -eq 0)) ("recentJobs=" + @($dash2.recentJobs).Count)

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
$rpFull = D (C "POST" "/api/v1/quality/reports" $HA '{"scope":"全域核心表","period":"2026-10"}')
$rpFullCtx = (D (C "GET" ("/api/v1/quality/reports/" + $rpFull.reportId) $HA $null)).context
Check "默认全域核心表范围 -> 综合分与纳入表数非零" (($rpFull.score -gt 0) -and ($rpFullCtx.tableCount -gt 0) -and ($rpFullCtx.avgScore -gt 0)) ("score=" + $rpFull.score + " tables=" + $rpFullCtx.tableCount)
$ana = Login "analyst_t1"
$k1 = (D (C "GET" "/api/v1/overview/dashboard" (Auth $ana) $null)).kpi
Check "分析师概览闭环率非空" (($null -ne $k1.closedRate) -and ("$($k1.closedRate)" -ne "")) ("closedRate=" + $k1.closedRate)

Write-Output ""
Write-Output "=== 迁移列表接口（TenantGuard 子查询 WHERE 回归） ==="
$cons = Login "consultant"
$ma = C "GET" "/api/v1/migration/agg" $H1 $null
Check "tenant1 用户访问聚合汇总列表 -> 200" ($ma.s -eq 200) ("HTTP " + $ma.s)
$mc = C "GET" "/api/v1/migration/agg" (Auth $cons) $null
Check "顾问访问聚合汇总列表 -> 200" ($mc.s -eq 200) ("HTTP " + $mc.s)
$ft1 = C "GET" "/api/v1/migration/files/tasks" $H1 $null
Check "tenant1 用户访问文件迁移列表 -> 200" ($ft1.s -eq 200) ("HTTP " + $ft1.s)
$ft2 = C "GET" "/api/v1/migration/files/tasks?keyword=客户" $H1 $null
Check "文件迁移列表带 keyword -> 200" ($ft2.s -eq 200) ("HTTP " + $ft2.s)

Write-Output ""
Write-Output ("===== 租户隔离 + 会话安全 汇总：PASS=" + $script:pass + "  FAIL=" + $script:fail + " =====")
if ($script:fail -gt 0) { Write-Output ("失败项：" + ($script:fails -join "; ")) }
