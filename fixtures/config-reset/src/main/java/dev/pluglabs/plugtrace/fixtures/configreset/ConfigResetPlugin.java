package dev.pluglabs.plugtrace.fixtures.configreset;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigResetPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        String previous = config.getString("marker", "unset");
        config.set("marker", "reset-" + System.currentTimeMillis());
        config.set("previous-marker", previous);
        config.set("intentionally-wiped", true);
        saveConfig();
        getLogger().info("ConfigReset fixture rewrote config; previous=" + previous);
    }
}
