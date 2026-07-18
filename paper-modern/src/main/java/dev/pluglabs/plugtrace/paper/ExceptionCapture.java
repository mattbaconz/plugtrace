package dev.pluglabs.plugtrace.paper;

import dev.pluglabs.plugtrace.domain.IssueEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class ExceptionCapture implements Listener {
    private final PlugTraceService service;
    private final Plugin plugin;
    private Handler handler;

    public ExceptionCapture(PlugTraceService service, Plugin plugin) {
        this.service = service;
        this.plugin = plugin;
    }

    public void register() {
        handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record == null || record.getThrown() == null) {
                    return;
                }
                if (record.getLevel().intValue() < Level.WARNING.intValue()) {
                    return;
                }
                Throwable thrown = record.getThrown();
                StringWriter writer = new StringWriter();
                thrown.printStackTrace(new PrintWriter(writer));
                String loggerName = record.getLoggerName() == null ? "" : record.getLoggerName();
                List<String> ownership = service.resolveOwnership(
                        writer.toString(), ownershipFromLogger(loggerName));
                service.enqueue(new IssueEvent(
                        null,
                        Instant.ofEpochMilli(record.getMillis()),
                        service.currentDeployment().id(),
                        "logger",
                        record.getLevel().getName().toLowerCase(),
                        thrown.getClass().getName(),
                        thrown.getMessage() == null ? record.getMessage() : thrown.getMessage(),
                        writer.toString(),
                        ownership,
                        Thread.currentThread().getName()
                ));
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger root = Logger.getLogger("");
        root.addHandler(handler);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void unregister() {
        if (handler != null) {
            Logger.getLogger("").removeHandler(handler);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEnable(PluginEnableEvent event) {
        // Lifecycle visibility — enable success is already reflected in next snapshot.
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDisable(PluginDisableEvent event) {
        if (event.getPlugin().equals(plugin)) {
            return;
        }
        service.enqueue(new IssueEvent(
                null,
                Instant.now(),
                service.currentDeployment().id(),
                "lifecycle",
                "info",
                "plugin.disable",
                "Plugin disabled: " + event.getPlugin().getName(),
                "",
                List.of("lifecycle:" + event.getPlugin().getName()),
                Thread.currentThread().getName()
        ));
    }

    private static List<String> ownershipFromLogger(String loggerName) {
        if (loggerName.startsWith("Minecraft.")) {
            return List.of();
        }
        // Common pattern: plugin logger uses plugin name.
        if (!loggerName.isBlank() && !loggerName.contains(".")) {
            return List.of(loggerName);
        }
        String[] parts = loggerName.split("\\.");
        if (parts.length > 0) {
            return List.of(parts[parts.length - 1]);
        }
        return List.of();
    }
}
