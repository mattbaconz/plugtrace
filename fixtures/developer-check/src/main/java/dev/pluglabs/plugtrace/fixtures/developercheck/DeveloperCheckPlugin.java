package dev.pluglabs.plugtrace.fixtures.developercheck;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class DeveloperCheckPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTask(this, () -> {
            assertFalse("PlugTrace fixture: intentional developer check failure", false);
        });
        getLogger().info("DeveloperCheck fixture scheduled assertion failure");
    }

    private static void assertFalse(String message, boolean condition) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
