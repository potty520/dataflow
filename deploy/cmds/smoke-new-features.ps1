param([string]$Base = "http://127.0.0.1:8083")
$ErrorActionPreference = "Continue"
function C($m, $p, $h, $b) {
  $x = @{ Uri = "$Base$p"; Method = $m; UseBasicParsing = $true; TimeoutSec = 120 }
  if ($h) { $x["Headers"] = $h }
  if ($b -ne $null) { $x["Body"] = [Text.Encoding]::UTF8.GetBytes($b); $x["ContentType"] = "application/json" }
  try { $r = Invoke-WebRequest @x; return @{ s = [int]$r.StatusCode; j = ($r.Content | ConvertFrom-Json) } }
  catch {
    $resp = $_.Exception.Response
    if ($resp) { $sr = New-Object System.IO.StreamReader($resp.GetResponseStream()); $txt = $sr.ReadToEnd(); try { return @{ s = [int]$resp.StatusCode; j = ($txt | ConvertFrom-Json) } } catch { return @{ s = [int]$resp.StatusCode; j = $null } } }
    return @{ s = -1; j = $null }
  }
}
function Show($label, $r) {
  $code = if ($r.j) { $r.j.code } else { "?" }
  $msg = if ($r.j) { $r.j.message } else { "" }
  Write-Host ("{0,-42} HTTP {1} code={2} {3}" -f $label, $r.s, $code, $msg)
  return $r.j
}

$lg = C "POST" "/api/v1/auth/login" $null '{"username":"admin","password":"Dgp@2026"}'
if ($lg.s -ne 200) { Write-Output "登录失败：$($lg.s)"; exit 1 }
$H = @{ Authorization = "Bearer " + $lg.j.data.accessToken }
Write-Output ("登录 OK，权限数=" + $lg.j.data.user.permissions.Count)
Write-Output ""

Write-Output "===== 流式质检 ====="
$ov = Show "概览 /quality/stream/overview" (C "GET" "/api/v1/quality/stream/overview" $H $null)
Show "健康度 /stream/health" (C "GET" "/api/v1/quality/stream/health" $H $null) | Out-Null
$src = Show "流源列表" (C "GET" "/api/v1/quality/stream/sources?pageSize=10" $H $null)
$rules = Show "规则列表" (C "GET" "/api/v1/quality/stream/rules?pageSize=10" $H $null)
if ($src -and $src.data.records.Count -gt 0) {
  # 所有流源：重置水位 + 窗口放宽到 48h，确保历史演示数据能落进首个窗口
  foreach ($s0 in $src.data.records) {
    C "POST" ("/api/v1/quality/stream/sources/" + $s0.id + "/reset-watermark") $H $null | Out-Null
    C "PUT" ("/api/v1/quality/stream/sources/" + $s0.id) $H '{"windowSeconds":172800}' | Out-Null
  }
  Show "重置水位并放宽窗口（全部流源）" @{ s = 200; j = @{ code = 0; message = "ok" } } | Out-Null
}
$run = Show "执行全部窗口 /sources/run-all" (C "POST" "/api/v1/quality/stream/sources/run-all" $H $null)
if ($run) { Write-Output ("   流源=" + $run.data.total + " 失败源=" + $run.data.failedSources) }
$res = Show "窗口结果列表" (C "GET" "/api/v1/quality/stream/results?pageSize=5" $H $null)
if ($res) { Write-Output ("   结果数=" + $res.data.total); $res.data.records | Select-Object -First 6 | ForEach-Object { Write-Output ("     - " + $_.sourceCode + " / " + $_.ruleName + " rows=" + $_.rowCount + " fail=" + $_.failCount + " " + $_.status) } }
Show "告警列表" (C "GET" "/api/v1/quality/stream/alerts?pageSize=5" $H $null) | Out-Null
$nr = C "POST" "/api/v1/quality/stream/rules" $H ('{"sourceId":' + $src.data.records[0].id + ',"code":"SR-TEST-' + (Get-Random -Maximum 9999) + '","name":"测试规则-指标非空","dimension":"COMPLETENESS","expression":"metric_value IS NULL","tolerance":0.001,"severity":"MAJOR"}')
Show "新建规则 -> 200" $nr | Out-Null
$bad = C "POST" "/api/v1/quality/stream/rules" $H ('{"sourceId":' + $src.data.records[0].id + ',"code":"SR-BAD-' + (Get-Random -Maximum 9999) + '","expression":"1=1; DELETE FROM qa_rule"}')
Show "非法条件 -> 应 4xx" $bad | Out-Null
Write-Output ""

Write-Output "===== 数据迁移 ====="
$mov = Show "概览 /migration/overview" (C "GET" "/api/v1/migration/overview" $H $null)
$projs = Show "项目列表" (C "GET" "/api/v1/migration/projects?pageSize=10" $H $null)
if ($projs -and $projs.data.records.Count -gt 0) {
  $projId = $projs.data.records[0].id
  Show "项目详情" (C "GET" ("/api/v1/migration/projects/" + $projId) $H $null) | Out-Null
  $rec = Show "映射推荐" (C "GET" ("/api/v1/migration/projects/" + $projId + "/recommend") $H $null)
  if ($rec) { Write-Output ("   候选=" + $rec.data.candidates.Count + " 匹配=" + $rec.data.matched); $rec.data.candidates | Where-Object { $_.targetColumn } | Select-Object -First 4 | ForEach-Object { Write-Output ("     - " + $_.sourceColumn + " -> " + $_.targetColumn + " (" + $_.score + ", " + $_.transformType + ")") } }
  $pre = Show "预检" (C "POST" ("/api/v1/migration/projects/" + $projId + "/precheck") $H $null)
  if ($pre) { Write-Output ("   ready=" + $pre.data.ready + " errors=" + ($pre.data.errors -join ";") + " warnings=" + ($pre.data.warnings -join ";")) }
  Show "生成 SQL" (C "GET" ("/api/v1/migration/projects/" + $projId + "/statement") $H $null) | Out-Null
  $runM = Show "执行迁移" (C "POST" ("/api/v1/migration/projects/" + $projId + "/run") $H '{"replace":1}')
  if ($runM) {
    Write-Output ("   源行数=" + $runM.data.sourceRows + " 写入=" + $runM.data.insertedRows + " 目标行数=" + $runM.data.targetRows)
    $rc = Show "对账" (C "POST" ("/api/v1/migration/runs/" + $runM.data.runId + "/reconcile") $H $null)
    if ($rc) {
      Write-Output ("   检查项=" + $rc.data.checkCount + " 失败=" + $rc.data.failedCount + " 结论=" + $rc.data.status)
      $rc.data.diffs | ForEach-Object { Write-Output ("     - " + $_.checkType + " " + $_.columnName + " 期望=" + $_.expected + " 实际=" + $_.actual + " " + $_.status + " " + $_.detail) }
    }
  }
  Show "执行记录" (C "GET" ("/api/v1/migration/projects/" + $projId + "/runs") $H $null) | Out-Null
}
Write-Output ""
Write-Output "===== 完成 ====="
