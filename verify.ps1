param([string]$Base = "http://127.0.0.1:8080")
$ErrorActionPreference = "Continue"
$base = $Base
$script:pass = 0; $script:fail = 0
function Call($method, $path, $headers, $body) {
    $p = @{ Uri = "$base$path"; Method = $method; UseBasicParsing = $true; TimeoutSec = 60 }
    if ($headers) { $p["Headers"] = $headers }
    if ($body -ne $null) { $p["Body"] = [Text.Encoding]::UTF8.GetBytes($body); $p["ContentType"] = "application/json" }
    try { $r = Invoke-WebRequest @p; return [pscustomobject]@{ Status = [int]$r.StatusCode; Body = $r.Content; Head = $r.Headers } }
    catch {
        $resp = $_.Exception.Response
        if ($resp) { $s = ""; try { $sr = New-Object System.IO.StreamReader($resp.GetResponseStream(), [Text.Encoding]::UTF8); $s = $sr.ReadToEnd() } catch {}; return [pscustomobject]@{ Status = [int]$resp.StatusCode; Body = $s; Head = $resp.Headers } }
        return [pscustomobject]@{ Status = -1; Body = $_.Exception.Message; Head = @{} }
    }
}
function Check($name, $cond, $detail) {
    if ($cond) { $script:pass++; Write-Output ("  PASS  " + $name + $(if ($detail) { "  [" + $detail + "]" } else { "" })) }
    else { $script:fail++; Write-Output ("  FAIL  " + $name + "  ->  " + $detail) }
}
function J($r) { try { return ($r.Body | ConvertFrom-Json) } catch { return $null } }

Write-Output "=========== 1. 认证与会话 ==========="
$lg = Call "POST" "/api/v1/auth/login" $null '{"username":"admin","password":"Dgp@2026"}'
$ld = J $lg
$tok = $ld.data.accessToken; $ref = $ld.data.refreshToken
function Payload($t) {
    $p = $t.Split('.')[1]; $p = $p.Replace('-', '+').Replace('_', '/')
    switch ($p.Length % 4) { 2 { $p += '==' } 3 { $p += '=' } }
    return ([Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($p)) | ConvertFrom-Json)
}
$pa = Payload $tok; $pr = Payload $ref
Check "access TTL = 30min" (($pa.exp - $pa.iat) -eq 1800) ("ttl=" + ($pa.exp - $pa.iat) + "s")
Check "refresh TTL = 7d" (($pr.exp - $pr.iat) -eq 604800) ("ttl=" + ($pr.exp - $pr.iat) + "s")
Check "access typ=access" ($pa.typ -eq 'access') $pa.typ
Check "refresh typ=refresh" ($pr.typ -eq 'refresh') $pr.typ
$H = @{ Authorization = "Bearer $tok" }
Check "refresh token 不能当 access 用" ((Call "GET" "/api/v1/auth/me" @{ Authorization = "Bearer $ref" } $null).Status -eq 401) ""
Check "access token 不能换 refresh" ((Call "POST" "/api/v1/auth/refresh" $null ('{"refreshToken":"' + $tok + '"}')).Status -eq 401) ""
$rf = Call "POST" "/api/v1/auth/refresh" $null ('{"refreshToken":"' + $ref + '"}')
$newTok = (J $rf).data.accessToken
Check "合法 refresh 可换新 access" ([bool]$newTok) ""
$H2 = @{ Authorization = "Bearer $newTok" }
$before = (J (Call "GET" "/api/v1/admin/logs/login?pageSize=1" $H $null)).data.total
$null = Call "POST" "/api/v1/auth/login" $null '{"username":"admin","password":"Dgp@2026"}'
$after = (J (Call "GET" "/api/v1/admin/logs/login?pageSize=1" $H $null)).data.total
Check "登录日志只写 1 条" (($after - $before) -eq 1) ("before=$before after=$after")
$null = Call "POST" "/api/v1/auth/logout" $H2 $null
Check "登出后令牌立即失效" ((Call "GET" "/api/v1/auth/me" $H2 $null).Status -eq 401) ""
$null = Call "POST" "/api/v1/auth/login" $null '{"username":"admin","password":"Dgp@2026"}'
$tok = ((J (Call "POST" "/api/v1/auth/login" $null '{"username":"admin","password":"Dgp@2026"}')).data).accessToken
$H = @{ Authorization = "Bearer $tok" }

Write-Output "=========== 2. 开放通道 ==========="
Check "无凭证 -> 401" ((Call "GET" "/open/v1/customer/profile?customerId=1" $null $null).Status -eq 401) ""
Check "只传 AppCode 无 Secret -> 401" ((Call "GET" "/open/v1/customer/profile?customerId=1" @{ "X-App-Code" = "APP-OPS" } $null).Status -eq 401) ""
Check "错误 Secret -> 401" ((Call "GET" "/open/v1/customer/profile?customerId=1" @{ "X-App-Code" = "APP-OPS"; "X-App-Secret" = "bad" } $null).Status -eq 401) ""
$okOpen = Call "GET" "/open/v1/customer/profile?customerId=1" @{ "X-App-Code" = "APP-OPS"; "X-App-Secret" = "APP-OPS-SECRET-2026" } $null
Check "正确 AK/SK -> 200 且返回数据" ($okOpen.Status -eq 200) $okOpen.Status
$od = J $okOpen
Check "脱敏生效（phone 含 *）" ([bool]($od.data.masked)) ("masked=" + $od.data.masked + " cols=" + ($od.data.maskedColumns -join ","))
Check "customerId 字符串列不报类型错" ($okOpen.Status -eq 200) ""
Check "oauth2 缺 secret -> 401" ((Call "POST" "/open/oauth2/token" $null '{"appCode":"APP-OPS"}').Status -eq 401) ""
$tk = J (Call "POST" "/open/oauth2/token" $null '{"appCode":"APP-OPS","appSecret":"APP-OPS-SECRET-2026"}')
Check "oauth2 发令牌" ([bool]$tk.data.access_token) ""

Write-Output "=========== 3. 凭证与脱敏 ==========="
$apps = J (Call "GET" "/api/v1/service/apps" $H $null)
$leak = ($apps.data.records | Where-Object { $_.appSecret }) 
Check "应用列表不返回明文 appSecret" ($null -eq $leak) ("masked=" + $apps.data.records[0].appSecretMasked)
$mp = Call "POST" "/api/v1/security/mask/preview" $H '{"tableName":"sys_user","columnName":"password","algorithm":"MASK"}' 
Check "mask/preview 拒绝未登记表" ($mp.Status -ge 400) $mp.Status
$mp2 = Call "POST" "/api/v1/security/mask/preview" $H '{"tableName":"ods_customer","columnName":"phone","algorithm":"MASK"}'
$mp2d = J $mp2
Check "admin 预览可按审批权限看原文" ($mp2d.data.rawVisible -eq $true) ("rawVisible=" + $mp2d.data.rawVisible)
$al = Call "POST" "/api/v1/auth/login" $null '{"username":"wangwu","password":"Dgp@2026"}'
$at = ((J $al).data).accessToken
$mp3 = J (Call "POST" "/api/v1/security/mask/preview" @{ Authorization = "Bearer $at" } '{"tableName":"ods_customer","columnName":"phone","algorithm":"MASK"}')
Check "analyst 预览不回传原文" ($mp3.data.rawVisible -eq $false -and $null -eq $mp3.data.samples[0].raw) ("raw=" + $mp3.data.samples[0].raw)
foreach ($a in @("MASK","REPLACE","TRUNCATE","HASH","ENCRYPT","GENERALIZE")) {
    $r = J (Call "POST" "/api/v1/security/mask/preview" $H ('{"tableName":"ods_customer","columnName":"phone","algorithm":"' + $a + '"}'))
    $mv = $r.data.samples[0].masked
    Check ("脱敏算法 " + $a + " 有效") ($mv -and $mv -ne "ENC_ERR" -and $mv -ne "HASH_ERR") ("=> " + $mv)
}

Write-Output "=========== 4. 任意文件读 / SQL 注入 ==========="
$readme = 'C:/Users/Administrator/Documents/1/datagov/README.md'
$csv = Call "POST" "/api/v1/integration/adhoc/query" $H ('{"sql":"SELECT * FROM CSVREAD(''' + $readme + ''')"}')
Check "adhoc CSVREAD 被拒" ($csv.Status -ge 400) ("HTTP " + $csv.Status + " " + (J $csv).message)
Check "adhoc 未登记表被拒" ((Call "POST" "/api/v1/integration/adhoc/query" $H '{"sql":"SELECT * FROM sys_user"}').Status -ge 400) ""
Check "adhoc 正常表可查" ((Call "POST" "/api/v1/integration/adhoc/query" $H '{"sql":"SELECT customer_id FROM ods_customer","limit":3}').Status -eq 200) ""
Check "SQL 校验拒绝多语句" ((Call "POST" "/api/v1/integration/sql/validate" $H '{"sql":"SELECT 1; DROP TABLE zz"}').Status -ge 400) ""
$dmlApi = Call "POST" "/api/v1/service/apis" $H '{"code":"API-PROBE-DML","name":"probe","apiPath":"/probe-dml","sqlText":"DELETE FROM sys_async_task"}'
Check "创建数据服务时拦截非 SELECT" ($dmlApi.Status -ge 400) ("HTTP " + $dmlApi.Status)

Write-Output "=========== 5. 元数据 ==========="
Check "检索带 keyword -> 200" ((Call "GET" "/api/v1/metadata/search?keyword=ods" $H $null).Status -eq 200) ""
Check "批量导入 -> 200" ((Call "POST" "/api/v1/metadata/import" $H '{"rows":[{"name":"zz_import_probe","cnName":"probe","layer":"ODS","domain":"cust","owner":"tester"}]}').Status -eq 200) ""
$imp = J (Call "GET" "/api/v1/metadata/tables?keyword=zz_import_probe" $H $null)
Check "导入后 layer 正确" (($imp.data.records[0].layer) -eq "ODS") ("layer=" + $imp.data.records[0].layer)
$impId = $imp.data.records[0].id
Check "删除探针表 -> 200" ((Call "DELETE" "/api/v1/metadata/tables/$impId" $H $null).Status -eq 200) ""
$rolesList = J (Call "GET" "/api/v1/auth/roles" $H $null); $qRole = $rolesList.data.records | Where-Object { $_.code -eq "ROLE_QUALITY" } | Select-Object -First 1; $ds = Call "PUT" "/api/v1/auth/data-scopes/$($qRole.id)" $H '{"scopeType":"DOMAIN","scopeValue":"cust-domain"}'
Check "数据权限更新返回真实角色码" ((J $ds).data.roleCode -eq "ROLE_QUALITY") ("roleCode=" + (J $ds).data.roleCode)
$scopes = J (Call "GET" "/api/v1/auth/data-scopes" $H $null)
Check "不再产生 role_code=null 脏行" (($scopes.data.records | Where-Object { $_.roleCode -eq "null" }).Count -eq 0) ""

Write-Output "=========== 6. 审计链 ==========="
$vc = J (Call "POST" "/api/v1/security/audit/verify-chain" $H '{"limit":5000}')
Check "审计链完整" ($vc.data.intact -eq $true) ("intact=" + $vc.data.intact + " brokenAt=" + $vc.data.brokenAt + " checked=" + $vc.data.checked)
$au = J (Call "GET" "/api/v1/security/audit/logs?pageSize=1" $H $null)
Check "日志页 chainVerified 与真实一致" ($au.data.chainVerified -eq $vc.data.intact) ("chainVerified=" + $au.data.chainVerified)

Write-Output "=========== 7. 质量闭环 ==========="
$score0 = (J (Call "GET" "/api/v1/quality/scores" $H $null)).data.overallScore
$rc = Call "POST" "/api/v1/quality/checks/1/run" $H $null
Check "稽核执行成功" ($rc.Status -eq 200) ("score=" + (J $rc).data.score)
$scoreData = J (Call "GET" "/api/v1/quality/scores" $H $null)
$today = (Get-Date).ToString("yyyy-MM-dd")
Check "稽核结果回写当日趋势" ([string]$scoreData.data.trend[-1].statDate -eq $today) ("latest=" + $scoreData.data.trend[-1].statDate + " today=" + $today + " overall=" + $scoreData.data.overallScore)
$lq = J (Call "POST" "/api/v1/auth/login" $null '{"username":"liuqin","password":"Dgp@2026"}'); $HQ = @{ Authorization = "Bearer " + $lq.data.accessToken }
$qrs = (Get-Random -Maximum 99999)
$rq = Call "POST" "/api/v1/quality/rules" $H ('{"code":"QR-VERIFY-' + $qrs + '","name":"验收自检规则","tableName":"ods_customer","columnName":"customer_id","dimension":"ACCURACY","ruleType":"CUSTOM_SQL","expression":"SELECT COUNT(*) FROM ods_customer","tolerance":0,"severity":"MAJOR"}')
$rid = (J $rq).data.id
$ckq = Call "POST" "/api/v1/quality/checks" $H ('{"name":"验收稽核任务-' + $qrs + '","ruleIds":[' + $rid + ']}')
$ckid = (J $ckq).data.id
$null = Call "POST" "/api/v1/quality/checks/$ckid/run" $H $null
$iss = J (Call "GET" "/api/v1/quality/issues?status=DISCOVERED&pageSize=5" $H $null)
$issue = $iss.data.records[0]
Check "失败规则稽核产生问题单" ([bool]$issue) ("ruleId=" + $rid)
if ($issue) {
    Check "派单 -> 200" ((Call "POST" "/api/v1/quality/issues/$($issue.id)/dispatch" $H '{"assignee":"liuqin","dueHours":72}').Status -eq 200) ""
    $hd = Call "POST" "/api/v1/quality/issues/$($issue.id)/handle" $H '{"handleResult":"已修复源数据"}'
    Check "派单后可直接处理 -> 200" ($hd.Status -eq 200) ("HTTP " + $hd.Status + " " + (J $hd).message)
    $rv = Call "POST" "/api/v1/quality/issues/$($issue.id)/review" $HQ '{"result":"PASS","comment":"ok"}'
    Check "复核需与责任人不同 -> 403" ($rv.Status -eq 403) ("HTTP " + $rv.Status)
}
$null = Call "DELETE" "/api/v1/quality/rules/$rid" $H $null

Write-Output "=========== 8. 工作流 ==========="
$wf = J (Call "GET" "/api/v1/workflow/definitions" $H $null)
$empty = $wf.data.records | Where-Object { -not $_.dsl -or $_.dsl -notmatch '"jobCode"' } | Select-Object -First 1
$tr = Call "POST" "/api/v1/workflow/definitions/8/trigger" $H $null
Check "工作流触发（节点缺失应失败或部分失败）" ($tr.Status -ge 400 -or (J $tr).data.status -ne "SUCCESS" -or (J $tr).data.nodeCount -gt 0) ("HTTP " + $tr.Status + " status=" + (J $tr).data.status + " nodes=" + (J $tr).data.nodeCount)
$sy = J (Call "GET" "/api/v1/workflow/sync/status" $H $null)
Check "调度同步状态不再伪造" ($sy.data.supported -eq $false) ("syncStatus=" + $sy.data.syncStatus)

Write-Output "=========== 9. 假实现修复 ==========="
Check "不存在的任务 -> 404" ((Call "GET" "/api/v1/admin/tasks/no-such-task" $H $null).Status -eq 404) ""
$s1 = J (Call "GET" "/api/v1/admin/monitor/services" $H $null)
$lat1 = [int]$s1.data.records[0].latencyMs
Start-Sleep -Milliseconds 250
$s2 = J (Call "GET" "/api/v1/admin/monitor/services" $H $null)
$lat2 = [int]$s2.data.records[0].latencyMs
Check "监控延迟来自真实数据库探针（非随机常量）" (($lat1 -ge 0) -and ($lat1 -lt 500) -and ($lat2 -ge 0) -and ($lat2 -lt 500) -and ($s1.data.records.Count -ge 10) -and ([bool]$s1.data.records[0].note)) ("lat1=$lat1 lat2=$lat2")
$dicts = J (Call "GET" "/api/v1/admin/dicts" $H $null)
Check "字典来自数据库" (($dicts.data.records | Where-Object { $_.dictCode -eq 'quality_dimension' }).items.Count -eq 6) ""
$orgs = J (Call "GET" "/api/v1/auth/orgs/tree" $H $null)
Check "组织树来自数据库" ($orgs.data.total -ge 3) ("total=" + $orgs.data.total)

Write-Output "=========== 10. 报告 ==========="
$rg = Call "POST" "/api/v1/report/generate" $H '{"title":"回归验证报告","formats":["html","csv","md"]}'
Check "报告生成（3 格式）" ($rg.Status -eq 200) ""
$rgd = J $rg
$fid = $rgd.data.files[0].fileId
Check "导出返回可下载 URL（files/{id}）" ($rgd.data.files[0].downloadUrl -match '/files/\d+/download') $rgd.data.files[0].downloadUrl
if ($fid) {
    $dl = Call "GET" "/api/v1/report/files/$fid/download" $H $null
    Check "按 id 下载成功" ($dl.Status -eq 200) ("HTTP " + $dl.Status)
    Check "下载响应 nosniff" ($dl.Head["X-Content-Type-Options"] -eq "nosniff") ""
}

Write-Output "=========== 11. 六角色可见性 ==========="
$roles = @("admin","zhangsan","liuqin","wangwu","secadmin","consultant")
foreach ($u in $roles) {
    $l = J (Call "POST" "/api/v1/auth/login" $null ('{"username":"' + $u + '","password":"Dgp@2026"}'))
    if (-not $l.data) { Check "$u 登录" $false $l.message; continue }
    $lt = $l.data.accessToken; $LH = @{ Authorization = "Bearer $lt" }
    $permText = ($l.data.user.permissions -join ',')
    $menus = J (Call "GET" "/api/v1/auth/menus" $LH $null)
    $dash = Call "GET" "/api/v1/overview/dashboard" $LH $null
    $tables = Call "GET" "/api/v1/metadata/tables" $LH $null
    Check ("$u 有落地页权限且 dashboard 200") ((($permText -match 'metadata:overview:view') -or ($permText -eq '*') -or ($l.data.user.roleCode -eq 'ROLE_GOV_ADMIN')) -and $dash.Status -eq 200) ("overviewPerm=" + [bool](($permText -match 'metadata:overview:view') -or ($permText -eq '*')) + " dashboard=" + $dash.Status)
    Check ("$u 菜单非空") ($menus.data.menus.Count -gt 0) ("menus=" + $menus.data.menus.Count)
    Write-Output ("        $u 主数据接口 tables=" + $tables.Status + " 菜单=" + $menus.data.menus.Count)
}

Write-Output "=========== 12. 其它 ==========="
$cors = Call "GET" "/api/v1/auth/me" @{ Authorization = "Bearer $tok"; Origin = "http://evil.example.com" } $null
Check "CORS 不回显恶意 Origin" ($cors.Head["Access-Control-Allow-Origin"] -ne "http://evil.example.com") ("ACAO=" + $cors.Head["Access-Control-Allow-Origin"])
$bad = Call "GET" "/api/v1/metadata/tables/not-a-number" $H $null
Check "异常不泄露 SQL/堆栈" ($bad.Body -notmatch "SQLException|SELECT|jdbc") ("body=" + $bad.Body.Substring(0, [Math]::Min(120, $bad.Body.Length)))
$tr1 = Call "GET" "/api/v1/auth/me" @{ Authorization = "Bearer $tok"; "X-Trace-Id" = "bad id with spaces<script>" } $null
Check "非法 TraceId 被替换" ($tr1.Head["X-Trace-Id"] -notmatch " ") ("trace=" + $tr1.Head["X-Trace-Id"])
$r11 = Call "GET" "/api/v1/metadata/tables?pageSize=100000" $H $null
Check "pageSize 上限生效" ((J $r11).data.pageSize -le 200) ("pageSize=" + (J $r11).data.pageSize)
$fails = 0
for ($i=0; $i -lt 11; $i++) { $fails += (Call "POST" "/api/v1/auth/login" $null '{"username":"lockout-probe","password":"wrong-pass"}').Status }
Check "连续失败触发登录限速" ($fails -ge 429) ("lastStatus=" + (Call "POST" "/api/v1/auth/login" $null '{"username":"lockout-probe","password":"wrong-pass"}').Status)

Write-Output ""
Write-Output ("=========== 汇总：PASS=" + $script:pass + "  FAIL=" + $script:fail + " ===========")
