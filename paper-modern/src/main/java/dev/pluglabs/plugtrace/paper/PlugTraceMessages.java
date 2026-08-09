package dev.pluglabs.plugtrace.paper;

import dev.pluglabs.plugtrace.domain.DeploymentHealth;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.ansi.ColorLevel;
import net.kyori.adventure.text.serializer.ansi.ANSIComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Premium MiniMessage style for PlugTrace.
 * Players + true console: Adventure components (Paper -> ANSI).
 * RCON / PlugDev: ANSI truecolor strings — PlugDev echoes {@code §x} hex as garbage.
 */
public final class PlugTraceMessages {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final ANSIComponentSerializer ANSI = ANSIComponentSerializer.builder()
            .colorLevel(ColorLevel.TRUE_COLOR)
            .build();

    /** Brand prefix: mark + PlugTrace + pipe (ASCII-safe for Windows consoles). */
    public static final String PREFIX =
            "<gradient:#22d3ee:#2dd4bf><bold>*</bold></gradient> "
                    + "<gradient:#e2e8f0:#94a3b8><bold>PlugTrace</bold></gradient> "
                    + "<dark_gray>|</dark_gray> ";

    /**
     * Soft max visible body columns before another prefixed line.
     * Narrow PowerShell panes soft-wrap mid-token and drop the brand prefix — looks truncated.
     */
    public static final int MAX_BODY_COLS = ConsoleLineFormat.MAX_BODY_COLS;
    public static final int MAX_COMPONENT_COLS = ConsoleLineFormat.MAX_COMPONENT_COLS;
    public static final int MAX_EXPLANATION_COLS = ConsoleLineFormat.MAX_EXPLANATION_COLS;

    /** Clickable / suggestable next-action chip for compact ritual rows. */
    public record ActionChip(String label, String command) {
    }

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

    public static Component brandPrefix() {
        return MM.deserialize(PREFIX);
    }

    public static Component prefixed(String bodyMini) {
        return MM.deserialize(PREFIX + bodyMini);
    }

    public static void send(CommandSender sender, String bodyMini) {
        sendComponent(sender, prefixed(bodyMini));
    }

    public static void sendComponent(CommandSender sender, Component fullMessage) {
        // Players + true console: Adventure (Paper → ANSI in the log).
        if (sender instanceof Player || sender instanceof ConsoleCommandSender) {
            if (sender instanceof Audience audience) {
                audience.sendMessage(fullMessage);
                return;
            }
        }
        // RCON / PlugDev: emit ANSI ourselves. Audience→RCON uses §x legacy, which
        // PlugDev prints as garbage; ANSI matches boot-line gradients in the TUI.
        try {
            sender.sendMessage(ANSI.serialize(fullMessage));
        } catch (Throwable ignored) {
            sender.sendMessage(PLAIN.serialize(fullMessage));
        }
    }

    /** Body component after the brand prefix (Component API — safe for URLs with # / _). */
    public static void sendBody(CommandSender sender, Component body) {
        sendComponent(sender, brandPrefix().append(body));
    }

    /**
     * Full share URL as an open_url link for players; plain aqua text for console.
     * Hover mentions privacy; never put the delete token here.
     */
    public static void sendOpenUrl(CommandSender sender, String url) {
        sendOpenUrl(sender, url, url, "Open report (includes #k=)");
    }

    /**
     * Labeled open_url (e.g. "Open dashboard"). Console still prints the full URL.
     */
    public static void sendOpenUrl(
            CommandSender sender, String url, String label, String hoverPlain) {
        if (url == null || url.isBlank()) {
            return;
        }
        String display = label == null || label.isBlank() ? url : label;
        boolean clickable = sender instanceof Player;
        TextComponent.Builder link = Component.text()
                .content(display)
                .color(NamedTextColor.AQUA)
                .decorate(TextDecoration.UNDERLINED);
        if (clickable) {
            link.clickEvent(ClickEvent.openUrl(url));
            link.hoverEvent(HoverEvent.showText(Component.text(
                    hoverPlain == null || hoverPlain.isBlank() ? url : hoverPlain,
                    NamedTextColor.GRAY)));
        } else {
            // Console: show label + URL so operators can copy
            if (!display.equals(url)) {
                send(sender, "<aqua><underlined>" + escape(display) + "</underlined></aqua>"
                        + " <dark_gray>·</dark_gray> <gray>" + escape(url) + "</gray>");
                return;
            }
            link.hoverEvent(HoverEvent.showText(Component.text(
                    "Copy URL", NamedTextColor.GRAY)));
        }
        sendBody(sender, link.build());
    }

    /** Delete token line — hover only, never open_url. */
    public static void sendPrivateToken(CommandSender sender, String token) {
        Component body = Component.text("Delete token (private): ", NamedTextColor.GRAY)
                .append(Component.text(token == null ? "" : token, NamedTextColor.WHITE)
                        .hoverEvent(HoverEvent.showText(Component.text(
                                "Keep private — deletes the hosted report", NamedTextColor.GRAY))));
        sendBody(sender, body);
    }

    /**
     * Compact action row. Players get suggest_command chips (taste/help pattern);
     * console / non-players get short aqua commands on one line.
     */
    public static void sendActionRow(CommandSender sender, List<ActionChip> chips) {
        if (chips == null || chips.isEmpty()) {
            return;
        }
        if (sender instanceof Player) {
            TextComponent.Builder row = Component.text();
            boolean first = true;
            for (ActionChip chip : chips) {
                if (!first) {
                    row.append(Component.text(" ", NamedTextColor.DARK_GRAY));
                }
                first = false;
                String cmd = normalizeCommand(chip.command());
                row.append(Component.text("[" + chip.label() + "]", NamedTextColor.AQUA)
                        .decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.suggestCommand(cmd))
                        .hoverEvent(HoverEvent.showText(Component.text(cmd, NamedTextColor.GRAY))));
            }
            sendBody(sender, row.build());
            return;
        }
        send(sender, formatConsoleActionRow(chips));
    }

    /** Console ritual equivalent of {@link #sendActionRow}. */
    public static void consoleActionRow(Logger logger, List<ActionChip> chips) {
        consoleRitualWarn(logger, formatConsoleActionRow(chips));
    }

    private static String formatConsoleActionRow(List<ActionChip> chips) {
        StringBuilder mini = new StringBuilder();
        boolean first = true;
        for (ActionChip chip : chips) {
            if (!first) {
                mini.append(" <dark_gray>·</dark_gray> ");
            }
            first = false;
            mini.append("<aqua>").append(escape(normalizeCommand(chip.command()))).append("</aqua>");
        }
        return mini.toString();
    }

    private static String normalizeCommand(String command) {
        if (command == null || command.isBlank()) {
            return "";
        }
        return command.startsWith("/") ? command : "/" + command;
    }

    /** Escape plain text and send as muted body under the brand prefix. */
    public static void plain(CommandSender sender, String plainText) {
        for (String chunk : ConsoleLineFormat.wrapPlain(plainText, MAX_BODY_COLS)) {
            send(sender, "<gray>" + escape(chunk) + "</gray>");
        }
    }

    /**
     * Status / diff change row: short first line + optional explanation.
     * Avoids mega-lines that soft-wrap mid-path without a PlugTrace prefix.
     */
    public static void changeRow(
            CommandSender sender,
            String type,
            String component,
            String explanation,
            boolean knownChurn) {
        String compactComp = ConsoleLineFormat.shortComponentKey(component);
        StringBuilder line = new StringBuilder();
        line.append("<dark_gray>-</dark_gray> <aqua>")
                .append(escape(type == null ? "?" : type))
                .append("</aqua> <white>")
                .append(escape(compactComp))
                .append("</white>");
        if (knownChurn) {
            line.append(" <dark_gray>[known churn]</dark_gray>");
        }
        send(sender, line.toString());
        String expl = ConsoleLineFormat.shortenExplanation(explanation, component);
        if (expl != null && !expl.isBlank()) {
            for (String chunk : ConsoleLineFormat.wrapPlain(expl, MAX_EXPLANATION_COLS)) {
                send(sender, "<dark_gray>  </dark_gray><gray>" + escape(chunk) + "</gray>");
            }
        }
    }

    /** Middle-ellipsis for long paths / keys so one console row stays readable. */
    public static String compactLabel(String text, int maxChars) {
        return ConsoleLineFormat.compactLabel(text, maxChars);
    }

    /** Word/path-aware wrap; each chunk is sent as its own prefixed message. */
    public static List<String> wrapPlain(String text, int maxCols) {
        return ConsoleLineFormat.wrapPlain(text, maxCols);
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
        String safeLabel = label == null ? "" : label;
        String safeValue = value == null ? "" : value;
        if (safeValue.length() <= 60) {
            send(sender, "<dark_gray>-</dark_gray> <gray>" + escape(safeLabel) + ":</gray> <white>"
                    + escape(safeValue) + "</white>");
            return;
        }
        send(sender, "<dark_gray>-</dark_gray> <gray>" + escape(safeLabel) + ":</gray>");
        for (String chunk : ConsoleLineFormat.wrapPlain(safeValue, MAX_BODY_COLS)) {
            send(sender, "<dark_gray>  </dark_gray><white>" + escape(chunk) + "</white>");
        }
    }

    public static void title(CommandSender sender, String titlePlain) {
        send(sender, "<gradient:#22d3ee:#2dd4bf><bold>" + escape(titlePlain) + "</bold></gradient>");
    }

    /**
     * Blank visual line between chat sections (Minecraft drops empty components).
     * No brand prefix — keeps the wall of {@code * PlugTrace |} from blending.
     */
    public static void spacer(CommandSender sender) {
        sendComponent(sender, Component.text(" ").color(NamedTextColor.DARK_GRAY));
    }

    /** Spacer + gradient section title. */
    public static void section(CommandSender sender, String titlePlain) {
        spacer(sender);
        title(sender, titlePlain);
    }

    /** Console ritual equivalent of {@link #spacer}. */
    public static void consoleSpacer(Logger logger) {
        Audience audience = console();
        if (audience != Audience.empty()) {
            audience.sendMessage(Component.text(" ").color(NamedTextColor.DARK_GRAY));
        } else {
            logger.info(" ");
        }
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

    /** Ritual header: health + optional # + window. No == walls (mc-plugin-taste). */
    public static void bannerOpen(Logger logger, DeploymentHealth health) {
        bannerOpen(logger, health, -1, null);
    }

    /** Header with deployment # and window label (e.g. early check). */
    public static void bannerOpen(
            Logger logger, DeploymentHealth health, long sequence, String window) {
        StringBuilder body = new StringBuilder();
        body.append(healthMini(health));
        if (sequence >= 0) {
            body.append("  <aqua>#").append(sequence).append("</aqua>");
        }
        if (window != null && !window.isBlank()) {
            body.append("  <dark_gray>").append(escape(window)).append("</dark_gray>");
        }
        consoleRitualWarn(logger, body.toString());
    }

    /** @deprecated No-op — end banners removed for flat short ritual chat. */
    @Deprecated
    public static void bannerClose(Audience audience, DeploymentHealth health) {
        // Intentionally empty (taste: no == end walls).
    }

    public static Audience console() {
        CommandSender sender = Bukkit.getConsoleSender();
        if (sender instanceof Audience audience) {
            return audience;
        }
        return Audience.empty();
    }

    /**
     * Boot / ritual lines: Adventure on the real console (ANSI gradients).
     * JUL plain fallback when Adventure console is unavailable.
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

    /** Short type mark for one-line JAR summaries. */
    public static String jarTypeMark(String changeType) {
        if (changeType == null) {
            return "?";
        }
        return switch (changeType) {
            case "COMPONENT_ADDED" -> "+";
            case "COMPONENT_REMOVED" -> "-";
            case "BINARY_CHANGED_SAME_VERSION" -> "BINARY";
            case "VERSION_CHANGED" -> "VER";
            default -> changeType.length() > 12 ? changeType.substring(0, 12) : changeType;
        };
    }

    public static String shortComponentKey(String key) {
        return ConsoleLineFormat.shortComponentKey(key);
    }
}
