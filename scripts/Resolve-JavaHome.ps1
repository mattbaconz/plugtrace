# Resolves pinned JDK homes for PlugTrace ephemeral farm work.
# Prefer scoop Temurin pins, then Eclipse Adoptium Program Files.

function Get-PlugTraceJavaHome {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet('17', '21', '25')]
        [string]$Major
    )

    $candidates = switch ($Major) {
        '17' {
            @(
                'C:\Users\mattbaconz\scoop\apps\temurin17-jdk\current',
                'C:\Program Files\Eclipse Adoptium\jdk-17.0.19+10-hotspot',
                'C:\Program Files\Eclipse Adoptium\jdk-17'
            )
        }
        '21' {
            @(
                'C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot',
                'C:\Users\mattbaconz\scoop\apps\temurin21-jdk\current'
            )
        }
        '25' {
            @(
                'C:\Users\mattbaconz\scoop\apps\temurin25-jdk\current',
                'C:\Users\mattbaconz\scoop\apps\openjdk25\current'
            )
        }
    }

    foreach ($root in $candidates) {
        $java = Join-Path $root 'bin\java.exe'
        if (Test-Path $java) {
            return (Resolve-Path $root).Path
        }
    }

    # Fallback: scan Adoptium / scoop for matching major
    $scanRoots = @(
        'C:\Program Files\Eclipse Adoptium',
        'C:\Users\mattbaconz\scoop\apps'
    )
    foreach ($scan in $scanRoots) {
        if (-not (Test-Path $scan)) { continue }
        $hit = Get-ChildItem $scan -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match "jdk-?$Major|temurin$Major|openjdk$Major" } |
            Select-Object -First 1
        if ($hit) {
            $java = Join-Path $hit.FullName 'bin\java.exe'
            if (-not (Test-Path $java) -and (Test-Path (Join-Path $hit.FullName 'current\bin\java.exe'))) {
                return (Resolve-Path (Join-Path $hit.FullName 'current')).Path
            }
            if (Test-Path $java) { return $hit.FullName }
        }
    }

    throw "No JDK $Major found. Install Temurin $Major (scoop bucket java / Adoptium)."
}

function Get-PlugTraceJavaExe {
    param([Parameter(Mandatory = $true)][ValidateSet('17', '21', '25')][string]$Major)
    $home = Get-PlugTraceJavaHome -Major $Major
    return (Join-Path $home 'bin\java.exe')
}
