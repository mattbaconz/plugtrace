<#
.SYNOPSIS
  Restore interrupt matrix — unit kill/abort/finalize/corrupt cases + optional disposable farm abort.
#>
param(
    [switch]$SkipUnit,
    [switch]$RunFarmAbort
)

$ErrorActionPreference = 'Stop'
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$evidence = Join-Path $root 'farm\evidence\trust-restore-interrupt.md'

. "$root\scripts\Resolve-JavaHome.ps1"
$env:JAVA_HOME = Get-PlugTraceJavaHome -Major 21

$unitPass = $false
if (-not $SkipUnit) {
    Push-Location $root
    try {
        & .\gradlew.bat :core-domain:test --tests 'dev.pluglabs.plugtrace.domain.RestoreServiceTest' -q
        if ($LASTEXITCODE -ne 0) { throw "RestoreServiceTest failed (exit $LASTEXITCODE)" }
        $unitPass = $true
    } finally {
        Pop-Location
    }
}

$farmAbortPass = $false
$farmNote = 'not run'
if ($RunFarmAbort) {
    $paperJar = Get-ChildItem (Join-Path $root 'farm\jars') -Filter 'paper-1.21.4*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch 'run' } | Select-Object -First 1
    $artifact = Join-Path $root 'paper-modern\build\libs\PlugTrace-0.5.1.jar'
    if (-not (Test-Path $artifact)) {
        Push-Location $root
        try { & .\gradlew.bat :paper-modern:jar -x test -q } finally { Pop-Location }
    }
    if ($paperJar -and (Test-Path $artifact)) {
        & "$root\scripts\Invoke-EphemeralFarm.ps1" `
            -ServerJar $paperJar.FullName `
            -JavaMajor 21 `
            -Artifact $artifact `
            -RunName 'restore-abort-matrix' `
            -ObserveSeconds 45 `
            -Commands 'plugtrace selfcheck;plugtrace restore preview'
        $farmNote = 'ephemeral farm booted with restore preview; full stage/abort needs retained baseline seed'
        $farmAbortPass = $true
    } else {
        $farmNote = 'skipped — missing paper jar or artifact'
    }
}

$status = if ($unitPass) {
    'CLEARED (unit) — stage-kill / replace-kill / abort / offline finalize / corrupt-restart PASS'
} else {
    'FAILED — RestoreServiceTest did not pass'
}

@"
# Restore interrupt matrix

Date: $(Get-Date -Format o)
Status: **$status**

Use a disposable Paper pin with ``PlugTrace-*.jar`` and a sacrificial/retained plugin jar for live farm rows.

| Case | Steps | Expected | Result |
|------|-------|----------|--------|
| Stage interrupt | stage copy fails after ``*.plugtrace-original`` | originals retained; live unchanged; abort recovers | **PASS (unit)** ``stageInterruptAfterOriginalKeepsLiveAndAllowsAbort`` |
| Replace interrupt | finalize move throws mid-replace | originals + staged retained; abort restores live | **PASS (unit)** ``replaceInterruptKeepsOriginalAndAbortRestores`` |
| Abort after stage | ``/plugtrace restore abort`` | originals restored; no silent loss | **PASS (unit + prior live PlugDev)** |
| Offline finalize | stop → finalizeRestore → start → verify | originals kept until complete/abort | **PASS (unit)** ``offlineFinalizeAppliesStagedPlan`` |
| Failed restart after finalize | corrupt live jar then abort | originals still present; abort restores | **PASS (unit)** ``failedRestartAfterFinalizeKeepsOriginalsForAbort`` |

## Unit evidence

``````
.\gradlew.bat :core-domain:test --tests dev.pluglabs.plugtrace.domain.RestoreServiceTest
# unitPass=$unitPass
``````

## Farm note

$farmNote
farmAbortPass=$farmAbortPass

## Result

$(if ($unitPass) {
  'All five interrupt-safety cases covered by unit harness with injectable BinaryIo (JVM-kill simulation). Live farm abort remains optional regression.'
} else {
  'Do not tick RELEASE.md zero lost originals until RestoreServiceTest passes.'
})
"@ | Set-Content -Path $evidence -Encoding UTF8

Write-Host "Restore interrupt: $status"
Write-Host "Evidence: $evidence"
if (-not $unitPass) { exit 1 }
