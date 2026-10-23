param([string]$Base = "http://127.0.0.1:8080")
$ErrorActionPreference = "Continue"
$script:pass = 0; $script:fail = 0; $script:fails = @()

function C($m, $p, $h, $b) {
    $x = @{ Uri = "$Base$p"; Method = $m; UseBasicParsing = $true; TimeoutSec = 180 }
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
function Section($t) { Write-Output ""; Write-Output ("=== " + $t + " ===") }
function AuthH($t) { return @{ Authorization = "Bearer $t" } }

$tok = (D (C "POST" "/api/v1/auth/login" $null '{"username":"admin","password":"Dgp@2026"}')).accessToken
if (-not $tok) { Write-Output "登录失败"; exit 1 }
$HA = AuthH $tok
$sfx = Get-Random -Maximum 99999
$code1 = "AGG-VERIFY-$sfx"
$code2 = "AGG-VERIFY-UPS-$sfx"
$tbl1 = "agg_verify_$sfx"
$tbl2 = "agg_verify_ups_$sfx"
$d1 = 0; $d2 = 0

Section "V1. 聚合视图迁移向导"
$body = '{"code":"' + $code1 + '","name":"验收-客户订单汇总","sourceDsId":0,"targetDsId":0,"sourceTable":"ods_order","targetTable":"' + $tbl1 + '","dims":["customer_id"],"metrics":[{"fn":"COUNT","column":"*","alias":"order_cnt"},{"fn":"SUM","column":"amount","alias":"total_amount"},{"fn":"AVG","column":"amount","alias":"avg_amount"},{"fn":"MAX","column":"order_time","alias":"last_order_time"}],"refreshMode":"FULL","refreshMinutes":0}'
$cr = C "POST" "/api/v1/migration/agg" $HA $body
$d1 = (D $cr).id
Check "创建聚合定义 -> 200" ($cr.s -eq 200) ("id=" + $d1)

$pv = D (C "POST" ("/api/v1/migration/agg/" + $d1 + "/preview") $HA $null)
Check "预览 SQL -> 含 GROUP BY 与聚合函数" (($pv.viewSql -match "GROUP BY") -and ($pv.viewSql -match "COUNT") -and ($pv.viewSql -match "SUM")) ($pv.viewSql.Substring(0, [Math]::Min(90, $pv.viewSql.Length)))
Check "预览目标 DDL -> 指标类型正确" (($pv.targetDdl -match "BIGINT") -and ($pv.targetDdl -match "DECIMAL")) ($pv.targetDdl.Substring(0, [Math]::Min(90, $pv.targetDdl.Length)))

$bd = D (C "POST" ("/api/v1/migration/agg/" + $d1 + "/build") $HA $null)
Check "一键生成视图/目标表/迁移项目 -> 200" (($bd.viewName) -and ($bd.projectId -gt 0) -and ($bd.targetCreated -eq $true)) ("view=" + $bd.viewName + " project=" + $bd.projectId + " mappings=" + $bd.mappingCount)

$rf = C "POST" ("/api/v1/migration/agg/" + $d1 + "/refresh") $HA '{}'
$rfd = D $rf
Check "刷新 FULL -> 行数>0" (($rf.s -eq 200) -and ($rfd.rows -gt 0)) ("rows=" + $rfd.rows + " mode=" + $rfd.mode)

$det = D (C "GET" ("/api/v1/migration/agg/" + $d1) $HA $null)
Check "详情 -> 上次成功且记录>=1" (($det.lastStatus -eq "DONE") -and (@($det.runs).Count -ge 1)) ("runs=" + @($det.runs).Count)

Section "V2. UPSERT 汇总刷新"
$body2 = '{"code":"' + $code2 + '","name":"验收-UPSERT汇总","sourceDsId":0,"targetDsId":0,"sourceTable":"ods_order","targetTable":"' + $tbl2 + '","dims":["customer_id"],"metrics":[{"fn":"COUNT","column":"*","alias":"order_cnt"},{"fn":"SUM","column":"amount","alias":"total_amount"}],"refreshMode":"UPSERT","refreshMinutes":1}'
$cr2 = C "POST" "/api/v1/migration/agg" $HA $body2
$d2 = (D $cr2).id
Check "创建 UPSERT 定义 -> 200" ($cr2.s -eq 200) ("id=" + $d2)
$bd2 = D (C "POST" ("/api/v1/migration/agg/" + $d2 + "/build") $HA $null)
Check "UPSERT 生成 -> 维度唯一索引" (($bd2.uniqueIndex -eq $true) -and ($bd2.targetCreated -eq $true)) ("uniqueIndex=" + $bd2.uniqueIndex)
$r1 = D (C "POST" ("/api/v1/migration/agg/" + $d2 + "/refresh") $HA '{}')
$r2 = D (C "POST" ("/api/v1/migration/agg/" + $d2 + "/refresh") $HA '{}')
Check "UPSERT 刷新两次 -> 行数稳定（不重复）" (($r1.mode -eq "UPSERT") -and ($r2.mode -eq "UPSERT") -and ($r1.rows -eq $r2.rows)) ("rows=" + $r1.rows + "/" + $r2.rows)
$det2 = D (C "GET" ("/api/v1/migration/agg/" + $d2) $HA $null)
Check "定时配置生效 -> nextRunTime 非空" ($null -ne $det2.nextRunTime) ("next=" + $det2.nextRunTime)

Section "V3. 权限与清理"
$ana = (D (C "POST" "/api/v1/auth/login" $null '{"username":"analyst_t1","password":"Dgp@2026"}')).accessToken
$HN = AuthH $ana
Check "分析师访问聚合汇总 -> 403" ((C "GET" "/api/v1/migration/agg" $HN $null).s -eq 403) ""
$null = C "DELETE" ("/api/v1/migration/agg/" + $d1) $HA $null
$null = C "DELETE" ("/api/v1/migration/agg/" + $d2) $HA $null
Check "清理验收定义 -> 200" ((D (C "GET" ("/api/v1/migration/agg?keyword=" + $code1) $HA $null)).records.Count -eq 0) ""

Write-Output ""
Write-Output ("===== 聚合汇总（v1.5）汇总：PASS=" + $script:pass + "  FAIL=" + $script:fail + " =====")
if ($script:fail -gt 0) { Write-Output ("失败项：" + ($script:fails -join "; ")) }
