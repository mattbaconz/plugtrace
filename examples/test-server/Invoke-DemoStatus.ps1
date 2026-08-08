#Requires -Version 5.1
<#
.SYNOPSIS
  Print PlugTrace ritual status (and optional verify) via PlugDev RCON after the demo server is up.

.EXAMPLE
  .\Invoke-DemoStatus.ps1
  .\Invoke-DemoStatus.ps1 -Verify
#>
param(
    [switch]$Verify,
    [string]$Cli = '@plugdev/cli@1.0.1'
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Set-Location $root

function Invoke-PlugCmd([string]$Command) {
    Write-Host ">>> $Command" -ForegroundColor Cyan
    cmd /c "npx --yes $Cli server command `"$Command`" 2>&1"
}

$status = cmd /c "npx --yes $Cli server status 2>&1"
Write-Host $status
if ($status -notmatch 'running|ready|25565') {
    Write-Host "Server does not look ready. Start with: npx --yes $Cli run" -ForegroundColor Yellow
    exit 1
}

if ($Verify) {
    Invoke-PlugCmd 'plugtrace verify run'
    Start-Sleep -Seconds 6
}

Invoke-PlugCmd 'plugtrace status'
Invoke-PlugCmd 'plugtrace incidents'

Write-Host ''
Write-Host 'Next (FAILING sticky): plugtrace incidents ack' -ForegroundColor DarkGray
Write-Host 'Web: http://127.0.0.1:9465 (token create is console-only)' -ForegroundColor DarkGray
