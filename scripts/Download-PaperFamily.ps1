<#
.SYNOPSIS
  Download Paper / Folia / Purpur jars into farm/jars (Paper Fill v3 + Purpur API).
#>
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('paper', 'folia', 'purpur')]
    [string]$Project,

    [Parameter(Mandatory = $true)]
    [string]$Version,

    [int]$Build = 0
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$outDir = Join-Path $root 'farm\jars'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$ua = 'PlugTraceFarm/0.4 (+https://github.com/mattbaconz/plugtrace)'

function Get-FillJar {
    param($Project, $Version, $Build)
    $headers = @{ 'User-Agent' = $ua }
    $buildsUrl = "https://fill.papermc.io/v3/projects/$Project/versions/$Version/builds"
    $builds = Invoke-RestMethod -Uri $buildsUrl -Headers $headers
    if (-not $builds) { throw "No builds returned for $Project $Version" }

    $selected = $null
    if ($Build -gt 0) {
        $selected = $builds | Where-Object { [int]$_.id -eq $Build } | Select-Object -First 1
        if (-not $selected) { throw "Build $Build not found for $Project $Version" }
    } else {
        $selected = $builds | Where-Object { $_.channel -eq 'STABLE' } | Select-Object -First 1
        if (-not $selected) {
            $selected = $builds | Select-Object -First 1
        }
    }

    $url = $selected.downloads.'server:default'.url
    if (-not $url) { throw "No server:default download URL for $Project $Version build $($selected.id)" }
    $buildId = [int]$selected.id
    $dest = Join-Path $outDir "$Project-$Version-$buildId.jar"
    Write-Host "Downloading $url (channel=$($selected.channel))"
    Invoke-WebRequest -Uri $url -OutFile $dest -Headers $headers
    return @{ Path = $dest; Build = $buildId }
}

function Get-PurpurJar {
    param($Version, $Build)
    $meta = Invoke-RestMethod "https://api.purpurmc.org/v2/purpur/$Version"
    if ($Build -le 0) { $Build = [int]$meta.builds.latest }
    $url = "https://api.purpurmc.org/v2/purpur/$Version/$Build/download"
    $dest = Join-Path $outDir "purpur-$Version-$Build.jar"
    Write-Host "Downloading $url"
    Invoke-WebRequest -Uri $url -OutFile $dest
    return @{ Path = $dest; Build = $Build }
}

$result = if ($Project -eq 'purpur') {
    Get-PurpurJar -Version $Version -Build $Build
} else {
    Get-FillJar -Project $Project -Version $Version -Build $Build
}

Write-Host "OK: $($result.Path) (build $($result.Build))"
Write-Output $result.Path
