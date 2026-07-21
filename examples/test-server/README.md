# Example test server (Paper 26.1.2 · Prism · PlugTrace 0.5.1)

Interactive dogfood for **you**. Automated smoke for **us**. Marketplace still frozen until soak day 7 + freeze lift.

Pins: **PlugDev `@plugdev/cli@1.0.1`**, **Temurin 25**, Paper **`26.1.2`**, Prism instance **`FO 26.1.2`**, artifact `PlugTrace-0.5.1.jar`.

## What's next (product)

1. Soak days 3–7 (automator / `Log-SoakDay.ps1` only — no invented ticks)
2. Tick soak in `RELEASE.md`
3. Owner freeze lift → Modrinth / Hangar / Spigot with v0.5.1 JARs

See [`DOGFOOD.md`](../../DOGFOOD.md) and [`farm/evidence/SOAK_STATUS.md`](../../farm/evidence/SOAK_STATUS.md).

---

## You test (PlugDev + Prism — interactive)

```powershell
cd plugtrace/plugtrace
npm i -D @plugdev/cli@1.0.1   # or: npm i -g @plugdev/cli@1.0.1
npx plugdev -V                # expect 1.0.1

# Paper 26.1.2 needs JDK 25+
$env:JAVA_HOME = (.\scripts\Resolve-JavaHome.ps1; Get-PlugTraceJavaHome 25)
.\gradlew.bat :paper-modern:jar -x test

npx plugdev client detect
npx plugdev setup --instance "FO 26.1.2"
npx plugdev run
```

[`plugdev.yml`](../../plugdev.yml) pins Paper `26.1.2`, Prism `FO 26.1.2`, and `PlugTrace-0.5.1.jar`.

### Ritual (console / in-game)

Premium MiniMessage prefix: `◆ PlugTrace │ …`

1. `/plugtrace selfcheck`
2. After first healthy window: `/plugtrace checkpoint` → `/plugtrace expected capture` → `/plugtrace mark healthy`
3. Optional break (fixture recipe) → restart → `/plugtrace status` · `diff` · `suspect` · `report preview`
4. Optional share: `/plugtrace report upload`
5. Local web: `/plugtrace web token create …` → open the loopback URL (cyan/teal branded dashboard)

### Folia (secondary)

Use **only** `PlugTrace-folia-0.5.1.jar`. Agent path: `.\scripts\Invoke-FoliaObservation.ps1`.

### Soak / ephemeral farm

Paper soak pin remains **1.21.4 / JDK 21** via farm scripts. This PlugDev example is intentionally on **26.1.2**.

---

## We test (agent / farm)

| Layer | Command |
|-------|---------|
| Unit + pack | `.\gradlew.bat clean matrixSmoke` (JDK 21 for matrix; use 25 when booting 26.1.2) |
| This example smoke | `.\examples\test-server\Invoke-ExampleRitual.ps1` (Paper 1.21.4 ephemeral) |
| Folia | `.\scripts\Invoke-FoliaObservation.ps1` |
| Soak | `.\scripts\Invoke-SoakCalendar.ps1 -UntilDay 7 -WaitForNextCalendarDay` |

```powershell
.\examples\test-server\Invoke-ExampleRitual.ps1
.\examples\test-server\Invoke-ExampleRitual.ps1 -WithDelayedErrorFixture
.\examples\test-server\Invoke-ExampleRitual.ps1 -PrintPlugDevOnly
```

---

## Fixture break recipe (`delayed-error`)

```powershell
cd plugtrace/plugtrace
$env:JAVA_HOME = (.\scripts\Resolve-JavaHome.ps1; Get-PlugTraceJavaHome 21)
.\gradlew.bat :paper-modern:jar :fixtures:delayed-error:jar -x test
.\examples\test-server\Invoke-ExampleRitual.ps1 -WithDelayedErrorFixture
```

Interactive: mark healthy → drop fixture jar into `.plugdev/run/plugins/` → restart → `/plugtrace status`.

---

## Requirements

- `@plugdev/cli` **1.0.1**
- JDK **25** for Paper 26.1.2 PlugDev runs; JDK **21** for farm/matrixSmoke
- Prism Launcher with instance **`FO 26.1.2`** (or `plugdev setup --instance "FO 26.1.2"`)
