param(
    [string]$Base = "http://127.0.0.1:8080",
    [switch]$Full
)
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root

Write-Output "=== 1) 微服务冒烟 ==="
& "$PSScriptRoot\smoke_microservices.ps1" -Base $Base
if ($LASTEXITCODE -ne 0) { throw "smoke_microservices 失败" }

Write-Output ""
Write-Output "=== 2) 根目录 verify.ps1 冒烟 ==="
& "$root\verify.ps1" -Base $Base
if ($LASTEXITCODE -ne 0) { Write-Warning "verify.ps1 存在 FAIL，请查看上方输出" }

if ($Full) {
    Write-Output ""
    Write-Output "=== 3) 全量 flows（较慢）==="
    foreach ($s in @(
            "flows_core.ps1", "flows_ext.ps1", "flows_rest.ps1",
            "flows_features.ps1", "flows_tenant.ps1", "flows_agg.ps1"
        )) {
        Write-Output "---- $s ----"
        & "$PSScriptRoot\$s" -Base $Base
    }
}

Write-Output ""
Write-Output "网关验收套件结束。Base=$Base"
