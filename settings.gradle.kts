rootProject.name = "plugtrace"

include(
    "core-domain",
    "storage-sqlite",
    "report",
    "api",
    "platform-common",
    "paper-modern",
    "folia",
    "bukkit-modern",
    "fixtures:enable-fail",
    "fixtures:event-throw",
    "fixtures:missing-dependency",
    "fixtures:same-version-binary",
    "fixtures:delayed-error",
    "fixtures:config-reset",
    "fixtures:missing-service",
    "fixtures:command-loss",
    "fixtures:wrapper-chain",
    "fixtures:developer-check",
    "fixtures:unsafe-migration",
)
