# Trust campaign scaffolds (Phase C → 1.0)

These are operator/check harnesses. They do **not** clear 1.0 hard blocks until evidenced.

| Campaign | Script | Gate |
|----------|--------|------|
| Zero secret leaks | `Invoke-RedactionCampaign.ps1` | Any known leak = hard stop |
| Zero lost restore originals | `Invoke-RestoreInterruptMatrix.ps1` | `*.plugtrace-original` never lost |
| HIGH ≥90% or downgrade | `Review-HighAttribution.ps1` | ≥10 reviewed incidents |
| Perf gates | `Measure-PerfGates.ps1` | main/region p95 + memory @ 100 plugins / 10k events |

Start only after Paper/Folia soak day-0 harness is verified (`Log-SoakDay.ps1 -Day 0 -HarnessVerified`).
