#Requires -Version 5.1
<#
.SYNOPSIS
  Seed FAILING + /plugtrace share for thumbnail #3 screenshots.

.DESCRIPTION
  Installs delayed-error fixture (JAR delta) and adds a fake expected plugin in
  PlugTrace config so verify hits critical expected-plugins FAIL (fixture issues
  alone are often ONGOING after dogfood; PlugDev deps also reinstall WorldEdit).

  Title: Share like a spark link
  Subtitle: Redacted report. You choose when to share.

.EXAMPLE
  .\examples\test-server\Invoke-DemoThumb3.ps1
#>
param(
    [string]$Cli = '@plugdev/cli@1.0.1',
    [int]$ReadyTimeoutSec = 180
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
    & npx --yes $Cli server command $Command 2>&1 | ForEach-Object { Write-Host $_ }
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

function Set-ThumbBreakExpected([bool]$Enable) {
    if (-not (Test-Path $cfg)) { throw "Missing $cfg - boot once first." }
    $text = [IO.File]::ReadAllText($cfg)
    # Strip prior thumb break lines
    $text = [regex]::Replace($text, "(?m)^\s*-\s*$([regex]::Escape($breakPlugin))\s*\r?\n", '')
    if ($Enable) {
        if ($text -match '(?ms)(expected:\s*\r?\n\s*plugins:\s*\r?\n)(\s*commands:)') {
            $text = $text -replace '(?ms)(expected:\s*\r?\n\s*plugins:\s*\r?\n)', "`$1  - $breakPlugin`r`n"
        } elseif ($text -match '(?ms)expected:\s*\r?\n\s*plugins:\s*\[\s*\]') {
            $text = $text -replace 'plugins:\s*\[\s*\]', "plugins:`r`n  - $breakPlugin"
        } else {
            throw "Could not patch expected.plugins in $cfg"
        }
    }
    [IO.File]::WriteAllText($cfg, $text)
}

if (-not (Test-Path $plugins)) {
    throw "No .plugdev/run/plugins - run Setup-DemoServer.ps1 first."
}
if (-not $fixtureSource) {
    throw "No delayed-error fixture in $drop - run Setup-DemoServer.ps1"
}

Write-Host '=== Thumbnail #3 (FAILING + share) ===' -ForegroundColor Yellow

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

Get-ChildItem $fixturePattern -ErrorAction SilentlyContinue | Remove-Item -Force
Copy-Item $fixtureSource.FullName $plugins -Force
Write-Host "Installed break jar: $($fixtureSource.Name)"

Set-ThumbBreakExpected $true
Write-Host "Patched expected.plugins += $breakPlugin"

# Restore any WorldEdit held-aside from older thumb3 attempts
$hold = Join-Path $drop 'held-aside'
if (Test-Path $hold) {
    Get-ChildItem $hold -Filter '*.jar' -ErrorAction SilentlyContinue | ForEach-Object {
        Move-Item -Force $_.FullName (Join-Path $plugins $_.Name)
        Write-Host "Restored $($_.Name) from held-aside"
    }
}

Remove-Item (Join-Path $root '.plugdev\run\.reload-trigger') -Force -ErrorAction SilentlyContinue

Write-Host 'Starting headless server...'
Invoke-Plug @('server', 'start')
Wait-ServerReady

Write-Host 'Forcing FAILING verification...'
Start-Sleep -Seconds 6
Invoke-PlugCmd 'plugtrace reload'
Start-Sleep -Seconds 2
Invoke-PlugCmd 'plugtrace verify run'
Start-Sleep -Seconds 12

Write-Host ''
Write-Host '---------- THUMB #3 MIDDLE (capture from here) ----------' -ForegroundColor Magenta
Invoke-PlugCmd 'plugtrace status'
Write-Host ''
Invoke-PlugCmd 'plugtrace share'
Write-Host '---------- end capture ----------' -ForegroundColor Magenta
Write-Host ''

Write-Host 'In plug run for nicer gradients, clear then paste:' -ForegroundColor DarkGray
Write-Host '  plugtrace status'
Write-Host '  plugtrace share'
Write-Host ''
Write-Host 'Open the share URL for the hosted FAILING hero.'
Write-Host 'Title: Share like a spark link'
Write-Host 'Subtitle: Redacted report. You choose when to share.'
Write-Host ''
Write-Host 'Restore HEALTHY: .\examples\test-server\Invoke-DemoHealth.ps1 -Mode Healthy'
Write-Host "(clears fixture + removes $breakPlugin from expected.plugins)"
