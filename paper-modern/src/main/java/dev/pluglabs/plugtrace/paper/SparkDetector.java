package dev.pluglabs.plugtrace.paper;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

final class SparkDetector {
    private SparkDetector() {
    }

    static Detection detect(PluginManager pluginManager, Server server) {
        Plugin spark = pluginManager.getPlugin("spark");
        if (spark == null) {
            spark = pluginManager.getPlugin("Spark");
        }
        boolean pluginEnabled = spark != null && spark.isEnabled();
        String pluginVersion = pluginEnabled ? spark.getDescription().getVersion() : null;
        boolean helpTopic = false;
        try {
            helpTopic = server != null && server.getHelpMap().getHelpTopic("/spark") != null;
        } catch (RuntimeException ignored) {
            // Optional integration: absence or an unavailable help map is not fatal.
        }
        return decide(pluginEnabled, pluginVersion, helpTopic);
    }

    static Detection decide(boolean pluginEnabled, String pluginVersion, boolean bundledHelpTopic) {
        if (pluginEnabled) {
            return new Detection(true, pluginVersion == null || pluginVersion.isBlank() ? "unknown" : pluginVersion);
        }
        if (bundledHelpTopic) {
            return new Detection(true, "bundled");
        }
        return new Detection(false, null);
    }

    record Detection(boolean detected, String version) {
    }
}
