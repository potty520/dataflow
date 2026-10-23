param([string]$Base = "http://127.0.0.1:8080")
$ErrorActionPreference = "Continue"
$script:pass = 0; $script:fail = 0; $script:skip = 0; $script:fails = @()

function C($m, $p, $h, $b) {
    $x = @{ Uri = "$Base$p"; Method = $m; UseBasicParsing = $true; TimeoutSec = 180 }
    if ($h) { $x["Headers"] = $h }
    if ($b -ne $null) { $x["Body"] = [Text.Encoding]::UTF8.GetBytes($b); $x["ContentType"] = "application/json" }
    try { $r = Invoke-WebRequest @x; return @{ s = [int]$r.StatusCode; j = ($r.Content | ConvertFrom-Json) } }
    catch {
        $resp = $_.Exception.Response
        if ($resp) {
            $sr = New-Object System.IO.StreamReader($resp.GetResponseStream(), [Text.Encoding]::UTF8)
            $txt = $sr.ReadToEnd()
            try { return @{ s = [int]$resp.StatusCode; j = ($txt | ConvertFrom-Json) } } catch { return @{ s = [int]$resp.StatusCode; j = $null } }
        }
        return @{ s = -1; j = $null }
    }
}
function D($r) { if ($r.j) { return $r.j.data } else { return $null } }
function Check($name, $cond, $detail) {
    if ($cond) { $script:pass++; Write-Output ("  PASS  " + $name + $(if ($detail) { "  [" + $detail + "]" } else { "" })) }
    else { $script:fail++; $script:fails += $name; Write-Output ("  FAIL  " + $name + "  ->  " + $detail) }
}
function Skip($name, $why) { $script:skip++; Write-Output ("  SKIP  " + $name + "  ->  " + $why) }
function Section($t) { Write-Output ""; Write-Output ("=== " + $t + " ===") }
function AuthH($t) { return @{ Authorization = "Bearer $t" } }
function LoginRetry($u, $p) {
    for ($i = 0; $i -lt 4; $i++) {
        $r = C "POST" "/api/v1/auth/login" $null ('{"username":"' + $u + '","password":"' + $p + '"}')
        $tk = (D $r).accessToken
        if ($tk) { return $tk }
        Start-Sleep -Seconds 3
    }
    return $null
}
function JStr($v) { if ($null -eq $v -or "$v" -eq "") { return 'null' } else { return '"' + ("$v" -replace '"', '\"') + '"' } }

$dbPass = $env:DGP_DB_PASSWORD
$ftpPass = $env:DGP_FTP_PASS
$ftpHost = $env:DGP_FTP_HOST
if (-not $ftpHost) { $ftpHost = "127.0.0.1" }
$proxyHost = $env:DGP_SOCKS5_HOST
if (-not $proxyHost) { $proxyHost = "127.0.0.1" }
$proxyPort = $env:DGP_SOCKS5_PORT
if (-not $proxyPort) { $proxyPort = 1080 }

$tok = LoginRetry "admin" "Dgp@2026"
if (-not $tok) { Write-Output "登录失败，终止"; exit 1 }
$HA = AuthH $tok
$anaTok = LoginRetry "analyst_t1" "Dgp@2026"
$HN = AuthH $anaTok
$sfx = Get-Random -Maximum 999999
$tgtCross = "zz_cross_$sfx"
$tgtProxy = "zz_cross_proxy_$sfx"
$tgtFtp = "zz_ftp_$sfx"
$remoteFile = "dgp_verify_$sfx.csv"
$projIds = @(); $dsIds = @(); $taskIds = @()

# ================= V1. 远程数据源（直连） =================
Section "V1. 远程数据源：直连"
if (-not $dbPass) { Skip "远程数据源直连" "未设置 DGP_DB_PASSWORD" }
else {
    $dsBody = '{"code":"DS-VERIFY-' + $sfx + '","name":"验收-直连","dbType":"MYSQL","host":"127.0.0.1","port":3306,"dbName":"dgp","username":"dgp","password":"' + $dbPass + '","proxyType":"DIRECT"}'
    $ds = C "POST" "/api/v1/migration/datasources" $HA $dsBody
    Check "创建远程数据源（直连）-> 200" ($ds.s -eq 200) ("id=" + (D $ds).id)
    $dsId = (D $ds).id
    if ($dsId) { $dsIds += $dsId }
    $tst = C "POST" ("/api/v1/migration/datasources/" + $dsId + "/test") $HA $null
    $tstd = D $tst
    Check "连接测试 -> ok 且返回版本/耗时" (($tst.s -eq 200) -and ($tstd.ok -eq $true)) ("latency=" + $tstd.latencyMs + "ms version=" + $tstd.version)
    $cols = D (C "GET" ("/api/v1/migration/datasources/" + $dsId + "/columns?table=ods_customer") $HA $null)
    Check "远程字段探测 -> 含 customer_id" (($cols.records.Count -gt 0) -and (@($cols.records | Where-Object { $_.name -eq 'customer_id' }).Count -ge 1)) ("cols=" + $cols.records.Count)
    $badBody = '{"code":"DS-BAD-' + $sfx + '","name":"验收-错误口令","dbType":"MYSQL","host":"127.0.0.1","port":3306,"dbName":"dgp","username":"dgp","password":"wrong-pass","proxyType":"DIRECT"}'
    $bad = C "POST" "/api/v1/migration/datasources" $HA $badBody
    $badId = (D $bad).id
    if ($badId) { $dsIds += $badId }
    Check "错误口令连接测试 -> 4xx" ((C "POST" ("/api/v1/migration/datasources/" + $badId + "/test") $HA $null).s -ge 400) ""
}

# ================= V2. 远程数据源（SOCKS5 代理） =================
Section "V2. 远程数据源：SOCKS5 代理隧道"
$proxyDsId = $null
if (-not $dbPass) { Skip "代理隧道数据源" "未设置 DGP_DB_PASSWORD" }
else {
    $pBody = '{"code":"DS-PROXY-' + $sfx + '","name":"验收-代理","dbType":"MYSQL","host":"127.0.0.1","port":3306,"dbName":"dgp","username":"dgp","password":"' + $dbPass + '","proxyType":"SOCKS5","proxyHost":"' + $proxyHost + '","proxyPort":' + $proxyPort + '}'
    $pd = C "POST" "/api/v1/migration/datasources" $HA $pBody
    Check "创建代理数据源 -> 200" ($pd.s -eq 200) ("id=" + (D $pd).id)
    $proxyDsId = (D $pd).id
    if ($proxyDsId) { $dsIds += $proxyDsId }
    $pt = C "POST" ("/api/v1/migration/datasources/" + $proxyDsId + "/test") $HA $null
    $ptd = D $pt
    Check "经 SOCKS5 连接远端库 -> ok" (($pt.s -eq 200) -and ($ptd.ok -eq $true)) ("proxy=SOCKS5 " + $proxyHost + ":" + $proxyPort + " latency=" + $ptd.latencyMs + "ms")
}

# ================= V3. 跨库迁移：远程 → 本地 =================
Section "V3. 跨库迁移：远程数据源 → 本地"
if ($dsIds.Count -eq 0 -or -not $dbPass) { Skip "跨库迁移（直连）" "无可用远程数据源" }
else {
    $directDsId = $dsIds[0]
    $code = "MIG-CROSS-" + $sfx
    $pb = '{"code":"' + $code + '","name":"验收-跨库迁移","sourceDsId":' + $directDsId + ',"targetDsId":0,"sourceTable":"ods_customer","targetTable":"' + $tgtCross + '","mode":"FULL","replaceTarget":1}'
    $pj = C "POST" "/api/v1/migration/projects" $HA $pb
    $projId = (D $pj).id
    if ($projId) { $projIds += $projId }
    Check "创建跨库迁移项目 -> 200" ($pj.s -eq 200) ("project=" + $projId + " target=" + $tgtCross)
    # 远程字段探测（用真实字段名）
    $rcols = D (C "GET" ("/api/v1/migration/datasources/" + $directDsId + "/columns?table=ods_customer") $HA $null)
    Check "远程字段探测 -> 14 列且含 customer_id" ((@($rcols.records).Count -eq 14) -and (@($rcols.records | Where-Object { $_.name -eq 'customer_id' }).Count -eq 1)) ("cols=" + @($rcols.records).Count)
    $mapItems = @('{"sourceColumn":"customer_id","targetColumn":"customer_id","transformType":"DIRECT"}',
                  '{"sourceColumn":"cust_name","targetColumn":"cust_name","transformType":"DIRECT"}',
                  '{"sourceColumn":"gender","targetColumn":"gender","transformType":"DIRECT"}',
                  '{"sourceColumn":"phone","targetColumn":"phone","transformType":"DIRECT"}')
    Check "保存跨库映射 -> 200" ((C "POST" ("/api/v1/migration/projects/" + $projId + "/mappings") $HA ('{"mappings":[' + ($mapItems -join ",") + ']}')).s -eq 200) ("mappings=" + $mapItems.Count)
    $ct = C "POST" ("/api/v1/migration/projects/" + $projId + "/create-target") $HA $null
    Check "跨库一键建目标表 -> 200" (($ct.s -eq 200) -and ((D $ct).created -eq $true)) ""
    $rec = D (C "GET" ("/api/v1/migration/projects/" + $projId + "/recommend") $HA $null)
    Check "映射推荐（读取远程字段） -> 匹配>=3" ($rec.matched -ge 3) ("matched=" + $rec.matched)
    $pr = C "POST" ("/api/v1/migration/projects/" + $projId + "/precheck") $HA $null
    Check "跨库预检 -> ready=true 且样例来自远程" (((D $pr).ready -eq $true) -and ((D $pr).sample.Count -ge 1)) ("sample=" + (D $pr).sample.Count)
    $run = C "POST" ("/api/v1/migration/projects/" + $projId + "/run") $HA '{"replace":1}'
    $rund = D $run
    Check "跨库执行 -> cross=true 且写入>0" (($run.s -eq 200) -and ($rund.cross -eq $true) -and ($rund.insertedRows -gt 0)) ("source=" + $rund.sourceRows + " inserted=" + $rund.insertedRows)
    Check "跨库执行行数一致" ($rund.sourceRows -eq $rund.insertedRows) ("source=" + $rund.sourceRows + " inserted=" + $rund.insertedRows)
    Check "全量跨库批次回滚 -> 400" ((C "POST" ("/api/v1/migration/runs/" + $rund.runId + "/rollback") $HA $null).s -eq 400) ""
}

# ================= V4. 跨库迁移：经代理 =================
Section "V4. 跨库迁移：经 SOCKS5 代理"
if (-not $proxyDsId -or -not $dbPass) { Skip "跨库迁移（代理）" "无代理数据源" }
else {
    $code = "MIG-CROSSP-" + $sfx
    $pb = '{"code":"' + $code + '","name":"验收-跨库代理","sourceDsId":' + $proxyDsId + ',"targetDsId":0,"sourceTable":"ods_customer","targetTable":"' + $tgtProxy + '","mode":"FULL","replaceTarget":1}'
    $pj = C "POST" "/api/v1/migration/projects" $HA $pb
    $projId = (D $pj).id
    if ($projId) { $projIds += $projId }
    $mapItems = @('{"sourceColumn":"customer_id","targetColumn":"cid","transformType":"DIRECT"}',
                  '{"sourceColumn":"cust_name","targetColumn":"cname","transformType":"DIRECT"}')
    $null = C "POST" ("/api/v1/migration/projects/" + $projId + "/mappings") $HA ('{"mappings":[' + ($mapItems -join ",") + ']}')
    $null = C "POST" ("/api/v1/migration/projects/" + $projId + "/create-target") $HA $null
    $run = C "POST" ("/api/v1/migration/projects/" + $projId + "/run") $HA '{"replace":1}'
    $rund = D $run
    Check "经代理跨库执行 -> 写入>0" (($run.s -eq 200) -and ($rund.insertedRows -gt 0)) ("source=" + $rund.sourceRows + " inserted=" + $rund.insertedRows)
}

# ================= V5/V6. FTP 文件迁移（导出/导入） =================
Section "V5. FTP 文件迁移：导出（表 → CSV → FTP）"
$ftpTaskOk = $false
if (-not $ftpPass) { Skip "FTP 文件迁移" "未设置 DGP_FTP_PASS" }
else {
    $pb = '{"code":"MIG-FTP-' + $sfx + '","name":"验收-FTP迁移","sourceTable":"ods_customer","targetTable":"' + $tgtFtp + '","mode":"FULL"}'
    $pj = C "POST" "/api/v1/migration/projects" $HA $pb
    $projId = (D $pj).id
    if ($projId) { $projIds += $projId }
    $mapItems = @('{"sourceColumn":"customer_id","targetColumn":"cid","transformType":"DIRECT"}',
                  '{"sourceColumn":"cust_name","targetColumn":"cname","transformType":"DIRECT"}',
                  '{"sourceColumn":"member_level","targetColumn":"clevel","transformType":"DIRECT"}')
    $null = C "POST" ("/api/v1/migration/projects/" + $projId + "/mappings") $HA ('{"mappings":[' + ($mapItems -join ",") + ']}')
    $null = C "POST" ("/api/v1/migration/projects/" + $projId + "/create-target") $HA $null
    $expBody = '{"code":"FTP-EXP-' + $sfx + '","name":"验收-FTP导出","direction":"EXPORT","projectId":' + $projId + ',"remoteHost":"' + $ftpHost + '","remotePort":21,"remoteUser":"dgpftp","remotePassword":"' + $ftpPass + '","remoteDir":"/upload","remoteFile":"' + $remoteFile + '","proxyType":"DIRECT"}'
    $exp = C "POST" "/api/v1/migration/files/tasks" $HA $expBody
    $expId = (D $exp).id
    if ($expId) { $taskIds += $expId }
    Check "创建 FTP 导出任务 -> 200" ($exp.s -eq 200) ("task=" + $expId)
    $r = C "POST" ("/api/v1/migration/files/tasks/" + $expId + "/run") $HA $null
    $rd = D $r
    Check "FTP 导出执行 -> 行数>0 且校验和" (($r.s -eq 200) -and ($rd.rows -gt 0) -and ($rd.checksum.Length -eq 64)) ("rows=" + $rd.rows + " bytes=" + $rd.bytes)
    $ftpTaskOk = $true

    Section "V6. FTP 文件迁移：导入（FTP → CSV → 表）"
    $impBody = '{"code":"FTP-IMP-' + $sfx + '","name":"验收-FTP导入","direction":"IMPORT","projectId":' + $projId + ',"remoteHost":"' + $ftpHost + '","remotePort":21,"remoteUser":"dgpftp","remotePassword":"' + $ftpPass + '","remoteDir":"/upload","remoteFile":"' + $remoteFile + '","proxyType":"DIRECT"}'
    $imp = C "POST" "/api/v1/migration/files/tasks" $HA $impBody
    $impId = (D $imp).id
    if ($impId) { $taskIds += $impId }
    $ir = C "POST" ("/api/v1/migration/files/tasks/" + $impId + "/run") $HA $null
    $ird = D $ir
    Check "FTP 导入执行 -> 行数与导出一致" (($ir.s -eq 200) -and ($ird.rows -eq $rd.rows) -and ($ird.rows -gt 0)) ("imported=" + $ird.rows + " exported=" + $rd.rows)
    $runs = D (C "GET" ("/api/v1/migration/files/tasks/" + $impId + "/runs") $HA $null)
    Check "执行记录可查询" ($runs.records.Count -ge 1) ("runs=" + $runs.records.Count)
    Check "FTP 错误口令测试 -> 4xx" ((C "POST" "/api/v1/migration/files/test" $HA ('{"remoteHost":"' + $ftpHost + '","remotePort":21,"remoteUser":"dgpftp","remotePassword":"bad"}')).s -ge 400) ""

    Section "V7. FTP 经 SOCKS5 代理"
    $expBody2 = '{"code":"FTP-EXPP-' + $sfx + '","name":"验收-FTP代理导出","direction":"EXPORT","projectId":' + $projId + ',"remoteHost":"' + $ftpHost + '","remotePort":21,"remoteUser":"dgpftp","remotePassword":"' + $ftpPass + '","remoteDir":"/upload","remoteFile":"proxy_' + $remoteFile + '","proxyType":"SOCKS5","proxyHost":"' + $proxyHost + '","proxyPort":' + $proxyPort + '}'
    $exp2 = C "POST" "/api/v1/migration/files/tasks" $HA $expBody2
    $exp2Id = (D $exp2).id
    if ($exp2Id) { $taskIds += $exp2Id }
    $r2 = C "POST" ("/api/v1/migration/files/tasks/" + $exp2Id + "/run") $HA $null
    $r2d = D $r2
    Check "经代理导出 FTP -> 行数>0" (($r2.s -eq 200) -and ($r2d.rows -gt 0)) ("rows=" + $r2d.rows + " bytes=" + $r2d.bytes)
}

# ================= V9. TXT 文本格式 =================
Section "V9. TXT 文本格式（TXT / TXT_PIPE）"
if (-not $ftpPass) { Skip "TXT 文本格式" "未设置 DGP_FTP_PASS" }
else {
    $txtFile = "dgp_verify_$sfx.txt"
    $txtExpBody = '{"code":"FTP-TXTEXP-' + $sfx + '","name":"验收-TXT导出","direction":"EXPORT","projectId":' + $projId + ',"fileFormat":"TXT","remoteHost":"' + $ftpHost + '","remotePort":21,"remoteUser":"dgpftp","remotePassword":"' + $ftpPass + '","remoteDir":"/upload","remoteFile":"' + $txtFile + '","proxyType":"DIRECT"}'
    $te = C "POST" "/api/v1/migration/files/tasks" $HA $txtExpBody
    $teId = (D $te).id
    if ($teId) { $taskIds += $teId }
    $ter = C "POST" ("/api/v1/migration/files/tasks/" + $teId + "/run") $HA $null
    $terd = D $ter
    Check "TXT 导出（制表符）-> 行数>0 且校验和" (($ter.s -eq 200) -and ($terd.rows -gt 0) -and ($terd.checksum.Length -eq 64)) ("rows=" + $terd.rows + " bytes=" + $terd.bytes)

    $txtImpBody = '{"code":"FTP-TXTIMP-' + $sfx + '","name":"验收-TXT导入","direction":"IMPORT","projectId":' + $projId + ',"fileFormat":"TXT","remoteHost":"' + $ftpHost + '","remotePort":21,"remoteUser":"dgpftp","remotePassword":"' + $ftpPass + '","remoteDir":"/upload","remoteFile":"' + $txtFile + '","proxyType":"DIRECT"}'
    $ti = C "POST" "/api/v1/migration/files/tasks" $HA $txtImpBody
    $tiId = (D $ti).id
    if ($tiId) { $taskIds += $tiId }
    $tir = C "POST" ("/api/v1/migration/files/tasks/" + $tiId + "/run") $HA $null
    Check "TXT 导入 -> 行数与 TXT 导出一致" ((($tir.s -eq 200)) -and ((D $tir).rows -eq $terd.rows)) ("imported=" + (D $tir).rows + " exported=" + $terd.rows)

    $pipeFile = "dgp_verify_$sfx.psv"
    $pipeExpBody = '{"code":"FTP-TXPPEXP-' + $sfx + '","name":"验收-TXT竖线导出","direction":"EXPORT","projectId":' + $projId + ',"fileFormat":"TXT_PIPE","remoteHost":"' + $ftpHost + '","remotePort":21,"remoteUser":"dgpftp","remotePassword":"' + $ftpPass + '","remoteDir":"/upload","remoteFile":"' + $pipeFile + '","proxyType":"DIRECT"}'
    $pse = C "POST" "/api/v1/migration/files/tasks" $HA $pipeExpBody
    $pseId = (D $pse).id
    if ($pseId) { $taskIds += $pseId }
    $pser = C "POST" ("/api/v1/migration/files/tasks/" + $pseId + "/run") $HA $null
    $pserd = D $pser
    Check "TXT 竖线导出 -> 行数>0" (($pser.s -eq 200) -and ($pserd.rows -gt 0)) ("rows=" + $pserd.rows + " bytes=" + $pserd.bytes)

    $pipeImpBody = '{"code":"FTP-TXPIMP-' + $sfx + '","name":"验收-TXT竖线导入","direction":"IMPORT","projectId":' + $projId + ',"fileFormat":"TXT_PIPE","remoteHost":"' + $ftpHost + '","remotePort":21,"remoteUser":"dgpftp","remotePassword":"' + $ftpPass + '","remoteDir":"/upload","remoteFile":"' + $pipeFile + '","proxyType":"DIRECT"}'
    $pi = C "POST" "/api/v1/migration/files/tasks" $HA $pipeImpBody
    $piId = (D $pi).id
    if ($piId) { $taskIds += $piId }
    $pir = C "POST" ("/api/v1/migration/files/tasks/" + $piId + "/run") $HA $null
    Check "TXT 竖线导入 -> 行数一致" ((($pir.s -eq 200)) -and ((D $pir).rows -eq $pserd.rows)) ("imported=" + (D $pir).rows + " exported=" + $pserd.rows)
}

# ================= V8. 权限边界 =================
Section "V8. 权限边界"
$anaUser = "va$sfx"
$anaPwd = "Veri@2026ana"
$cu = C "POST" "/api/v1/auth/users" $HA ('{"username":"' + $anaUser + '","password":"' + $anaPwd + '","realName":"验收分析师","roleCode":"ROLE_ANALYST"}')
Check "创建临时分析师账号 -> 200" ($cu.s -eq 200) ("user=" + $anaUser)
$anaId = (D $cu).id
$anaTok2 = LoginRetry $anaUser $anaPwd
$HN2 = AuthH $anaTok2
Check "分析师访问远程数据源 -> 403" ((C "GET" "/api/v1/migration/datasources" $HN2 $null).s -eq 403) ""
Check "分析师执行文件迁移 -> 403" ((C "POST" "/api/v1/migration/files/tasks/1/run" $HN2 $null).s -eq 403) ""
if ($anaId) { $null = C "DELETE" ("/api/v1/auth/users/" + $anaId) $HA $null }

# ================= 清理测试数据 =================
Section "清理验收数据"
foreach ($t in $taskIds) { $null = C "DELETE" ("/api/v1/migration/files/tasks/" + $t) $HA $null }
foreach ($p in $projIds) { $null = C "DELETE" ("/api/v1/migration/projects/" + $p) $HA $null }
foreach ($d in $dsIds) { $null = C "DELETE" ("/api/v1/migration/datasources/" + $d) $HA $null }
Write-Output ("  已清理：tasks=" + $taskIds.Count + " projects=" + $projIds.Count + " datasources=" + $dsIds.Count)

Write-Output ""
Write-Output ("===== v1.4（FTP + 代理 + 跨网迁移）汇总：PASS=" + $script:pass + "  FAIL=" + $script:fail + "  SKIP=" + $script:skip + " =====")
if ($script:fail -gt 0) { Write-Output ("失败项：" + ($script:fails -join "; ")) }
