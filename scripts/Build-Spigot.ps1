<#
.SYNOPSIS
  Build Spigot server jars via SpigotMC BuildTools under pinned Java 17.

.EXAMPLE
  .\scripts\Build-Spigot.ps1 -Rev 1.20.4
  .\scripts\Build-Spigot.ps1 -Rev 1.20.1
#>
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('1.20.1', '1.20.4')]
    [string]$Rev
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\Resolve-JavaHome.ps1"

$root = Split-Path $PSScriptRoot -Parent
$work = Join-Path $root "farm\buildtools-$Rev"
New-Item -ItemType Directory -Force -Path $work | Out-Null

$javaHome = Get-PlugTraceJavaHome -Major '17'
$java = Join-Path $javaHome 'bin\java.exe'
$env:JAVA_HOME = $javaHome
$env:Path = "$(Join-Path $javaHome 'bin');$env:Path"

$bt = Join-Path $work 'BuildTools.jar'
if (-not (Test-Path $bt)) {
    Write-Host "Downloading BuildTools.jar..."
    Invoke-WebRequest -Uri 'https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar' -OutFile $bt
}

Push-Location $work
try {
    Write-Host "Building Spigot $Rev with JAVA_HOME=$javaHome"
    & $java -jar $bt --rev $Rev
    if ($LASTEXITCODE -ne 0) { throw "BuildTools failed with exit $LASTEXITCODE" }

    $spigot = Get-ChildItem $work -Filter "spigot-$Rev*.jar" | Select-Object -First 1
    if (-not $spigot) {
        $spigot = Get-ChildItem $work -Filter 'spigot-*.jar' | Where-Object { $_.Name -notmatch 'sources|javadoc' } | Select-Object -First 1
    }
    if (-not $spigot) { throw "No spigot jar produced in $work" }

    $outDir = Join-Path $root 'farm\jars'
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
    $dest = Join-Path $outDir "spigot-$Rev.jar"
    Copy-Item $spigot.FullName $dest -Force
    Write-Host "OK: $dest"
    Write-Output $dest
}
finally {
    Pop-Location
}
