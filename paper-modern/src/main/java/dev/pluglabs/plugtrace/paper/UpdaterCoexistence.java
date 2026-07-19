package dev.pluglabs.plugtrace.paper;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Soft-detect coexistence plugins (updaters). Never a runtime dependency.
 * Presence is context for reports — never guilt.
 */
final class UpdaterCoexistence {
    private static final String[] KNOWN = {
            "AutoUpdatePlugins",
            "PluginManager",
            "PlugManX",
            "PlugMan",
            "Updater",
            "UpdateChecker"
    };

    private UpdaterCoexistence() {
    }

    static List<String> detectNames(PluginManager pluginManager) {
        List<String> found = new ArrayList<>();
        if (pluginManager == null) {
            return found;
        }
        for (String name : KNOWN) {
            Plugin plugin = pluginManager.getPlugin(name);
            if (plugin != null && plugin.isEnabled()) {
                found.add(plugin.getName());
            }
        }
        // Also scan by name contains "update" for common forks (bounded)
        for (Plugin plugin : pluginManager.getPlugins()) {
            if (plugin == null || !plugin.isEnabled()) {
                continue;
            }
            String n = plugin.getName();
            String lower = n.toLowerCase(Locale.ROOT);
            if ((lower.contains("autoupdate") || lower.contains("pluginmanager"))
                    && found.stream().noneMatch(f -> f.equalsIgnoreCase(n))) {
                found.add(n);
            }
        }
        return List.copyOf(found);
    }

    static String summary(List<String> names) {
        if (names == null || names.isEmpty()) {
            return null;
        }
        return "Updater/coexistence plugins present: " + String.join(", ", names)
                + " — PlugTrace verifies after their JAR swaps; it does not replace them.";
    }
}
