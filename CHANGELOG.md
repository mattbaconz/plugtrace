# Changelog

## 0.5.0 - 2026-07-18

External dogfood packaging. Marketplace listings stay frozen until seven-day soak, trust gates, and owner freeze lift.

### [added]
- Fixture farm evidence PASS on Paper 1.21.4 for delayed-error, config-reset, missing-service, command-loss, wrapper-chain, developer-check, unsafe-migration, event-throw, missing-dependency, same-version-binary, and enable-fail
- Hosted purge API `/api/v1/cron/purge` for expired ciphertext reports (privacy ADR Accepted)
- Claim-mapped marketplace drafts from farm evidence (still unpublished)

### [changed]
- Release status: early public / external dogfood at **0.5.0** (listings still frozen until soak day 7 + trust + owner freeze lift)
- Pufferfish: probed unavailable → **do not claim** until farm PASS
- Spigot 1.20.1 / 1.20.4 kept as Experimental dogfood (PASS boots; not Certified soak)
- Hosted report defaults remain `https://plugtrace.dev/`

### [fixed]
- Feature catalog and marketplace drafts aligned to passing matrix rows only (no Pufferfish claim; Spigot Experimental only)
- Public clone messaging synced to early public release language (private alpha wording scrubbed where it contradicted shipped 0.5 packaging)
- Artifact tests pin `plugin.yml` version to **0.5.0**; Folia/Bukkit `processResources` and paper `sourcesJar`/`jar` depend on `:paper-modern:copyWebUi` so `clean matrixSmoke` packages the web UI reliably

## 0.4.0 — private alpha (unlisted)

- Repositioned around checkpoint → verify → explain → report → recover.
- Added SQLite schema v3 checkpoints, verifications, and incidents.
- Added value-free structural config evidence and conservative reset detection.
- Added expected-state built-ins and public developer verification/migration APIs.
- Added granular permissions and checkpoint/verify/expected/incidents/web commands.
- Published report schema 1.0.0 while retaining earlier schemas.
- Added bundled local React/Vite/Primer web UI and scoped hashed bearer tokens.
- Added lifecycle reconstruction, issue ownership/wrapper evidence, runtime incident aggregation, recovery verification, and bundled-spark detection.
- Added Java 17 bytecode for the public/shared and `bukkit-modern` artifacts while retaining Java 21 modern platform artifacts.
- Dogfood-tested Paper 1.21.4, Paper 26.1.2, Purpur 1.21.4, Folia 1.21.11, and the Bukkit adapter on Paper 1.20.4; none of these rows bypasses soak gates.
- Kept compatibility, soak, security, and marketplace claims frozen pending evidence.
- **2026-07-17 release-readiness pass:** `/plugtrace reload` with validation + effective config in selfcheck/web status; `expected capture` snapshots live commands/services; `privacy.mode: hash-only`; web UI Diff/Checkpoints/Timeline + poll refresh; Gradle `:paper-modern:copyWebUi`; automated RELEASE.md gates checked.
- **2026-07-18 hosted report MVP (D-032):** optional `/plugtrace report upload` sends a redacted, encrypted report bundle to [plugtrace.dev](https://plugtrace.dev) and returns a shareable link. Explicit administrator action only — no automatic upload on report generation or background sync.

## 0.3.0 — private dogfood (unlisted)

Phases 3–5 user-visible for internal operators. Marketplace listing still blocked.

### [added]
- PlugDev release identity ingest (`plugdev-identity.json`) on reports and status
- Three artifacts: `paper-modern`, `folia` (separate scheduler artifact), `bukkit-modern` (Experimental)
- Folia-safe `SchedulerFacade` for store I/O; `matrixSmoke` build gate
- Phase 5 JAR retention under `plugins/PlugTrace/retained/`
- Restore workflow: `preview` → `stage confirm` → **offline finalize** → `verify` → `complete`
- `/plugtrace restore abort` restores `*.plugtrace-original`
- Selfcheck performance block: queue depth, dropped events, last snapshot ms, DB size, retained JAR bytes
- Offline tool: `OfflineRestoreFinalizer` / `./gradlew :core-domain:finalizeRestore`

### [changed]
- In-plugin `restore finalize` refuses live JAR swaps while the server is running (points at offline finalize)
- Staging requires explicit `confirm` / `--confirm` after reviewing migration warnings
- Post-restore `complete` tags deployment `restored-from-baseline` and annotates ops

### Deferred

- Hangar / Modrinth, Velocity, automatic rollback, world/DB restore, code signing
