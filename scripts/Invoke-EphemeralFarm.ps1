<#
.SYNOPSIS
  Boot an ephemeral Minecraft server with a PlugTrace artifact, run smoke commands, stop cleanly.

.EXAMPLE
  .\scripts\Invoke-EphemeralFarm.ps1 -ServerJar farm\jars\spigot-1.20.4.jar -JavaMajor 17 `
    -Artifact bukkit-modern\build\libs\PlugTrace-bukkit-modern-0.4.0.jar -RunName spigot-1.20.4 `
    -Commands '/plugtrace selfcheck;/plugtrace report preview'
#>
param(
    [Parameter(Mandatory = $true)][string]$ServerJar,
    [Parameter(Mandatory = $true)][ValidateSet('17', '21', '25')][string]$JavaMajor,
    [Parameter(Mandatory = $true)][string]$Artifact,
    [Parameter(Mandatory = $true)][string]$RunName,
    [string[]]$FixtureJars = @(),
    [string]$Commands = 'plugtrace selfcheck',
    [int]$BootTimeoutSec = 300,
    [int]$ObserveSeconds = 30,
    [int]$Port = 0,
    [int]$WebPort = 0
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\Resolve-JavaHome.ps1"

$ExtraCommands = @($Commands -split ';' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
if ($ExtraCommands.Count -eq 0) { $ExtraCommands = @('plugtrace selfcheck') }
# Console stdin: prefer bare commands (no leading /). Keep / if operator supplied it.
$ExtraCommands = @($ExtraCommands | ForEach-Object {
    if ($_ -match '^/') { $_.TrimStart('/') } else { $_ }
})

$root = Split-Path $PSScriptRoot -Parent
$ServerJar = if ([IO.Path]::IsPathRooted($ServerJar)) { $ServerJar } else { Join-Path $root $ServerJar }
$Artifact = if ([IO.Path]::IsPathRooted($Artifact)) { $Artifact } else { Join-Path $root $Artifact }

if (-not (Test-Path $ServerJar)) { throw "Missing server jar: $ServerJar" }
if (-not (Test-Path $Artifact)) { throw "Missing PlugTrace artifact: $Artifact" }

$javaHome = Get-PlugTraceJavaHome -Major $JavaMajor
$java = Join-Path $javaHome 'bin\java.exe'
$env:JAVA_HOME = $javaHome

$runRoot = Join-Path $root "farm\runs\$RunName"
if (Test-Path $runRoot) { Remove-Item $runRoot -Recurse -Force }
$pluginsDir = Join-Path $runRoot 'plugins'
$ptDataDir = Join-Path $pluginsDir 'PlugTrace'
New-Item -ItemType Directory -Force -Path $pluginsDir, $ptDataDir | Out-Null

Copy-Item $ServerJar (Join-Path $runRoot 'server.jar')
Copy-Item $Artifact (Join-Path $pluginsDir 'PlugTrace.jar')
foreach ($fx in $FixtureJars) {
    $fxPath = if ([IO.Path]::IsPathRooted($fx)) { $fx } else { Join-Path $root $fx }
    if (-not (Test-Path $fxPath)) { throw "Missing fixture jar: $fxPath" }
    $destFx = Join-Path $pluginsDir (Split-Path $fxPath -Leaf)
    Copy-Item $fxPath $destFx
}

# Seed Paperclip/Mojang cache from farm/cache to avoid corrupt re-downloads between runs.
# Copy only top-level entries; never recurse into a destination that already exists as a directory
# (avoids libraries/libraries/... path explosion on Windows).
$serverLeaf = [IO.Path]::GetFileNameWithoutExtension($ServerJar)
$cacheRoot = Join-Path $root "farm\cache\$serverLeaf"
if (Test-Path $cacheRoot) {
    Get-ChildItem $cacheRoot -Force | ForEach-Object {
        $dest = Join-Path $runRoot $_.Name
        if (Test-Path $dest) { return }
        if ($_.PSIsContainer) {
            New-Item -ItemType Directory -Force -Path $dest | Out-Null
            & robocopy $_.FullName $dest /E /NFL /NDL /NJH /NJS /nc /ns /np | Out-Null
            if ($LASTEXITCODE -ge 8) {
                Write-Warning "robocopy cache seed failed for $($_.Name) exit=$LASTEXITCODE"
            }
        } else {
            Copy-Item $_.FullName $dest -Force
        }
    }
}

Set-Content -Path (Join-Path $runRoot 'eula.txt') -Value 'eula=true' -Encoding ASCII
if ($Port -le 0) {
    $Port = 25000 + (Get-Random -Minimum 1 -Maximum 4000)
}
if ($WebPort -le 0) {
    $WebPort = 19000 + (Get-Random -Minimum 1 -Maximum 4000)
}
@"
online-mode=false
server-port=$Port
max-players=4
spawn-protection=0
motd=PlugTrace ephemeral farm
enable-command-block=false
sync-chunk-writes=true
"@ | Set-Content -Path (Join-Path $runRoot 'server.properties') -Encoding ASCII

@"
web:
  enabled: true
  bind: 127.0.0.1
  port: $WebPort
  allowRemote: false
privacy:
  mode: hash-only
"@ | Set-Content -Path (Join-Path $ptDataDir 'config.yml') -Encoding UTF8

$evidenceDir = Join-Path $root 'farm\evidence'
New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null
$logFile = Join-Path $evidenceDir "$RunName.log"
$summaryFile = Join-Path $evidenceDir "$RunName.md"

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $java
$psi.Arguments = '-Xms512M -Xmx1G -jar server.jar nogui'
$psi.WorkingDirectory = $runRoot
$psi.UseShellExecute = $false
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.CreateNoWindow = $true
$psi.Environment['JAVA_HOME'] = $javaHome

$proc = New-Object System.Diagnostics.Process
$proc.StartInfo = $psi

$sb = New-Object System.Text.StringBuilder
$outputHandler = {
    if (-not [string]::IsNullOrEmpty($EventArgs.Data)) {
        [void]$Event.MessageData.AppendLine($EventArgs.Data)
        Write-Host $EventArgs.Data
    }
}
$outEvent = Register-ObjectEvent -InputObject $proc -EventName OutputDataReceived -Action $outputHandler -MessageData $sb
$errEvent = Register-ObjectEvent -InputObject $proc -EventName ErrorDataReceived -Action $outputHandler -MessageData $sb

[void]$proc.Start()
$proc.BeginOutputReadLine()
$proc.BeginErrorReadLine()

$booted = $false
$deadline = (Get-Date).AddSeconds($BootTimeoutSec)
while ((Get-Date) -lt $deadline -and -not $proc.HasExited) {
    $text = $sb.ToString()
    if ($text -match 'Done \(' -or $text -match 'Done!') {
        $booted = $true
        break
    }
    if ($text -match 'UnsupportedClassVersionError|Error occurred during initialization|You need to agree to the EULA') {
        break
    }
    Start-Sleep -Milliseconds 500
}

$pass = $false
$notes = New-Object System.Collections.Generic.List[string]
if (-not $booted) {
    [void]$notes.Add('FAIL: server did not reach Done within boot timeout')
} else {
    [void]$notes.Add('PASS: reached Done')
    foreach ($cmd in $ExtraCommands) {
        $proc.StandardInput.WriteLine($cmd)
        $proc.StandardInput.Flush()
        Start-Sleep -Seconds 3
    }
    if ($ObserveSeconds -gt 0) {
        Write-Host "Observing for $ObserveSeconds seconds..."
        Start-Sleep -Seconds $ObserveSeconds
    }
    $proc.StandardInput.WriteLine('stop')
    $proc.StandardInput.Flush()
    if (-not $proc.WaitForExit(120000)) {
        [void]$notes.Add('WARN: stop timed out; killing process')
        $proc.Kill()
        [void]$proc.WaitForExit(30000)
    } else {
        [void]$notes.Add(('PASS: clean stop exit={0}' -f $proc.ExitCode))
    }
}

Unregister-Event -SourceIdentifier $outEvent.Name -ErrorAction SilentlyContinue
Unregister-Event -SourceIdentifier $errEvent.Name -ErrorAction SilentlyContinue
Get-EventSubscriber | Where-Object { $_.SourceObject -eq $proc } | Unregister-Event -ErrorAction SilentlyContinue

$fullLog = $sb.ToString()
Set-Content -Path $logFile -Value $fullLog -Encoding UTF8

$plugtraceEnabled = ($fullLog -match 'PlugTrace')
$dataOk = Test-Path $ptDataDir
if ($plugtraceEnabled) { [void]$notes.Add('PASS: PlugTrace log presence') } else { [void]$notes.Add('FAIL: no PlugTrace log lines') }
if ($dataOk) { [void]$notes.Add('PASS: plugins/PlugTrace data dir created') } else { [void]$notes.Add('WARN: no PlugTrace data dir') }

$prevEap = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$javaVer = (& $java -version 2>&1 | ForEach-Object { "$_" }) -join "`n"
$ErrorActionPreference = $prevEap

$hasFail = ($notes | Where-Object { $_ -like 'FAIL:*' }).Count -gt 0
$pass = $booted -and $plugtraceEnabled -and -not $hasFail

if ($pass) {
    $cacheRoot = Join-Path $root "farm\cache\$serverLeaf"
    New-Item -ItemType Directory -Force -Path $cacheRoot | Out-Null
    foreach ($name in @('cache', 'libraries', 'versions')) {
        $src = Join-Path $runRoot $name
        if (Test-Path $src) {
            Copy-Item $src (Join-Path $cacheRoot $name) -Recurse -Force
        }
    }
}

$notesText = ($notes | ForEach-Object { '- ' + $_ }) -join "`n"

@"
# Farm run: $RunName

- Date: $(Get-Date -Format o)
- Server jar: ``$ServerJar``
- Artifact: ``$Artifact``
- JAVA_HOME: ``$javaHome``
- Java major pin: $JavaMajor
- Port: $Port
- WebPort: $WebPort
- ObserveSeconds: $ObserveSeconds
- Result: $(if ($pass) { 'PASS' } else { 'FAIL' })

## Java -version
``````
$($javaVer.Trim())
``````

## Notes
$notesText

## Evidence
- Log: ``farm/evidence/$RunName.log``
- Run dir: ``farm/runs/$RunName``
"@ | Set-Content -Path $summaryFile -Encoding UTF8

Write-Host "=== $RunName => $(if ($pass) { 'PASS' } else { 'FAIL' }) ==="
Write-Host "Summary: $summaryFile"
if (-not $pass) { exit 1 }
exit 0
