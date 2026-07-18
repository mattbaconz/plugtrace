package dev.pluglabs.plugtrace.fixtures.delayederror;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class DelayedErrorPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("DelayedError fixture enabled — scheduling intentional failure");
        Bukkit.getScheduler().runTaskLater(this, () -> {
            throw new IllegalStateException("PlugTrace fixture: intentional delayed task failure");
        }, 40L);
    }
}
