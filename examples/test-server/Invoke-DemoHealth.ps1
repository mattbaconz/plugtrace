#Requires -Version 5.1
<#
.SYNOPSIS
  Switch PlugDev demo between clean HEALTHY and FAILING (for screenshots / video).

.DESCRIPTION
  There is no in-game toggle. HEALTHY = no break jar + restart + ritual.
  FAILING = break jar in plugins + restart + verify.

.PARAMETER Mode
  Healthy | Failing

.EXAMPLE
  .\Invoke-DemoHealth.ps1 -Mode Healthy
  .\Invoke-DemoHealth.ps1 -Mode Failing
#>
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Healthy', 'Failing')]
    [string]$Mode,

    [string]$Cli = '@plugdev/cli@1.0.1',
    [int]$ReadyTimeoutSec = 180
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Set-Location $root

$plugins = Join-Path $root '.plugdev\run\plugins'
$fixturePattern = Join-Path $plugins 'PlugTraceFixture-*.jar'
$drop = Join-Path $PSScriptRoot 'demo-drop'
$fixtureSource = Get-ChildItem (Join-Path $drop 'PlugTraceFixture-DelayedError-*.jar') -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1

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
        if ($status -match 'Running pid') {
            Start-Sleep -Seconds 12
            return
        }
        Start-Sleep -Seconds 3
    }
    throw "Server did not become ready within ${ReadyTimeoutSec}s. Run: npx --yes $Cli server start"
}

Write-Host "=== Demo mode: $Mode ===" -ForegroundColor Yellow

if (-not (Test-Path $plugins)) {
    throw "No .plugdev/run/plugins - run Setup-DemoServer.ps1 and plug run once first."
}

# Stop if running so jar add/remove takes effect on next boot.
$statusText = (& npx --yes $Cli server status 2>&1 | ForEach-Object { "$_" }) -join "`n"
$running = $statusText -match 'Running pid'
if ($running) {
    Write-Host 'Stopping server (jar changes need a restart)...'
    Write-Host 'If plug run is open in another terminal, press Ctrl+C there first if stop fails.'
    Invoke-Plug @('server', 'stop')
    Start-Sleep -Seconds 6
}

if ($Mode -eq 'Healthy') {
    Get-ChildItem $fixturePattern -ErrorAction SilentlyContinue | ForEach-Object {
        Write-Host "Removing break jar: $($_.Name)"
        Remove-Item $_.FullName -Force
    }
    $hold = Join-Path $drop 'held-aside'
    if (Test-Path $hold) {
        Get-ChildItem $hold -Filter '*.jar' -ErrorAction SilentlyContinue | ForEach-Object {
            Write-Host "Restoring staged jar: $($_.Name)"
            Move-Item -Force $_.FullName (Join-Path $plugins $_.Name)
        }
    }
    $cfg = Join-Path $plugins 'PlugTrace\config.yml'
    if (Test-Path $cfg) {
        $cfgText = [IO.File]::ReadAllText($cfg)
        $cleaned = [regex]::Replace($cfgText, "(?m)^\s*-\s*ThumbnailBreakPlugin\s*\r?\n", '')
        if ($cleaned -ne $cfgText) {
            [IO.File]::WriteAllText($cfg, $cleaned)
            Write-Host 'Removed ThumbnailBreakPlugin from expected.plugins'
        }
    }
} else {
    if (-not $fixtureSource) {
        throw "No delayed-error fixture in $drop - run Setup-DemoServer.ps1"
    }
    Get-ChildItem $fixturePattern -ErrorAction SilentlyContinue | Remove-Item -Force
    Copy-Item $fixtureSource.FullName $plugins -Force
    Write-Host "Installed break jar: $($fixtureSource.Name)"
}

Write-Host 'Starting server (headless)...'
Invoke-Plug @('server', 'start')

Wait-ServerReady

if ($Mode -eq 'Healthy') {
    Write-Host 'Waiting for first verification window, then seeding HEALTHY...'
    Start-Sleep -Seconds 40
    # Finalize HEALTHY, then ritual order matches thumbnail #2 stack.
    Invoke-PlugCmd 'plugtrace verify run'
    Start-Sleep -Seconds 8
    Invoke-PlugCmd 'plugtrace checkpoint demo'
    Invoke-PlugCmd 'plugtrace expected capture'
    Invoke-PlugCmd 'plugtrace mark healthy'
    Invoke-PlugCmd 'plugtrace status'
    Write-Host ''
    Write-Host 'HEALTHY mode ready.' -ForegroundColor Green
    Write-Host 'Thumb #2 stack only: .\examples\test-server\Invoke-DemoThumb2.ps1'
    Write-Host 'Screenshot: plug run terminal after status, or web Overview at http://127.0.0.1:9465'
    Write-Host 'Web token: plugtrace web token create demo admin  (in plug run console, not RCON)'
} else {
    Write-Host 'Forcing FAILING verification...'
    Start-Sleep -Seconds 5
    Invoke-PlugCmd 'plugtrace verify run'
    Start-Sleep -Seconds 8
    Invoke-PlugCmd 'plugtrace status'
    Write-Host ''
    Write-Host 'FAILING mode ready.' -ForegroundColor Red
    Write-Host 'Screenshot: FAILING lines in plug run log, or refresh web Overview.'
    Write-Host 'Share URL: plugtrace share'
}

Write-Host ''
Write-Host "Flip back: .\examples\test-server\Invoke-DemoHealth.ps1 -Mode $(if ($Mode -eq 'Healthy') { 'Failing' } else { 'Healthy' })" -ForegroundColor DarkGray
