<#
.SYNOPSIS
  Log one soak day for Paper or Folia. Does not invent calendar progress — operator must run daily.
#>
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('paper', 'folia')]
    [string]$Target,

    [Parameter(Mandatory = $true)]
    [ValidateRange(0, 7)]
    [int]$Day,

    [string]$Notes = '',

    [switch]$HarnessVerified
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$logPath = Join-Path $root 'farm\evidence\soak-log.md'

if (-not (Test-Path $logPath)) {
    @"
# PlugTrace 7-day soak log

Started: $(Get-Date -Format 'yyyy-MM-dd')
Pins: Paper (see farm evidence) + Folia 1.21.11 / Temurin 21, PlugTrace-only.

| Day | Target | Timestamp | Harness verified | Notes |
|-----|--------|-----------|------------------|-------|
"@ | Set-Content -Path $logPath -Encoding UTF8
}

$ts = Get-Date -Format o
$hv = if ($HarnessVerified) { 'yes' } else { 'no' }
$safeNotes = ($Notes -replace '\|', '/').Trim()
if ([string]::IsNullOrWhiteSpace($safeNotes)) { $safeNotes = '—' }

Add-Content -Path $logPath -Value "| $Day | $Target | $ts | $hv | $safeNotes |" -Encoding UTF8
Write-Host "Logged soak day $Day for $Target → $logPath"
