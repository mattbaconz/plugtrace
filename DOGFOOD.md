# PlugTrace Phase 4/5 dogfood notes

Started: 2026-07-13  
Updated: 2026-07-17  
Artifacts: `paper-modern`, `folia` (dogfood verified; soak day-0 started), `bukkit-modern` (Spigot 1.20.1 + 1.20.4 live PASS) - **0.5.0** (private alpha, unlisted)  
Public listing: **blocked**  
Release checklist: [`RELEASE.md`](RELEASE.md) · Changelog: [`CHANGELOG.md`](CHANGELOG.md)  
Farm scripts: [`scripts/README.md`](scripts/README.md) · Soak log: [`farm/evidence/soak-log.md`](farm/evidence/soak-log.md)

## Validation

| Check | Result |
|-------|--------|
| `./gradlew test` / `matrixSmoke` | Pass on JDK 21 (2026-07-17) - UI build + unit/web/report suites |
| Three fat JARs + API | Built (`*-0.5.0.jar`) with bundled web assets |
| SchedulerFacade wired for store I/O | Done |
| safeFields + rawSamplesPerIssue | In reports / ingest |
| PlugDev identity | Ingest + CLI writer present |
| Phase 5 restore (confirm -> offline finalize -> complete) | Commands + unit tests |
| `/plugtrace reload` + expected live capture | Done (2026-07-17) |
| Web Diff / Checkpoints / Timeline | Done (2026-07-17) |
| Spigot 1.20.4 / Temurin 17 | **PASS** 2026-07-17 (`farm/evidence/spigot-1.20.4-j17`) |
| Spigot 1.20.1 / Temurin 17 | **PASS** 2026-07-17 (`farm/evidence/spigot-1.20.1-j17`) |
| Paper 1.21.4 / Temurin 21 | **PASS** (`farm/evidence/paper-1.21.4-j21`) |
| Paper 26.1.2 / Temurin 25 | **PASS** (`farm/evidence/paper-26.1.2-j25`) |
| Purpur 1.21.4 / Temurin 21 | **PASS** (`farm/evidence/purpur-1.21.4-j21`) |
| Pufferfish | **Unverified** (`farm/evidence/pufferfish-availability.md`) |
| Live Paper â‰¥7 days | **Day 0 started** 2026-07-17 - see soak log |
| Folia observation + soak | Observation harness **PASS**; soak day-0 logged |

## Artifact selector

See [`ARTIFACTS.md`](ARTIFACTS.md). Upgrade path: [`UPGRADE.md`](UPGRADE.md).

## Ephemeral farm (pinned JDK)

```powershell
cd plugtrace/plugtrace
# Spigot (Java 17)
.\scripts\Build-Spigot.ps1 -Rev 1.20.4
.\scripts\Invoke-EphemeralFarm.ps1 -ServerJar farm\jars\spigot-1.20.4.jar -JavaMajor 17 `
  -Artifact bukkit-modern\build\libs\PlugTrace-bukkit-modern-0.5.0.jar -RunName spigot-1.20.4-j17

# Paper pin (Java 21)
.\scripts\Download-PaperFamily.ps1 -Project paper -Version 1.21.4 -Build 232
.\scripts\Invoke-EphemeralFarm.ps1 -ServerJar farm\jars\paper-1.21.4-232.jar -JavaMajor 21 `
  -Artifact paper-modern\build\libs\PlugTrace-0.5.0.jar -RunName paper-1.21.4-j21

# Folia observation window (Java 21, PlugTrace-only; default 900s before soak)
.\scripts\Invoke-FoliaObservation.ps1 -ObserveSeconds 900
.\scripts\Log-SoakDay.ps1 -Target folia -Day 0 -HarnessVerified -Notes 'observation PASS'
.\scripts\Log-SoakDay.ps1 -Target paper -Day 0 -HarnessVerified -Notes 'paper-1.21.4-j21 PASS'
```

Fixture expansion (roadmap failure modes) lives under `fixtures/` - build with `.\gradlew.bat :fixtures:<name>:jar` and pass `-FixtureJars`.

## Smoke checklist (Paper)

1. Install `PlugTrace-0.5.0.jar` -> restart
2. `/plugtrace selfcheck` -> integrity ok; scheduler=bukkit; perf lines present
3. `/plugtrace mark healthy`
4. Change a plugin / drop a fixture -> restart
5. `/plugtrace diff` · `suspect` · `report preview`
6. Open `plugins/PlugTrace/reports/deployment-*.html` offline

## Smoke checklist (Folia)

1. Install **only** `PlugTrace-folia-*.jar` (not paper-modern)
2. Restart Folia -> log should say Folia-safe scheduler / dogfood verified (soak pending)
3. `/plugtrace compatibility` -> Running artifact: `folia`; no migrate hint
4. `/plugtrace selfcheck` -> scheduler=folia
5. Confirm paper-modern on Folia prints migrate warning (negative test)
6. Exercise delayed/repeating global work for the full observation window (`verification.observationMinutes`, default 15) before starting the soak clock

## Operator soak (7-day Paper + Folia)

Soak validates live Paper/Folia exits for **0.5.0** - it does **not** block tagging **0.5.0**. Marketplace stays blocked.

Daily cycle (each target):

1. Restart server on the pinned JAR + JDK
2. Deploy or swap one fixture / plugin change
3. Run `/plugtrace diff` · `issues` · `suspect` (or restore drill)
4. Confirm clean stop
5. `.\scripts\Log-SoakDay.ps1 -Target paper|folia -Day N -Notes '...'`

Tick [`RELEASE.md`](RELEASE.md) live soak boxes only when calendar evidence reaches day 7 for both targets.

Trust campaigns (Phase C) scaffolds: `scripts/trust/` - start after day-0 harness verified.

## Dogfood trackers (1.0 path)

- Soak log: [`farm/evidence/soak-log.md`](farm/evidence/soak-log.md) - day 1 logged 2026-07-18 for Paper + Folia; tick RELEASE at day 7 both
- 1.0 servers / incidents / developer acceptances: [`farm/evidence/one-point-oh-tracker.md`](farm/evidence/one-point-oh-tracker.md)
- Trust scaffolds: `farm/evidence/trust-*.md` (harness ready - not cleared)
- Pufferfish: still **Unverified** (`farm/evidence/pufferfish-availability.md`)

## PlugDev E2E (optional)

```yaml
integrations:
  plugtrace:
    enabled: true
    jar: ../pluglabs/plugtrace/plugtrace/paper-modern/build/libs/PlugTrace-0.5.0.jar
    artifact: auto
```

Or dogfood PlugTrace itself from this repo:

```powershell
cd plugtrace   # or plugtrace/plugtrace in the monorepo
npm i          # @plugdev/cli
plugdev setup
.\gradlew.bat :paper-modern:shadowJar
plug run
# console: /plugtrace selfcheck -> report preview -> report upload
```

Public product repo: https://github.com/mattbaconz/plugtrace · Hosted viewer: https://plugtrace.dev

`plugdev run` / `sync` -> `plugins/PlugTrace.jar` + `plugdev-identity.json` -> `/plugtrace status` shows PlugDev commit.

## Restore (Phase 5)

```text
/plugtrace restore preview
/plugtrace restore stage confirm
# clean server stop
./gradlew :core-domain:finalizeRestore -PplugtraceData=plugins/PlugTrace
# or: java -jar core-domain/build/libs/core-domain-0.5.0.jar plugins/PlugTrace
# start
/plugtrace restore verify
/plugtrace restore complete
# when stable: /plugtrace mark healthy
# or abort (before or after stage): /plugtrace restore abort
```

In-plugin `/plugtrace restore finalize` refuses while the server holds JAR locks - offline finalize is the supported path. Originals kept as `*.plugtrace-original`; abort restores them.

## Commands

```text
/plugtrace status|deployments|diff|issues|suspect|issue
/plugtrace report [preview|upload|plugin <name>|discord|github]
/plugtrace mark|annotate|spark|compatibility|selfcheck|restore
```
