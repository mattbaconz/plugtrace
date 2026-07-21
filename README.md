<div align="center">

<img src="brand/plugtrace-logo.png" alt="PlugTrace" width="128" />

# PlugTrace · v1.0.0

---

**Know whether your Minecraft server update actually worked.**

After every risky restart: `/plugtrace status` — `HEALTHY` / `FAILING` / `DEGRADED`. Install **before** you break things (it cannot invent last night’s healthy baseline). Local-first checkpoint → verify → explain → report → recover. [Spark](https://spark.lucko.me/) answers *what is slow*; PlugTrace answers *what changed and what died* — not a profiler.

**[Site](https://plugtrace.dev)** · [Discord](https://discord.gg/C4X3rThtAM) · [PlugDev](https://github.com/mattbaconz/plugdev)

[![license](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![release](https://img.shields.io/github/v/release/mattbaconz/plugtrace?display_name=tag&label=release)](https://github.com/mattbaconz/plugtrace/releases)
[![site](https://img.shields.io/badge/site-plugtrace.dev-0ea5e9)](https://plugtrace.dev)
[![discord](https://img.shields.io/badge/discord-PLUG%20Labs-5865F2)](https://discord.gg/C4X3rThtAM)

<br />

<img src="brand/pluglabs-banner.png" alt="PLUG Labs" width="280" />

</div>

## Status

**1.0.0 deployment-safety** - ritual-first status + spark-like hosted report viewer. Marketplace listings stay frozen until soak day 7 + owner freeze lift ([`RELEASE.md`](RELEASE.md); prefer listing **0.5.1** first per D-035). Optional hosted report sharing on [plugtrace.dev](https://plugtrace.dev) after explicit `/plugtrace report upload` - nothing uploads automatically.

## Quick start

1. Download the JAR for your server (`PlugTrace-*.jar` paper-modern, `PlugTrace-folia-*.jar`, or experimental `PlugTrace-bukkit-modern-*.jar`) from [Releases](https://github.com/mattbaconz/plugtrace/releases).
2. Drop into `plugins/` and restart **while the server is still healthy**.
3. `/plugtrace selfcheck` · wait for `HEALTHY` · `/plugtrace checkpoint` · `/plugtrace expected capture` · `/plugtrace mark healthy`
4. After a risky update / restart: `/plugtrace status` (PASS/FAIL ritual). On `FAILING`: `/plugtrace report upload` to share like a spark link (explicit only).
5. Local web UI (default): `http://127.0.0.1:9465` — create a token with `/plugtrace web token create …`

## Test with PlugDev

Full example ritual (you vs we, Prism 26.1.2, fixture break): [`examples/test-server/README.md`](examples/test-server/README.md).

```powershell
npm i -g @plugdev/cli@1.0.1
cd plugtrace   # this repo
$env:JAVA_HOME = (.\scripts\Resolve-JavaHome.ps1; Get-PlugTraceJavaHome 25)
.\gradlew.bat :paper-modern:jar -x test
npx plugdev setup --instance "FO 26.1.2"
npx plugdev run
# or one-shot farm smoke: .\examples\test-server\Invoke-ExampleRitual.ps1
```

To co-install PlugTrace while developing another plugin, in that project's `plugdev.yml`:

```yaml
integrations:
  plugtrace:
    enabled: true
    jar: path/to/PlugTrace-0.5.1.jar
    artifact: auto
```

## Hosted reports (optional) — share like a spark link

```text
/plugtrace report preview
/plugtrace report upload
```

Prints a share URL like `https://plugtrace.dev/r/{id}#k=…` (ciphertext in the cloud; key in the fragment). Paste the full URL into Discord/GitHub the same way you paste a spark profile. Nothing uploads automatically. Local JSON/Markdown/HTML always remain under `plugins/PlugTrace/reports/`. Privacy: [plugtrace.dev/privacy](https://plugtrace.dev/privacy).

## Build

Requirements: **JDK 21**. Bundled web assets live under `paper-modern/src/main/resources/web`.

```powershell
cd web-ui
pnpm install
pnpm run build
cd ..
.\gradlew.bat :paper-modern:copyWebUi
.\gradlew.bat clean matrixSmoke
```

## Config

Defaults in `config.yml` (synced across artifacts). After edits: `/plugtrace reload`.

| Section | Notes |
|---------|--------|
| `retention` | Deployments, samples, JAR retention |
| `verification` | Post-ready delay + observation window |
| `expected` | Plugins/commands/worlds/services |
| `privacy` | `hash-only` only in 0.5.0 |
| `web` | Local UI bind/port; remote is opt-in |
| `cloud` | Optional `uploadUrl` / `viewerUrl` for hosted reports |

## License

Apache License 2.0 - see [`LICENSE`](LICENSE). Managed cloud on plugtrace.dev is a separate service; the plugin never requires it.
