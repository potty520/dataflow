param(
    [string]$Base = "http://127.0.0.1:8080",
    [string]$AuthDirect = "http://127.0.0.1:8081",
    [string]$CoreDirect = "http://127.0.0.1:8082",
    [string]$MetadataDirect = "http://127.0.0.1:8083",
    [string]$SecurityDirect = "http://127.0.0.1:8084",
    [string]$SearchDirect = "http://127.0.0.1:8085",
    [string]$SchedulerDirect = "http://127.0.0.1:8086",
    [string]$QualityDirect = "http://127.0.0.1:8087",
    [string]$MigrationDirect = "http://127.0.0.1:8088"
)
$ErrorActionPreference = "Continue"
$script:pass = 0
$script:fail = 0

function Call($method, $url, $headers, $body) {
    $p = @{ Uri = $url; Method = $method; UseBasicParsing = $true; TimeoutSec = 45 }
    if ($headers) { $p["Headers"] = $headers }
    if ($null -ne $body) {
        $p["Body"] = [Text.Encoding]::UTF8.GetBytes($body)
        $p["ContentType"] = "application/json"
    }
    try {
        $r = Invoke-WebRequest @p
        return [pscustomobject]@{ Status = [int]$r.StatusCode; Body = $r.Content }
    } catch {
        $resp = $_.Exception.Response
        if ($resp) {
            $s = ""
            try {
                $sr = New-Object System.IO.StreamReader($resp.GetResponseStream(), [Text.Encoding]::UTF8)
                $s = $sr.ReadToEnd()
            } catch {}
            return [pscustomobject]@{ Status = [int]$resp.StatusCode; Body = $s }
        }
        return [pscustomobject]@{ Status = -1; Body = $_.Exception.Message }
    }
}
function Check($name, $cond, $detail) {
    if ($cond) {
        $script:pass++
        Write-Output ("  PASS  " + $name + $(if ($detail) { "  [$detail]" } else { "" }))
    } else {
        $script:fail++
        Write-Output ("  FAIL  " + $name + "  ->  " + $detail)
    }
}
function J($r) { try { return ($r.Body | ConvertFrom-Json) } catch { return $null } }
function Payload($t) {
    $p = $t.Split('.')[1].Replace('-', '+').Replace('_', '/')
    switch ($p.Length % 4) { 2 { $p += '==' } 3 { $p += '=' } }
    return ([Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($p)) | ConvertFrom-Json)
}
function Header($t) {
    $p = $t.Split('.')[0].Replace('-', '+').Replace('_', '/')
    switch ($p.Length % 4) { 2 { $p += '==' } 3 { $p += '=' } }
    return ([Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($p)) | ConvertFrom-Json)
}

Write-Output "=========== 微服务冒烟（网关 $Base） ==========="

# --- 直连健康（可选，服务未起时仅记 FAIL，不中断） ---
Write-Output "----------- 直连健康探针 -----------"
foreach ($pair in @(
        @{ N = "auth"; U = "$AuthDirect/actuator/health" },
        @{ N = "core"; U = "$CoreDirect/actuator/health" },
        @{ N = "metadata"; U = "$MetadataDirect/actuator/health" },
        @{ N = "quality"; U = "$QualityDirect/actuator/health" },
        @{ N = "migration"; U = "$MigrationDirect/actuator/health" },
        @{ N = "security"; U = "$SecurityDirect/actuator/health" },
        @{ N = "search"; U = "$SearchDirect/actuator/health" },
        @{ N = "scheduler"; U = "$SchedulerDirect/actuator/health" }
    )) {
    $r = Call "GET" $pair.U $null $null
    Check ("直连 $($pair.N) health") ($r.Status -eq 200) ("HTTP $($r.Status)")
}
$gwPing = Call "GET" "$Base/api/v1/admin/ping" $null $null
Check "网关 -> core ping" ($gwPing.Status -eq 200) ("HTTP $($gwPing.Status)")

Write-Output "----------- 认证 RS256 / JWKS -----------"
$jwksGw = Call "GET" "$Base/api/v1/auth/jwks" $null $null
$jwks = J $jwksGw
Check "网关 JWKS 可达" ($jwksGw.Status -eq 200 -and $jwks.keys.Count -ge 1) ("keys=" + $jwks.keys.Count)
Check "JWKS 为 RSA" ($jwks.keys[0].kty -eq "RSA" -and $jwks.keys[0].alg -eq "RS256") ("alg=" + $jwks.keys[0].alg)

$lg = Call "POST" "$Base/api/v1/auth/login" $null '{"username":"admin","password":"Dgp@2026"}'
$ld = J $lg
$tok = $ld.data.accessToken
Check "网关登录成功" ([bool]$tok) ("HTTP $($lg.Status)")
if (-not $tok) {
    Write-Output ("登录失败，后续跳过。body=" + $lg.Body.Substring(0, [Math]::Min(200, $lg.Body.Length)))
    Write-Output ("=========== 汇总：PASS=$script:pass  FAIL=$script:fail ===========")
    exit 1
}
$hdr = Header $tok
$pay = Payload $tok
Check "JWT alg=RS256" ($hdr.alg -eq "RS256") ("alg=" + $hdr.alg)
Check "access typ=access" ($pay.typ -eq "access") ("typ=" + $pay.typ)
Check "token 含 tid" ($null -ne $pay.tid) ("tid=" + $pay.tid)
$H = @{ Authorization = "Bearer $tok" }

Write-Output "----------- 经网关业务路由 -----------"
$me = Call "GET" "$Base/api/v1/auth/me" $H $null
Check "auth/me" ($me.Status -eq 200) ("HTTP $($me.Status)")
$dash = Call "GET" "$Base/api/v1/overview/dashboard" $H $null
Check "core overview" ($dash.Status -eq 200) ("HTTP $($dash.Status)")
$meta = Call "GET" "$Base/api/v1/metadata/tables?pageSize=5" $H $null
Check "metadata 服务经网关" ($meta.Status -eq 200) ("HTTP $($meta.Status)")
$qa = Call "GET" "$Base/api/v1/quality/rules?pageSize=5" $H $null
Check "quality 服务经网关" ($qa.Status -eq 200) ("HTTP $($qa.Status)")
$mig = Call "GET" "$Base/api/v1/migration/projects?pageSize=5" $H $null
Check "migration 服务经网关" ($mig.Status -eq 200) ("HTTP $($mig.Status)")
$search = Call "GET" "$Base/api/v1/search?keyword=ods&types=TABLE&pageSize=5" $H $null
$sd = J $search
Check "search 服务" ($search.Status -eq 200) ("HTTP $($search.Status) engine=" + $sd.data.engine)
$sch = Call "GET" "$Base/api/v1/scheduler/status" $H $null
$scd = J $sch
Check "scheduler 状态" ($sch.Status -eq 200 -and [bool]$scd.data.engine) ("engine=" + $scd.data.engine + " xxl=" + $scd.data.xxlEnabled)
$chain = Call "GET" "$Base/api/v1/security/audit/chain" $H $null
$chd = J $chain
Check "security 审计链" ($chain.Status -eq 200) ("HTTP $($chain.Status) intact=" + $chd.data.intact)
$wf = Call "GET" "$Base/api/v1/workflow/sync/status" $H $null
$wfd = J $wf
Check "workflow sync 如实标注" ($wf.Status -eq 200 -and $null -ne $wfd.data.engine) ("engine=" + $wfd.data.engine + " sync=" + $wfd.data.syncStatus)

Write-Output "----------- 无令牌经网关拒绝 -----------"
$deny = Call "GET" "$Base/api/v1/metadata/tables" $null $null
Check "无 Bearer -> 401" ($deny.Status -eq 401) ("HTTP $($deny.Status)")

Write-Output "----------- 登出吊销（需 Redis 时跨实例一致） -----------"
$lg2 = J (Call "POST" "$Base/api/v1/auth/login" $null '{"username":"admin","password":"Dgp@2026"}')
$tok2 = $lg2.data.accessToken
$H2 = @{ Authorization = "Bearer $tok2" }
$null = Call "POST" "$Base/api/v1/auth/logout" $H2 $null
$after = Call "GET" "$Base/api/v1/auth/me" $H2 $null
Check "登出后 access 失效" ($after.Status -eq 401) ("HTTP $($after.Status)")

Write-Output ""
Write-Output ("=========== 汇总：PASS=" + $script:pass + "  FAIL=" + $script:fail + " ===========")
if ($script:fail -gt 0) { exit 1 } else { exit 0 }
