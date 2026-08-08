# Demo server boot evidence (2026-07-25)

Paper **26.1.2-74** · flat world · creative · offline · peaceful · JVM 4G · PlugTrace **1.0.0**

## Healthy seed

- Deps enabled: LuckPerms, VaultUnlocked, EssentialsX, PlaceholderAPI, WorldEdit, Multiverse-Core, ViaVersion, PlugTrace 1.0.0; spark Paper-bundled (versions from 2026-07-25 smoke jars).
- `plugtrace mark healthy` → deployment **#1 HEALTHY**
- `plugtrace checkpoint demo` + `plugtrace expected capture` (plugins=9, commands=160, worlds=2, services=7)
- Web UI: `http://127.0.0.1:9465` (token create is console-only)

## FAILING fixture pass

- Dropped `PlugTraceFixture-DelayedError-1.0.0.jar` into `.plugdev/run/plugins/`
- Restart → deployment **#3 FAILING** after `plugtrace verify run`
- Strongest suspect: `PLUGIN:plugtracefixturedelayederror [HIGH]`
- Issue: `STARTUP_REGRESSION/NEW` — intentional delayed task failure

## Active interrupt smoke (2026-07-25 follow-up)

- `/plugtrace verify run` → **FAILING** with delayed-error fixture still loaded
- `/plugtrace incidents` showed OPEN incident + ack hint
- `/plugtrace incidents ack` → **INVESTIGATING** (sticky join nag cleared)
- `notify.discordWebhookUrl` empty by default (webhook off)

Filming entrypoint: `DEMO_SERVER.md`
