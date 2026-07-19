<#
.SYNOPSIS
  Advance Paper+Folia soak one calendar day at a time. Refuses same-day invention.
#>
param(
    [ValidateRange(3, 7)]
    [int]$UntilDay = 7,
    [int]$FoliaObserveSeconds = 900,
    [switch]$WaitForNextCalendarDay,
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

$paperDay = Get-MaxDay 'paper'
$foliaDay = Get-MaxDay 'folia'
Write-Host "Current soak: Paper $paperDay/7 Folia $foliaDay/7"

for ($day = [math]::Max($paperDay, $foliaDay) + 1; $day -le $UntilDay; $day++) {
    $lastPaper = Get-LastSoakDate 'paper'
    $lastFolia = Get-LastSoakDate 'folia'
    $last = if ($lastPaper -gt $lastFolia) { $lastPaper } else { $lastFolia }
    if ($null -eq $last) { throw 'No prior soak rows' }

    if ($WhatIf) {
        $eligible = (Get-Date).Date -gt $last.Date
        Write-Host "WhatIf: day $day paper+folia eligible=$eligible (last soak $($last.ToString('yyyy-MM-dd')))"
        if (-not $eligible) {
            Write-Host "WhatIf: would wait until $($last.Date.AddDays(1).ToString('yyyy-MM-dd')) before running."
        }
        continue
    }

    if ((Get-Date).Date -le $last.Date) {
        if (-not $WaitForNextCalendarDay) {
            Write-Host "REFUSED: same calendar day as last soak ($($last.ToString('yyyy-MM-dd'))). Re-run with -WaitForNextCalendarDay or tomorrow."
            exit 2
        }
        Wait-UntilNewCalendarDay -AfterDate $last
    }

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
        -RunName ("soak-paper-day$day-" + (Get-Date -Format 'yyyyMMdd')) `
        -ObserveSeconds 120 `
        -Commands 'plugtrace selfcheck;plugtrace mark healthy;plugtrace status'
    & "$PSScriptRoot\Log-SoakDay.ps1" -Target paper -Day $day -HarnessVerified -Notes "calendar soak day $day ephemeral Paper ritual"

    Write-Host "=== Soak day $day Folia (${FoliaObserveSeconds}s) ==="
    & "$PSScriptRoot\Invoke-FoliaObservation.ps1" -ObserveSeconds $FoliaObserveSeconds
    & "$PSScriptRoot\Log-SoakDay.ps1" -Target folia -Day $day -HarnessVerified -Notes "calendar soak day $day Folia ${FoliaObserveSeconds}s"

    if ($day -lt $UntilDay -and $WaitForNextCalendarDay) {
        Wait-UntilNewCalendarDay -AfterDate (Get-Date)
    }
}

Write-Host "Soak advancement finished through day $UntilDay (check soak-log.md before ticking RELEASE)."
