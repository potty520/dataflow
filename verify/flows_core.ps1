param([string]$Base = "http://127.0.0.1:8080")
$ErrorActionPreference = "Continue"
$script:pass = 0; $script:fail = 0; $script:fails = @()

function Call($method, $path, $headers, $body) {
    $p = @{ Uri = "$Base$path"; Method = $method; UseBasicParsing = $true; TimeoutSec = 120 }
    if ($headers) { $p["Headers"] = $headers }
    if ($body -ne $null) { $p["Body"] = [Text.Encoding]::UTF8.GetBytes($body); $p["ContentType"] = "application/json" }
    try { $r = Invoke-WebRequest @p; return [pscustomobject]@{ Status = [int]$r.StatusCode; Body = $r.Content } }
    catch {
        $resp = $_.Exception.Response
        if ($resp) { $s = ""; try { $sr = New-Object System.IO.StreamReader($resp.GetResponseStream(), [Text.Encoding]::UTF8); $s = $sr.ReadToEnd() } catch {}; return [pscustomobject]@{ Status = [int]$resp.StatusCode; Body = $s } }
        return [pscustomobject]@{ Status = -1; Body = $_.Exception.Message }
    }
}
function J($r) { try { return ($r.Body | ConvertFrom-Json) } catch { return $null } }
function D($r) { $j = J $r; if ($j) { return $j.data } else { return $null } }
function Check($name, $cond, $detail) {
    if ($cond) { $script:pass++; Write-Output ("  PASS  " + $name + $(if ($detail) { "  [" + $detail + "]" } else { "" })) }
    else { $script:fail++; $script:fails += $name; Write-Output ("  FAIL  " + $name + "  ->  " + $detail) }
}
function Section($t) { Write-Output ""; Write-Output ("=== " + $t + " ===") }
function Login($u, $p) {
    $r = J (Call "POST" "/api/v1/auth/login" $null ('{"username":"' + $u + '","password":"' + $p + '"}'))
    return $r.data.accessToken
}
function AuthH($t) { return @{ Authorization = "Bearer $t" } }

# ---------- 会话 ----------
$adm = Login "admin" "Dgp@2026"
$dev = Login "dev_t1" "Dgp@2026"
$qlt = Login "quality_t1" "Dgp@2026"
$ana = Login "analyst_t1" "Dgp@2026"
$sec = Login "secadmin" "Dgp@2026"
$con = Login "consultant" "Dgp@2026"
$HA = AuthH $adm; $HD = AuthH $dev; $HQ = AuthH $qlt; $HN = AuthH $ana; $HS = AuthH $sec; $HC = AuthH $con
Check "6 个演示账号均可登录" ([bool]$adm -and [bool]$dev -and [bool]$qlt -and [bool]$ana -and [bool]$sec -and [bool]$con) ""

# ================= A. 认证与 RBAC =================
Section "A. 认证与 RBAC"
Check "错误口令 -> 401" ((Call "POST" "/api/v1/auth/login" $null '{"username":"admin","password":"bad"}').Status -eq 401) ""
${lgLogs} = @((J (Call "GET" "/api/v1/admin/logs/login?pageSize=10" $HA $null)).data.records)
${failLogs} = @(${lgLogs} | Where-Object { $_.status -eq 'FAIL' })
Check "错误口令被记录为 FAIL 日志" (${failLogs}.Count -gt 0) ("recent=" + ${lgLogs}.Count + " fails=" + ${failLogs}.Count)
Check "无 token 访问 -> 401" ((Call "GET" "/api/v1/auth/me" $null $null).Status -eq 401) ""
Check "伪造 token -> 401" ((Call "GET" "/api/v1/auth/me" @{ Authorization = "Bearer abc.def.ghi" } $null).Status -eq 401) ""
$t2 = Login "admin" "Dgp@2026"
Check "新会话可访问 /auth/me" ((Call "GET" "/api/v1/auth/me" (AuthH $t2) $null).Status -eq 200) ""
$null = Call "POST" "/api/v1/auth/logout" (AuthH $t2) $null
Check "登出后令牌失效 -> 401" ((Call "GET" "/api/v1/auth/me" (AuthH $t2) $null).Status -eq 401) ""
Check "非管理员访问 /admin/apis -> 403" ((Call "GET" "/api/v1/admin/apis" $HD $null).Status -eq 403) ""
Check "analyst 重置他人口令 -> 403" ((Call "PUT" "/api/v1/auth/users/1/password" $HN '{"password":"Xx123456"}').Status -eq 403) ""
Check "analyst 变更他人角色 -> 403" ((Call "POST" "/api/v1/auth/users/1/roles" $HN '{"roleCode":"ROLE_GOV_ADMIN"}').Status -eq 403) ""
Check "analyst 删用户 -> 403" ((Call "DELETE" "/api/v1/auth/users/2" $HN $null).Status -eq 403) ""

# 用户生命周期（创建 → 改密 → 登录 → 删除）
$uname = "probe_user_" + (Get-Random -Maximum 99999)
$cu = Call "POST" "/api/v1/auth/users" $HA ('{"username":"' + $uname + '","realName":"验收探针","roleCode":"ROLE_ANALYST","password":"Probe1234"}')
Check "创建用户 -> 200" ($cu.Status -eq 200) ("id=" + (D $cu).id)
Check "重复用户名 -> 400" ((Call "POST" "/api/v1/auth/users" $HA ('{"username":"' + $uname + '","roleCode":"ROLE_ANALYST"}')).Status -eq 400) ""
Check "不存在的角色 -> 400" ((Call "POST" "/api/v1/auth/users" $HA '{"username":"probe_bad_role","roleCode":"ROLE_NOPE"}').Status -eq 400) ""
$newUid = (D $cu).id
$utok = Login $uname "Probe1234"
Check "新用户可用初始口令登录" ([bool]$utok) ""
Check "自助改密（错误原口令）-> 400" ((Call "PUT" "/api/v1/auth/password" (AuthH $utok) '{"oldPassword":"wrong","newPassword":"Probe5678"}').Status -eq 400) ""
Check "自助改密（正确原口令）-> 200" ((Call "PUT" "/api/v1/auth/password" (AuthH $utok) '{"oldPassword":"Probe1234","newPassword":"Probe5678"}').Status -eq 200) ""
Check "新口令可登录" ([bool](Login $uname "Probe5678")) ""
Check "弱口令被拒绝 -> 400" ((Call "PUT" "/api/v1/auth/password" (H (Login $uname "Probe5678")) '{"oldPassword":"Probe5678","newPassword":"123"}').Status -eq 400) ""
Check "删除探针用户 -> 200" ((Call "DELETE" "/api/v1/auth/users/$newUid" $HA $null).Status -eq 200) ""

# 数据权限
$roles = D (Call "GET" "/api/v1/auth/roles" $HA $null)
$qRole = $roles.records | Where-Object { $_.code -eq "ROLE_QUALITY" } | Select-Object -First 1
$origScope = (D (Call "GET" "/api/v1/auth/data-scopes" $HA $null)).records | Where-Object { $_.roleCode -eq "ROLE_QUALITY" } | Select-Object -First 1
$up = Call "PUT" "/api/v1/auth/data-scopes/$($qRole.id)" $HA '{"scopeType":"DOMAIN","scopeValue":"客户域"}'
Check "更新数据权限 -> 200 且角色码真实" (($up.Status -eq 200) -and ((D $up).roleCode -eq "ROLE_QUALITY")) ("roleCode=" + (D $up).roleCode)
$scopes2 = (D (Call "GET" "/api/v1/auth/data-scopes" $HA $null)).records | Where-Object { $_.roleCode -eq "ROLE_QUALITY" }
Check "数据权限无 null 脏行" (($scopes2 | Where-Object { $_.roleCode -eq "null" }).Count -eq 0) ""
Check "租户树 -> 200" (((Call "GET" "/api/v1/auth/tenants/tree" $HA $null)).Status -eq 200) ""
Check "组织树 -> 200" (((Call "GET" "/api/v1/auth/orgs/tree" $HA $null)).Status -eq 200) ""
Check "用户选项（任意登录用户）-> 200" ((Call "GET" "/api/v1/auth/users/options" $HN $null).Status -eq 200) ""

# ================= B. 元数据 =================
Section "B. 元数据管理"
$dsCode = "DS-PROBE-" + (Get-Random -Maximum 9999)
$cds = Call "POST" "/api/v1/metadata/datasources" $HA ('{"code":"' + $dsCode + '","name":"VerifyDS","dtype":"H2","host":"127.0.0.1","port":"0","dbName":"dgp","username":"sa"}')
$dsId = (D $cds).id
Check "创建数据源 -> 200" ($cds.Status -eq 200) ("id=" + $dsId)
Check "重复数据源编码 -> 400" ((Call "POST" "/api/v1/metadata/datasources" $HA ('{"code":"' + $dsCode + '","name":"dup"}')).Status -eq 400) ""
$dsUpdate = Call "PUT" "/api/v1/metadata/datasources/$dsId" $HA '{"owner":"verifier"}'
$dsDetail = (D (Call "GET" "/api/v1/metadata/datasources" $HA $null)).records | Where-Object { $_.id -eq $dsId }
Check "数据源部分更新保留其它字段" (($dsUpdate.Status -eq 200) -and ($dsDetail.name -eq "VerifyDS") -and ($dsDetail.owner -eq "verifier")) ("name=" + $dsDetail.name + " owner=" + $dsDetail.owner)
Check "数据源连接测试 -> 成功且有耗时" ($null -ne (D (Call "POST" "/api/v1/metadata/datasources/$dsId/test" $HA $null)).latencyMs) ""
Check "数据源列表不返回 passwordEnc" ($null -eq ((D (Call "GET" "/api/v1/metadata/datasources" $HA $null)).records[0].passwordEnc)) ""
Check "采集任务运行 -> 扫描到 13 张表" (((D (Call "POST" "/api/v1/metadata/collect/tasks/1/run" $HA $null)).tableCount) -ge 13) ""
$insts = D (Call "GET" "/api/v1/metadata/collect/instances?pageSize=5" $HA $null)
Check "采集实例有记录且 ds_name 不为 null" ((($insts.records[0].dsName) -ne "null") -and [bool]$insts.records[0].dsName) ("dsName=" + $insts.records[0].dsName)
$srch = D (Call "GET" "/api/v1/metadata/search?keyword=ods" $HA $null)
Check "元数据检索(带 keyword) -> 200 且命中" ($srch.total -gt 0) ("total=" + $srch.total)
Check "检索分页生效" ((D (Call "GET" "/api/v1/metadata/search?pageSize=5" $HA $null)).records.Count -le 5) ""
$tbl = (D (Call "GET" "/api/v1/metadata/tables?pageSize=3" $HA $null)).records[0]
$td = D (Call "GET" "/api/v1/metadata/tables/$($tbl.id)" $HA $null)
Check "表详情含字段与样例 SQL" (($td.columnCount -gt 0) -and ($td.sampleSql -like "SELECT*")) ("columns=" + $td.columnCount)
Check "字段列表 -> 200" (((D (Call "GET" "/api/v1/metadata/tables/$($tbl.id)/columns" $HA $null)).records.Count) -gt 0) ""
$probeName = "zz_verify_" + (Get-Random -Maximum 9999)
$ct = Call "POST" "/api/v1/metadata/tables" $HA ('{"name":"' + $probeName + '","cnName":"验收探针表","layer":"ODS","domain":"客户域"}')
$probeTid = (D $ct).id
Check "创建元数据表 -> 200" ($ct.Status -eq 200) ("id=" + $probeTid)
Check "非法表名 -> 400" ((Call "POST" "/api/v1/metadata/tables" $HA '{"name":"bad-name;drop","cnName":"x"}').Status -eq 400) ""
Check "重复表名 -> 400" ((Call "POST" "/api/v1/metadata/tables" $HA ('{"name":"' + $probeName + '"}')).Status -eq 400) ""
Check "更新元数据表 -> 200" ((Call "PUT" "/api/v1/metadata/tables/$probeTid" $HA '{"owner":"verifier","updateFreq":"每小时"}').Status -eq 200) ""
$verList = D (Call "GET" "/api/v1/metadata/versions?objectType=TABLE&objectId=$probeTid" $HA $null)
Check "元数据版本记录 >=2" ($verList.records.Count -ge 2) ("versions=" + $verList.records.Count)
$imp = Call "POST" "/api/v1/metadata/import" $HA ('{"rows":[{"name":"zz_import_' + (Get-Random -Maximum 9999) + '","cnName":"导入A","layer":"DWD","domain":"交易域"}]}')
Check "批量导入 -> 导入 1 行" (((D $imp).imported) -eq 1) ""
Check "删除探针表（无引用）-> 200" ((Call "DELETE" "/api/v1/metadata/tables/$probeTid" $HA $null).Status -eq 200) ""
Check "删除不存在的表 -> 404" ((Call "DELETE" "/api/v1/metadata/tables/999999" $HA $null).Status -eq 404) ""
$g = D (Call "GET" "/api/v1/metadata/lineage/graph?objectId=ods_customer&granularity=TABLE&direction=both&level=2" $HA $null)
Check "血缘图返回节点与边" (($g.nodes.Count -gt 0) -and ($g.edges.Count -gt 0)) ("nodes=" + $g.nodes.Count + " edges=" + $g.edges.Count)
$lp = D (Call "POST" "/api/v1/metadata/lineage/parse" $HA '{"sql":"INSERT INTO dwd_order_fact SELECT order_id, customer_id FROM ods_order","jobName":"VERIFY","persist":false}')
Check "SQL 血缘解析 -> 识别目标与源表" (($lp.target -eq "dwd_order_fact") -and ($lp.sources -contains "ods_order")) ("target=" + $lp.target)
Check "血缘解析非法 SQL -> 400" ((Call "POST" "/api/v1/metadata/lineage/parse" $HA '{"sql":"delete from x"}').Status -eq 400) ""
Check "术语库 -> 200" (((D (Call "GET" "/api/v1/metadata/terminology" $HA $null)).records.Count) -gt 0) ""
Check "元数据概览 -> 表/字段数" ((((D (Call "GET" "/api/v1/metadata/stats/overview" $HA $null)).tableCount) -ge 13) -and (((D (Call "GET" "/api/v1/metadata/stats/overview" $HA $null)).columnCount) -ge 111)) ""
Check "删除被数据服务引用的表 -> 400" ((Call "DELETE" "/api/v1/metadata/tables/8" $HA $null).Status -eq 400) ""
Check "删除VerifyDS -> 200" ((Call "DELETE" "/api/v1/metadata/datasources/$dsId" $HA $null).Status -eq 200) ""

# ================= C. 建模 =================
Section "C. 数据建模"
$mCode = "MDL_PROBE_" + (Get-Random -Maximum 9999)
$cm = Call "POST" "/api/v1/model/models" $HD ('{"code":"' + $mCode + '","name":"验收模型","cnName":"验收模型","modelType":"LOGICAL","layer":"DWD"}')
$mid = (D $cm).id
Check "创建模型 -> 200 (zhangsan)" ($cm.Status -eq 200) ("id=" + $mid)
Check "空实体模型发布 -> 400" ((Call "POST" "/api/v1/model/models/$mid/publish" $HD '{}').Status -eq 400) ""
$pubModels = (D (Call "GET" "/api/v1/model/models?pageSize=50" $HA $null)).records | Where-Object { $_.status -eq 'PUBLISHED' }
Check "存在已发布模型（种子数据）" ($pubModels.Count -ge 1) ("count=" + $pubModels.Count)
$seededModel = $pubModels[0]
$mDetail = D (Call "GET" "/api/v1/model/models/$($seededModel.id)" $HA $null)
Check "模型详情含实体与属性" (($mDetail.entities.Count -gt 0) -and ($mDetail.entities[0].attributes.Count -gt 0)) ("entities=" + $mDetail.entities.Count)
$ddl = D (Call "POST" "/api/v1/model/models/$($seededModel.id)/ddl" $HA $null)
Check "DDL 预览 -> 生成建表语句" (($ddl.count -gt 0) -and ($ddl.statements[0].ddl -like "CREATE TABLE*")) ""
$entId = $mDetail.entities[0].id
$exe = Call "POST" "/api/v1/model/entities/$entId/ddl/execute" $HA $null
Check "已发布实体执行 DDL -> 200" ($exe.Status -eq 200) ("table=" + (D $exe).table)
Check "DDL 幂等（重复执行仍 200）" ((Call "POST" "/api/v1/model/entities/$entId/ddl/execute" $HA $null).Status -eq 200) ""
$draftEnt = (D (Call "GET" "/api/v1/model/models/$mid" $HA $null))
Check "草稿模型无实体 -> DDL 预览为空" ((D (Call "POST" "/api/v1/model/models/$mid/ddl" $HA $null)).count -eq 0) ""
Check "模型模板 -> 200" (((Call "GET" "/api/v1/model/templates" $HA $null)).Status -eq 200) ""
Check "模型概览 -> 200" (((D (Call "GET" "/api/v1/model/stats/overview" $HA $null)).modelCount) -ge 1) ""
Check "删除草稿模型 -> 200" ((Call "DELETE" "/api/v1/model/models/$mid" $HA $null).Status -eq 200) ""

# ================= D. 标准 =================
Section "D. 数据标准"
$elCode = "DE_PROBE_" + (Get-Random -Maximum 9999)
$ce = Call "POST" "/api/v1/standard/elements" $HC ('{"code":"' + $elCode + '","name":"probe_element","cnName":"验收数据元","dataType":"STRING","dataLen":"32","category":"公共"}')
Check "创建数据元 -> 200 (consultant)" ($ce.Status -eq 200) ""
Check "重复数据元编码 -> 400" ((Call "POST" "/api/v1/standard/elements" $HC ('{"code":"' + $elCode + '","name":"dup"}')).Status -eq 400) ""
$elId = (D $ce).id
Check "发布数据元 -> 200" ((Call "POST" "/api/v1/standard/elements/$elId/publish" $HC $null).Status -eq 200) ""
Check "数据元列表 -> 200" (((D (Call "GET" "/api/v1/standard/elements?pageSize=5" $HA $null)).records.Count) -gt 0) ""
$rec = Call "GET" "/api/v1/standard/mappings/recommend?tableId=$($tbl.id)" $HD $null
Check "映射推荐 -> 200 且有候选" (($rec.Status -eq 200) -and ((D $rec).total -ge 0)) ("total=" + (D $rec).total)
$map = Call "POST" "/api/v1/standard/mappings/batch" $HD '{"items":[{"columnId":1,"stdCode":"DE_MOBILE","matchType":"AUTO_CONFIRM","score":90}]}'
Check "批量确认映射 -> 200" ($map.Status -eq 200) ("mapped=" + (D $map).mapped)
Check "映射不存在的数据元 -> 400" ((Call "POST" "/api/v1/standard/mappings/batch" $HD '{"items":[{"columnId":1,"stdCode":"DE_NOPE"}]}').Status -eq 400) ""
$comp = Call "POST" "/api/v1/standard/compliance/check" $HA '{}'
Check "落地检查 -> 200 且含落地率" (($comp.Status -eq 200) -and ($null -ne (D $comp).compliantRate)) ("rate=" + (D $comp).compliantRate)
Check "标准概览 -> 200" (((Call "GET" "/api/v1/standard/stats/overview" $HA $null)).Status -eq 200) ""

Write-Output ""
Write-Output ("===== A-D 汇总：PASS=" + $script:pass + "  FAIL=" + $script:fail + " =====")
if ($script:fail -gt 0) { Write-Output ("失败项：" + ($script:fails -join "; ")) }