package dev.pluglabs.plugtrace.paper;

import dev.pluglabs.plugtrace.platform.CapabilityRegistry;
import dev.pluglabs.plugtrace.platform.SchedulerFacade;
import dev.pluglabs.plugtrace.platform.ShutdownSequence;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.ServerLoadEvent;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class PlugTracePlugin extends JavaPlugin implements Listener {
    private PlugTraceService service;
    private ExceptionCapture capture;
    private SchedulerFacade scheduler;
    private LocalWebServer web;
    private PlugTraceCommand command;
    @SuppressWarnings("unused")
    private Metrics metrics;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        String artifactId = readArtifactId();
        scheduler = SchedulerFacade.create(
                this,
                getServer().getName(),
                getServer().getVersion() + " " + getServer().getBukkitVersion()
        );
        CapabilityRegistry caps = CapabilityRegistry.forArtifact(artifactId);
        PlugTraceMessages.consoleRitual(getLogger(),
                "<gray>Capabilities</gray> <dark_gray>-</dark_gray> <aqua>"
                        + PlugTraceMessages.escape(String.valueOf(caps.all())) + "</aqua>");
        if (scheduler.isFolia() && caps.has(CapabilityRegistry.Capability.FOLIA_SCHEDULERS)) {
            PlugTraceMessages.consoleRitual(getLogger(),
                    "<green>+</green> <gray>Using Folia-safe scheduler facade (async worker for store I/O).</gray>");
        } else if (scheduler.isFolia()) {
            PlugTraceMessages.consoleRitualWarn(getLogger(),
                    "<gold>!</gold> <gray>Folia runtime without Folia scheduler capability — use PlugTrace-1.0.0.jar</gray>");
        }

        service = new PlugTraceService(getLogger(), getDataFolder().toPath(), getConfig(), artifactId, scheduler);
        service.start(getServer(), getServer().getPluginManager());
        getServer().getPluginManager().registerEvents(this, this);
        capture = new ExceptionCapture(service, this);
        capture.register();

        OperatorConfig metricsCfg = service.operatorConfig();
        metrics = BStatsMetrics.startIfEnabled(this, service, metricsCfg != null && metricsCfg.metricsEnabled);
        if (metrics == null) {
            getLogger().fine("PlugTrace metrics disabled (metrics.enabled=false)");
        }

        startWebFromConfig();

        command = new PlugTraceCommand(service, this);
        var pluginCommand = getCommand("plugtrace");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        } else {
            getLogger().severe("Command 'plugtrace' missing from plugin.yml");
        }
    }

    /**
     * Reload {@code config.yml}, re-apply retention/privacy/expected, and restart web if bind/port/enabled changed.
     */
    public List<String> reloadOperatorConfig() {
        List<String> messages = new ArrayList<>();
        reloadConfig();
        OperatorConfig next = OperatorConfig.from(getConfig());
        messages.addAll(service.applyOperatorConfig(next));
        messages.add("retention/privacy/expected re-applied");
        messages.add("verification timing applies on next server ready (current schedule unchanged)");
        try {
            restartWeb(next);
            if (next.webEnabled) {
                messages.add("web restarted at " + (web == null ? "(failed)" : web.address()));
            } else {
                messages.add("web disabled");
            }
        } catch (Exception e) {
            messages.add("web restart failed: " + e.getMessage());
            getLogger().warning("PlugTrace Web restart failed: " + e.getMessage());
        }
        if (command != null) {
            command.setWeb(web);
        }
        return messages;
    }

    LocalWebServer web() {
        return web;
    }

    private void startWebFromConfig() {
        OperatorConfig cfg = service.operatorConfig();
        if (cfg == null || !cfg.webEnabled) {
            web = null;
            return;
        }
        web = new LocalWebServer(service, getLogger(), getDataFolder().toPath(),
                cfg.webBind, cfg.webPort, cfg.webAllowRemote);
        try {
            web.start();
            PlugTraceMessages.consoleRitual(getLogger(),
                    "<aqua>*</aqua> <gray>Web listening at</gray> <white>"
                            + PlugTraceMessages.escape(web.address())
                            + "</white> <dark_gray>(token required)</dark_gray>");
        } catch (Exception e) {
            PlugTraceMessages.consoleRitualWarn(getLogger(),
                    "<gold>!</gold> <gray>Web disabled:</gray> <white>"
                            + PlugTraceMessages.escape(e.getMessage()) + "</white>");
            web = null;
        }
    }

    private void restartWeb(OperatorConfig cfg) throws Exception {
        if (web != null) {
            web.close();
            web = null;
        }
        if (!cfg.webEnabled) {
            return;
        }
        web = new LocalWebServer(service, getLogger(), getDataFolder().toPath(),
                cfg.webBind, cfg.webPort, cfg.webAllowRemote);
        web.start();
        PlugTraceMessages.consoleRitual(getLogger(),
                "<aqua>*</aqua> <gray>Web listening at</gray> <white>"
                        + PlugTraceMessages.escape(web.address())
                        + "</white> <dark_gray>(token required)</dark_gray>");
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        if (service != null) {
            service.onServerReady();
            service.registerPlaceholderApi(this);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (service == null || event.getPlayer() == null) {
            return;
        }
        var player = event.getPlayer();
        if (!player.hasPermission("plugtrace.view") && !player.hasPermission("plugtrace.admin") && !player.isOp()) {
            return;
        }
        service.onOperatorJoin(player);
    }

    private String readArtifactId() {
        try (InputStream in = getResource("artifact-id.txt")) {
            if (in == null) {
                return "paper-modern";
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                return line == null || line.isBlank() ? "paper-modern" : line.trim();
            }
        } catch (Exception e) {
            return "paper-modern";
        }
    }

    @Override
    public void onDisable() {
        if (capture != null) {
            capture.unregister();
        }
        if (web != null) {
            web.close();
        }
        ShutdownSequence.close(scheduler, service);
    }

    public PlugTraceService service() {
        return service;
    }
}
