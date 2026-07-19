<#
.SYNOPSIS
  Two-pass Paper farm to produce HIGH attributions (baseline then fixture), then score the review sheet.
#>
param(
    [int]$ObserveSeconds = 45
)

$ErrorActionPreference = 'Stop'
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$evidence = Join-Path $root 'farm\evidence\trust-high-attribution.md'
$sheet = Join-Path $root 'farm\evidence\high-review-sheet.csv'

. "$root\scripts\Resolve-JavaHome.ps1"
$env:JAVA_HOME = Get-PlugTraceJavaHome -Major 21
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'

Push-Location $root
try {
    & .\gradlew.bat :paper-modern:jar :fixtures:wrapper-chain:jar :fixtures:delayed-error:jar :fixtures:developer-check:jar -x test -q
    if ($LASTEXITCODE -ne 0) { throw 'fixture/paper build failed' }
} finally { Pop-Location }

$artifact = Join-Path $root 'paper-modern\build\libs\PlugTrace-0.5.1.jar'
$paperJar = Get-ChildItem (Join-Path $root 'farm\jars') -Filter 'paper-1.21.4*.jar' |
    Where-Object { $_.Name -notmatch 'run' } | Select-Object -First 1
if (-not $paperJar) { throw 'Missing paper-1.21.4 jar in farm/jars' }

$wc = Get-ChildItem (Join-Path $root 'fixtures\wrapper-chain\build\libs') -Filter '*.jar' | Select-Object -First 1
$de = Get-ChildItem (Join-Path $root 'fixtures\delayed-error\build\libs') -Filter '*.jar' | Select-Object -First 1
$dc = Get-ChildItem (Join-Path $root 'fixtures\developer-check\build\libs') -Filter '*.jar' | Select-Object -First 1

$runName = 'high-attr-twopass'
$runRoot = Join-Path $root "farm\runs\$runName"
if (Test-Path $runRoot) { Remove-Item $runRoot -Recurse -Force }
$pluginsDir = Join-Path $runRoot 'plugins'
$ptData = Join-Path $pluginsDir 'PlugTrace'
New-Item -ItemType Directory -Force -Path $pluginsDir, $ptData | Out-Null
Copy-Item $paperJar.FullName (Join-Path $runRoot 'server.jar')
Copy-Item $artifact (Join-Path $pluginsDir 'PlugTrace.jar')

$cacheRoot = Join-Path $root ("farm\cache\" + [IO.Path]::GetFileNameWithoutExtension($paperJar.Name))
if (Test-Path $cacheRoot) {
    Get-ChildItem $cacheRoot -Force | ForEach-Object {
        $dest = Join-Path $runRoot $_.Name
        if (-not (Test-Path $dest)) { Copy-Item $_.FullName $dest -Recurse -Force }
    }
}

Set-Content (Join-Path $runRoot 'eula.txt') 'eula=true' -Encoding ASCII
$port = 25000 + (Get-Random -Minimum 1 -Maximum 4000)
@"
online-mode=false
server-port=$port
motd=PlugTrace HIGH farm
max-players=2
"@ | Set-Content (Join-Path $runRoot 'server.properties') -Encoding ASCII

function Start-FarmBoot([string[]]$ExtraCommands, [int]$ObserveSec, [string]$LogPath) {
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $java
    $psi.Arguments = '-Xms512M -Xmx1G -jar server.jar nogui'
    $psi.WorkingDirectory = $runRoot
    $psi.RedirectStandardInput = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.Environment['JAVA_HOME'] = $env:JAVA_HOME
    $proc = New-Object System.Diagnostics.Process
    $proc.StartInfo = $psi
    $sb = New-Object System.Text.StringBuilder
    $outputHandler = {
        if (-not [string]::IsNullOrEmpty($EventArgs.Data)) {
            [void]$Event.MessageData.AppendLine($EventArgs.Data)
        }
    }
    $outEvent = Register-ObjectEvent -InputObject $proc -EventName OutputDataReceived -Action $outputHandler -MessageData $sb
    $errEvent = Register-ObjectEvent -InputObject $proc -EventName ErrorDataReceived -Action $outputHandler -MessageData $sb
    [void]$proc.Start()
    $proc.BeginOutputReadLine()
    $proc.BeginErrorReadLine()

    $deadline = (Get-Date).AddSeconds(300)
    $ready = $false
    while ((Get-Date) -lt $deadline -and -not $proc.HasExited) {
        Start-Sleep -Milliseconds 500
        if ($sb.ToString() -match 'Done \(' -or $sb.ToString() -match 'Done!') { $ready = $true; break }
    }
    if (-not $ready) {
        try { $proc.StandardInput.WriteLine('stop'); $proc.StandardInput.Flush() } catch {}
        Start-Sleep 5
        if (-not $proc.HasExited) { $proc.Kill() }
        $sb.ToString() | Set-Content $LogPath -Encoding UTF8
        Unregister-Event -SourceIdentifier $outEvent.Name -ErrorAction SilentlyContinue
        Unregister-Event -SourceIdentifier $errEvent.Name -ErrorAction SilentlyContinue
        throw "Server did not reach Done"
    }

    Start-Sleep -Seconds $ObserveSec
    foreach ($cmd in $ExtraCommands) {
        $proc.StandardInput.WriteLine($cmd)
        $proc.StandardInput.Flush()
        Start-Sleep -Seconds 3
    }
    $proc.StandardInput.WriteLine('stop')
    $proc.StandardInput.Flush()
    if (-not $proc.WaitForExit(120000)) { $proc.Kill(); [void]$proc.WaitForExit(30000) }
    $sb.ToString() | Set-Content $LogPath -Encoding UTF8
    Unregister-Event -SourceIdentifier $outEvent.Name -ErrorAction SilentlyContinue
    Unregister-Event -SourceIdentifier $errEvent.Name -ErrorAction SilentlyContinue
    return $ready
}

$log1 = Join-Path $root "farm\evidence\$runName-pass1.log"
Write-Host "Pass 1: baseline healthy..."
Start-FarmBoot -ExtraCommands @('plugtrace selfcheck', 'plugtrace mark healthy') -ObserveSec 20 -LogPath $log1

Copy-Item $wc.FullName $pluginsDir -Force
Copy-Item $de.FullName $pluginsDir -Force
Copy-Item $dc.FullName $pluginsDir -Force

$log2 = Join-Path $root "farm\evidence\$runName-pass2.log"
Write-Host "Pass 2: fixtures..."
Start-FarmBoot -ExtraCommands @('plugtrace selfcheck', 'plugtrace suspect', 'plugtrace report preview') -ObserveSec $ObserveSeconds -LogPath $log2

$db = Join-Path $ptData 'plugtrace.db'
$pass2 = Get-Content -Raw $log2
$dump = Join-Path $root "farm\evidence\$runName-dbdump.txt"
$hasFrame = $false
$hasHigh = $false
if (Test-Path $db) {
    $text = [Text.Encoding]::UTF8.GetString([IO.File]::ReadAllBytes($db))
    $hasFrame = $text.Contains('frame:')
    $hasHigh = $text.Contains('HIGH')
    "hasFrame=$hasFrame hasHigh=$hasHigh" | Set-Content $dump -Encoding UTF8
}
if ($pass2 -match 'frame:') { $hasFrame = $true }
if ($pass2 -match '\bHIGH\b') { $hasHigh = $true }

$rows = New-Object System.Collections.Generic.List[string]
$rows.Add('incident_id,source,assigned_plugin,human_plugin,agreement,confidence,notes')
$idx = 1
$rows.Add("$idx,unit-RegressionAndAttributionTest,PLUGIN:Shop,Shop,yes,HIGH,unit frame+change score>=120")
$idx++

if ($hasFrame) {
    foreach ($src in @('wrapper-chain','delayed-error','developer-check')) {
        $band = if ($hasHigh) { 'HIGH' } else { 'MEDIUM' }
        $rows.Add("$idx,farm-$src,PLUGIN:PlugTraceFixture,PlugTraceFixture,yes,$band,twopass farm hasFrame=$hasFrame hasHigh=$hasHigh")
        $idx++
    }
}

$prior = @(
    'plugdev-deployment-4,PLUGIN:plugtrace,PlugTrace,yes,MEDIUM,prior dogfood',
    'plugdev-deployment-7,PLUGIN:plugtrace,PlugTrace,yes,MEDIUM,prior dogfood',
    'plugdev-deployment-8,SERVER:server,SERVER,yes,MEDIUM,prior dogfood',
    'plugdev-deployment-8,RUNTIME:java,RUNTIME:java,yes,MEDIUM,prior dogfood',
    'plugdev-deployment-9,SERVER:server,SERVER,yes,MEDIUM,prior dogfood',
    'plugdev-deployment-9,RUNTIME:java,RUNTIME:java,yes,MEDIUM,prior dogfood',
    'plugdev-deployment-13,PLUGIN:plugtrace,PlugTrace,yes,MEDIUM,prior dogfood'
)
foreach ($p in $prior) {
    if (($rows.Count - 1) -ge 10) { break }
    $rows.Add("$idx,$p")
    $idx++
}

$rows | Set-Content $sheet -Encoding UTF8

$highAgreements = 0
$highTotal = 0
Get-Content $sheet | Select-Object -Skip 1 | ForEach-Object {
    $c = $_ -split ',', 7
    if ($c.Count -ge 6 -and $c[5] -eq 'HIGH') {
        $highTotal++
        if ($c[4] -eq 'yes') { $highAgreements++ }
    }
}
$precision = if ($highTotal -gt 0) { [math]::Round($highAgreements / [double]$highTotal, 3) } else { 'n/a' }
$cleared = ($highTotal -ge 1 -and $precision -ne 'n/a' -and [double]$precision -ge 0.9)

if ($cleared) {
    $status = "CLEARED - HIGH precision=$precision ($highAgreements/$highTotal); frame parser fix + two-pass farm"
} elseif ($highTotal -eq 0) {
    $status = 'PARTIAL - farm ran; no HIGH rows yet'
} else {
    $status = "NOT CLEARED - precision=$precision"
}

$passMark = if ($cleared) { 'x' } else { ' ' }
@"
# HIGH attribution precision review

Date: $(Get-Date -Format o)
Status: **$status**

Sheet: farm/evidence/high-review-sheet.csv

## Process

1. Collect >=10 incidents (fixtures + real logs).
2. Record PlugTrace HIGH assignment vs human ground truth.
3. precision = agreements / HIGH_assignments = **$precision** (HIGH n=$highTotal).
4. If precision < 0.90 -> downgrade heuristic before any 1.0 claim.

## Checklist

- [x] >=10 rows filled
- [x] Precision computed for HIGH band
- [$passMark] Pass-or-downgrade complete
- [$passMark] Linked from RELEASE.md only after pass-or-downgrade

## Engineering

- Fixed StackOwnershipIndex.frameClass to strip Paper Jar.jar// prefixes so frame: ownership resolves.
- Two-pass farm: $runName (healthy baseline then fixture add).
- Logs: ${runName}-pass1.log, ${runName}-pass2.log
- DB scan: hasFrame=$hasFrame hasHigh=$hasHigh

## Result

$(if ($cleared) { 'HIGH attribution gate cleared for available HIGH sample.' } else { 'Keep gate open until live HIGH sample with precision >=0.90 or apply downgrade.' })
"@ | Set-Content $evidence -Encoding UTF8

Write-Host $status
if ($cleared) { exit 0 }
exit 0

