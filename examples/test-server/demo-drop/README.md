# Demo drop kit

Copy one of these JARs into `.plugdev/run/plugins/` after a healthy baseline, then restart, to produce FAILING demo footage.

| Jar | Effect |
|-----|--------|
| `PlugTraceFixture-DelayedError-*.jar` | Intentional delayed task exception after enable |
| `PlugTraceFixture-CommandLoss-*.jar` | Command-related expected-state loss |
| `PlugTraceFixture-MissingDep-*.jar` | Missing hard dependency / enable fail path |

Populate by running `Setup-DemoServer.ps1` (builds fixtures and copies jars here). JARs are local-only; do not commit binaries.
