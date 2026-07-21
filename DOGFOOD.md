# PlugTrace Phase 4/5 dogfood notes

Started: 2026-07-13  
Updated: 2026-07-19  
Artifacts: `paper-modern`, `folia`, `bukkit-modern` — **0.5.1** (external dogfood)  
Public listing: **blocked** until soak day 7 + owner freeze lift (**D-035**)  
Release checklist: [`RELEASE.md`](RELEASE.md) · Changelog: [`CHANGELOG.md`](CHANGELOG.md)  
Example test server: [`examples/test-server/README.md`](examples/test-server/README.md)  
Farm scripts: [`scripts/README.md`](scripts/README.md) · Soak log: [`farm/evidence/soak-log.md`](farm/evidence/soak-log.md)

## What's next

1. Soak automator through Paper+Folia days **3–7** (`Invoke-SoakCalendar.ps1`; next eligible calendar day after last soak row). Do not invent ticks.
2. Tick soak in `RELEASE.md` when both targets hit day 7.
3. Owner records **freeze lift** in `RELEASE.md` + CHECKPOINT.
4. Publish Modrinth / Hangar / Spigot from drafts with GitHub **v0.5.1** JARs.
5. Trackers (10×30 / 3-dev) continue **after** listing (D-035).

Residual: [`farm/evidence/MARKETPLACE_PUBLISH_RESIDUAL.md`](farm/evidence/MARKETPLACE_PUBLISH_RESIDUAL.md) · Status: [`farm/evidence/SOAK_STATUS.md`](farm/evidence/SOAK_STATUS.md)

## Validation

| Check | Result |
|-------|--------|
| `./gradlew test` / `matrixSmoke` | Pass on JDK 21 (2026-07-19) for **0.5.1** |
| Three fat JARs + API | Built (`*-0.5.1.jar`) with bundled web assets |
| Paper 0.5.1 ephemeral smoke | **PASS** (`farm/evidence/paper-0.5.1-smoke`) |
| Folia 0.5.1 observation | **PASS** (`farm/evidence/folia-observation`) |
| SchedulerFacade wired for store I/O | Done |
| safeFields + rawSamplesPerIssue | In reports / ingest |
| PlugDev identity | Ingest + CLI writer present |
| Phase 5 restore (confirm -> offline finalize -> complete) | Commands + unit tests |
| `/plugtrace reload` + expected live capture | Done |
| Web Diff / Checkpoints / Timeline | Done |
| Spigot 1.20.4 / Temurin 17 | **PASS** (`farm/evidence/spigot-1.20.4-j17`) |
| Spigot 1.20.1 / Temurin 17 | **PASS** (`farm/evidence/spigot-1.20.1-j17`) |
| Paper 1.21.4 / Temurin 21 | **PASS** (`farm/evidence/paper-1.21.4-j21`) |
| Paper 26.1.2 / Temurin 25 | **PASS** (`farm/evidence/paper-26.1.2-j25`) |
| Purpur 1.21.4 / Temurin 21 | **PASS** (`farm/evidence/purpur-1.21.4-j21`) |
| Pufferfish | **Unverified** (`farm/evidence/pufferfish-availability.md`) |
| Live Paper ≥7 days | **Day 2/7** — see soak log; automator waiting |
| Folia soak | **Day 2/7** — observation harness PASS |
| Trust campaigns | Cleared for 0.5.x (`farm/evidence/trust-*.md`) |

## Artifact selector

See [`ARTIFACTS.md`](ARTIFACTS.md). Upgrade path: [`UPGRADE.md`](UPGRADE.md). Release: https://github.com/mattbaconz/plugtrace/releases/tag/v0.5.1

## You test (interactive)

Primary path: [`examples/test-server/README.md`](examples/test-server/README.md) — **PlugDev 1.0.1** · Paper **26.1.2** · Prism **`FO 26.1.2`** · Temurin **25**.

```powershell
cd plugtrace/plugtrace
npm i -D @plugdev/cli@1.0.1
npx plugdev -V   # 1.0.1
$env:JAVA_HOME = (.\scripts\Resolve-JavaHome.ps1; Get-PlugTraceJavaHome 25)
.\gradlew.bat :paper-modern:jar -x test
npx plugdev setup --instance "FO 26.1.2"
npx plugdev run
# console: selfcheck → checkpoint → expected capture → mark healthy
```

Or drop `PlugTrace-0.5.1.jar` from GitHub onto any healthy Paper server.

## We test (agent / farm)

| Layer | Command |
|-------|---------|
| Unit + pack | `.\gradlew.bat clean matrixSmoke` (JDK 21) |
| Example smoke | `.\examples\test-server\Invoke-ExampleRitual.ps1` |
| Paper smoke | `Invoke-EphemeralFarm.ps1` + `PlugTrace-0.5.1.jar` |
| Folia smoke | `Invoke-FoliaObservation.ps1` |
| Soak | `Invoke-SoakCalendar.ps1 -UntilDay 7 -WaitForNextCalendarDay` |

## Ephemeral farm (pinned JDK)

```powershell
cd plugtrace/plugtrace
# Spigot (Java 17)
.\scripts\Build-Spigot.ps1 -Rev 1.20.4
.\scripts\Invoke-EphemeralFarm.ps1 -ServerJar farm\jars\spigot-1.20.4.jar -JavaMajor 17 `
  -Artifact bukkit-modern\build\libs\PlugTrace-bukkit-modern-0.5.1.jar -RunName spigot-1.20.4-j17

# Paper pin (Java 21)
.\scripts\Download-PaperFamily.ps1 -Project paper -Version 1.21.4 -Build 232
.\scripts\Invoke-EphemeralFarm.ps1 -ServerJar farm\jars\paper-1.21.4-232.jar -JavaMajor 21 `
  -Artifact paper-modern\build\libs\PlugTrace-0.5.1.jar -RunName paper-1.21.4-j21

# Folia observation (Java 21, PlugTrace-only; prefer 900s for soak confidence)
.\scripts\Invoke-FoliaObservation.ps1 -ObserveSeconds 900
```

Fixture expansion lives under `fixtures/` — build with `.\gradlew.bat :fixtures:<name>:jar` and pass `-FixtureJars`.

## Smoke checklist (Paper)

1. Install `PlugTrace-0.5.1.jar` → restart
2. `/plugtrace selfcheck` → integrity ok; scheduler=bukkit; perf lines present
3. `/plugtrace mark healthy`
4. Change a plugin / drop a fixture → restart
5. `/plugtrace diff` · `suspect` · `report preview`
6. Open `plugins/PlugTrace/reports/deployment-*.html` offline

## Smoke checklist (Folia)

1. Install **only** `PlugTrace-folia-0.5.1.jar` (not paper-modern)
2. Restart Folia → Folia-safe scheduler / dogfood verified (soak pending)
3. `/plugtrace compatibility` → Running artifact: `folia`
4. `/plugtrace selfcheck` → scheduler=folia
5. Confirm paper-modern on Folia prints migrate warning (negative test)

## Operator soak (7-day Paper + Folia)

Soak validates live Paper/Folia exits for **0.5.1**. Marketplace stays blocked until day 7 + freeze lift.

Daily cycle (each target):

1. Restart server on the pinned JAR + JDK
2. Deploy or swap one fixture / plugin change
3. Run `/plugtrace diff` · `issues` · `suspect` (or restore drill)
4. Confirm clean stop
5. `.\scripts\Log-SoakDay.ps1 -Target paper|folia -Day N -Notes '...'`

Tick [`RELEASE.md`](RELEASE.md) live soak boxes only when calendar evidence reaches day 7 for both targets.

## Dogfood trackers (post-list / 1.0 path)

- Soak log: [`farm/evidence/soak-log.md`](farm/evidence/soak-log.md) — day 2 logged 2026-07-19; tick RELEASE at day 7 both
- 1.0 servers / incidents / developer acceptances: [`farm/evidence/one-point-oh-tracker.md`](farm/evidence/one-point-oh-tracker.md) (post-list per D-035)
- Trust: `farm/evidence/trust-*.md` cleared
- Pufferfish: still **Unverified**

## PlugDev E2E

```yaml
integrations:
  plugtrace:
    enabled: true
    jar: path/to/PlugTrace-0.5.1.jar
    artifact: auto
```

Dogfood this repo:

```powershell
cd plugtrace/plugtrace
npm i -g @plugdev/cli   # if needed
plugdev setup
.\gradlew.bat :paper-modern:jar -x test
plug run
```

Public product repo: https://github.com/mattbaconz/plugtrace · Hosted viewer: https://plugtrace.dev

## Restore (Phase 5)

```text
/plugtrace restore preview
/plugtrace restore stage confirm
# clean server stop
./gradlew :core-domain:finalizeRestore -PplugtraceData=plugins/PlugTrace
# or: java -jar core-domain/build/libs/core-domain-0.5.1.jar plugins/PlugTrace
# start
/plugtrace restore verify
/plugtrace restore complete
# when stable: /plugtrace mark healthy
# or abort: /plugtrace restore abort
```

## Commands

```text
/plugtrace status|deployments|diff|issues|suspect|issue
/plugtrace report [preview|upload|plugin <name>|discord|github]
/plugtrace mark|annotate|spark|compatibility|selfcheck|restore
```
