# PlugTrace farm scripts

Pinned-JDK ephemeral server farm for compatibility evidence. Marketplace claims stay frozen.

| Script | Purpose |
|--------|---------|
| `Resolve-JavaHome.ps1` | Locate Temurin 17 / 21 / 25 |
| `Build-Spigot.ps1` | SpigotMC BuildTools → `farm/jars/spigot-*.jar` |
| `Download-PaperFamily.ps1` | Paper / Folia / Purpur API download |
| `Try-DownloadPufferfish.ps1` | Best-effort Pufferfish probe (Unverified OK) |
| `Invoke-EphemeralFarm.ps1` | Boot → smoke commands → stop → evidence md |
| `Invoke-FoliaObservation.ps1` | Single-plugin Folia 15-minute observation window |
| `Log-SoakDay.ps1` | Append day row to `farm/evidence/soak-log.md` |
| `trust/*` | Phase C campaign scaffolds (redaction / restore / HIGH / perf) |

## Java pins

| Work | JAVA major |
|------|------------|
| Spigot 1.20.1 / 1.20.4 + `bukkit-modern` | **17** |
| Paper/Purpur 1.21.x, Folia | **21** |
| Paper 26.1.2 | **25** |

Never Certified-claim Paper 26.2 experimental.

## Fixture farm

Build all fixtures:

```powershell
$env:JAVA_HOME = (.\scripts\Resolve-JavaHome.ps1; Get-PlugTraceJavaHome 21)  # or set manually
.\gradlew.bat :fixtures:delayed-error:jar :fixtures:config-reset:jar :fixtures:missing-service:jar `
  :fixtures:command-loss:jar :fixtures:wrapper-chain:jar :fixtures:developer-check:jar :fixtures:unsafe-migration:jar
```

Drop a fixture into `Invoke-EphemeralFarm.ps1 -FixtureJars @('fixtures\...\build\libs\....jar')`.
