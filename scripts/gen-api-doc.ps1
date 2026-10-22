param([string]$Base = "http://127.0.0.1:8080")
$ErrorActionPreference = "Stop"
$root = "C:\Users\Administrator\Documents\1\datagov"
$lg = Invoke-WebRequest -Uri "$Base/api/v1/auth/login" -Method POST -UseBasicParsing -ContentType "application/json" -Body '{"username":"admin","password":"Dgp@2026"}'
$tok = ($lg.Content | ConvertFrom-Json).data.accessToken
$apis = Invoke-WebRequest -Uri "$Base/api/v1/admin/apis" -Headers @{ Authorization = "Bearer $tok" } -UseBasicParsing
$recs = ($apis.Content | ConvertFrom-Json).data.records
$bt = [char]96
$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("# DGP 接口清单（v1.1，实时导出自 Spring MVC 注册表）")
$lines.Add("")
$lines.Add("> 生成时间：" + (Get-Date -Format "yyyy-MM-dd HH:mm:ss") + " ｜ 接口总数：**" + $recs.Count + "**")
$lines.Add("")
$lines.Add("| 方法 | 路径 | 权限编码 | 实现 |")
$lines.Add("|---|---|---|---|")
foreach ($r in ($recs | Sort-Object path)) {
    $lines.Add("| " + $r.method + " | " + $bt + $r.path + $bt + " | " + $r.perm + " | " + $r.handler + " |")
}
$out = Join-Path $root "docs\API清单.md"
[IO.File]::WriteAllText($out, ($lines -join "`r`n"), (New-Object System.Text.UTF8Encoding($false)))
Write-Output ("API doc regenerated: " + $recs.Count + " endpoints -> " + $out)
