# PlugTrace ephemeral farm

Local-only caches for BuildTools, downloaded jars, and run directories.

Do **not** commit jars, run trees, or personal evidence logs. Keep evidence out of git (see `.gitignore`).

## Layout

- `jars/` — downloaded Paper/Folia/Purpur/Spigot jars
- `buildtools-*/` — Spigot BuildTools workdirs
- `runs/` — ephemeral server instances
- `evidence/` — local-only run summaries (ignored)

## Quick start

```powershell
.\scripts\Build-Spigot.ps1 -Rev 1.20.4
.\scripts\Invoke-EphemeralFarm.ps1 `
  -ServerJar farm\jars\spigot-1.20.4.jar `
  -JavaMajor 17 `
  -Artifact bukkit-modern\build\libs\PlugTrace-bukkit-modern-0.4.0.jar `
  -RunName spigot-1.20.4
```

See [`../scripts/README.md`](../scripts/README.md).
