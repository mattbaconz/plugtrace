package dev.pluglabs.plugtrace.paper;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.logging.Logger;

/**
 * Registers PlaceholderAPI expansions when PAPI is present.
 * compileOnly dependency — runtime absence is fine.
 */
final class PlaceholderApiHook {
    private PlaceholderApiHook() {
    }

    static boolean tryRegister(JavaPlugin host, PlugTraceService service, Logger logger) {
        try {
            Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
            if (papi == null || !papi.isEnabled()) {
                return false;
            }
            PlugTracePlaceholderExpansion expansion = new PlugTracePlaceholderExpansion(host, service);
            boolean ok = expansion.register();
            if (ok && logger != null) {
                logger.info("PlaceholderAPI soft-integration: %plugtrace_*% registered.");
            }
            return ok;
        } catch (NoClassDefFoundError | ExceptionInInitializerError e) {
            return false;
        } catch (Throwable t) {
            if (logger != null) {
                logger.warning("PlaceholderAPI soft-integration skipped: " + t.getClass().getSimpleName()
                        + " — core PlugTrace continues.");
            }
            return false;
        }
    }

    static String resolve(PlugTraceService service, String params) {
        if (service == null || params == null) {
            return "";
        }
        String key = params.toLowerCase(Locale.ROOT).trim();
        return switch (key) {
            case "status", "health" -> service.currentHealthName();
            case "deployment", "deployment_id", "seq" -> String.valueOf(service.currentDeploymentSequence());
            case "suspect" -> service.strongestSuspectLabel();
            case "spark" -> service.sparkDetected() ? "yes" : "no";
            default -> null;
        };
    }
}
