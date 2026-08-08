package dev.pluglabs.plugtrace.paper;

import dev.pluglabs.plugtrace.domain.PlatformInfo;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

/**
 * Anonymous bStats charts for PlugTrace (plugin id 32755).
 * No configs, IPs, plugin lists, or report contents.
 */
final class BStatsMetrics {
    static final int PLUGIN_ID = 32755;

    private BStatsMetrics() {
    }

    /**
     * @return started Metrics, or {@code null} when disabled
     */
    static Metrics startIfEnabled(JavaPlugin plugin, PlugTraceService service, boolean enabled) {
        if (!enabled || plugin == null || service == null) {
            return null;
        }
        Metrics metrics = new Metrics(plugin, PLUGIN_ID);
        metrics.addCustomChart(new SimplePie("server_family", () -> serverFamily(service.platformInfo())));
        metrics.addCustomChart(new SimplePie("web_enabled", () ->
                onOff(service.operatorConfig() != null && service.operatorConfig().webEnabled)));
        metrics.addCustomChart(new SimplePie("cloud_enabled", () ->
                onOff(service.operatorConfig() != null && service.operatorConfig().cloudEnabled)));
        return metrics;
    }

    static String serverFamily(PlatformInfo info) {
        if (info == null) {
            return "other";
        }
        String family = info.forkFamily() == null ? "" : info.forkFamily().toLowerCase(Locale.ROOT);
        return switch (family) {
            case "paper" -> "paper";
            case "folia" -> "folia";
            case "purpur" -> "purpur";
            case "bukkit-family", "spigot", "bukkit" -> "spigot";
            default -> "other";
        };
    }

    private static String onOff(boolean enabled) {
        return enabled ? "on" : "off";
    }
}
