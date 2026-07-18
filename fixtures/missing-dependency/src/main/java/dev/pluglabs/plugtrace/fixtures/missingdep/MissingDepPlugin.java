package dev.pluglabs.plugtrace.fixtures.missingdep;

import org.bukkit.plugin.java.JavaPlugin;

public final class MissingDepPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("MissingDep fixture enabled — depends on NonexistentProvider");
    }
}
