param(
    [string]$Base = "http://127.0.0.1:8080"
)
$ErrorActionPreference = "Continue"
$script:issues = @()

function Call($method, $path, $headers, $body) {
    $p = @{ Uri = "$Base$path"; Method = $method; UseBasicParsing = $true; TimeoutSec = 40 }
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

$users = @("admin", "dev_t1", "quality_t1", "analyst_t1", "secadmin", "consultant")
$sessions = @{}
foreach ($u in $users) {
    $lg = J (Call "POST" "/api/v1/auth/login" $null ('{"username":"' + $u + '","password":"Dgp@2026"}'))
    if (-not $lg -or -not $lg.data) { Write-Output "LOGIN FAILED: $u"; exit 1 }
    $perms = @($lg.data.user.permissions)
    $sessions[$u] = [pscustomobject]@{
        Token = $lg.data.accessToken
        Perms = $perms
        IsAdmin = ($perms -contains "*") -or ($lg.data.user.roleCode -eq "ROLE_GOV_ADMIN")
    }
}
Write-Output "登录 6 个角色：OK"

# 取接口清单
$apiDoc = J (Call "GET" "/api/v1/admin/apis" @{ Authorization = "Bearer $($sessions['admin'].Token)" } $null)
$apis = @($apiDoc.data.records | Where-Object { $_.method -eq "GET" -and $_.path -like "/api/v1/*" })
Write-Output ("GET 接口数：" + $apis.Count)

# 占位符替换策略
function ResolvePath($p) {
    $path = $p
    $path = $path -replace "\{taskId\}", "T-1"
    $path = $path -replace "\{nodeKey\}", "n1"
    $path = $path -replace "\{key\}", "platform.version"
    $path = $path -replace "\{code\}", "TELECOM_BSS"
    $path = $path -replace "\{objectType\}", "TABLE"
    $path = $path -replace "\{id\}", "1"
    $path = $path -replace "\{[A-Za-z]+\}", "1"
    return $path
}

$skip = @("/api/v1/integration/jobs/{id}/logs/stream", "/api/v1/security/audit/logs/export")
$results = @()
foreach ($u in $users) {
    $s = $sessions[$u]
    $h = @{ Authorization = "Bearer $($s.Token)" }
    $stat = @{ ok = 0; denied = 0; other4xx = 0; err5xx = 0; netfail = 0 }
    $bad = @()
    foreach ($api in $apis) {
        if ($skip -contains $api.path) { continue }
        $path = ResolvePath $api.path
        $perm = [string]$api.perm
        # 权限标识都带冒号（module:resource:action）；不带冒号的表示"登录即可访问"，任意登录用户都可访问
        $loginOnly = ($perm -eq "" -or $perm -eq "null" -or -not $perm.Contains(":"))
        $expectAllowed = $s.IsAdmin -or $loginOnly -or ($s.Perms -contains $perm)
        $r = Call "GET" $path $h $null
        $st = $r.Status
        if ($st -ge 200 -and $st -lt 300) {
            $stat.ok++
            if (-not $expectAllowed) {
                $bad += [pscustomobject]@{ Kind = "AUTHZ_HOLE"; Path = $api.path; Status = $st; Perm = $perm }
            }
        } elseif ($st -eq 403) {
            $stat.denied++
            if ($expectAllowed) {
                $bad += [pscustomobject]@{ Kind = "UNEXPECTED_403"; Path = $api.path; Status = $st; Perm = $perm }
            }
        } elseif ($st -ge 500) {
            $stat.err5xx++
            $bad += [pscustomobject]@{ Kind = "5XX"; Path = $api.path; Status = $st; Perm = $perm }
        } elseif ($st -eq -1) {
            $stat.netfail++
            $bad += [pscustomobject]@{ Kind = "NETFAIL"; Path = $api.path; Status = $st; Perm = $perm }
        } else {
            $stat.other4xx++
        }
    }
    Write-Output ""
    Write-Output ("=== {0}：200={1} 403={2} 其它4xx={3} 5xx={4} 网络失败={5}" -f $u, $stat.ok, $stat.denied, $stat.other4xx, $stat.err5xx, $stat.netfail)
    if ($bad.Count -gt 0) {
        $bad | Group-Object Kind | ForEach-Object {
            Write-Output ("  [" + $_.Name + "] " + $_.Count + " 项")
            $_.Group | Select-Object -First 12 | ForEach-Object { Write-Output ("     {0} {1}  perm={2}" -f $_.Status, $_.Path, $_.Perm) }
        }
        $script:issues += $bad
    }
}

Write-Output ""
Write-Output ("===== 合计异常：" + $script:issues.Count + " =====")
if ($script:issues.Count -gt 0) {
    $script:issues | Group-Object Kind | ForEach-Object { Write-Output ("  " + $_.Name + ": " + $_.Count) }
}
