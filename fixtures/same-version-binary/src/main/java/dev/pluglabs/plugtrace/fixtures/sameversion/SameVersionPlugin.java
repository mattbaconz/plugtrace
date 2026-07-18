package dev.pluglabs.plugtrace.fixtures.sameversion;

import org.bukkit.plugin.java.JavaPlugin;

public final class SameVersionPlugin extends JavaPlugin {
    // Marker string differs between builds to force binary hash change while keeping version 2.4.0.
    public static final String BUILD_MARKER = "BUILD_A";

    @Override
    public void onEnable() {
        getLogger().info("SameVersion fixture enabled marker=" + BUILD_MARKER);
    }
}
