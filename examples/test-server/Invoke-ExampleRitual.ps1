<#
.SYNOPSIS
  Example Paper 1.21.4 smoke for PlugTrace 0.5.1 (or print PlugDev interactive steps).

.EXAMPLE
  .\examples\test-server\Invoke-ExampleRitual.ps1
  .\examples\test-server\Invoke-ExampleRitual.ps1 -WithDelayedErrorFixture
  .\examples\test-server\Invoke-ExampleRitual.ps1 -PrintPlugDevOnly
#>
param(
    [switch]$WithDelayedErrorFixture,
    [switch]$PrintPlugDevOnly,
    [int]$ObserveSeconds = 45
)

$ErrorActionPreference = 'Stop'
# .../plugtrace/examples/test-server -> .../plugtrace
$root = Resolve-Path (Join-Path $PSScriptRoot '..\..') | Select-Object -ExpandProperty Path
if (-not (Test-Path (Join-Path $root 'gradlew.bat'))) {
    throw "Could not locate PlugTrace root (gradlew.bat) from $PSScriptRoot (resolved $root)"
}

Set-Location $root
. "$root\scripts\Resolve-JavaHome.ps1"
$env:JAVA_HOME = Get-PlugTraceJavaHome -Major 21

$artifact = Join-Path $root 'paper-modern\build\libs\PlugTrace-0.5.1.jar'
$paperJar = Join-Path $root 'farm\jars\paper-1.21.4-232.jar'

function Show-PlugDevSteps {
    Write-Host @"

=== PlugDev interactive example (you test) ===
  cd $root
  `$env:JAVA_HOME = (.\scripts\Resolve-JavaHome.ps1; Get-PlugTraceJavaHome 21)
  .\gradlew.bat :paper-modern:jar -x test
  # once: npm i -g @plugdev/cli@1.0.1 && plugdev setup --instance "FO 26.1.2"
  # Paper 26.1.2 needs Temurin 25: Get-PlugTraceJavaHome 25
  plug run

Ritual:
  /plugtrace selfcheck
  /plugtrace checkpoint
  /plugtrace expected capture
  /plugtrace mark healthy
  # optional break: drop fixtures/delayed-error jar → restart → status / diff / suspect
  /plugtrace report preview
  /plugtrace web token create ops

Docs: examples\test-server\README.md
"@
}

if ($PrintPlugDevOnly) {
    Show-PlugDevSteps
    exit 0
}

if (-not (Test-Path $artifact)) {
    Write-Host "Building PlugTrace-0.5.1.jar..."
    $webUi = Join-Path $root 'web-ui'
    if (-not (Test-Path (Join-Path $webUi 'node_modules'))) {
        Write-Host "Installing web-ui dependencies (pnpm)..."
        Push-Location $webUi
        try { & pnpm install } finally { Pop-Location }
        if ($LASTEXITCODE -ne 0) { throw "pnpm install failed in web-ui ($LASTEXITCODE)" }
    }
    & .\gradlew.bat :paper-modern:jar -x test
    if ($LASTEXITCODE -ne 0) { throw "gradle :paper-modern:jar failed ($LASTEXITCODE)" }
}
if (-not (Test-Path $artifact)) { throw "Missing artifact after build: $artifact" }

if (-not (Test-Path $paperJar)) {
    Write-Host "Downloading Paper 1.21.4-232..."
    & "$root\scripts\Download-PaperFamily.ps1" -Project paper -Version 1.21.4 -Build 232
}
if (-not (Test-Path $paperJar)) { throw "Missing Paper jar: $paperJar" }

$fixtureArgs = @()
if ($WithDelayedErrorFixture) {
    Write-Host "Building delayed-error fixture..."
    & .\gradlew.bat :fixtures:delayed-error:jar -x test
    if ($LASTEXITCODE -ne 0) { throw "gradle :fixtures:delayed-error:jar failed ($LASTEXITCODE)" }
    $fixtureJar = Get-ChildItem (Join-Path $root 'fixtures\delayed-error\build\libs') -Filter '*.jar' |
        Where-Object { $_.Name -notmatch 'sources|javadoc' } |
        Select-Object -First 1
    if (-not $fixtureJar) { throw 'delayed-error fixture jar not found' }
    $fixtureArgs = @('-FixtureJars', $fixtureJar.FullName)
    Write-Host "Using fixture: $($fixtureJar.FullName)"
}

$runName = if ($WithDelayedErrorFixture) { 'example-ritual-delayed-error' } else { 'example-ritual-smoke' }
$commands = 'plugtrace selfcheck;plugtrace status'

Write-Host "=== Example ritual ephemeral smoke ($runName) ==="
& "$root\scripts\Invoke-EphemeralFarm.ps1" `
    -ServerJar $paperJar `
    -JavaMajor 21 `
    -Artifact $artifact `
    -RunName $runName `
    -ObserveSeconds $ObserveSeconds `
    -Commands $commands `
    @fixtureArgs

Write-Host ""
Write-Host "Ephemeral smoke finished. For interactive play:"
Show-PlugDevSteps
