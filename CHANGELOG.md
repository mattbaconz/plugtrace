# Changelog

## Unreleased

## 1.0.2 - 2026-08-09

Point release: share upload reliability, clearer chat spacing, honest LOW-band for change-only suspects.

### [changed]
- Console/chat ritual output uses blank section breaks (status, suspect, share, FAILING) so blocks are easier to scan

### [fixed]
- Console status/diff no longer soft-wraps mid-path without a PlugTrace prefix (compact change rows + path-aware wrap)
- `/plugtrace reload` now rebinds the live config instance so `expected.plugins` patches affect verify (was stuck on boot-time empty lists)
- `/plugtrace share` report render no longer dies on verification/incident `Instant` fields (Gson)
- Change-only binary churn no longer ranks as MEDIUM suspect confidence (stays LOW without ownership)

## 1.0.1 - 2026-08-08

Point release: anonymous bStats + lighter exception/ingest/SSE hot paths. Same single-jar Modrinth story as 1.0.0.

### [added]
- Anonymous bStats metrics (plugin id 32755) with privacy-safe pies (server family, web/cloud on/off); disable via `metrics.enabled: false` or global `plugins/bStats/config.yml`

### [changed]
- Exception capture defers stack formatting to the async worker, rate-limits full stacks during storms, and debounces ingest SQLite/suspect recompute
- Local web SSE status publish already skips work when no `/events` clients are connected
- Release jar ~4MB (was ~17MB): trimmed SQLite natives to Win/Linux/macOS x64 + Linux/macOS arm64; use server Gson instead of shading Jackson

## 1.0.0 - 2026-08-06

First marketplace release candidate. Deployment-safety ritual + spark-like hosted reports. Soak/trust CLEARED; freeze lifted 2026-07-24.

### [added]
- Ritual-first `/plugtrace status` and local web Overview (health, changes, suspect, next commands)
- `/plugtrace share` (hosted encrypted upload — explicit only); FAILING deep-link `?lens=checks`
- Hosted viewer on plugtrace.dev + local Overview FAILING/HEALTHY hero surfaces
- Op-join FAILING/DEGRADED digest until `/plugtrace incidents ack|ignore`
- Opt-in Discord webhook notify (`notify.*`) — redacted FAILING digest, 1/deploy
- Checkpoint / expected capture / conservative restore workflow
- Soft integrations: PlaceholderAPI, Vault/LuckPerms checks, updater coexistence note

### [changed]
- Share UX is capability-URL-first (`#k=` required); compact FAILING ritual chat
- `/plugtrace verify run` finalizes observation; `verify early` for mid-window probes
- Checkpoint on UNKNOWN auto-marks HEALTHY then creates (FAILING/DEGRADED/CRASHED still refused)

### [fixed]
- Early verify probes no longer downgrade finalized HEALTHY to UNKNOWN
- Expected capture only records registered commands (avoids Essentials unbound-command FAILING)
- RCON/console Adventure ANSI gradients (no raw `§x` dumps)
- Local web status JSON serializes `Instant` correctly

## 0.5.1 - 2026-07-19

External dogfood point-release. Marketplace still frozen until soak day 7 + owner freeze lift (**D-035**: trackers continue after listing).

### [added]
- Soft integrations: PlaceholderAPI `%plugtrace_*%` when present; Vault economy/permissions + LuckPerms API soft verification checks; updater coexistence annotation (AutoUpdatePlugins and similar)
- Wrapper-registry prefixes for FAWE, Multiverse, ViaVersion, Geyser/Floodgate, Skript, MythicMobs, GriefDefender, ChestSort
- Reverse-proxy note for local web UI (`plugtrace-docs/WEB_REVERSE_PROXY.md`)
- D-035 marketplace bar (soak + trust + freeze; 10×30 / 3-dev post-list)

### [changed]
- `PUBLISH_CHECKLIST.md` / `RELEASE.md` align with D-035 (SpigotMC included; trackers post-list)
- Hosted purge: document HTTP cron + `wrangler.jsonc` cron reminder (OpenNext still needs HTTP purge job)

### [fixed]
- (none yet beyond 0.5.0 trust campaign follow-through)

## 0.5.0 - 2026-07-18

External dogfood packaging. Marketplace listings stay frozen until seven-day soak, trust gates, and owner freeze lift.

### [added]
- Fixture farm evidence PASS on Paper 1.21.4 for delayed-error, config-reset, missing-service, command-loss, wrapper-chain, developer-check, unsafe-migration, event-throw, missing-dependency, same-version-binary, and enable-fail
- Hosted purge API `/api/v1/cron/purge` for expired ciphertext reports (privacy ADR Accepted)
- Claim-mapped marketplace drafts from farm evidence (still unpublished)
- First-HEALTHY console ritual (checkpoint + expected capture prompt) and FAILING/DEGRADED console surface (failed checks, JAR deltas, restore + report upload next steps)
- Developer/host adoption kit (issue template, Discord paste, schema acceptance checklist, demo narrative)

### [changed]
- Release status: early public / external dogfood at **0.5.0** (listings still frozen until soak day 7 + trust + owner freeze lift)
- Concept packaging locked (D-034): after-update PASS/FAIL ritual; spark = lag, PlugTrace = what changed / what died; install-before-break
- Pufferfish: probed unavailable → **do not claim** until farm PASS
- Spigot 1.20.1 / 1.20.4 kept as Experimental dogfood (PASS boots; not Certified soak)
- Hosted report defaults remain `https://plugtrace.dev/`; report preview/upload copy framed as spark-like share

### [fixed]
- Feature catalog and marketplace drafts aligned to passing matrix rows only (no Pufferfish claim; Spigot Experimental only)
- Public clone messaging synced to early public release language (private alpha wording scrubbed where it contradicted shipped 0.5 packaging)
- Artifact tests pin `plugin.yml` version to **0.5.1**; Folia/Bukkit `processResources` and paper `sourcesJar`/`jar` depend on `:paper-modern:copyWebUi` so `clean matrixSmoke` packages the web UI reliably
- Redaction: bare AWS access keys (`AKIA`/`ASIA`) and `aws_access_key_id` labels now scrubbed (live five-shape campaign PASS 2026-07-19)
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
