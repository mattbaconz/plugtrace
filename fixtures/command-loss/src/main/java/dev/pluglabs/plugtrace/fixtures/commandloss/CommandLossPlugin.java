package dev.pluglabs.plugtrace.fixtures.commandloss;

import org.bukkit.plugin.java.JavaPlugin;

public final class CommandLossPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        // Intentionally leave /ptfixtest without an executor so the declared command is lost.
        getLogger().warning("CommandLoss fixture enabled — /ptfixtest has no executor");
    }
}
