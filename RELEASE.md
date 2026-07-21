# PlugTrace 1.0.0 product / first-public gates

This branch may produce dogfood artifacts. Marketplace pages publish only after soak + trust + owner freeze lift (**D-033** / **D-035**). Prefer first Modrinth/Hangar/Spigot list as **0.5.1** after soak; **v1.0.0** marks the ritual + spark-like viewer product bar.

## Automated gate

- [x] Fresh `clean matrixSmoke` on pinned JDK 21 - evidenced 2026-07-19 for **0.5.1** (`JAVA_HOME` Temurin 21.0.11; three fat JARs + unit suites); re-run for **1.0.0** before attaching release assets
- [x] `pnpm install && pnpm run build` from `web-ui` - evidenced 2026-07-17 (`index-DSoURs67.js` / `index-K_Orikfw.css`); rebuild for 1.0.0 Overview ritual
- [x] UI assets present inside all server JARs - `web/index.html` + assets in paper/folia/bukkit-modern JARs
- [x] SQLite v2 -> v3 upgrade and interrupted migration tests - covered by `:storage-sqlite:test` / domain suite
- [x] Report schema 1.0.0 validation while retaining old schemas - `:report:test`
- [x] Descriptor/permission/artifact inspection - paper/folia/bukkit artifact tests + `printArtifacts`
- [x] Web auth/origin/CSP/CORS/traversal/XSS/port tests - `:paper-modern:test` web security suite
- [x] Redaction fuzz and recovery interruption tests - report + core-domain restore suites

## Live gate (blocks marketplace)

- [ ] Seven-day Paper/Folia soak - Paper **day 3/7** (2026-07-20); Folia **day 2/7**; automator waiting for next calendar days (`scripts/Invoke-SoakCalendar.ps1`, `soak-log.md`); do not tick until day 7 both targets
- [ ] Every compatibility claim launched on a real ephemeral server - Spigot 1.20.1+1.20.4 and pinned Paper/Purpur/Folia rows evidenced 2026-07-17; Pufferfish: do not claim (unavailable 2026-07-18; see pufferfish-availability.md)
- [x] Roadmap fixture farm modes on Paper 1.21.4 - evidenced 2026-07-18 (farm/evidence/paper-fixture-*.md PASS)
- [x] Zero known secret leaks and zero lost restore originals - redaction five-shape **CLEARED** (`trust-redaction.md`); restore interrupt matrix **CLEARED** unit (`trust-restore-interrupt.md`)
- [x] `HIGH` attribution precision at least 90%, otherwise downgrade - precision **1.0** (`trust-high-attribution.md`)
- [x] Perf gates measured - syncTick p95 &lt; 1 ms + memory/event proxies PASS (`trust-perf-gates.md`)

## Product bar (v1.0.0 tag — does not alone unlock marketplace)

- [x] Ritual-first status / Overview + noise policy (`NOISE_POLICY.md`)
- [x] Spark-like hosted report viewer (lenses + deep-links + local JSON)

## Post-list dogfood (D-035 — does not block first Modrinth/Hangar/Spigot listing)

- [ ] Thirty days on ten real servers - tracker: `farm/evidence/one-point-oh-tracker.md` (server #1 enrolled; outreach: `TRACKER_OUTREACH.md`)
- [ ] Ten real incidents manually reviewed - continue beyond farm/unit sample in `high-review-sheet.csv`
- [ ] Three plugin developers accept report 1.0.0 - tracker + `TRACKER_OUTREACH.md`

The repository owner must explicitly lift the public-launch freeze before marketplace publish.
