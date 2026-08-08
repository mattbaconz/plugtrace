# PlugTrace ephemeral farm

Local-only caches for BuildTools, downloaded jars, run directories, and evidence markdown.

Do **not** commit jars or run trees. Evidence summaries under `evidence/*.md` may be committed when they document matrix results.

## Layout

- `jars/` — downloaded Paper/Folia/Purpur/Spigot/Pufferfish jars
- `buildtools-*/` — Spigot BuildTools workdirs
- `runs/` — ephemeral server instances
- `evidence/` — run summaries + soak log

## Quick start

```powershell
cd plugtrace/plugtrace
.\scripts\Build-Spigot.ps1 -Rev 1.20.4
.\scripts\Invoke-EphemeralFarm.ps1 `
  -ServerJar farm\jars\spigot-1.20.4.jar `
  -JavaMajor 17 `
  -Artifact bukkit-modern\build\libs\PlugTrace-bukkit-modern-0.4.0.jar `
  -RunName spigot-1.20.4
```

See [`../scripts/README.md`](../scripts/README.md).
