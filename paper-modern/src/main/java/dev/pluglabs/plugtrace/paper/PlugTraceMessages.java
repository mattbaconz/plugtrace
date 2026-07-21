package dev.pluglabs.plugtrace.paper;

import dev.pluglabs.plugtrace.domain.DeploymentHealth;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Premium MiniMessage console/chat style for PlugTrace.
 * Cyan→teal industrial brand. ASCII marks so Windows/PlugDev consoles don't show "?".
 * Shared by paper/folia/bukkit sources — Adventure Audience when available, plain fallback on Spigot.
 */
public final class PlugTraceMessages {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    /** Brand prefix: mark + PlugTrace + pipe (ASCII-safe for Windows consoles). */
    public static final String PREFIX =
            "<gradient:#22d3ee:#2dd4bf><bold>*</bold></gradient> "
                    + "<gradient:#e2e8f0:#94a3b8><bold>PlugTrace</bold></gradient> "
                    + "<dark_gray>|</dark_gray> ";

    private PlugTraceMessages() {
    }

    public static String escape(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("<", "\\<");
    }

    public static Component parse(String miniMessage) {
        return MM.deserialize(miniMessage);
    }

    public static Component prefixed(String bodyMini) {
        return MM.deserialize(PREFIX + bodyMini);
    }

    public static void send(CommandSender sender, String bodyMini) {
        Component component = prefixed(bodyMini);
        if (sender instanceof Audience audience) {
            audience.sendMessage(component);
        } else {
            sender.sendMessage(PLAIN.serialize(component));
        }
    }

    /** Escape plain text and send as muted body under the brand prefix. */
    public static void plain(CommandSender sender, String plainText) {
        send(sender, "<gray>" + escape(plainText) + "</gray>");
    }

    public static void ok(CommandSender sender, String plainText) {
        send(sender, "<green>+</green> <white>" + escape(plainText) + "</white>");
    }

    public static void warn(CommandSender sender, String plainText) {
        send(sender, "<gold>!</gold> <white>" + escape(plainText) + "</white>");
    }

    public static void fail(CommandSender sender, String plainText) {
        send(sender, "<red>x</red> <white>" + escape(plainText) + "</white>");
    }

    public static void info(CommandSender sender, String plainText) {
        send(sender, "<aqua>*</aqua> <white>" + escape(plainText) + "</white>");
    }

    public static void row(CommandSender sender, String label, String value) {
        send(sender, "<dark_gray>-</dark_gray> <gray>" + escape(label) + ":</gray> <white>"
                + escape(value) + "</white>");
    }

    public static void title(CommandSender sender, String titlePlain) {
        send(sender, "<gradient:#22d3ee:#2dd4bf><bold>" + escape(titlePlain) + "</bold></gradient>");
    }

    public static Component healthLabel(DeploymentHealth health) {
        return parse(healthMini(health));
    }

    public static String healthMini(DeploymentHealth health) {
        DeploymentHealth h = health == null ? DeploymentHealth.UNKNOWN : health;
        return switch (h) {
            case HEALTHY -> "<green><bold>+ HEALTHY</bold></green>";
            case FAILING -> "<red><bold>x FAILING</bold></red>";
            case DEGRADED -> "<gold><bold>! DEGRADED</bold></gold>";
            case CRASHED -> "<dark_red><bold>x CRASHED</bold></dark_red>";
            case UNKNOWN -> "<gray><bold>* UNKNOWN</bold></gray>";
        };
    }

    public static String healthSymbolCss(String health) {
        if (health == null) {
            return "*";
        }
        return switch (health.toUpperCase(Locale.ROOT)) {
            case "HEALTHY" -> "+";
            case "FAILING", "CRASHED" -> "x";
            case "DEGRADED" -> "!";
            default -> "*";
        };
    }

    /** Ritual section opener for console Audience (gradients in modern terminals). */
    public static void bannerOpen(Audience audience, DeploymentHealth health) {
        audience.sendMessage(prefixed(
                "<gradient:#22d3ee:#2dd4bf><bold>==</bold></gradient> "
                        + healthMini(health)
                        + " <gradient:#22d3ee:#2dd4bf><bold>==</bold></gradient>"));
    }

    public static void bannerClose(Audience audience, DeploymentHealth health) {
        String name = health == null ? "UNKNOWN" : health.name();
        audience.sendMessage(prefixed(
                "<dark_gray>== end " + escape(name) + " ==</dark_gray>"));
    }

    public static Audience console() {
        CommandSender sender = Bukkit.getConsoleSender();
        if (sender instanceof Audience audience) {
            return audience;
        }
        return Audience.empty();
    }

    /**
     * Adventure console when available; JUL fallback when Spigot CommandSender is not an Audience.
     */
    public static void consoleRitual(Logger logger, String bodyMini) {
        Audience audience = console();
        if (audience != Audience.empty()) {
            audience.sendMessage(prefixed(bodyMini));
        } else {
            logger.info(PLAIN.serialize(prefixed(bodyMini)));
        }
    }

    public static void consoleRitualWarn(Logger logger, String bodyMini) {
        Audience audience = console();
        if (audience != Audience.empty()) {
            audience.sendMessage(prefixed(bodyMini));
        } else {
            logger.warning(PLAIN.serialize(prefixed(bodyMini)));
        }
    }

    public static void consoleLines(Logger logger, boolean warn, List<String> plainLines) {
        for (String line : plainLines) {
            String body = "<gray>" + escape(line) + "</gray>";
            if (warn) {
                consoleRitualWarn(logger, body);
            } else {
                consoleRitual(logger, body);
            }
        }
    }
}
