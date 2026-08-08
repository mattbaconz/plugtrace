#Requires -Version 5.1
<#
.SYNOPSIS
  Seed HEALTHY + print the exact thumbnail #2 ritual stack for screenshots.

.DESCRIPTION
  Removes break jars, ensures PlugDev server is up, finalizes verify, then runs
  the three commands that produce the gallery middle:

    Checkpoint created for deployment #N.
    Captured expected state from deployment #N.
    - plugins=... commands=... worlds=... services=...
    Deployment #N marked HEALTHY

.EXAMPLE
  .\examples\test-server\Invoke-DemoThumb2.ps1
#>
param(
    [string]$Cli = '@plugdev/cli@1.0.1',
    [int]$ReadyTimeoutSec = 180
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Set-Location $root

$plugins = Join-Path $root '.plugdev\run\plugins'
$fixturePattern = Join-Path $plugins 'PlugTraceFixture-*.jar'

function Invoke-Plug([string[]]$PlugArgs) {
    $out = & npx --yes $Cli @PlugArgs 2>&1 | ForEach-Object { "$_" }
    $code = $LASTEXITCODE
    if ($out) { $out | ForEach-Object { Write-Host $_ } }
    if ($code -ne 0) { throw "plugdev $($PlugArgs -join ' ') failed ($code)" }
}

function Invoke-PlugCmd([string]$Command) {
    Write-Host ">>> $Command" -ForegroundColor Cyan
    & npx --yes $Cli server command $Command 2>&1 | ForEach-Object { Write-Host $_ }
}

function Wait-ServerReady {
    $deadline = (Get-Date).AddSeconds($ReadyTimeoutSec)
    while ((Get-Date) -lt $deadline) {
        $status = (& npx --yes $Cli server status 2>&1 | ForEach-Object { "$_" }) -join "`n"
        if ($status -match 'Running pid|"running":true') {
            Start-Sleep -Seconds 10
            return
        }
        Start-Sleep -Seconds 3
    }
    throw "Server not ready. From another terminal: npx --yes $Cli run"
}

if (-not (Test-Path $plugins)) {
    throw "No .plugdev/run/plugins - run Setup-DemoServer.ps1 first."
}

Write-Host '=== Thumbnail #2 ritual seed ===' -ForegroundColor Yellow

Get-ChildItem $fixturePattern -ErrorAction SilentlyContinue | ForEach-Object {
    Write-Host "Removing $($_.Name)"
    Remove-Item $_.FullName -Force
}

$st = (& npx --yes $Cli server status --json 2>&1 | ForEach-Object { "$_" }) -join "`n"
if ($st -notmatch '"running":true') {
    Write-Host 'Starting headless server...'
    Invoke-Plug @('server', 'start')
}
Wait-ServerReady

Write-Host 'Finalizing HEALTHY, then ritual (screenshot these next lines)...' -ForegroundColor Green
Invoke-PlugCmd 'plugtrace verify run'
Start-Sleep -Seconds 8

Write-Host ''
Write-Host '---------- THUMB #2 MIDDLE (capture from here) ----------' -ForegroundColor Magenta
Invoke-PlugCmd 'plugtrace checkpoint demo'
Invoke-PlugCmd 'plugtrace expected capture'
Invoke-PlugCmd 'plugtrace mark healthy'
Write-Host '---------- end capture ----------' -ForegroundColor Magenta
Write-Host ''

Write-Host 'In plug run for nicer gradients, clear the log then paste:' -ForegroundColor DarkGray
Write-Host '  plugtrace checkpoint demo'
Write-Host '  plugtrace expected capture'
Write-Host '  plugtrace mark healthy'
Write-Host ''
Write-Host 'Title: Checkpoint before you break it'
Write-Host 'Subtitle: Install before the incident.'
