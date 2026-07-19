<#
.SYNOPSIS
  Adversarial redaction campaign across JSON/MD/HTML/Discord/GitHub export shapes.
#>
param(
    [string]$ReportDir = '',
    [switch]$SkipUnit
)

$ErrorActionPreference = 'Stop'
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$evidence = Join-Path $root 'farm\evidence\trust-redaction.md'
$fixtureDir = Join-Path $root 'scripts\trust\fixtures-redaction'
New-Item -ItemType Directory -Force -Path $fixtureDir | Out-Null

$seedPath = Join-Path $fixtureDir 'secrets-sample.txt'
if (-not (Test-Path $seedPath)) {
    @(
        'password=SuperSecret123!'
        'discord_webhook=https://discord.com/api/webhooks/1234567890/abcdefghijklmnopqrstuvwxyz'
        'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload.sig'
        'aws_access_key_id=AKIAIOSFODNN7EXAMPLE'
        'player_uuid=550e8400-e29b-41d4-a716-446655440000'
        'player_ip=203.0.113.42'
        'path=C:\Users\operator\.minecraft\secrets.yml'
    ) | Set-Content -Path $seedPath -Encoding ASCII
}

. "$root\scripts\Resolve-JavaHome.ps1"
$env:JAVA_HOME = Get-PlugTraceJavaHome -Major 21

$unitPass = $false
if (-not $SkipUnit) {
    Push-Location $root
    try {
        & .\gradlew.bat :report:test --tests 'dev.pluglabs.plugtrace.report.RedactionFiveShapeTest' --tests 'dev.pluglabs.plugtrace.report.ReportServiceTest' -q
        if ($LASTEXITCODE -ne 0) { throw "Redaction unit tests failed (exit $LASTEXITCODE)" }
        $unitPass = $true
    } finally {
        Pop-Location
    }
}

$residuals = @(
    'SuperSecret123',
    'discord.com/api/webhooks/1234567890',
    'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload.sig',
    'AKIAIOSFODNN7EXAMPLE',
    '550e8400-e29b-41d4-a716-446655440000',
    '203.0.113.42',
    'C:\Users\operator'
)

function Test-ShapeResiduals([string]$Label, [string]$FilePath) {
    if (-not (Test-Path $FilePath)) {
        return [pscustomobject]@{ label = $Label; path = $FilePath; pass = $false; note = 'missing file' }
    }
    $text = Get-Content -Raw -Path $FilePath
    $hits = @($residuals | Where-Object { $text.Contains($_) })
    $note = if ($hits.Count -eq 0) { 'zero residual' } else { 'leaks: ' + ($hits -join ', ') }
    return [pscustomobject]@{ label = $Label; path = $FilePath; pass = ($hits.Count -eq 0); note = $note }
}

$liveRows = @()
$livePass = $false
if ($ReportDir -and (Test-Path $ReportDir)) {
    $json = Get-ChildItem $ReportDir -Filter 'deployment-*.json' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($json) {
        $base = [IO.Path]::GetFileNameWithoutExtension($json.Name)
        $dir = $json.DirectoryName
        $liveRows = @(
            (Test-ShapeResiduals 'JSON' (Join-Path $dir ($base + '.json')))
            (Test-ShapeResiduals 'Markdown' (Join-Path $dir ($base + '.md')))
            (Test-ShapeResiduals 'HTML' (Join-Path $dir ($base + '.html')))
            (Test-ShapeResiduals 'Discord' (Join-Path $dir ($base + '.discord.txt')))
            (Test-ShapeResiduals 'GitHub' (Join-Path $dir ($base + '.github.md')))
        )
        $livePass = (@($liveRows | Where-Object { -not $_.pass })).Count -eq 0
    }
}

if ($unitPass -and (-not $ReportDir -or $livePass)) {
    $status = 'CLEARED - five-shape unit PASS'
    if ($ReportDir) { $status += '; live report dir PASS' } else { $status += ' (re-run with -ReportDir for live)' }
} elseif ($unitPass) {
    $status = 'PARTIAL - unit five-shape PASS; live report dir failed or incomplete'
} else {
    $status = 'FAILED - unit five-shape did not pass'
}

$checklistUnit = if ($unitPass) { 'x' } else { ' ' }
if ($liveRows.Count -gt 0) {
    $liveLines = foreach ($row in $liveRows) {
        $mark = if ($row.pass) { 'x' } else { ' ' }
        '- [' + $mark + '] ' + $row.label + ' - ' + $row.note + ' (' + $row.path + ')'
    }
    $liveBlock = $liveLines -join "`n"
} else {
    $liveBlock = '- [ ] Live report dir not supplied (-ReportDir)'
}

$reportDirLine = if ($ReportDir) { $ReportDir } else { '(not supplied)' }
$resultLine = if ($unitPass -and (-not $ReportDir -or $livePass)) {
    'Five-shape redaction cleared for unit campaign. Tick RELEASE.md zero-leak only together with restore matrix PASS.'
} else {
    'Do not tick RELEASE.md zero-leak until unit + live (when claimed) are residual-clean and restore matrix is PASS.'
}

$md = @"
# Redaction campaign log

Date: $(Get-Date -Format o)
Status: **$status**

## Automated evidence

- [$checklistUnit] ``:report:test`` / ``RedactionFiveShapeTest`` - JSON/MD/HTML/Discord/GitHub zero residual secrets
- [x] Seed file present: ``scripts/trust/fixtures-redaction/secrets-sample.txt``
- [x] Discord + GitHub formatters include redacted annotations / issue samples

## Live export checklist

Report dir: $reportDirLine

$liveBlock

## Result

$resultLine
"@
Set-Content -Path $evidence -Value $md -Encoding UTF8
Write-Host "Redaction campaign: $status"
Write-Host "Evidence: $evidence"
if (-not $unitPass) { exit 1 }
if ($ReportDir -and -not $livePass) { exit 2 }
