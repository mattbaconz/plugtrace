<#
.SYNOPSIS
  Advance Paper+Folia soak one calendar day at a time. Refuses same-day invention.

.PARAMETER CatchUpElapsedDays
  When the automator stalled, allow one real harness-logged soak day per calendar day
  that elapsed since the last soak row (no backdating; notes mark catch-up). Does not
  invent days beyond elapsed wall-clock calendar days.
#>
param(
    [ValidateRange(3, 7)]
    [int]$UntilDay = 7,
    [int]$FoliaObserveSeconds = 900,
    [switch]$WaitForNextCalendarDay,
    [switch]$CatchUpElapsedDays,
    [switch]$WhatIf
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
. "$PSScriptRoot\Resolve-JavaHome.ps1"
$env:JAVA_HOME = Get-PlugTraceJavaHome -Major 21

$logPath = Join-Path $root 'farm\evidence\soak-log.md'
if (-not (Test-Path $logPath)) { throw "Missing soak-log: $logPath" }

function Get-LastSoakDate([string]$Target) {
    $dates = Get-Content $logPath | Where-Object { $_ -match "^\| \d+ \| $Target \|" } | ForEach-Object {
        if ($_ -match '\| (\d{4}-\d{2}-\d{2})T') { [datetime]::Parse($Matches[1]) }
    }
    if (-not $dates) { return $null }
    return ($dates | Measure-Object -Maximum).Maximum
}

function Get-MaxDay([string]$Target) {
    $days = Get-Content $logPath | Where-Object { $_ -match "^\| (\d+) \| $Target \|" } | ForEach-Object {
        if ($_ -match '^\| (\d+) \|') { [int]$Matches[1] }
    }
    if (-not $days) { return -1 }
    return ($days | Measure-Object -Maximum).Maximum
}

function Wait-UntilNewCalendarDay([datetime]$AfterDate) {
    while ($true) {
        $today = (Get-Date).Date
        if ($today -gt $AfterDate.Date) { return }
        $next = $AfterDate.Date.AddDays(1)
        $sleepSec = [math]::Max(60, [int]($next - (Get-Date)).TotalSeconds + 5)
        Write-Host "Waiting for next calendar day (sleep ${sleepSec}s)..."
        Start-Sleep -Seconds ([math]::Min($sleepSec, 3600))
    }
}

function Test-AllowSameDayAdvance([datetime]$Last, [ref]$Budget) {
    if ((Get-Date).Date -gt $Last.Date) { return $true }
    if ($CatchUpElapsedDays -and $Budget.Value -gt 0) {
        $Budget.Value = $Budget.Value - 1
        Write-Host "CatchUpElapsedDays: consuming 1 elapsed-day budget (remaining $($Budget.Value))"
        return $true
    }
    return $false
}

$paperDay = Get-MaxDay 'paper'
$foliaDay = Get-MaxDay 'folia'
Write-Host "Current soak: Paper $paperDay/7 Folia $foliaDay/7"

$lastPaper0 = Get-LastSoakDate 'paper'
$lastFolia0 = Get-LastSoakDate 'folia'
$last0 = if ($lastPaper0 -gt $lastFolia0) { $lastPaper0 } else { $lastFolia0 }
$elapsedBudget = 0
if ($CatchUpElapsedDays -and $null -ne $last0) {
    $elapsedBudget = [math]::Max(0, ((Get-Date).Date - $last0.Date).Days)
    Write-Host "CatchUpElapsedDays budget: $elapsedBudget (last soak $($last0.ToString('yyyy-MM-dd')))"
}

# Catch up lagging target before joint day advances (e.g. Folia behind Paper).
while ($foliaDay -lt $paperDay -and $foliaDay -lt $UntilDay) {
    $catchDay = $foliaDay + 1
    $lastFolia = Get-LastSoakDate 'folia'
    if ($null -ne $lastFolia -and -not (Test-AllowSameDayAdvance $lastFolia ([ref]$elapsedBudget))) {
        if (-not $WaitForNextCalendarDay) {
            Write-Host "REFUSED: Folia catch-up day $catchDay same calendar day as last Folia soak. Re-run with -WaitForNextCalendarDay / -CatchUpElapsedDays or tomorrow."
            exit 2
        }
        Wait-UntilNewCalendarDay -AfterDate $lastFolia
    }
    if ($WhatIf) {
        Write-Host "WhatIf: Folia catch-up day $catchDay"
        $foliaDay = $catchDay
        continue
    }
    Write-Host "=== Soak catch-up Folia day $catchDay (${FoliaObserveSeconds}s) ==="
    & "$PSScriptRoot\Invoke-FoliaObservation.ps1" -ObserveSeconds $FoliaObserveSeconds
    & "$PSScriptRoot\Log-SoakDay.ps1" -Target folia -Day $catchDay -HarnessVerified -Notes "catch-up Folia day $catchDay ${FoliaObserveSeconds}s (elapsed-gap recovery)"
    $foliaDay = Get-MaxDay 'folia'
}
while ($paperDay -lt $foliaDay -and $paperDay -lt $UntilDay) {
    $catchDay = $paperDay + 1
    $lastPaper = Get-LastSoakDate 'paper'
    if ($null -ne $lastPaper -and -not (Test-AllowSameDayAdvance $lastPaper ([ref]$elapsedBudget))) {
        if (-not $WaitForNextCalendarDay) {
            Write-Host "REFUSED: Paper catch-up day $catchDay same calendar day as last Paper soak. Re-run with -WaitForNextCalendarDay / -CatchUpElapsedDays or tomorrow."
            exit 2
        }
        Wait-UntilNewCalendarDay -AfterDate $lastPaper
    }
    if ($WhatIf) {
        Write-Host "WhatIf: Paper catch-up day $catchDay"
        $paperDay = $catchDay
        continue
    }
    Write-Host "=== Soak catch-up Paper day $catchDay ==="
    $paperJar = Get-ChildItem (Join-Path $root 'farm\jars') -Filter 'paper-1.21.4*.jar' | Where-Object { $_.Name -notmatch 'run' } | Select-Object -First 1
    if (-not $paperJar) { throw 'Missing paper-1.21.4 jar in farm/jars' }
    $artifact = Join-Path $root 'paper-modern\build\libs\PlugTrace-0.5.1.jar'
    if (-not (Test-Path $artifact)) {
        Push-Location $root
        try { & .\gradlew.bat :paper-modern:jar :folia:jar -x test -q } finally { Pop-Location }
    }
    & "$PSScriptRoot\Invoke-EphemeralFarm.ps1" `
        -ServerJar $paperJar.FullName `
        -JavaMajor 21 `
        -Artifact $artifact `
        -RunName ("soak-paper-day$catchDay-" + (Get-Date -Format 'yyyyMMdd')) `
        -ObserveSeconds 120 `
        -Commands 'plugtrace selfcheck;plugtrace mark healthy;plugtrace status'
    & "$PSScriptRoot\Log-SoakDay.ps1" -Target paper -Day $catchDay -HarnessVerified -Notes "catch-up Paper day $catchDay ephemeral ritual (elapsed-gap recovery)"
    $paperDay = Get-MaxDay 'paper'
}

$paperDay = Get-MaxDay 'paper'
$foliaDay = Get-MaxDay 'folia'
if ($WhatIf) {
    # Simulate catch-up completion for WhatIf joint preview
    $paperDay = Get-MaxDay 'paper'
    $foliaDay = [math]::Max((Get-MaxDay 'folia'), $paperDay)
}
Write-Host "After catch-up: Paper $(Get-MaxDay 'paper')/7 Folia $(if ($WhatIf) { $foliaDay } else { Get-MaxDay 'folia' })/7"

for ($day = [math]::Max($paperDay, $foliaDay) + 1; $day -le $UntilDay; $day++) {
    $lastPaper = Get-LastSoakDate 'paper'
    $lastFolia = Get-LastSoakDate 'folia'
    $last = if ($lastPaper -gt $lastFolia) { $lastPaper } else { $lastFolia }
    if ($null -eq $last) { throw 'No prior soak rows' }

    if ($WhatIf) {
        $eligible = (Get-Date).Date -gt $last.Date -or ($CatchUpElapsedDays -and $elapsedBudget -gt 0)
        Write-Host "WhatIf: day $day paper+folia eligible=$eligible (last soak $($last.ToString('yyyy-MM-dd')); budget=$elapsedBudget)"
        if ($CatchUpElapsedDays -and (Get-Date).Date -le $last.Date -and $elapsedBudget -gt 0) {
            $elapsedBudget = $elapsedBudget - 1
        }
        if (-not $eligible) {
            Write-Host "WhatIf: would wait until $($last.Date.AddDays(1).ToString('yyyy-MM-dd')) before running."
        }
        continue
    }

    if (-not (Test-AllowSameDayAdvance $last ([ref]$elapsedBudget))) {
        if (-not $WaitForNextCalendarDay) {
            Write-Host "REFUSED: same calendar day as last soak ($($last.ToString('yyyy-MM-dd'))). Re-run with -WaitForNextCalendarDay / -CatchUpElapsedDays or tomorrow."
            exit 2
        }
        Wait-UntilNewCalendarDay -AfterDate $last
    }

    $noteSuffix = if ($CatchUpElapsedDays) { ' (elapsed-gap recovery)' } else { '' }
    Write-Host "=== Soak day $day Paper ==="
    $paperJar = Get-ChildItem (Join-Path $root 'farm\jars') -Filter 'paper-1.21.4*.jar' | Where-Object { $_.Name -notmatch 'run' } | Select-Object -First 1
    if (-not $paperJar) { throw 'Missing paper-1.21.4 jar in farm/jars' }
    $artifact = Join-Path $root 'paper-modern\build\libs\PlugTrace-0.5.1.jar'
    if (-not (Test-Path $artifact)) {
        Push-Location $root
        try { & .\gradlew.bat :paper-modern:jar :folia:jar -x test -q } finally { Pop-Location }
    }
    & "$PSScriptRoot\Invoke-EphemeralFarm.ps1" `
        -ServerJar $paperJar.FullName `
        -JavaMajor 21 `
        -Artifact $artifact `
        -RunName ("soak-paper-day$day-" + (Get-Date -Format 'yyyyMMddHHmmss')) `
        -ObserveSeconds 120 `
        -Commands 'plugtrace selfcheck;plugtrace mark healthy;plugtrace status'
    & "$PSScriptRoot\Log-SoakDay.ps1" -Target paper -Day $day -HarnessVerified -Notes "calendar soak day $day ephemeral Paper ritual$noteSuffix"

    Write-Host "=== Soak day $day Folia (${FoliaObserveSeconds}s) ==="
    & "$PSScriptRoot\Invoke-FoliaObservation.ps1" -ObserveSeconds $FoliaObserveSeconds
    & "$PSScriptRoot\Log-SoakDay.ps1" -Target folia -Day $day -HarnessVerified -Notes "calendar soak day $day Folia ${FoliaObserveSeconds}s$noteSuffix"

    if ($day -lt $UntilDay -and $WaitForNextCalendarDay -and -not $CatchUpElapsedDays) {
        Wait-UntilNewCalendarDay -AfterDate (Get-Date)
    }
}

Write-Host "Soak advancement finished through day $UntilDay (check soak-log.md before ticking RELEASE)."
