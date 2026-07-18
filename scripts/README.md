# PlugTrace farm scripts

Pinned-JDK ephemeral server farm for local compatibility checks.

| Script | Purpose |
|--------|---------|
| `Resolve-JavaHome.ps1` | Locate Temurin 17 / 21 / 25 |
| `Build-Spigot.ps1` | SpigotMC BuildTools → `farm/jars/spigot-*.jar` |
| `Download-PaperFamily.ps1` | Paper / Folia / Purpur API download |
| `Try-DownloadPufferfish.ps1` | Best-effort Pufferfish probe |
| `Invoke-EphemeralFarm.ps1` | Boot → smoke commands → stop |
| `Invoke-FoliaObservation.ps1` | Single-plugin Folia observation window |
| `Log-SoakDay.ps1` | Append a local soak day row under `farm/evidence/` |

## Java pins

| Work | JAVA major |
|------|------------|
| Spigot 1.20.x + `bukkit-modern` | **17** |
| Paper/Purpur 1.21.x, Folia | **21** |
| Paper 26.1.x | **25** |

## Fixture farm

```powershell
$env:JAVA_HOME = (.\scripts\Resolve-JavaHome.ps1; Get-PlugTraceJavaHome 21)
.\gradlew.bat :fixtures:delayed-error:jar :fixtures:config-reset:jar
```

Pass fixtures with `Invoke-EphemeralFarm.ps1 -FixtureJars @('fixtures\...\build\libs\....jar')`.
