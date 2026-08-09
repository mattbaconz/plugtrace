<#
.SYNOPSIS
  Build PlugTrace + demo fixtures, install PlugDev demo deps, stage drop-kit jars.

.EXAMPLE
  cd plugtrace/plugtrace
  .\examples\test-server\Setup-DemoServer.ps1
#>
param(
    [switch]$SkipDeps,
    [switch]$SkipBuild,
    [switch]$SkipClean
)

$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..\..') | Select-Object -ExpandProperty Path
Set-Location $root

. "$root\scripts\Resolve-JavaHome.ps1"
$env:JAVA_HOME = Get-PlugTraceJavaHome -Major 25
Write-Host "JAVA_HOME=$env:JAVA_HOME"

$plugdev = 'npx'
$plugdevArgs = @('--yes', '@plugdev/cli@1.0.1')

function Invoke-PlugDev {
    param(
        [Parameter(Mandatory = $true)][string[]]$PlugArgs,
        [switch]$AllowCrashAfterOk
    )
    $out = & $plugdev @plugdevArgs @PlugArgs 2>&1 | ForEach-Object { "$_" }
    $code = $LASTEXITCODE
    $out | ForEach-Object { Write-Host $_ }
    $okText = ($out -join "`n") -match 'Installed|Added to plugdev|already|✓'
    if ($code -ne 0) {
        if ($AllowCrashAfterOk -and $okText) {
            Write-Warning "plugdev exited $code after success output (known Windows uv quirk); continuing"
            return
        }
        throw "plugdev $($PlugArgs -join ' ') failed ($code)"
    }
}

Write-Host '=== PlugDev version ==='
Invoke-PlugDev -PlugArgs @('-V')

if (-not $SkipClean) {
    Write-Host '=== Clean run folder (fresh flat world) ==='
    Invoke-PlugDev -PlugArgs @('clean', '--all', '--force')
}

if (-not $SkipBuild) {
    Write-Host '=== Build PlugTrace 1.0.2 + demo fixtures ==='
    $webUi = Join-Path $root 'web-ui'
    if (-not (Test-Path (Join-Path $webUi 'node_modules'))) {
        Push-Location $webUi
        try { & pnpm install } finally { Pop-Location }
        if ($LASTEXITCODE -ne 0) { throw "pnpm install failed ($LASTEXITCODE)" }
    }
    Push-Location $webUi
    try { & pnpm run build } finally { Pop-Location }
    if ($LASTEXITCODE -ne 0) { throw "web-ui build failed ($LASTEXITCODE)" }

    & .\gradlew.bat `
        :paper-modern:jar `
        :fixtures:delayed-error:jar `
        :fixtures:command-loss:jar `
        :fixtures:missing-dependency:jar `
        -x test
    if ($LASTEXITCODE -ne 0) { throw "gradle fixture/product jars failed ($LASTEXITCODE)" }
}

$drop = Join-Path $PSScriptRoot 'demo-drop'
New-Item -ItemType Directory -Force -Path $drop | Out-Null
$fixtureMap = @{
    'delayed-error'      = 'fixtures\delayed-error\build\libs'
    'command-loss'       = 'fixtures\command-loss\build\libs'
    'missing-dependency' = 'fixtures\missing-dependency\build\libs'
}
foreach ($name in $fixtureMap.Keys) {
    $dir = Join-Path $root $fixtureMap[$name]
    $jar = Get-ChildItem $dir -Filter '*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch 'sources|javadoc' } |
        Select-Object -First 1
    if (-not $jar) { throw "Missing fixture jar for $name under $dir" }
    Copy-Item $jar.FullName (Join-Path $drop $jar.Name) -Force
    Write-Host "Drop-kit: $($jar.Name)"
}

if (-not $SkipDeps) {
    Write-Host '=== Install demo dependency plugins ==='
    # name + source (presets from `plugdev deps list`; default hangar needs -- for aliases that are modrinth-only)
    $depSpecs = @(
        @{ Name = 'luckperms'; Source = 'modrinth' },
        @{ Name = 'vault'; Source = 'hangar' },
        @{ Name = 'essentials'; Source = 'modrinth' },
        @{ Name = 'placeholderapi'; Source = 'hangar' },
        @{ Name = 'worldedit'; Source = 'hangar' },
        @{ Name = 'multiverse'; Source = 'hangar' },
        @{ Name = 'viaversion'; Source = 'hangar' }
        # spark: bundled in Paper 1.21+ / 26.x — do not deps-add
        # AutoUpdatePlugins: no PlugDev-resolved 26.1.2 channel; optional URL install below
    )
    foreach ($spec in $depSpecs) {
        Write-Host "deps add $($spec.Name) --source $($spec.Source)"
        try {
            $ErrorActionPreference = 'Continue'
            cmd /c "npx --yes @plugdev/cli@1.0.1 deps add $($spec.Name) --source $($spec.Source) 2>&1"
            $code = $LASTEXITCODE
            $ErrorActionPreference = 'Stop'
            if ($code -ne 0 -and $code -ne -1073740791) {
                throw "plugdev deps add $($spec.Name) failed ($code)"
            }
            if ($code -eq -1073740791) {
                Write-Warning "plugdev uv crash after dep add $($spec.Name); verify plugdev.yml"
            }
        } catch {
            throw
        }
    }
    Write-Host 'spark: using Paper-bundled profiler (Paper 1.21+ / 26.x)'
    # Optional soft-depend: pin a known Paper 1.21.x AutoUpdatePlugins build via URL if available
    $aupUrl = $env:PLUGTRACE_DEMO_AUP_URL
    if ($aupUrl) {
        Write-Host "deps add autoupdateplugin --source url"
        cmd /c "npx --yes @plugdev/cli@1.0.1 deps add autoupdateplugin --source url --url `"$aupUrl`" 2>&1"
    } else {
        Write-Host 'AutoUpdatePlugins: skipped (set PLUGTRACE_DEMO_AUP_URL for optional URL install)'
    }
}

Write-Host '=== Record deps from plugdev.yml ==='
Invoke-PlugDev -PlugArgs @('deps', 'list')

$ptJar = Join-Path $root 'paper-modern\build\libs\PlugTrace-1.0.2.jar'
if (-not (Test-Path $ptJar)) { throw "Missing product jar: $ptJar" }

Write-Host @"

Demo server ready to boot.

  cd $root
  `$env:JAVA_HOME = (.\scripts\Resolve-JavaHome.ps1; Get-PlugTraceJavaHome 25)
  npx --yes @plugdev/cli@1.0.1 setup --instance "FO 26.1.2"
  npx --yes @plugdev/cli@1.0.1 run

Ritual + screenshots: examples\test-server\DEMO_SERVER.md
Break jars staged in: examples\test-server\demo-drop\
"@
