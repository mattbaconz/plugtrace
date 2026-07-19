package dev.pluglabs.plugtrace.paper;

import dev.pluglabs.plugtrace.domain.CheckCriticality;
import dev.pluglabs.plugtrace.domain.CheckResult;
import dev.pluglabs.plugtrace.domain.CheckStatus;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Soft Vault / LuckPerms service presence checks. ClassNotFound → SKIPPED, never fatal to core.
 */
final class ProviderServiceChecks {
    private ProviderServiceChecks() {
    }

    static List<CheckResult> run(Server server, PluginManager pluginManager, Logger logger) {
        List<CheckResult> out = new ArrayList<>();
        out.add(checkService(
                server,
                pluginManager,
                "provider-economy",
                "Economy provider (Vault)",
                "net.milkbowl.vault.economy.Economy",
                "Vault",
                logger
        ));
        out.add(checkService(
                server,
                pluginManager,
                "provider-permissions",
                "Permissions provider (Vault or LuckPerms)",
                "net.milkbowl.vault.permission.Permission",
                "LuckPerms",
                logger
        ));
        // LuckPerms API service (optional stronger signal)
        out.add(checkLuckPermsApi(server, pluginManager, logger));
        return out;
    }

    private static CheckResult checkLuckPermsApi(Server server, PluginManager pluginManager, Logger logger) {
        Plugin lp = pluginManager == null ? null : pluginManager.getPlugin("LuckPerms");
        if (lp == null || !lp.isEnabled()) {
            return new CheckResult(
                    "provider-luckperms",
                    "LuckPerms API",
                    CheckStatus.SKIPPED,
                    CheckCriticality.WARNING,
                    "LuckPerms not installed — check skipped",
                    Map.of("present", false)
            );
        }
        try {
            Class<?> api = Class.forName("net.luckperms.api.LuckPerms", false, lp.getClass().getClassLoader());
            ServicesManager sm = server.getServicesManager();
            RegisteredServiceProvider<?> rsp = sm.getRegistration(api);
            boolean ok = rsp != null && rsp.getProvider() != null;
            return new CheckResult(
                    "provider-luckperms",
                    "LuckPerms API",
                    ok ? CheckStatus.PASS : CheckStatus.WARN,
                    CheckCriticality.WARNING,
                    ok ? "LuckPerms API registered" : "LuckPerms plugin present but API service not registered",
                    Map.of("present", true, "registered", ok)
            );
        } catch (ClassNotFoundException e) {
            return new CheckResult(
                    "provider-luckperms",
                    "LuckPerms API",
                    CheckStatus.UNKNOWN,
                    CheckCriticality.WARNING,
                    "LuckPerms present but API class not visible",
                    Map.of("present", true)
            );
        } catch (RuntimeException e) {
            if (logger != null) {
                logger.fine("LuckPerms soft-check failed: " + e.getMessage());
            }
            return new CheckResult(
                    "provider-luckperms",
                    "LuckPerms API",
                    CheckStatus.UNKNOWN,
                    CheckCriticality.WARNING,
                    "LuckPerms soft-check error: " + e.getClass().getSimpleName(),
                    Map.of()
            );
        }
    }

    private static CheckResult checkService(
            Server server,
            PluginManager pluginManager,
            String id,
            String display,
            String serviceClassName,
            String relatedPluginHint,
            Logger logger
    ) {
        Plugin related = pluginManager == null ? null : pluginManager.getPlugin(relatedPluginHint);
        boolean relatedPresent = related != null && related.isEnabled();
        try {
            Class<?> serviceClass = Class.forName(serviceClassName, false, ProviderServiceChecks.class.getClassLoader());
            RegisteredServiceProvider<?> rsp = server.getServicesManager().getRegistration(serviceClass);
            boolean ok = rsp != null && rsp.getProvider() != null;
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("service", serviceClassName);
            details.put("registered", ok);
            details.put("relatedPlugin", relatedPluginHint);
            details.put("relatedPresent", relatedPresent);
            if (ok) {
                return new CheckResult(id, display, CheckStatus.PASS, CheckCriticality.WARNING,
                        display + " is registered", details);
            }
            if (!relatedPresent) {
                return new CheckResult(id, display, CheckStatus.SKIPPED, CheckCriticality.WARNING,
                        relatedPluginHint + " not installed — " + display + " check skipped", details);
            }
            return new CheckResult(id, display, CheckStatus.WARN, CheckCriticality.WARNING,
                    relatedPluginHint + " present but " + serviceClassName + " not registered", details);
        } catch (ClassNotFoundException e) {
            if (!relatedPresent) {
                return new CheckResult(id, display, CheckStatus.SKIPPED, CheckCriticality.WARNING,
                        "Service class absent and " + relatedPluginHint + " not installed — skipped",
                        Map.of("service", serviceClassName, "relatedPresent", false));
            }
            return new CheckResult(id, display, CheckStatus.UNKNOWN, CheckCriticality.WARNING,
                    "Could not load " + serviceClassName + " (related plugin may use a different API)",
                    Map.of("service", serviceClassName, "relatedPresent", true));
        } catch (RuntimeException e) {
            if (logger != null) {
                logger.fine(id + " soft-check failed: " + e.getMessage());
            }
            return new CheckResult(id, display, CheckStatus.UNKNOWN, CheckCriticality.WARNING,
                    "Soft-check error: " + e.getClass().getSimpleName(), Map.of());
        }
    }
}
