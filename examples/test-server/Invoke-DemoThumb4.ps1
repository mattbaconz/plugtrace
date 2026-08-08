#Requires -Version 5.1
<#
.SYNOPSIS
  Seed local Overview (:9465) for thumbnail #4 - web HEALTHY then FAILING.

.DESCRIPTION
  Thumbnail #4 middle is real Overview screenshots (not console):
    HEALTHY Overview  ->  red arrow  ->  FAILING Overview

  Title: Did this update work?
  Subtitle: After every risky restart.

.PARAMETER Phase
  Healthy | Failing | Both (default Both: Healthy, wait for Enter, then Failing)

.EXAMPLE
  .\examples\test-server\Invoke-DemoThumb4.ps1
  .\examples\test-server\Invoke-DemoThumb4.ps1 -Phase Healthy
  .\examples\test-server\Invoke-DemoThumb4.ps1 -Phase Failing
#>
param(
    [ValidateSet('Healthy', 'Failing', 'Both')]
    [string]$Phase = 'Both',

    [string]$Cli = '@plugdev/cli@1.0.1',
    [int]$ReadyTimeoutSec = 180,
    [switch]$SkipPause
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Set-Location $root

$plugins = Join-Path $root '.plugdev\run\plugins'
$drop = Join-Path $PSScriptRoot 'demo-drop'
$cfg = Join-Path $plugins 'PlugTrace\config.yml'
$fixturePattern = Join-Path $plugins 'PlugTraceFixture-*.jar'
$fixtureSource = Get-ChildItem (Join-Path $drop 'PlugTraceFixture-DelayedError-*.jar') -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
$breakPlugin = 'ThumbnailBreakPlugin'
$tokenFile = Join-Path $drop 'thumb4-web-token.txt'

function Invoke-Plug([string[]]$PlugArgs) {
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $out = & npx --yes $Cli @PlugArgs 2>&1
    $code = $LASTEXITCODE
    $ErrorActionPreference = $prev
    $out | ForEach-Object { Write-Host $_ }
    $text = ($out | ForEach-Object { "$_" }) -join "`n"
    if ($code -ne 0 -and $text -notmatch '"ok"\s*:\s*true') {
        throw "plugdev $($PlugArgs -join ' ') failed ($code)"
    }
}

function Invoke-PlugCmd([string]$Command) {
    Write-Host ">>> $Command" -ForegroundColor Cyan
    $out = & npx --yes $Cli server command $Command 2>&1
    $out | ForEach-Object { Write-Host $_ }
    return ($out | ForEach-Object { "$_" }) -join "`n"
}

function Wait-ServerReady {
    $deadline = (Get-Date).AddSeconds($ReadyTimeoutSec)
    while ((Get-Date) -lt $deadline) {
        $status = (& npx --yes $Cli server status 2>&1 | ForEach-Object { "$_" }) -join "`n"
        if ($status -match 'Running pid|"running":true') {
            Start-Sleep -Seconds 12
            return
        }
        Start-Sleep -Seconds 3
    }
    throw "Server not ready. From another terminal: npx --yes $Cli run"
}

function Stop-DemoServer {
    $st = (& npx --yes $Cli server status --json 2>&1 | ForEach-Object { "$_" }) -join "`n"
    if ($st -match '"running":true') {
        Write-Host 'Stopping server...'
        Invoke-Plug @('server', 'stop')
        Start-Sleep -Seconds 6
    }
    Get-CimInstance Win32_Process -Filter "name='java.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -match '\.plugdev\\run\\server\.jar' } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
    Start-Sleep -Seconds 2
}

function Set-ThumbBreakExpected([bool]$Enable) {
    if (-not (Test-Path $cfg)) { throw "Missing $cfg - boot once first." }
    $text = [IO.File]::ReadAllText($cfg)
    $text = [regex]::Replace($text, "(?m)^\s*-\s*$([regex]::Escape($breakPlugin))\s*\r?\n", '')
    if ($Enable) {
        if ($text -match '(?ms)expected:\s*\r?\n\s*plugins:\s*\[\s*\]') {
            $text = $text -replace 'plugins:\s*\[\s*\]', "plugins:`r`n  - $breakPlugin"
        } elseif ($text -match '(?ms)(expected:\s*\r?\n\s*plugins:\s*\r?\n)') {
            $text = $text -replace '(?ms)(expected:\s*\r?\n\s*plugins:\s*\r?\n)', "`$1  - $breakPlugin`r`n"
        } else {
            throw "Could not patch expected.plugins in $cfg"
        }
    }
    [IO.File]::WriteAllText($cfg, $text)
}

function Ensure-WebToken {
    $raw = Invoke-PlugCmd 'plugtrace web token create thumb4 admin'
    $plain = $raw -replace [char]27, '' -replace '\[[0-9;]*m', ''
    if ($plain -match 'shown once\)\s*:\s*(\S+)') {
        $token = $Matches[1].Trim()
        [IO.File]::WriteAllText($tokenFile, $token)
        Write-Host ''
        Write-Host 'WEB TOKEN (paste into Overview login):' -ForegroundColor Green
        Write-Host $token -ForegroundColor Yellow
        Write-Host "Saved: $tokenFile"
        return $token
    }
    Write-Host 'Could not parse token from output - create manually:' -ForegroundColor Yellow
    Write-Host '  plugtrace web token create thumb4 admin'
    return $null
}

function Show-OverviewHints([string]$HealthLabel) {
    Write-Host ''
    Write-Host "---------- THUMB #4 - $HealthLabel Overview ----------" -ForegroundColor Magenta
    Write-Host 'Open:  http://127.0.0.1:9465' -ForegroundColor Cyan
    Write-Host 'Tab:   Overview (Did this update work?)'
    if (Test-Path $tokenFile) {
        Write-Host "Token: $((Get-Content $tokenFile -Raw).Trim())" -ForegroundColor Yellow
    }
    Write-Host 'Screenshot the Overview hero only (full browser chrome off if you can).'
    Write-Host '----------------------------------------------------' -ForegroundColor Magenta
}

function Seed-Healthy {
    Write-Host '=== Thumb #4 phase: HEALTHY Overview ===' -ForegroundColor Green
    Stop-DemoServer
    Get-ChildItem $fixturePattern -ErrorAction SilentlyContinue | ForEach-Object {
        Write-Host "Removing $($_.Name)"; Remove-Item $_.FullName -Force
    }
    Set-ThumbBreakExpected $false
    $hold = Join-Path $drop 'held-aside'
    if (Test-Path $hold) {
        Get-ChildItem $hold -Filter '*.jar' -ErrorAction SilentlyContinue | ForEach-Object {
            Move-Item -Force $_.FullName (Join-Path $plugins $_.Name)
        }
    }
    Remove-Item (Join-Path $root '.plugdev\run\.reload-trigger') -Force -ErrorAction SilentlyContinue
    Invoke-Plug @('server', 'start')
    Wait-ServerReady
    Invoke-PlugCmd 'plugtrace verify run' | Out-Null
    Start-Sleep -Seconds 10
    Invoke-PlugCmd 'plugtrace expected capture' | Out-Null
    Invoke-PlugCmd 'plugtrace mark healthy' | Out-Null
    Invoke-PlugCmd 'plugtrace checkpoint thumb4' | Out-Null
    Invoke-PlugCmd 'plugtrace status' | Out-Null
    Ensure-WebToken | Out-Null
    Show-OverviewHints 'HEALTHY'
}

function Seed-Failing {
    Write-Host '=== Thumb #4 phase: FAILING Overview ===' -ForegroundColor Red
    if (-not $fixtureSource) { throw "No delayed-error fixture in $drop - run Setup-DemoServer.ps1" }
    Stop-DemoServer
    Get-ChildItem $fixturePattern -ErrorAction SilentlyContinue | Remove-Item -Force
    Copy-Item $fixtureSource.FullName $plugins -Force
    Write-Host "Installed $($fixtureSource.Name)"
    Set-ThumbBreakExpected $true
    Write-Host "Patched expected.plugins += $breakPlugin"
    Remove-Item (Join-Path $root '.plugdev\run\.reload-trigger') -Force -ErrorAction SilentlyContinue
    Invoke-Plug @('server', 'start')
    Wait-ServerReady
    Invoke-PlugCmd 'plugtrace reload' | Out-Null
    Start-Sleep -Seconds 2
    Invoke-PlugCmd 'plugtrace verify run' | Out-Null
    Start-Sleep -Seconds 12
    Invoke-PlugCmd 'plugtrace status' | Out-Null
    if (-not (Test-Path $tokenFile)) { Ensure-WebToken | Out-Null }
    Show-OverviewHints 'FAILING'
    Write-Host 'Refresh the Overview tab after login (same token).' -ForegroundColor DarkGray
}

if (-not (Test-Path $plugins)) {
    throw "No .plugdev/run/plugins - run Setup-DemoServer.ps1 first."
}

Write-Host 'Thumbnail #4: web Overview before/after' -ForegroundColor Yellow
Write-Host 'Title: Did this update work?'
Write-Host 'Subtitle: After every risky restart.'
Write-Host ''

switch ($Phase) {
    'Healthy' { Seed-Healthy }
    'Failing' { Seed-Failing }
    'Both' {
        Seed-Healthy
        if (-not $SkipPause) {
            Write-Host ''
            Write-Host 'Screenshot HEALTHY Overview now, then press Enter for FAILING...' -ForegroundColor Yellow
            [void][Console]::ReadLine()
        }
        Seed-Failing
    }
}

Write-Host ''
Write-Host "Compose: HEALTHY shot | red arrow (after update) | FAILING shot"
Write-Host 'Restore later: .\examples\test-server\Invoke-DemoHealth.ps1 -Mode Healthy'
