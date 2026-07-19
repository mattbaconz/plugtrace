package dev.pluglabs.plugtrace.paper;

import dev.pluglabs.plugtrace.platform.CapabilityRegistry;
import dev.pluglabs.plugtrace.platform.SchedulerFacade;
import dev.pluglabs.plugtrace.platform.ShutdownSequence;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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
        getLogger().info("Capabilities: " + caps.all());
        if (scheduler.isFolia() && caps.has(CapabilityRegistry.Capability.FOLIA_SCHEDULERS)) {
            getLogger().info("Using Folia-safe scheduler facade (async worker for store I/O).");
        } else if (scheduler.isFolia()) {
            getLogger().warning("Folia runtime detected on non-folia artifact — prefer PlugTrace-folia.");
        }

        service = new PlugTraceService(getLogger(), getDataFolder().toPath(), getConfig(), artifactId, scheduler);
        service.start(getServer(), getServer().getPluginManager());
        getServer().getPluginManager().registerEvents(this, this);
        capture = new ExceptionCapture(service, this);
        capture.register();

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
            getLogger().info("PlugTrace Web listening at " + web.address() + " (token required)");
        } catch (Exception e) {
            getLogger().warning("PlugTrace Web disabled: " + e.getMessage());
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
        getLogger().info("PlugTrace Web listening at " + web.address() + " (token required)");
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        if (service != null) {
            service.onServerReady();
            service.registerPlaceholderApi(this);
        }
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
