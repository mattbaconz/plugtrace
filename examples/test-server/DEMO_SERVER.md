# PlugTrace demo PlugDev server

Paper **26.1.2** · flat world · creative · offline · 4G · Prism `FO 26.1.2`  
Product jar: `PlugTrace-1.0.2.jar` (latest UI for filming).

Single entrypoint when filming Modrinth / Spigot assets.

## One-time setup

```powershell
cd plugtrace/plugtrace
npm i -g @plugdev/cli@1.0.1   # or use npx
.\examples\test-server\Setup-DemoServer.ps1
npx --yes @plugdev/cli@1.0.1 setup --instance "FO 26.1.2"
```

`Setup-DemoServer.ps1` will:

1. Wipe `.plugdev/run` (fresh flat world)
2. Build PlugTrace + demo fixtures
3. Install demo deps (LuckPerms, Vault, EssentialsX, PlaceholderAPI, WorldEdit, Multiverse, ViaVersion). spark is Paper-bundled on 26.x. AutoUpdatePlugins optional via `PLUGTRACE_DEMO_AUP_URL`.
4. Stage break jars under `examples/test-server/demo-drop/`

Paper 26.1.2 needs **JDK 25+** (`Resolve-JavaHome.ps1` / Temurin 25).

## Boot

```powershell
cd plugtrace/plugtrace
$env:JAVA_HOME = (.\scripts\Resolve-JavaHome.ps1; Get-PlugTraceJavaHome 25)
npx --yes @plugdev/cli@1.0.1 run
```

Expect: flat void/superflat, creative, OP, PlugTrace + dep stack enable without red errors.

Quick status after detach / second terminal:

```powershell
.\examples\test-server\Invoke-DemoStatus.ps1
.\examples\test-server\Invoke-DemoStatus.ps1 -Verify   # force early verify
```

## Seed healthy baseline (before any screenshots of “good”)

**Easy way:** stop `plug run` if it is attached, then from another shell:

```powershell
.\examples\test-server\Invoke-DemoHealth.ps1 -Mode Healthy
```

That removes the break jar, restarts the server, and runs mark healthy / checkpoint / expected for you.

**Manual way** — only works when the delayed-error fixture is **not** in `.plugdev/run/plugins/` (remove it and restart first):

In the PlugDev console (same terminal / RCON):

```text
plugtrace selfcheck
```

Wait until verification settles **HEALTHY** (first-window prompts may ask for checkpoint).

```text
plugtrace mark healthy
plugtrace checkpoint demo
plugtrace expected capture
plugtrace status
```

Web tokens are **console-only** (not RCON). In the true server console (or PlugDev attached TTY), run:

```text
plugtrace web token create demo admin
```

Open `http://127.0.0.1:9465` (default). Keep the printed token for the browser.

### Healthy screenshot checklist

- [ ] Console: HEALTHY / ritual lines
- [ ] Web Overview: “Did this update work?” healthy
- [ ] Web Deployments / Diff with a full plugin inventory (LuckPerms, spark, WorldEdit, …)

### Thumbnail #2 (checkpoint stack)

Exact middle lines (no UUID / no “full backup” disclaimer):

```powershell
.\examples\test-server\Invoke-DemoThumb2.ps1
```

Or in `plug run` after HEALTHY:

```text
plugtrace checkpoint demo
plugtrace expected capture
plugtrace mark healthy
```

Title: **Checkpoint before you break it** · Subtitle: **Install before the incident.**

## Break → FAILING (demo video punch)

**Easy way:**

```powershell
.\examples\test-server\Invoke-DemoHealth.ps1 -Mode Failing
```

**Manual way:**

1. Stop the server cleanly (`stop` in console, or stop PlugDev).
2. Copy **one** jar from `examples/test-server/demo-drop/` into `.plugdev/run/plugins/`.
   - Prefer `PlugTraceFixture-DelayedError-1.0.0.jar` for a clear FAILING + issue fingerprint.
3. `plugdev run` again (or restart from TUI).
4. After ready (fixture throws ~2s after enable). For filming without waiting the full 15m observation window:

```text
plugtrace verify run
plugtrace status
plugtrace diff
plugtrace suspect
plugtrace report preview
```

Or from a second shell: `.\examples\test-server\Invoke-DemoStatus.ps1 -Verify`

Ops with `plugtrace.view` get a **one-shot join digest** while FAILING/DEGRADED until:

```text
plugtrace incidents ack
```

Optional share (spark-class visual instance on plugtrace.dev):

```text
plugtrace share
```

Copy the **full** printed URL (includes `#k=…` and usually `?lens=checks` when FAILING). Open it for hosted hero stills. Alias of `report upload` — nothing uploads without this command.

Opt-in Discord interrupt (config `notify.discordWebhookUrl`) posts a **redacted** ritual digest on FAILING — never auto `/share`.

### FAILING screenshot checklist

- [ ] Console FAILING surface (failed checks / next commands)
- [ ] Join digest / `incidents ack` sticky clear (optional)
- [ ] Web Overview FAILING (health-colored hero + failed checks / JAR bands)
- [ ] Diff: jar / expected-state deltas
- [ ] `/plugtrace share` → open plugtrace.dev URL (FAILING hero + checks lens)
- [ ] Local Overview “Share to plugtrace.dev” copy points at console share (no browser upload)

### Reset after filming the break

Remove the fixture jar from `.plugdev/run/plugins/`, restart, re-mark healthy if needed. Or `plugdev clean --all` and re-run `Setup-DemoServer.ps1 -SkipDeps` (deps already cached).

## Demo video shot list (~60–90s)

| Beat | What to show |
|------|----------------|
| 1 | Flat creative world + plugin list feel (inventory) |
| 2 | Seed ritual: mark healthy / checkpoint / expected |
| 3 | Stop → drop fixture → restart |
| 4 | FAILING status + Diff |
| 5 | Report upload URL (optional) |
| 6 | End card: PlugTrace logo + summary line |

Do **not** film fleet dashboards, AI blame, or auto-rollback.

## Dep stack (why)

| Plugin | Role |
|--------|------|
| PlugTrace | Product |
| LuckPerms | Soft perms check |
| Vault + EssentialsX | Soft Vault path |
| PlaceholderAPI | Soft PAPI |
| spark | Paper-bundled on 26.1.2 (no separate jar) |
| AutoUpdatePlugins | Optional via `PLUGTRACE_DEMO_AUP_URL` (no PlugDev channel for 26.1.2) |
| WorldEdit | Inventory / wrappers |
| Multiverse-Core | Worlds in expected-state |
| ViaVersion | Common inventory noise |

Exact versions live in `plugdev.yml` `deps:` after `Setup-DemoServer.ps1` (filled by PlugDev).

## Related

- Narrative: `../../plugtrace-docs/marketplace/DEMO_NARRATIVE.md` (monorepo: `plugtrace/plugtrace-docs/...`)
- Boot evidence (last smoke): `DEMO_BOOT_EVIDENCE.md`
- Adoption / require-report kit: `../../plugtrace-docs/marketplace/ADOPTION_KIT.md`
- Active product research: `../../plugtrace-docs/research/PLUGTRACE_ACTIVE_USEFUL_DANGEROUS.md`
- Ephemeral smoke (no full dep stack): `Invoke-ExampleRitual.ps1`
- Drop kit: `demo-drop/README.md`
- Status helper: `Invoke-DemoStatus.ps1`

### Thumbnail #3 (FAILING + share)

```powershell
.\examples\test-server\Invoke-DemoThumb3.ps1
```

Captures FAILING status + `plugtrace share` URL. Stages WorldEdit aside (plus delayed-error fixture) so expected-plugins fails even when fixture issues are already ONGOING.

Title: **Share like a spark link** · Subtitle: **Redacted report. You choose when to share.**

Restore: `.\examples\test-server\Invoke-DemoHealth.ps1 -Mode Healthy`

### Thumbnail #4 (web Overview before/after)

Real `:9465` Overview shots (not console).

```powershell
.\examples\test-server\Invoke-DemoThumb4.ps1
# or stepwise:
.\examples\test-server\Invoke-DemoThumb4.ps1 -Phase Healthy
.\examples\test-server\Invoke-DemoThumb4.ps1 -Phase Failing
```

1. Paste the printed token at http://127.0.0.1:9465 → screenshot **HEALTHY** Overview  
2. Run Failing phase (or press Enter in Both) → refresh → screenshot **FAILING** Overview  
3. Compose: HEALTHY → red arrow → FAILING  

Title: **Did this update work?** · Subtitle: **After every risky restart.**
