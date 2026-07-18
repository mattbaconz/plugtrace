package dev.pluglabs.plugtrace.fixtures.enablefail;

import org.bukkit.plugin.java.JavaPlugin;

public final class EnableFailPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        throw new IllegalStateException("PlugTrace fixture: intentional enable failure");
    }
}
