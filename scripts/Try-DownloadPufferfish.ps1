<#
.SYNOPSIS
  Attempt to download a Pufferfish jar. Records Unverified if unavailable.
#>
param(
    [string]$Version = '1.21.4',
    [string]$OutName = 'pufferfish-attempt.jar'
)

$ErrorActionPreference = 'Continue'
$root = Split-Path $PSScriptRoot -Parent
$outDir = Join-Path $root 'farm\jars'
$logDir = Join-Path $root 'farm\evidence'
New-Item -ItemType Directory -Force -Path $outDir, $logDir | Out-Null
$dest = Join-Path $outDir $OutName
$note = Join-Path $logDir 'pufferfish-availability.md'

$candidates = @(
    "https://ci.pufferfish.host/job/Pufferfish-1.21/lastSuccessfulBuild/artifact/build/libs/pufferfish-paperclip-$Version-R0.1-SNAPSHOT-reobf.jar",
    "https://ci.pufferfish.host/job/Pufferfish-1.21/lastSuccessfulBuild/artifact/pufferfish-paperclip-$Version-R0.1-SNAPSHOT-reobf.jar",
    "https://downloads.pufferfish.host/pufferfish-$Version.jar"
)

$buf = New-Object System.Text.StringBuilder
[void]$buf.AppendLine('# Pufferfish availability probe')
[void]$buf.AppendLine('')
[void]$buf.AppendLine("Date: $(Get-Date -Format o)")
[void]$buf.AppendLine("Requested version: $Version")
[void]$buf.AppendLine('')
$ok = $false

foreach ($url in $candidates) {
    [void]$buf.AppendLine(('Trying: {0}' -f $url))
    try {
        Invoke-WebRequest -Uri $url -OutFile $dest -MaximumRedirection 5 -TimeoutSec 60
        if ((Test-Path $dest) -and ((Get-Item $dest).Length -gt 1MB)) {
            [void]$buf.AppendLine(('PASS size={0}' -f (Get-Item $dest).Length))
            $ok = $true
            break
        }
        [void]$buf.AppendLine('FAIL empty or tiny file')
        Remove-Item $dest -Force -ErrorAction SilentlyContinue
    } catch {
        [void]$buf.AppendLine(('FAIL: {0}' -f $_.Exception.Message))
        Remove-Item $dest -Force -ErrorAction SilentlyContinue
    }
}

if (-not $ok) {
    [void]$buf.AppendLine('')
    [void]$buf.AppendLine('Result: Unverified / unavailable for automated farm.')
    [void]$buf.AppendLine('Do not invent a Pufferfish matrix pass. Leave Planned/Unverified until a matching jar boots.')
    [void]$buf.AppendLine('Manual fallback: download from https://pufferfish.host/downloads into farm/jars/ then re-run Invoke-EphemeralFarm.ps1.')
    Set-Content -Path $note -Value $buf.ToString() -Encoding UTF8
    Write-Host "Pufferfish unavailable - wrote $note"
    exit 2
}

[void]$buf.AppendLine('')
[void]$buf.AppendLine(('Result: downloaded {0}' -f $dest))
Set-Content -Path $note -Value $buf.ToString() -Encoding UTF8
Write-Host "OK: $dest"
Write-Output $dest
exit 0
