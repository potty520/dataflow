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
        if ($resp) { $sr = New-Object System.IO.StreamReader($resp.GetResponseStream(), [Text.Encoding]::UTF8); $txt = $sr.ReadToEnd(); try { return @{ s = [int]$resp.StatusCode; j = ($txt | ConvertFrom-Json) } } catch { return @{ s = [int]$resp.StatusCode; j = $null } } }
        return @{ s = -1; j = $null }
    }
}
function D($r) { if ($r.j) { return $r.j.data } else { return $null } }
function Check($name, $cond, $detail) {
    if ($cond) { $script:pass++; Write-Output ("  PASS  " + $name + $(if ($detail) { "  [" + $detail + "]" } else { "" })) }
    else { $script:fail++; $script:fails += $name; Write-Output ("  FAIL  " + $name + "  ->  " + $detail) }
}
function Section($t) { Write-Output ""; Write-Output ("=== " + $t + " ===") }
function Login($u, $p) { return (D (C "POST" "/api/v1/auth/login" $null ('{"username":"' + $u + '","password":"' + $p + '"}'))).accessToken }
function AuthH($t) { return @{ Authorization = "Bearer $t" } }

function JStr($v) { if ($null -eq $v -or "$v" -eq "") { return 'null' } else { return '"' + ("$v" -replace '"','\"') + '"' } }
$adm = Login "admin" "Dgp@2026"; $HA = AuthH $adm
$qlt = Login "liuqin" "Dgp@2026"; $HQ = AuthH $qlt
$ana = Login "wangwu" "Dgp@2026"; $HN = AuthH $ana

# ================= 流式质检 =================
Section "M1. 流式（微批）数据质检"
$ov = D (C "GET" "/api/v1/quality/stream/overview" $HA $null)
Check "概览接口 -> 200 且含流源/规则/通过率" (($null -ne $ov.sourceCount) -and ($null -ne $ov.ruleCount) -and ($null -ne $ov.avgPassRate)) ("sources=" + $ov.sourceCount + " rules=" + $ov.ruleCount)

$srcs = D (C "GET" "/api/v1/quality/stream/sources?pageSize=20" $HA $null)
Check "流源列表 -> 至少 2 个（演示数据）" ($srcs.total -ge 2) ("total=" + $srcs.total)
$src0 = $srcs.records[0]
Check "流源含水位字段与窗口配置" (($null -ne $src0.watermarkCol) -and ($null -ne $src0.windowSeconds)) ("wm=" + $src0.watermarkCol + " window=" + $src0.windowSeconds)

$rules = D (C "GET" "/api/v1/quality/stream/rules?pageSize=50" $HA $null)
Check "流式规则 -> 至少 5 条" ($rules.total -ge 5) ("total=" + $rules.total)

Check "重置水位 -> 200" ((C "POST" ("/api/v1/quality/stream/sources/" + $src0.id + "/reset-watermark") $HA $null).s -eq 200) ""
$null = C "PUT" ("/api/v1/quality/stream/sources/" + $src0.id) $HA '{"windowSeconds":172800}'
$run = C "POST" "/api/v1/quality/stream/sources/run-all" $HA $null
$rund = D $run
Check "手工执行全部窗口 -> 200 且 2 个流源" (($run.s -eq 200) -and ($rund.total -ge 2)) ("sources=" + $rund.total + " failed=" + $rund.failedSources)

$res = D (C "GET" "/api/v1/quality/stream/results?pageSize=20" $HA $null)
Check "窗口结果已产生 -> 至少 3 条" ($res.total -ge 3) ("total=" + $res.total)
Check "结果字段完整（窗口/行数/失败数/通过率）" (($null -ne $res.records[0].windowFrom) -and ($null -ne $res.records[0].rowCount) -and ($null -ne $res.records[0].passRate)) ("rows=" + $res.records[0].rowCount + " pass=" + $res.records[0].passRate)

$rcode = "SR-VERIFY-" + (Get-Random -Maximum 99999)
$cr = C "POST" "/api/v1/quality/stream/rules" $HA ('{"sourceId":' + $src0.id + ',"code":"' + $rcode + '","name":"验收流式规则","dimension":"COMPLETENESS","expression":"metric_value IS NULL","tolerance":0.001,"severity":"MAJOR"}')
Check "创建流式规则 -> 200" ($cr.s -eq 200) ""
$rid = (D $cr).id
Check "重复规则编码 -> 400" ((C "POST" "/api/v1/quality/stream/rules" $HA ('{"sourceId":' + $src0.id + ',"code":"' + $rcode + '","expression":"1=1"}')).s -eq 400) ""
Check "非法条件（多语句）-> 400" ((C "POST" "/api/v1/quality/stream/rules" $HA ('{"sourceId":' + $src0.id + ',"code":"SR-BAD-' + (Get-Random -Maximum 9999) + '","expression":"1=1; DELETE FROM qa_rule"}')).s -eq 400) ""
Check "非法条件（写操作）-> 400" ((C "POST" "/api/v1/quality/stream/rules" $HA ('{"sourceId":' + $src0.id + ',"code":"SR-BAD2-' + (Get-Random -Maximum 9999) + '","expression":"metric_value > 0 OR EXISTS (SELECT 1) -- x"}')).s -eq 400) ""
Check "不存在的流源 -> 400" ((C "POST" "/api/v1/quality/stream/rules" $HA '{"sourceId":99999,"code":"SR-BAD3","expression":"1=1"}').s -eq 400) ""
Check "删除验收规则 -> 200" ((C "DELETE" ("/api/v1/quality/stream/rules/" + $rid) $HA $null).s -eq 200) ""

Check "告警列表 -> 200" ((C "GET" "/api/v1/quality/stream/alerts" $HA $null).s -eq 200) ""
Check "处置不存在的告警 -> 404" ((C "POST" "/api/v1/quality/stream/alerts/999999/handle" $HA $null).s -eq 404) ""
Check "可选源表清单 -> 非空" ((D (C "GET" "/api/v1/quality/stream/tables" $HA $null)).records.Count -gt 0) ""
Check "源表字段清单 -> 200" ((C "GET" ("/api/v1/quality/stream/columns?table=" + $src0.tableName) $HA $null).s -eq 200) ""
Check "分析师可查看流式质检" ((C "GET" "/api/v1/quality/stream/overview" $HN $null).s -eq 200) ""
Check "分析师不能创建流源 -> 403" ((C "POST" "/api/v1/quality/stream/sources" $HN '{"code":"SS-X","name":"x","tableName":"ods_order","watermarkCol":"order_time"}').s -eq 403) ""

# ================= 数据迁移 =================
Section "M2. 数据迁移映射"
$mov = D (C "GET" "/api/v1/migration/overview" $HA $null)
Check "迁移概览 -> 200 且含项目/映射计数" (($null -ne $mov.projectCount) -and ($null -ne $mov.mappingCount)) ("projects=" + $mov.projectCount + " mappings=" + $mov.mappingCount)

$projs = D (C "GET" "/api/v1/migration/projects?pageSize=20" $HA $null)
Check "迁移项目 -> 至少 2 个" ($projs.total -ge 2) ("total=" + $projs.total)
$proj = $projs.records[0]
$projId = $proj.id
$detail = D (C "GET" ("/api/v1/migration/projects/" + $projId) $HA $null)
Check "项目详情含映射与对账项" (($detail.mappings.Count -ge 5) -and ($detail.checks.Count -ge 1)) ("mappings=" + $detail.mappings.Count + " checks=" + $detail.checks.Count)

$rec = D (C "GET" ("/api/v1/migration/projects/" + $projId + "/recommend") $HA $null)
Check "映射自动推荐 -> 匹配至少 5 个字段" ($rec.matched -ge 5) ("matched=" + $rec.matched + " candidates=" + $rec.candidates.Count)
Check "推荐结果含转换类型" ($rec.candidates[0].transformType -ne $null) ("type=" + $rec.candidates[0].transformType)

$mappingsJson = ($detail.mappings | ForEach-Object {
    '{"sourceColumn":' + (JStr $_.sourceColumn) + ',"targetColumn":' + (JStr $_.targetColumn) + ',"transformType":' + (JStr $_.transformType) + ',"transformExpr":' + (JStr $_.transformExpr) + ',"defaultValue":' + (JStr $_.defaultValue) + '}'
}) -join ","
Check "保存映射（原样回写）-> 200" ((C "POST" ("/api/v1/migration/projects/" + $projId + "/mappings") $HA ('{"mappings":[' + $mappingsJson + ']}')).s -eq 200) ""

$pre = C "POST" ("/api/v1/migration/projects/" + $projId + "/precheck") $HA $null
$pred = D $pre
Check "预检 -> 200 且 ready=true" (($pre.s -eq 200) -and ($pred.ready -eq $true)) ("errors=" + ($pred.errors -join ";"))
Check "预检返回样例数据" ($pred.sample.Count -ge 1) ("sample=" + $pred.sample.Count)

$st = D (C "GET" ("/api/v1/migration/projects/" + $projId + "/statement") $HA $null)
Check "映射生成 SQL -> 含 INSERT INTO 与 SELECT" (($st.sql -like "INSERT INTO*") -and ($st.sql -like "*SELECT*")) ""

$runM = C "POST" ("/api/v1/migration/projects/" + $projId + "/run") $HA '{"replace":1}'
$runMd = D $runM
Check "执行迁移 -> 200 且写入行数 > 0" (($runM.s -eq 200) -and ($runMd.insertedRows -gt 0)) ("source=" + $runMd.sourceRows + " inserted=" + $runMd.insertedRows)

$rc = C "POST" ("/api/v1/migration/runs/" + $runMd.runId + "/reconcile") $HA $null
$rcd = D $rc
Check "对账 -> 200 且检查项 >= 3" (($rc.s -eq 200) -and ($rcd.checkCount -ge 3)) ("checks=" + $rcd.checkCount + " failed=" + $rcd.failedCount)
Check "对账结论可读（PASS/FAIL）" (($rcd.status -eq "PASS") -or ($rcd.status -eq "FAIL")) ("status=" + $rcd.status)

$diffs = D (C "GET" ("/api/v1/migration/runs/" + $runMd.runId + "/diffs") $HA $null)
Check "差异明细可查询" ($diffs.records.Count -ge 1) ("diffs=" + $diffs.records.Count)
Check "执行记录列表 -> 含本次批次" ((D (C "GET" ("/api/v1/migration/projects/" + $projId + "/runs") $HA $null)).records[0].id -eq $runMd.runId) ""

$newCode = "MIG-VERIFY-" + (Get-Random -Maximum 99999)
$cp = C "POST" "/api/v1/migration/projects" $HA ('{"code":"' + $newCode + '","name":"验收迁移项目","sourceTable":"ods_customer","targetTable":"dwd_customer_dim","mode":"FULL"}')
Check "创建迁移项目 -> 200" ($cp.s -eq 200) ""
$newId = (D $cp).id
Check "重复项目编码 -> 400" ((C "POST" "/api/v1/migration/projects" $HA ('{"code":"' + $newCode + '","sourceTable":"ods_customer","targetTable":"dwd_customer_dim"}')).s -eq 400) ""
Check "源表与目标表相同 -> 400" ((C "POST" "/api/v1/migration/projects" $HA ('{"code":"MIG-SAME-' + (Get-Random -Maximum 999) + '","sourceTable":"ods_customer","targetTable":"ods_customer"}')).s -eq 400) ""
Check "删除验收项目 -> 200" ((C "DELETE" ("/api/v1/migration/projects/" + $newId) $HA $null).s -eq 200) ""
Check "分析师无迁移项目权限 -> 403" ((C "GET" "/api/v1/migration/projects" $HN $null).s -eq 403) ""
Check "分析师不能执行迁移 -> 403" ((C "POST" ("/api/v1/migration/projects/" + $projId + "/run") $HN '{"replace":0}').s -eq 403) ""

Write-Output ""
Write-Output ("===== 流式质检 + 数据迁移 汇总：PASS=" + $script:pass + "  FAIL=" + $script:fail + " =====")
if ($script:fail -gt 0) { Write-Output ("失败项：" + ($script:fails -join "; ")) }
