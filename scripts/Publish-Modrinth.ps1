<#
.SYNOPSIS
  Publish PlugTrace 0.5.1 to Modrinth (create project if needed + upload versions).

.NOTES
  Requires $env:MODRINTH_TOKEN (personal access token with PROJECT_CREATE / VERSION_CREATE).
  SpigotMC has no public upload API — use SPIGOT_DRAFT.md + browser after this script.
#>
param(
    [string]$Slug = 'plugtrace',
    [string]$VersionNumber = '0.5.1',
    [switch]$WhatIf
)

$ErrorActionPreference = 'Stop'
$token = $env:MODRINTH_TOKEN
if ([string]::IsNullOrWhiteSpace($token)) {
    throw 'MODRINTH_TOKEN is not set. Create a Modrinth PAT with project/version write scopes and set the env var.'
}

$pluginRoot = Split-Path $PSScriptRoot -Parent
$docsRoot = Join-Path (Split-Path $pluginRoot -Parent) 'plugtrace-docs'
if (-not (Test-Path (Join-Path $pluginRoot 'LICENSE'))) {
    throw "Could not locate plugin root from $PSScriptRoot"
}
$assets = Join-Path $docsRoot 'marketplace\assets'
$draft = Get-Content (Join-Path $docsRoot 'marketplace\MODRINTH_DRAFT.md') -Raw

$paperJar = Join-Path $pluginRoot 'paper-modern\build\libs\PlugTrace-0.5.1.jar'
$foliaJar = Join-Path $pluginRoot 'folia\build\libs\PlugTrace-folia-0.5.1.jar'
$bukkitJar = Join-Path $pluginRoot 'bukkit-modern\build\libs\PlugTrace-bukkit-modern-0.5.1.jar'
foreach ($j in @($paperJar, $foliaJar, $bukkitJar)) {
    if (-not (Test-Path $j)) { throw "Missing JAR: $j — download from GitHub release v0.5.1" }
}

$headers = @{
    Authorization = $token
    'User-Agent'  = 'pluglabs-plugtrace-publish/0.5.1 (github.com/mattbaconz/plugtrace)'
}

function Invoke-Modrinth([string]$Method, [string]$Path, $Body = $null, [string]$ContentType = 'application/json') {
    $uri = "https://api.modrinth.com/v2$Path"
    $params = @{ Uri = $uri; Method = $Method; Headers = $headers }
    if ($null -ne $Body) {
        $params.ContentType = $ContentType
        $params.Body = if ($ContentType -eq 'application/json') { ($Body | ConvertTo-Json -Depth 20 -Compress) } else { $Body }
    }
    return Invoke-RestMethod @params
}

Write-Host 'Checking Modrinth auth...'
$me = Invoke-Modrinth -Method GET -Path '/user'
Write-Host "Authenticated as $($me.username) ($($me.id))"

$project = $null
try {
    $project = Invoke-Modrinth -Method GET -Path "/project/$Slug"
    Write-Host "Project exists: $($project.title) ($($project.id))"
} catch {
    Write-Host "Project '$Slug' not found — creating..."
    $bodyMd = ($draft -split "`n" | Where-Object { $_ -notmatch '^# Modrinth listing' -and $_ -notmatch 'READY TO PUBLISH' -and $_ -notmatch 'Publication bar' -and $_ -notmatch 'Gallery assets' }) -join "`n"
    $create = @{
        title             = 'PlugTrace'
        project_type      = 'plugin'
        slug              = $Slug
        description       = 'Know whether your Minecraft server update actually worked. After every risky restart: /plugtrace status.'
        body              = $bodyMd.Trim()
        categories        = @('utility', 'management')
        client_side       = 'unsupported'
        server_side       = 'required'
        license_id        = 'Apache-2.0'
        is_draft          = $false
        initial_versions  = @()
    }
    if ($WhatIf) {
        Write-Host ($create | ConvertTo-Json -Depth 5)
    } else {
        $project = Invoke-Modrinth -Method POST -Path '/project' -Body $create
        Write-Host "Created project $($project.id)"
    }
}

if (-not $WhatIf -and (Test-Path (Join-Path $assets 'icon.png'))) {
    $iconPath = Join-Path $assets 'icon.png'
    $iconBytes = [IO.File]::ReadAllBytes($iconPath)
    Invoke-RestMethod -Uri "https://api.modrinth.com/v2/project/$($project.id)/icon?ext=png" -Method PATCH -Headers $headers -ContentType 'image/png' -Body $iconBytes | Out-Null
    Write-Host 'Icon uploaded'
}

function Publish-Version([string]$Name, [string]$JarPath, [string[]]$Loaders, [string[]]$GameVersions) {
    $boundary = [guid]::NewGuid().ToString('N')
    $data = @{
        name           = $Name
        version_number = $VersionNumber
        changelog      = "PlugTrace $VersionNumber — after-update ritual (HEALTHY/FAILING/DEGRADED). Experimental dogfood compatibility only; see claim map."
        dependencies   = @()
        game_versions  = $GameVersions
        version_type   = 'release'
        loaders        = $Loaders
        featured       = $true
        status         = 'listed'
        project_id     = $project.id
        file_parts     = @('file')
        primary_file   = 'file'
    } | ConvertTo-Json -Depth 10 -Compress

    if ($WhatIf) {
        Write-Host "Would upload $JarPath as $Name loaders=$($Loaders -join ',') games=$($GameVersions -join ',')"
        return
    }

    $fileBytes = [IO.File]::ReadAllBytes($JarPath)
    $fileName = Split-Path $JarPath -Leaf
    $enc = [Text.Encoding]::UTF8
    $ms = New-Object IO.MemoryStream
    $w = New-Object IO.BinaryWriter $ms
    $w.Write($enc.GetBytes("--$boundary`r`n"))
    $w.Write($enc.GetBytes("Content-Disposition: form-data; name=`"data`"`r`n`r`n"))
    $w.Write($enc.GetBytes($data))
    $w.Write($enc.GetBytes("`r`n--$boundary`r`n"))
    $w.Write($enc.GetBytes("Content-Disposition: form-data; name=`"file`"; filename=`"$fileName`"`r`n"))
    $w.Write($enc.GetBytes("Content-Type: application/java-archive`r`n`r`n"))
    $w.Write($fileBytes)
    $w.Write($enc.GetBytes("`r`n--$boundary--`r`n"))
    $w.Flush()
    $body = $ms.ToArray()

    $resp = Invoke-RestMethod -Uri 'https://api.modrinth.com/v2/version' -Method POST -Headers $headers -ContentType "multipart/form-data; boundary=$boundary" -Body $body
    Write-Host "Published version $($resp.id) — $($resp.name)"
}

# Paper-family primary (paper loader on Modrinth)
Publish-Version -Name "PlugTrace $VersionNumber (Paper)" -JarPath $paperJar -Loaders @('paper','purpur','folia') -GameVersions @('1.21.4','1.21.11')
# Folia-specific artifact as additional version channel
Publish-Version -Name "PlugTrace Folia $VersionNumber" -JarPath $foliaJar -Loaders @('folia') -GameVersions @('1.21.11')
# Bukkit/Spigot experimental
Publish-Version -Name "PlugTrace Bukkit $VersionNumber" -JarPath $bukkitJar -Loaders @('bukkit','spigot') -GameVersions @('1.20.1','1.20.4')

Write-Host "Done. Project URL: https://modrinth.com/plugin/$Slug"
Write-Host 'SpigotMC: paste SPIGOT_DRAFT.md and attach the three 0.5.1 JARs via the Spigot resource editor (no public API).'
