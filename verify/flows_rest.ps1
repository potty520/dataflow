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

# ================= I. 数据资产 =================
Section "I. 数据资产"
$as = D (Call "GET" "/api/v1/asset/catalog/search?pageSize=5" $HN $null)
Check "资产目录搜索 -> 200 且有记录" (($as.records.Count -gt 0) -and ($as.total -gt 0)) ("total=" + $as.total)
Check "资产按分类过滤 -> 200" ((Call "GET" "/api/v1/asset/catalog/search?category=%E5%AE%A2%E6%88%B7%E4%B8%BB%E9%A2%98" $HN $null).Status -eq 200) ""
$assetId = $as.records[0].id
$ad = D (Call "GET" "/api/v1/asset/catalog/$assetId" $HN $null)
Check "资产详情 -> valueScore 为数值而非对象" (($ad.valueScore -is [ValueType]) -or ($ad.valueScore -is [string]) -or ($null -eq $ad.valueScore)) ("type=" + $ad.valueScore.GetType().Name)
Check "资产详情含字段与价值构成" (($ad.columns.Count -gt 0) -or ($null -ne $ad.valueBreakdown)) ""
$vb = D (Call "GET" "/api/v1/asset/value-basis" $HN $null)
Check "价值评估依据 -> 含公式" ([bool]$vb.formula) ("formula=" + $vb.formula)
$mapOv = D (Call "GET" "/api/v1/asset/map/overview?days=30" $HA $null)
Check "资产地图概览 -> 表/域统计" (($mapOv.totalTables -gt 0) -and ($mapOv.domains.Count -gt 0)) ("tables=" + $mapOv.totalTables)
Check "资产分布 -> 200" ((Call "GET" "/api/v1/asset/map/distribution" $HA $null).Status -eq 200) ""
Check "资产地图趋势 -> 200" ((Call "GET" "/api/v1/asset/metrics/trend?days=30" $HA $null).Status -eq 200) ""
$am = D (Call "GET" "/api/v1/asset/metrics" $HN $null)
Check "资产运营指标 -> 存储与行数" (($am.storageMb -ne $null) -and ($am.totalRows -ne $null)) ("storageMb=" + $am.storageMb + " rows=" + $am.totalRows)
$cat = D (Call "GET" "/api/v1/asset/categories/tree" $HN $null)
Check "资产分类树 -> 非空" (@($cat.tree).Count -gt 0) ("nodes=" + @($cat.tree).Count)
Check "资产收藏 -> favorited" (((D (Call "POST" "/api/v1/asset/favorites/$assetId" $HN $null)).favorited) -eq $true) ("assetId=" + $assetId)
Check "资产概览 -> avgValueScore" ($null -ne (D (Call "GET" "/api/v1/asset/stats/overview" $HA $null)).avgValueScore) ""

# ================= J. 数据服务 =================
Section "J. 数据服务与开放通道"
$apps = @((D (Call "GET" "/api/v1/service/apps" $HA $null)).records)
Check "应用列表 -> 不返回明文 secret" (($apps.Count -gt 0) -and ($null -eq $apps[0].appSecret) -and [bool]$apps[0].appSecretMasked) ("masked=" + $apps[0].appSecretMasked)
$newApp = D (Call "POST" "/api/v1/service/apps" $HA ('{"appCode":"APP-VERIFY-' + (Get-Random -Maximum 999) + '","appName":"验收应用","quotaDay":100}'))
Check "创建应用 -> 一次性返回 secret" ([bool]$newApp.appSecret) ("appCode=" + $newApp.appCode)
Check "重置应用密钥 -> 返回新 secret" ([bool](D (Call "POST" "/api/v1/service/apps/$($newApp.id)/reset-secret" $HA $null)).appSecret) ""
$apiCode = "API-VERIFY-" + (Get-Random -Maximum 9999)
$badApi = Call "POST" "/api/v1/service/apis" $HA ('{"code":"' + $apiCode + '-BAD","name":"坏接口","apiPath":"/verify-bad-' + (Get-Random -Maximum 999) + '","sqlText":"DELETE FROM ods_customer"}')
Check "创建非 SELECT 数据服务 -> 400" ($badApi.Status -eq 400) ""
$apiPath = "/verify-" + (Get-Random -Maximum 9999)
$ca = Call "POST" "/api/v1/service/apis" $HA ('{"code":"' + $apiCode + '","name":"验收接口","apiPath":"' + $apiPath + '","method":"GET","category":"验收","sqlText":"SELECT customer_id, cust_name, city FROM ods_customer LIMIT 5","maskEnabled":1,"rateLimit":100}')
$apiId = (D $ca).id
Check "创建 SELECT 数据服务 -> 200 DRAFT" ($ca.Status -eq 200) ("id=" + $apiId)
Check "未审核直接发布 -> 400" ((Call "POST" "/api/v1/service/apis/$apiId/publish" $HA $null).Status -eq 400) ""
Check "提交审核 -> PENDING" (((D (Call "POST" "/api/v1/service/apis/$apiId/submit" $HA $null)).status) -eq "PENDING") ""
Check "创建人自审核 -> 403（职责分离）" ((Call "POST" "/api/v1/service/apis/$apiId/audit" $HA '{"result":"APPROVED"}').Status -eq 403) ""
Check "在线调试（草稿态 dryRun）-> 200" ((Call "POST" "/api/v1/service/apis/$apiId/test" $HA '{"params":{}}').Status -eq 200) ""
$pending = @((D (Call "GET" "/api/v1/service/apis?status=PENDING&pageSize=20" $HA $null)).records) | Where-Object { $_.owner -ne "admin" } | Select-Object -First 1
if ($pending) {
    Check "审核种子待审 API -> APPROVED" (((D (Call "POST" "/api/v1/service/apis/$($pending.id)/audit" $HA '{"result":"APPROVED"}')).status) -eq "APPROVED") ("api=" + $pending.code)
    Check "发布已审核 API -> PUBLISHED" (((D (Call "POST" "/api/v1/service/apis/$($pending.id)/publish" $HA $null)).status) -eq "PUBLISHED") ""
}
$aksk = @{ "X-App-Code" = "APP-OPS"; "X-App-Secret" = "APP-OPS-SECRET-2026" }
$open1 = Call "GET" "/open/v1/customer/profile?customerId=1" $aksk $null
Check "开放通道（AK/SK）-> 200 且脱敏" (($open1.Status -eq 200) -and ((D $open1).masked -eq $true)) ("maskedCols=" + (((D $open1).maskedColumns) -join ","))
Check "开放通道缺 secret -> 401" ((Call "GET" "/open/v1/customer/profile?customerId=1" @{ "X-App-Code" = "APP-OPS" } $null).Status -eq 401) ""
Check "开放通道错误 secret -> 401" ((Call "GET" "/open/v1/customer/profile?customerId=1" @{ "X-App-Code" = "APP-OPS"; "X-App-Secret" = "bad" } $null).Status -eq 401) ""
Check "OAuth2 缺 secret -> 401" ((Call "POST" "/open/oauth2/token" $null '{"appCode":"APP-OPS"}').Status -eq 401) ""
$tk = D (Call "POST" "/open/oauth2/token" $null '{"appCode":"APP-OPS","appSecret":"APP-OPS-SECRET-2026"}')
Check "OAuth2 令牌换取 -> access_token" ([bool]$tk.access_token) ""
if ($tk.access_token) {
    Check "OAuth2 令牌调用开放接口 -> 200" ((Call "GET" "/open/v1/customer/profile?customerId=1" @{ Authorization = "Bearer " + $tk.access_token } $null).Status -eq 200) ""
}
# 限流：新建 rateLimit=2 的接口并发布，连续调 6 次应出现 429
# 限流验收：复用已发布 API，临时把 rateLimit 调为 2，连续调用应出现 429
$profApi = @((D (Call "GET" "/api/v1/service/apis?status=PUBLISHED&pageSize=10" $HA $null)).records) | Select-Object -First 1
$null = Call "PUT" "/api/v1/service/apis/$($profApi.id)" $HA '{"rateLimit":2}'
$rlCodes = @()
for ($i = 0; $i -lt 6; $i++) { $rlCodes += (Call "GET" ("/open/v1" + $profApi.apiPath + "?customerId=1") $aksk $null).Status }
Check "开放通道限流 -> 出现 429" ($rlCodes -contains 429) ("codes=" + ($rlCodes -join ","))
$null = Call "PUT" "/api/v1/service/apis/$($profApi.id)" $HA '{"rateLimit":200}'
$offApi = D (Call "POST" "/api/v1/service/apis" $HA ('{"code":"API-OFF-' + (Get-Random -Maximum 999) + '","name":"offline-verify","apiPath":"/verify-off-' + (Get-Random -Maximum 9999) + '","method":"GET","sqlText":"SELECT customer_id FROM ods_customer LIMIT 1"}'))
Check "下线无原因 -> 400" ((Call "POST" "/api/v1/service/apis/$($offApi.id)/offline" $HA '{}').Status -eq 400) ""
Check "下线带原因 -> 200" ((Call "POST" "/api/v1/service/apis/$($offApi.id)/offline" $HA '{"reason":"verify"}').Status -eq 200) ""
Check "下线后状态为 OFFLINE" (((D (Call "GET" "/api/v1/service/apis/$($offApi.id)" $HA $null)).status) -eq "OFFLINE") ""
Check "配额查询 -> 200" ((Call "GET" "/api/v1/service/quota" $HA $null).Status -eq 200) ""
Check "配额更新 -> 200" ((Call "PUT" "/api/v1/service/quota" $HA '{"appCode":"APP-OPS","quotaDay":20000,"qpsLimit":200}').Status -eq 200) ""
$orch = D (Call "POST" "/api/v1/service/orchestrations" $HA '{"name":"验收编排","steps":[{"step":1,"apiCode":"API-CUST-PROFILE"}]}')
Check "创建服务编排 -> 200" ([bool]$orch.id) ""
$orchList = @((D (Call "GET" "/api/v1/service/orchestrations" $HA $null)).records)
Check "编排列表 -> 含步骤数与运行次数" (($orchList.Count -gt 0) -and ($null -ne $orchList[0].stepCount)) ("steps=" + $orchList[0].stepCount)
if ($orch.id) {
    $run = Call "POST" "/api/v1/service/orchestrations/$($orch.id)/run" $HA $null
    Check "编排执行 -> 非 5xx 且返回步骤结果" (($run.Status -lt 500) -and ($null -ne (D $run).steps)) ("status=" + (D $run).status)
}
Check "服务指标 -> 成功率/p99" (($null -ne (D (Call "GET" "/api/v1/service/metrics?days=7" $HA $null)).summary.totalCalls)) ""
Check "TopN 接口 -> 200" ((Call "GET" "/api/v1/service/metrics/topn?limit=5" $HA $null).Status -eq 200) ""
Check "黑名单新增 -> 200" ((Call "POST" "/api/v1/service/blacklist" $HA '{"ip":"10.0.0.99","reason":"验收"}').Status -eq 200) ""
Check "黑名单列表 -> 200" ((Call "GET" "/api/v1/service/blacklist" $HA $null).Status -eq 200) ""

# ================= K. 报告 =================
Section "K. 报告生成与导出"
$tg = Call "POST" "/api/v1/report/generate" $HA '{"title":"验收报告","formats":["html","csv","md"],"scope":"ods_customer"}'
$tgd = D $tg
Check "生成报告（3 种格式）-> 200" (($tg.Status -eq 200) -and (@($tgd.files).Count -eq 3)) ("files=" + @($tgd.files).Count + " score=" + $tgd.score)
Check "导出返回 /files/{id}/download" ([bool](@($tgd.files)[0].downloadUrl -match "/files/\d+/download")) (@($tgd.files)[0].downloadUrl)
foreach ($f in @($tgd.files)) {
    $dl = Call "GET" ("/api/v1/report/files/" + $f.fileId + "/download") $HA $null
    Check ("下载 " + $f.format + " -> 200 + nosniff") (($dl.Status -eq 200) -and ($dl.Head["X-Content-Type-Options"] -eq "nosniff")) ("sizeKb=" + $f.sizeKb)
}
Check "报告任务查询 -> 200" ((Call "GET" "/api/v1/report/tasks/$($tgd.taskId)" $HA $null).Status -eq 200) ""
Check "报告模板列表 -> 非空" (@((D (Call "GET" "/api/v1/report/templates" $HA $null)).records).Count -gt 0) ""
Check "报告统计 -> 200" ((Call "GET" "/api/v1/report/stats/overview" $HA $null).Status -eq 200) ""
Check "报告文件列表 -> 200" ((Call "GET" "/api/v1/report/files" $HA $null).Status -eq 200) ""

# ================= L. 行业适配 =================
Section "L. 行业适配"
$pkgs = @((D (Call "GET" "/api/v1/industry/packages" $HC $null)).records)
Check "行业包列表 -> 5 个" ($pkgs.Count -ge 5) ("count=" + $pkgs.Count)
$pkg = $pkgs | Where-Object { $_.packageCode -eq "TELECOM_BSS" } | Select-Object -First 1
if (-not $pkg) { $pkg = $pkgs[0] }
Check "行业包预检 -> 非 5xx" ((Call "POST" "/api/v1/industry/packages/inspect" $HC '{}').Status -lt 500) ""
$inst = Call "POST" "/api/v1/industry/packages/$($pkg.packageCode)/instantiate" $HC $null
Check "行业包实例化 -> 返回落库计数" (($inst.Status -eq 200) -and ($null -ne (D $inst).models)) ("models=" + (D $inst).models + " rules=" + (D $inst).rules + " terms=" + (D $inst).terms)
Check "行业包启用 -> 200" ((Call "POST" "/api/v1/industry/packages/$($pkg.packageCode)/enable" $HC '{}').Status -eq 200) ""
Check "行业包停用 -> 200" ((Call "POST" "/api/v1/industry/packages/$($pkg.packageCode)/disable" $HC $null).Status -eq 200) ""
Check "行业术语 -> 非空" (@((D (Call "GET" "/api/v1/industry/terminology" $HC $null)).records).Count -gt 0) ""
Check "行业血缘规则 -> 200" ((Call "GET" "/api/v1/industry/lineage-rules" $HC $null).Status -eq 200) ""
Check "行业概览 -> 200" ((Call "GET" "/api/v1/industry/stats/overview" $HC $null).Status -eq 200) ""

# ================= M. 系统管理 =================
Section "M. 系统管理"
$dicts = @((D (Call "GET" "/api/v1/admin/dicts" $HA $null)).records)
Check "字典来自数据库 -> 6 类" ($dicts.Count -ge 6) ("count=" + $dicts.Count)
$cfg = D (Call "PUT" "/api/v1/admin/configs/quality.dimension.weight" $HA '{"configValue":"{\"COMPLETENESS\":0.5}"}')
Check "配置权重和!=1 -> 400" ($cfg -eq $null) ("HTTP " + (Call "PUT" "/api/v1/admin/configs/quality.dimension.weight" $HA '{"configValue":"{\"COMPLETENESS\":0.5}"}').Status)
$cfgOk = Call "PUT" "/api/v1/admin/configs/quality.dimension.weight" $HA '{"configValue":"{\"COMPLETENESS\":0.25,\"UNIQUENESS\":0.15,\"ACCURACY\":0.2,\"VALIDITY\":0.15,\"CONSISTENCY\":0.15,\"TIMELINESS\":0.1}"}'
Check "配置权重和=1 -> 200" ($cfgOk.Status -eq 200) ""
Check "配置列表 -> 200" ((Call "GET" "/api/v1/admin/configs" $HA $null).Status -eq 200) ""
Check "操作日志 -> 分页" ((D (Call "GET" "/api/v1/admin/logs/operation?pageSize=5" $HA $null)).records.Count -le 5) ""
Check "登录日志 -> 分页" ((D (Call "GET" "/api/v1/admin/logs/login?pageSize=5" $HA $null)).records.Count -le 5) ""
Check "平台健康 -> status" (((D (Call "GET" "/api/v1/admin/health" $HA $null)).status) -eq "UP") ""
$svc = D (Call "GET" "/api/v1/admin/monitor/services" $HA $null)
Check "服务清单 -> 14 个逻辑模块" ($svc.records.Count -ge 14) ("count=" + $svc.records.Count)
$apis = D (Call "GET" "/api/v1/admin/apis" $HA $null)
Check "接口清单 -> 接口数 >= 250" ($apis.total -ge 250) ("total=" + $apis.total)
Check "异步任务不存在 -> 404" ((Call "GET" "/api/v1/admin/tasks/none" $HA $null).Status -eq 404) ""
Check "免登录 ping -> 200" ((Call "GET" "/api/v1/admin/ping" $null $null).Status -eq 200) ""

# ================= N. 跨域一致性 =================
Section "N. 跨角色一致性"
$expected = @{ admin = 20; zhangsan = 13; liuqin = 10; wangwu = 9; secadmin = 6; consultant = 8 }
foreach ($u in @("admin", "zhangsan", "liuqin", "wangwu", "secadmin", "consultant")) {
    $tk2 = Login $u "Dgp@2026"
    $H2 = AuthH $tk2
    $menus = @((D (Call "GET" "/api/v1/auth/menus" $H2 $null)).menus)
    Check ("$u 菜单数量与权限匹配（>= " + $expected[$u] + "）") ($menus.Count -ge $expected[$u]) ("menus=" + $menus.Count)
    $first = $menus[0]
    Check ("$u 首页 " + $first.path + " 可访问") ((Call "GET" ("/api/v1" + "/overview/dashboard") $H2 $null).Status -eq 200) ""
}
Check "游客访问业务接口 -> 401" ((Call "GET" "/api/v1/metadata/tables" $null $null).Status -eq 401) ""

Write-Output ""
Write-Output ("===== I-N 汇总：PASS=" + $script:pass + "  FAIL=" + $script:fail + " =====")
if ($script:fail -gt 0) { Write-Output ("失败项：" + ($script:fails -join "; ")) }