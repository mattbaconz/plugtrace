package dev.pluglabs.plugtrace.paper;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Soft PlaceholderAPI expansion. Only loaded when PAPI is present at runtime
 * (compileOnly dependency). Failure to register never stops PlugTrace.
 */
public final class PlugTracePlaceholderExpansion extends PlaceholderExpansion {
    private final JavaPlugin plugin;
    private final PlugTraceService service;

    public PlugTracePlaceholderExpansion(JavaPlugin plugin, PlugTraceService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public String getIdentifier() {
        return "plugtrace";
    }

    @Override
    public String getAuthor() {
        return "PlugLabs";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        return PlaceholderApiHook.resolve(service, params);
    }
}
