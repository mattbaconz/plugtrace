# PlugTrace upgrade / forward-compat checklist

## 0.3.0 to 0.5.0

- SQLite migrates transactionally from schema v2 to v3 and retains deployments, issues, annotations, and reports.
- v3 adds `checkpoints`, `verifications`, and `incidents` tables.
- Report writers emit schema **1.0.0**; readers should continue accepting 0.1.0, 0.2.0, and 0.2.1.
- Existing `plugtrace.admin` retains every new child permission.
- Web defaults to `127.0.0.1:9465` and rejects API requests until a console-created token is supplied.
- A checkpoint is a deployment reference, not a world or full-server backup.

Private dogfood - keep this in sync with schema claims.

## Database

| Item | Value |
|------|-------|
| Deployment `schema_version` | **3** |
| Storage | SQLite WAL (`plugins/PlugTrace/plugtrace.db`) |
| Migration | Additive only; do not delete columns without a bump |

On upgrade: copy `plugtrace.db` (and `retained/`, `restore-journal/` if present). Run `/plugtrace selfcheck` -> `integrity=ok`.

## Reports

| Schema | Notes |
|--------|-------|
| 0.1.0 | Historical |
| 0.2.0 | executiveSummary, annotations, spark, scope, platform |
| 0.2.1 | optional `release` (PlugDev), optional `pluginFields` |
| **1.0.0** | Current writers - verification, incidents, privacy, capability declarations |

Older schema files remain under `schemas/` for parsers.

## Artifacts

Upgrade PlugTrace JAR in place; keep matching artifact family (`paper-modern` / `folia` / `bukkit-modern`). Folia servers must use the Folia JAR.

## Phase 5 restore data

- `plugins/PlugTrace/retained/` - prior JAR hashes (local only)
- `plugins/PlugTrace/restore-journal/` - staged restore journals

Safe to keep across upgrades. Interrupted restore: prefer `/plugtrace restore abort` before deleting journals.

Supported apply path: stop server -> offline finalize (`:core-domain:finalizeRestore` or `OfflineRestoreFinalizer`) -> start -> `/plugtrace restore verify` -> `complete`.

## Product version

| Product | Notes |
|---------|-------|
| **0.3.0** | Private dogfood RC (Phases 3-5). Report schema **0.2.1**. |
| **0.5.0** | Private alpha (unlisted). SQLite **v3**, report schema **1.0.0**, local web UI. Soak pending. |

## Server / plugin upgrades

1. `/plugtrace mark healthy` on a known-good build
2. Upgrade server or plugins
3. Restart -> `/plugtrace diff` / `suspect` / `report`
4. Optional: `/plugtrace restore preview` if reverting JARs toward baseline
