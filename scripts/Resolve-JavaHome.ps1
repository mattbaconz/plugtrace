# Resolves pinned JDK homes for PlugTrace ephemeral farm work.
# Prefer JAVA_HOME when it matches, then scoop Temurin, then Eclipse Adoptium.

function Get-PlugTraceJavaHome {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet('17', '21', '25')]
        [string]$Major
    )

    $userProfile = $env:USERPROFILE
    $scoopRoot = if ($env:SCOOP) { Join-Path $env:SCOOP 'apps' } else { Join-Path $userProfile 'scoop\apps' }

    $candidates = switch ($Major) {
        '17' {
            @(
                (Join-Path $scoopRoot 'temurin17-jdk\current'),
                'C:\Program Files\Eclipse Adoptium\jdk-17'
            )
        }
        '21' {
            @(
                (Join-Path $scoopRoot 'temurin21-jdk\current'),
                'C:\Program Files\Eclipse Adoptium\jdk-21'
            )
        }
        '25' {
            @(
                (Join-Path $scoopRoot 'temurin25-jdk\current'),
                (Join-Path $scoopRoot 'openjdk25\current'),
                'C:\Program Files\Eclipse Adoptium\jdk-25'
            )
        }
    }

    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        $ver = & (Join-Path $env:JAVA_HOME 'bin\java.exe') -version 2>&1 | Out-String
        if ($ver -match "version `"$Major\.") {
            return (Resolve-Path $env:JAVA_HOME).Path
        }
    }

    foreach ($root in $candidates) {
        $java = Join-Path $root 'bin\java.exe'
        if (Test-Path $java) {
            return (Resolve-Path $root).Path
        }
    }

    $scanRoots = @(
        'C:\Program Files\Eclipse Adoptium',
        $scoopRoot
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
