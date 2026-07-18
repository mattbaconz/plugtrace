# Changelog

## 0.4.0

- Checkpoint → verify → explain → report → recover loop
- SQLite schema v3: checkpoints, verifications, incidents
- Value-free structural config evidence and conservative reset detection
- Expected-state built-ins and public developer verification/migration APIs
- Report schema 1.0.0 (earlier schemas retained for readers)
- Local React/Vite/Primer web UI with hashed bearer tokens
- Optional hosted report upload to plugtrace.dev (explicit only; never automatic)
- Artifacts: `paper-modern`, `folia`, `bukkit-modern`, plus public `api`

## 0.3.0

- PlugDev release identity ingest (`plugdev-identity.json`)
- Folia-safe `SchedulerFacade`; JAR retention and offline restore finalize
- Restore workflow: `preview` → `stage confirm` → offline finalize → `verify` → `complete`
