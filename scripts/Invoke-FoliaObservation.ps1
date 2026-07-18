<#
.SYNOPSIS
  Folia-only observation-window harness: single PlugTrace plugin, delayed/repeating global work window.
#>
param(
    [string]$ServerJar = 'farm\jars\folia-1.21.11-14.jar',
    [string]$Artifact = 'folia\build\libs\PlugTrace-folia-0.4.0.jar',
    [int]$ObserveSeconds = 900
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent

# Reject contaminated harnesses that also drop PlugDev Bootstrap.
$runCheck = Join-Path $root 'farm\runs\folia-observation\plugins'
if (Test-Path $runCheck) {
    $bootstrap = Get-ChildItem $runCheck -Filter '*Bootstrap*' -ErrorAction SilentlyContinue
    if ($bootstrap) {
        throw "Folia harness contaminated with Bootstrap jars: $($bootstrap.Name -join ', '). Use PlugTrace-only."
    }
}

& "$PSScriptRoot\Invoke-EphemeralFarm.ps1" `
    -ServerJar $ServerJar `
    -JavaMajor '21' `
    -Artifact $Artifact `
    -RunName 'folia-observation' `
    -ObserveSeconds $ObserveSeconds `
    -Commands 'plugtrace selfcheck;plugtrace compatibility;plugtrace report preview'
