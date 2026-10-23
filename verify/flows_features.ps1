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
$qlt = Login "quality_t1" "Dgp@2026"; $HQ = AuthH $qlt
$ana = Login "analyst_t1" "Dgp@2026"; $HN = AuthH $ana

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

$projs = D (C "GET" "/api/v1/migration/projects?pageSize=50" $HA $null)
Check "迁移项目 -> 至少 2 个" ($projs.total -ge 2) ("total=" + $projs.total)
# 固定使用演示项目（避免受验收过程中临时项目影响）
$proj = $projs.records | Where-Object { $_.code -eq "MIG-ORDER-FACT" } | Select-Object -First 1
if (-not $proj) { $proj = $projs.records | Where-Object { $_.checkCount -ge 1 } | Select-Object -First 1 }
if (-not $proj) { $proj = $projs.records[0] }
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


# ================= 实时推送接入（v1.3） =================
Section "M3. 流式实时推送接入"
$pushSrc = $srcs.records | Where-Object { $_.code -eq "SS-DEVICE-METRIC" } | Select-Object -First 1
if (-not $pushSrc) { $pushSrc = $src0 }
$pc = C "GET" ("/api/v1/quality/stream/sources/" + $pushSrc.id + "/push-config") $HA $null
Check "推送配置可查询 -> 200" ($pc.s -eq 200) ""
$mappingJson = '{\"id\":\"id\",\"device_id\":\"deviceId\",\"device_name\":\"deviceName\",\"workshop\":\"workshop\",\"metric_name\":\"metricName\",\"metric_value\":\"value\",\"unit\":\"unit\",\"collect_time\":\"collectTime\"}'
$sp = C "PUT" ("/api/v1/quality/stream/sources/" + $pushSrc.id + "/push-config") $HA ('{"fieldMapping":' + $mappingJson.Replace('\"','"') + ',"status":"ENABLED"}')
Check "保存推送配置 -> 200" ($sp.s -eq 200) ""
$now = Get-Date
$ts = $now.ToString("yyyy-MM-dd HH:mm:ss")
$eventId = 900000 + (Get-Random -Maximum 90000)
$evBody = '{"id":' + $eventId + ',"deviceId":"DEV-PUSH-VERIFY","deviceName":"推送验收设备","workshop":"推送车间","metricName":"push_metric","value":999.9,"unit":"u","collectTime":"' + $ts + '"}'
$pe = C "POST" ("/api/v1/quality/stream/push/" + $pushSrc.code) $HA $evBody
$ped = D $pe
Check "实时推送事件 -> 200 且逐规则判定" (($pe.s -eq 200) -and ($ped.ruleCount -ge 1)) ("rules=" + $ped.ruleCount + " failed=" + $ped.failedRules)
Check "推送事件命中越界规则（999.9 > 200）" ($ped.failedRules -ge 1) ("failed=" + $ped.failedRules)
Check "推送不存在的流源 -> 404" ((C "POST" "/api/v1/quality/stream/push/SS-NOT-EXIST" $HA $evBody).s -eq 404) ""
Check "推送报文缺少主键 -> 400" ((C "POST" ("/api/v1/quality/stream/push/" + $pushSrc.code) $HA '{"deviceId":"X","value":1}').s -eq 400) ""
Check "推送结果已入窗口结果表" ((D (C "GET" ("/api/v1/quality/stream/results?ruleId=" + $ped.results[0].ruleId + "&pageSize=5") $HA $null)).total -ge 1) ""

# ================= 迁移分批 / 断点续传 / 回滚（v1.3） =================
Section "M4. 迁移分批执行与回滚"
$verCode = "MIG-BATCH-" + (Get-Random -Maximum 99999)
$target = "zz_mig_verify_" + (Get-Random -Maximum 999999)
$np = C "POST" "/api/v1/migration/projects" $HA ('{"code":"' + $verCode + '","name":"验收-分批迁移","sourceTable":"ods_order","targetTable":"' + $target + '","mode":"INCREMENTAL","watermarkCol":"order_time","batchSize":2000,"rollbackOnFail":1}')
Check "创建增量迁移项目（分批 2000）-> 200" ($np.s -eq 200) ""
$newProj = (D $np).id
$mapBody = '{"mappings":[{"sourceColumn":"order_id","targetColumn":"k"},{"sourceColumn":"amount","targetColumn":"amt"},{"sourceColumn":"order_time","targetColumn":"ts","targetType":"datetime"}]}'
Check "保存映射 -> 200" ((C "POST" ("/api/v1/migration/projects/" + $newProj + "/mappings") $HA $mapBody).s -eq 200) ""
$ct = C "POST" ("/api/v1/migration/projects/" + $newProj + "/create-target") $HA $null
Check "一键按映射建目标表 -> 200 且 created" (($ct.s -eq 200) -and ((D $ct).created -eq $true)) ("target=" + $target)
$runB = C "POST" ("/api/v1/migration/projects/" + $newProj + "/run") $HA '{"replace":0,"batchSize":2000}'
$runBd = D $runB
Check "增量分批执行 -> 200 且分多批" (($runB.s -eq 200) -and ($runBd.chunkCount -ge 2)) ("chunks=" + $runBd.chunkCount + " inserted=" + $runBd.insertedRows)
Check "分批执行记录起止水位" (($null -ne $runBd.endWatermark) -and ($runBd.endWatermark -ne "")) ("end=" + $runBd.endWatermark)
$rb = C "POST" ("/api/v1/migration/runs/" + $runBd.runId + "/rollback") $HA $null
$rbd = D $rb
Check "回滚增量批次 -> 200 且删除行数>0" (($rb.s -eq 200) -and ($rbd.deletedRows -gt 0)) ("deleted=" + $rbd.deletedRows)
$fullProjs = @((D (C "GET" "/api/v1/migration/projects?status=DONE&pageSize=20" $HA $null)).records)
$fullProj = $fullProjs | Where-Object { $_.mode -eq "FULL" -and $_.sourceTable -notlike "v_agg_*" } | Select-Object -First 1
if ($fullProj) {
    $fr = C "POST" ("/api/v1/migration/projects/" + $fullProj.id + "/run") $HA '{"replace":1}'
    $frd = D $fr
    Check "全量批次回滚 -> 400（无起始水位）" ((C "POST" ("/api/v1/migration/runs/" + $frd.runId + "/rollback") $HA $null).s -eq 400) ""
} else { Check "全量批次回滚 -> 400（无起始水位）" $false "缺少 FULL 项目" }
Check "清理验收迁移项目 -> 200" ((C "DELETE" ("/api/v1/migration/projects/" + $newProj) $HA $null).s -eq 200) ""


Write-Output ""
Write-Output ("===== 流式质检 + 数据迁移 汇总：PASS=" + $script:pass + "  FAIL=" + $script:fail + " =====")
if ($script:fail -gt 0) { Write-Output ("失败项：" + ($script:fails -join "; ")) }
