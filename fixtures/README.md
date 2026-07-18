# PlugTrace server fixtures

Intentional failure / behavior plugins for ephemeral farm and soak drills.

| Module | Failure mode |
|--------|----------------|
| `enable-fail` | Throws on `onEnable` |
| `event-throw` | Throws on `PlayerJoinEvent` |
| `missing-dependency` | Hard-depends on nonexistent plugin |
| `same-version-binary` | Same version string, different binary hash |
| `delayed-error` | Succeeds enable; fails on delayed task |
| `config-reset` | Wipes/rewrites config marker on enable |
| `missing-service` | Throws when expected service missing |
| `command-loss` | Declares command without executor |
| `wrapper-chain` | Nested Completion/Execution wrappers |
| `developer-check` | AssertionError developer check |
| `unsafe-migration` | Destructive on-disk schema rewrite |

Build: `.\gradlew.bat :fixtures:<name>:jar`  
Drop: `Invoke-EphemeralFarm.ps1 -FixtureJars <path-to-jar>`
