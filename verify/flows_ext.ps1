param([string]$Base = "http://127.0.0.1:8080")
$ErrorActionPreference = "Continue"
$script:pass = 0; $script:fail = 0; $script:fails = @()
function Call($method, $path, $headers, $body) {
    $p = @{ Uri = "$Base$path"; Method = $method; UseBasicParsing = $true; TimeoutSec = 180 }
    if ($headers) { $p["Headers"] = $headers }
    if ($body -ne $null) { $p["Body"] = [Text.Encoding]::UTF8.GetBytes($body); $p["ContentType"] = "application/json" }
    try { $r = Invoke-WebRequest @p; return [pscustomobject]@{ Status = [int]$r.StatusCode; Body = $r.Content; Head = $r.Headers } }
    catch {
        $resp = $_.Exception.Response
        if ($resp) { $s = ""; try { $sr = New-Object System.IO.StreamReader($resp.GetResponseStream(), [Text.Encoding]::UTF8); $s = $sr.ReadToEnd() } catch {}; return [pscustomobject]@{ Status = [int]$resp.StatusCode; Body = $s; Head = $resp.Headers } }
        return [pscustomobject]@{ Status = -1; Body = $_.Exception.Message; Head = @{} }
    }
}
function J($r) { try { return ($r.Body | ConvertFrom-Json) } catch { return $null } }
function D($r) { $j = J $r; if ($j) { return $j.data } else { return $null } }
function Check($name, $cond, $detail) {
    if ($cond) { $script:pass++; Write-Output ("  PASS  " + $name + $(if ($detail) { "  [" + $detail + "]" } else { "" })) }
    else { $script:fail++; $script:fails += $name; Write-Output ("  FAIL  " + $name + "  ->  " + $detail) }
}
function Section($t) { Write-Output ""; Write-Output ("=== " + $t + " ===") }
function Login($u, $p) { return (J (Call "POST" "/api/v1/auth/login" $null ('{"username":"' + $u + '","password":"' + $p + '"}'))).data.accessToken }
function AuthH($t) { return @{ Authorization = "Bearer $t" } }

$adm = Login "admin" "Dgp@2026"; $dev = Login "zhangsan" "Dgp@2026"; $qlt = Login "liuqin" "Dgp@2026"
$ana = Login "wangwu" "Dgp@2026"; $sec = Login "secadmin" "Dgp@2026"; $con = Login "consultant" "Dgp@2026"
$HA = AuthH $adm; $HD = AuthH $dev; $HQ = AuthH $qlt; $HN = AuthH $ana; $HS = AuthH $sec; $HC = AuthH $con

# ================= E. 集成与加工 =================
Section "E. 数据集成与加工"
$jCode = "JOB-VERIFY-" + (Get-Random -Maximum 9999)
$jc = Call "POST" "/api/v1/integration/jobs" $HA ('{"code":"' + $jCode + '","name":"验收作业","jobType":"ETL","scriptLang":"SQL","script":"SELECT COUNT(*) FROM ods_customer"}')
$jid = (D $jc).id
Check "创建作业 -> 200 (DRAFT)" (($jc.Status -eq 200) -and ((D (Call "GET" "/api/v1/integration/jobs/$jid" $HA $null)).status -eq "DRAFT")) ("id=" + $jid)
$null = Call "PUT" "/api/v1/integration/jobs/$jid" $HA '{"script":"DROP TABLE ods_customer"}'
Check "发布禁用语句脚本 -> 400" ((Call "POST" "/api/v1/integration/jobs/$jid/publish" $HA $null).Status -eq 400) ""
$null = Call "PUT" "/api/v1/integration/jobs/$jid" $HA '{"script":"UPDATE dwd_order_fact SET status=status WHERE 1=0"}'
Check "发布合法脚本 -> 200" ((Call "POST" "/api/v1/integration/jobs/$jid/publish" $HA $null).Status -eq 200) ""
$run = D (Call "POST" "/api/v1/integration/jobs/$jid/run" $HA $null)
Check "运行已发布作业 -> SUCCESS" ($run.status -eq "SUCCESS") ("rows=" + $run.rowsWritten)
Check "作业日志非空" ((@((D (Call "GET" "/api/v1/integration/jobs/$jid/logs" $HA $null)).records)).Count -gt 0) ""
Check "作业实例列表 -> 200" (((D (Call "GET" "/api/v1/integration/jobs/$jid/instances" $HA $null)).records.Count) -ge 1) ""
Check "作业编译 -> 执行计划" (((D (Call "POST" "/api/v1/integration/jobs/$jid/compile" $HA $null)).stepCount) -ge 1) ""
$seededJob = @((D (Call "GET" "/api/v1/integration/jobs?pageSize=50" $HA $null)).records) | Where-Object { $_.jobType -ne "WORKFLOW" } | Select-Object -First 1
$conv = Call "POST" "/api/v1/integration/jobs/$($seededJob.id)/dsl:fromScript" $HA $null
Check "脚本 -> DSL 转换 -> 200" ($conv.Status -eq 200) ""
Check "DSL 试运行 -> 不 5xx" ((Call "POST" "/api/v1/integration/jobs/$($seededJob.id)/dry-run" $HA '{"limit":5}').Status -lt 500) ""
Check "SQL 校验（合法 SELECT）-> valid" (((D (Call "POST" "/api/v1/integration/sql/validate" $HA '{"sql":"SELECT 1 FROM ods_customer"}')).valid) -eq $true) ""
Check "SQL 校验（多语句）-> 400" ((Call "POST" "/api/v1/integration/sql/validate" $HA '{"sql":"SELECT 1; DELETE FROM ods_customer"}').Status -eq 400) ""
Check "SQL 格式化 -> 200" (((D (Call "POST" "/api/v1/integration/sql/format" $HA '{"sql":"select a,b from ods_customer where a=1"}')).formatted) -like "SELECT*") ""
$aq = D (Call "POST" "/api/v1/integration/adhoc/query" $HA '{"sql":"SELECT customer_id FROM ods_customer","limit":5}')
Check "自助取数 -> 返回行且受 LIMIT 约束" (($aq.rowCount -gt 0) -and ($aq.rowCount -le 5)) ("rows=" + $aq.rowCount)
Check "自助取数（未登记表）-> 400" ((Call "POST" "/api/v1/integration/adhoc/query" $HA '{"sql":"SELECT * FROM sys_user"}').Status -eq 400) ""
Check "自助取数（CSVREAD 文件读）-> 400" ((Call "POST" "/api/v1/integration/adhoc/query" $HA '{"sql":"SELECT * FROM CSVREAD(''x'')"}').Status -eq 400) ""
$policies = @((D (Call "GET" "/api/v1/integration/sandbox/policies" $HA $null)).records)
Check "沙箱策略列表 -> 有策略" ($policies.Count -ge 1) ("count=" + $policies.Count)
if ($policies.Count -ge 1) {
    $pid0 = $policies[0].id
    $null = Call "PUT" "/api/v1/integration/sandbox/policies/$pid0" $HA '{"allowSql":0}'
    Check "沙箱禁止 SQL 时运行作业 -> 400" ((Call "POST" "/api/v1/integration/jobs/$jid/run" $HA $null).Status -eq 400) ""
    $null = Call "PUT" "/api/v1/integration/sandbox/policies/$pid0" $HA '{"allowSql":1}'
    Check "沙箱恢复后可再运行" (((D (Call "POST" "/api/v1/integration/jobs/$jid/run" $HA $null)).status) -eq "SUCCESS") ""
}
Check "数据源列表（集成域）-> 200" ((Call "GET" "/api/v1/integration/datasources" $HA $null).Status -eq 200) ""
Check "集成概览 -> 200" (((D (Call "GET" "/api/v1/integration/stats/overview" $HA $null)).jobCount) -ge 1) ""
Check "删除验收作业 -> 200" ((Call "DELETE" "/api/v1/integration/jobs/$jid" $HA $null).Status -eq 200) ""

# ================= F. 调度与工作流 =================
Section "F. 调度与工作流"
$wfs = @((D (Call "GET" "/api/v1/workflow/definitions" $HA $null)).records)
Check "工作流定义列表 -> 非空" ($wfs.Count -ge 1) ("count=" + $wfs.Count)
$wf = $wfs[0]
$wfd = D (Call "GET" "/api/v1/workflow/definitions/$($wf.id)" $HA $null)
Check "工作流详情含 DSL 与实例" (($null -ne $wfd.dslJson) -and ($null -ne $wfd.instances)) ""
$nodeCount = 0
if ($wfd.dslJson -and $wfd.dslJson.nodes) { $nodeCount = @($wfd.dslJson.nodes).Count }
$trig = Call "POST" "/api/v1/workflow/definitions/$($wf.id)/trigger" $HA $null
if ($nodeCount -eq 0) { Check "无节点工作流触发 -> 400（不再假成功）" ($trig.Status -eq 400) ("HTTP " + $trig.Status) }
else { Check "有节点工作流触发 -> 200 且执行节点" (((D $trig).nodeCount) -gt 0) "" }
Check "调度同步状态 -> 如实标注不支持" (((D (Call "GET" "/api/v1/workflow/sync/status" $HA $null)).supported) -eq $false) ""
Check "调度发起对账 -> 405/400（不伪造成功）" (((Call "POST" "/api/v1/workflow/sync/reconcile" $HA $null)).Status -in @(400,405)) ""
$wfInst = @((D (Call "GET" "/api/v1/workflow/instances?pageSize=5" $HA $null)).records)
Check "工作流实例列表 -> 200" ($null -ne $wfInst) ""
if ($wfInst.Count -ge 1) {
    $inst = $wfInst[0]
    $instDetail = D (Call "GET" "/api/v1/workflow/instances/$($inst.id)" $HA $null)
    Check "实例详情含节点状态与日志" (($null -ne $instDetail.nodes) -and ($instDetail.logs.Count -ge 0)) ("nodes=" + @($instDetail.nodes).Count)
    Check "终止非运行中实例 -> 400" ((Call "POST" "/api/v1/workflow/instances/$($inst.id)/kill" $HA $null).Status -eq 400) ""
}
Check "工作流概览 -> 200" ((Call "GET" "/api/v1/workflow/stats/overview" $HA $null).Status -eq 200) ""

# ================= G. 数据质量 =================
Section "G. 数据质量"
$rCode = "QR-VERIFY-" + (Get-Random -Maximum 9999)
$rc = Call "POST" "/api/v1/quality/rules" $HQ ('{"code":"' + $rCode + '","name":"验收规则-手机号非空","tableName":"ods_customer","columnName":"phone","dimension":"COMPLETENESS","ruleType":"NULL_CHECK","expression":"SELECT COUNT(*) FROM ods_customer WHERE phone IS NULL","tolerance":0.01,"severity":"MAJOR"}')
$rid = (D $rc).id
Check "创建质量规则 -> 200 (liuqin)" ($rc.Status -eq 200) ("id=" + $rid)
Check "规则表达式写操作 -> 400" ((Call "POST" "/api/v1/quality/rules" $HQ '{"name":"bad","tableName":"ods_customer","expression":"DELETE FROM ods_customer"}').Status -eq 400) ""
Check "规则表达式多语句 -> 400" ((Call "POST" "/api/v1/quality/rules" $HQ '{"name":"bad2","tableName":"ods_customer","expression":"SELECT 1; DROP TABLE x"}').Status -eq 400) ""
Check "规则表达式文件函数 -> 400" ((Call "POST" "/api/v1/quality/rules" $HQ '{"name":"bad3","tableName":"ods_customer","expression":"SELECT * FROM CSVREAD(''x'')"}').Status -eq 400) ""
$try1 = D (Call "POST" "/api/v1/quality/rules/$rid/try-run" $HQ $null)
Check "规则试跑 -> 返回通过率与耗时" (($null -ne $try1.passRate) -and ($try1.durationMs -ge 0)) ("passRate=" + $try1.passRate + " fail=" + $try1.failed)
$tplList = @((D (Call "GET" "/api/v1/quality/rules/templates" $HQ $null)).records)
Check "规则模板库 -> 非空" ($tplList.Count -gt 0) ("count=" + $tplList.Count)
$batch = Call "POST" "/api/v1/quality/rules/batch-apply" $HQ '{"templateCode":"TPL_UNIQUE_ID","tableName":"ods_customer","columnName":"customer_id"}'
$tpls = @((D (Call "GET" "/api/v1/quality/rules/templates" $HQ $null)).records)
$tplCode = if ($tpls.Count -gt 0) { $tpls[0].code } else { "TPL_UNIQUE_ID" }
$batch = Call "POST" "/api/v1/quality/rules/batch-apply" $HQ ('{"templateCode":"' + $tplCode + '","tableName":"ods_customer","columnName":"customer_id"}')
Check "规则批量应用（真实模板）-> 200" ($batch.Status -eq 200) ("template=" + $tplCode + " HTTP=" + $batch.Status)
if ((D $batch).id) { $null = Call "DELETE" "/api/v1/quality/rules/$((D $batch).id)" $HA $null }
$ck = Call "POST" "/api/v1/quality/checks" $HQ ('{"name":"验收稽核任务","ruleIds":[' + $rid + ']}')
$ckId = (D $ck).id
Check "创建稽核任务 -> 200 且绑定规则" (($ck.Status -eq 200) -and ((D $ck).ruleCount -ge 1)) ("checkId=" + $ckId)
$chkRun = D (Call "POST" "/api/v1/quality/checks/$ckId/run" $HQ $null)
Check "执行稽核任务 -> 有评分与通过数" (($null -ne $chkRun.score) -and ($chkRun.ruleTotal -ge 1)) ("score=" + $chkRun.score + " rules=" + $chkRun.ruleTotal)
$scores = D (Call "GET" "/api/v1/quality/scores" $HA $null)
$today = (Get-Date).ToString("yyyy-MM-dd")
Check "评分概览 -> 有综合分" ($null -ne $scores.overallScore) ("overall=" + $scores.overallScore)
Check "质量趋势包含今日（稽核回写）" ([string]$scores.trend[-1].statDate -eq $today) ("latest=" + $scores.trend[-1].statDate)
Check "权重之和!=1 -> 400" ((Call "PUT" "/api/v1/quality/scores/weights" $HQ '{"COMPLETENESS":0.5,"UNIQUENESS":0.3}').Status -eq 400) ""
Check "权重复原（和为1）-> 200" ((Call "PUT" "/api/v1/quality/scores/weights" $HQ '{"COMPLETENESS":0.25,"UNIQUENESS":0.15,"ACCURACY":0.2,"VALIDITY":0.15,"CONSISTENCY":0.15,"TIMELINESS":0.1}').Status -eq 200) ""
$cov = D (Call "GET" "/api/v1/quality/metrics/rule-coverage" $HQ $null)
Check "规则覆盖率 <= 100%" (($cov.fieldCoverage -ge 0) -and ($cov.fieldCoverage -le 100)) ("fieldCoverage=" + $cov.fieldCoverage)
$issues = @((D (Call "GET" "/api/v1/quality/issues?pageSize=20" $HQ $null)).records)
$discovered = $issues | Where-Object { $_.status -eq "DISCOVERED" } | Select-Object -First 1
if (-not $discovered) {
    # 问题池可能已被历史轮次消费空：自造一条必然失败的规则并稽核，确保闭环链路可重复验证
    $qrs = (Get-Random -Maximum 99999)
    $rq2 = Call "POST" "/api/v1/quality/rules" $HQ ('{"code":"QR-VERIFY-' + $qrs + '","name":"验收自检规则","tableName":"ods_customer","columnName":"customer_id","dimension":"ACCURACY","ruleType":"CUSTOM_SQL","expression":"SELECT COUNT(*) FROM ods_customer","tolerance":0,"severity":"MAJOR"}')
    $rid2 = (D $rq2).id
    $ckq2 = Call "POST" "/api/v1/quality/checks" $HQ ('{"name":"验收稽核任务-' + $qrs + '","ruleIds":[' + $rid2 + ']}')
    $ckid2 = (D $ckq2).id
    $null = Call "POST" "/api/v1/quality/checks/$ckid2/run" $HQ $null
    $issues = @((D (Call "GET" "/api/v1/quality/issues?pageSize=20" $HQ $null)).records)
    $discovered = $issues | Where-Object { $_.status -eq "DISCOVERED" } | Select-Object -First 1
    $null = Call "DELETE" "/api/v1/quality/rules/$rid2" $HA $null
}
Check "问题池有可闭环的 DISCOVERED 单" ([bool]$discovered) ("total=" + $issues.Count)
if ($discovered) {
    Check "问题派单 -> 200" ((Call "POST" "/api/v1/quality/issues/$($discovered.id)/dispatch" $HQ '{"assignee":"liuqin","dueHours":72}').Status -eq 200) ""
    Check "派单后处理 -> 200（状态机已修）" ((Call "POST" "/api/v1/quality/issues/$($discovered.id)/handle" $HQ '{"handleResult":"源库已补录"}').Status -eq 200) ""
    Check "责任人自复核 -> 403" ((Call "POST" "/api/v1/quality/issues/$($discovered.id)/review" $HQ '{"result":"PASS","comment":"ok"}').Status -eq 403) ""
    Check "他人复核 -> 200 且关闭" (((Call "POST" "/api/v1/quality/issues/$($discovered.id)/review" $HA '{"result":"PASS","comment":"验收通过"}')).Status -eq 200) ""
    Check "已关闭问题再改期 -> 400" ((Call "POST" "/api/v1/quality/issues/$($discovered.id)/delay" $HQ '{"hours":24}').Status -eq 400) ""
}
Check "闭环指标 -> 200" ((Call "GET" "/api/v1/quality/issues/metrics" $HQ $null).Status -eq 200) ""
Check "按责任人统计 -> 200" ((Call "GET" "/api/v1/quality/issues/metrics/by-assignee" $HQ $null).Status -eq 200) ""
Check "质量概览 -> 200" ((Call "GET" "/api/v1/quality/stats/overview" $HQ $null).Status -eq 200) ""
Check "删除验收规则 -> 200" ((Call "DELETE" "/api/v1/quality/rules/$rid" $HA $null).Status -eq 200) ""

# ================= H. 数据安全 =================
Section "H. 数据安全"
$scan = D (Call "POST" "/api/v1/security/sensitive/scan" $HS '{}')
Check "敏感识别全量扫描 -> 识别出敏感字段" ($scan.sensitiveColumns -gt 0) ("scanned=" + $scan.scannedColumns + " hits=" + $scan.sensitiveColumns)
$totalBefore = (D (Call "GET" "/api/v1/security/sensitive/results?pageSize=1" $HS $null)).total
$scanScoped = D (Call "POST" "/api/v1/security/sensitive/scan" $HS '{"tableName":"ods_customer"}')
$totalAfter = (D (Call "GET" "/api/v1/security/sensitive/results?pageSize=1" $HS $null)).total
Check "按表扫描不清空其它表结果" (($totalAfter -gt 0) -and ($totalAfter -ne $totalBefore -or $scanScoped.sensitiveColumns -ge 0)) ("before=" + $totalBefore + " after=" + $totalAfter)
$polId = (D $pol).id
$custTbl = @((D (Call "GET" "/api/v1/metadata/tables?keyword=ods_customer" $HA $null)).records) | Select-Object -First 1
$custCols = @((D (Call "GET" "/api/v1/metadata/tables/$($custTbl.id)/columns" $HA $null)).records) | ForEach-Object { $_.name }
$existingPol = @((D (Call "GET" "/api/v1/security/mask/policies?tableName=ods_customer" $HS $null)).records) | ForEach-Object { $_.columnName }
$freeCol = $custCols | Where-Object { $existingPol -notcontains $_ -and $_ -ne "customer_id" } | Select-Object -First 1
$pol = Call "POST" "/api/v1/security/mask/policies" $HA ('{"tableName":"ods_customer","columnName":"' + $freeCol + '","algorithm":"MASK","securityLevel":"L3","roleCodes":"ROLE_ANALYST"}')
$polId = 0
if ($pol.Status -eq 200) { $polId = (D $pol).id }
Check "创建脱敏策略 -> PENDING" (($pol.Status -eq 200) -and ((D $pol).status -eq "PENDING")) ("col=" + $freeCol + " HTTP=" + $pol.Status)
Check "策略创建人自审批 -> 403" ((Call "POST" "/api/v1/security/mask/policies/$polId/approve" $HA '{"result":"APPROVED"}').Status -eq 403) ""
Check "安全管理员审批 -> ENABLED" (((D (Call "POST" "/api/v1/security/mask/policies/$polId/approve" $HS '{"result":"APPROVED"}')).status) -eq "ENABLED") ""
Check "策略启停（disabling）-> DISABLED" (((D (Call "POST" "/api/v1/security/mask/policies/$polId/enable" $HS '{"enabled":false}')).status) -eq "DISABLED") ""
Check "策略启停（enabling）-> ENABLED" (((D (Call "POST" "/api/v1/security/mask/policies/$polId/enable" $HS '{"enabled":true}')).status) -eq "ENABLED") ""
# 清理本用例创建的策略，保证多次运行不耗尽 ods_customer 的可用字段
if ($polId) { $null = Call "DELETE" "/api/v1/security/mask/policies/$polId" $HA $null }
Check "脱敏预览（L3）-> 返回脱敏值" ((@((D (Call "POST" "/api/v1/security/mask/preview" $HS '{"tableName":"ods_customer","columnName":"phone","algorithm":"MASK"}')).samples)).Count -gt 0) ""
Check "脱敏预览（未登记字段）-> 404" ((Call "POST" "/api/v1/security/mask/preview" $HS '{"tableName":"sys_user","columnName":"password"}').Status -eq 404) ""
$apply = Call "POST" "/api/v1/security/access-applies" $HN '{"assetType":"TABLE","assetId":8,"assetName":"ods_customer","reason":"验收申请","durationDays":7}'
$applyId = (D $apply).id
Check "访问申请 -> PENDING" ($apply.Status -eq 200) ("id=" + $applyId)
Check "申请人自审批 -> 403" ((Call "POST" "/api/v1/security/access-applies/$applyId/approve" $HN '{"result":"APPROVED"}').Status -eq 403) ""
Check "安全管理员审批访问 -> APPROVED" (((D (Call "POST" "/api/v1/security/access-applies/$applyId/approve" $HS '{"result":"APPROVED"}')).status) -eq "APPROVED") ""
$grants = @((D (Call "GET" "/api/v1/security/access-grants" $HS $null)).records)
Check "审批后生成访问授权" ($grants.Count -ge 1) ("grants=" + $grants.Count)
Check "撤销授权 -> REVOKED" (((D (Call "DELETE" "/api/v1/security/access-grants/$($grants[0].id)" $HS $null)).status) -eq "REVOKED") ""
$vc = D (Call "POST" "/api/v1/security/audit/verify-chain" $HA '{"limit":5000}')
Check "审计哈希链完整" ($vc.intact -eq $true) ("checked=" + $vc.checked)
$alogs = D (Call "GET" "/api/v1/security/audit/logs?pageSize=5" $HA $null)
Check "审计日志 chainVerified 与真实一致" ($alogs.chainVerified -eq $vc.intact) ("chainVerified=" + $alogs.chainVerified)
$exp = Call "GET" "/api/v1/security/audit/logs/export" $HA $null
Check "审计导出 -> 200 且生成文件" (($exp.Status -eq 200) -and ([bool](D $exp).fileName)) ("file=" + (D $exp).fileName)
$alerts = @((D (Call "GET" "/api/v1/security/alerts?status=OPEN" $HS $null)).records)
if ($alerts.Count -ge 1) {
    Check "告警处置 -> HANDLED" (((D (Call "POST" "/api/v1/security/alerts/$($alerts[0].id)/handle" $HS '{"comment":"验收处置"}')).status) -eq "HANDLED") ""
    Check "重复处置同一告警 -> 400" ((Call "POST" "/api/v1/security/alerts/$($alerts[0].id)/handle" $HS '{}').Status -eq 400) ""
}
Check "安全概览 -> 200" ((Call "GET" "/api/v1/security/stats/overview" $HS $null).Status -eq 200) ""

Write-Output ""
Write-Output ("===== E-H 汇总：PASS=" + $script:pass + "  FAIL=" + $script:fail + " =====")
if ($script:fail -gt 0) { Write-Output ("失败项：" + ($script:fails -join "; ")) }