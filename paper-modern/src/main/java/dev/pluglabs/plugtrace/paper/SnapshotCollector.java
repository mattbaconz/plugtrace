package dev.pluglabs.plugtrace.paper;

import dev.pluglabs.plugtrace.domain.ComponentIdentity;
import dev.pluglabs.plugtrace.domain.ComponentSnapshot;
import dev.pluglabs.plugtrace.domain.ComponentType;
import dev.pluglabs.plugtrace.domain.ConfigSnapshot;
import dev.pluglabs.plugtrace.domain.Deployment;
import dev.pluglabs.plugtrace.domain.DeploymentHealth;
import dev.pluglabs.plugtrace.domain.DeploymentLifecycle;
import dev.pluglabs.plugtrace.domain.Sha256Hasher;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.jar.JarFile;
import java.lang.management.ManagementFactory;

public final class SnapshotCollector {
    public Deployment collect(Server server, PluginManager pluginManager, String nodeId, long sequence, String parentId) {
        List<ComponentSnapshot> components = new ArrayList<>();
        List<ConfigSnapshot> configs = new ArrayList<>();
        Set<Path> loadedJarPaths = new HashSet<>();
        Path pluginsDirectory = null;

        for (Plugin plugin : pluginManager.getPlugins()) {
            PluginDescriptionFile description = plugin.getDescription();
            String hash = "";
            long size = 0;
            String relativePath = "plugins/" + plugin.getName() + ".jar";
            String absolutePath = null;
            try {
                var codeSource = plugin.getClass().getProtectionDomain().getCodeSource();
                if (codeSource != null && codeSource.getLocation() != null) {
                    Path path = Path.of(codeSource.getLocation().toURI());
                    if (Files.isRegularFile(path)) {
                        size = Files.size(path);
                        hash = Sha256Hasher.hashFile(path);
                        relativePath = "plugins/" + path.getFileName();
                        absolutePath = path.toAbsolutePath().toString();
                        loadedJarPaths.add(path.toAbsolutePath().normalize());
                    }
                }
            } catch (Exception ignored) {
                // Incomplete hash is better than failing startup snapshot.
            }

            ComponentIdentity identity = new ComponentIdentity(
                    ComponentType.PLUGIN,
                    description.getName(),
                    description.getVersion(),
                    hash,
                    description.getAuthors(),
                    description.getDepend(),
                    description.getSoftDepend(),
                    description.getMain(),
                    description.getAPIVersion()
            );
            components.add(new ComponentSnapshot(
                    identity,
                    relativePath,
                    size,
                    true,
                    plugin.isEnabled(),
                    plugin.isEnabled() ? null : "disabled-or-failed",
                    absolutePath
            ));

            File dataFolder = plugin.getDataFolder();
            if (dataFolder != null && dataFolder.isDirectory()) {
                if (pluginsDirectory == null && dataFolder.getParentFile() != null) {
                    pluginsDirectory = dataFolder.getParentFile().toPath();
                }
                collectConfigHashes(description.getName(), dataFolder.toPath(), dataFolder.toPath(), configs);
            }
        }

        if (pluginsDirectory != null) {
            collectUnloadedJars(pluginsDirectory, loadedJarPaths, components);
            Path serverRoot = pluginsDirectory.toAbsolutePath().normalize().getParent();
            if (serverRoot != null) {
                collectServerConfigs(serverRoot, configs);
            }
        }

        components.add(new ComponentSnapshot(
                new ComponentIdentity(
                        ComponentType.SERVER,
                        "server",
                        server.getVersion(),
                        "",
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        server.getBukkitVersion()
                ),
                "server",
                0,
                true,
                true,
                null
        ));

        components.add(new ComponentSnapshot(
                new ComponentIdentity(
                        ComponentType.RUNTIME,
                        "jvm-arguments",
                        sanitizeJvmArguments(ManagementFactory.getRuntimeMXBean().getInputArguments()),
                        "",
                        List.of(), List.of(), List.of(), null, null
                ),
                "runtime/jvm-arguments", 0, true, true, null
        ));

        components.add(new ComponentSnapshot(
                new ComponentIdentity(
                        ComponentType.RUNTIME,
                        "java",
                        System.getProperty("java.version", "unknown"),
                        "",
                        List.of(System.getProperty("java.vendor", "unknown")),
                        List.of(),
                        List.of(),
                        null,
                        null
                ),
                "runtime/java",
                0,
                true,
                true,
                null
        ));

        components.add(new ComponentSnapshot(
                new ComponentIdentity(
                        ComponentType.RUNTIME,
                        "memory-limit",
                        Long.toString(Runtime.getRuntime().maxMemory()),
                        "",
                        List.of("bytes"), List.of(), List.of(), null, null
                ),
                "runtime/memory-limit", 0, true, true, null
        ));

        return Deployment.builder()
                .id(UUID.randomUUID().toString())
                .localSequence(sequence)
                .nodeId(nodeId)
                .parentId(parentId)
                .startedAt(Instant.now())
                .lifecycle(DeploymentLifecycle.SNAPSHOTTING)
                .health(DeploymentHealth.UNKNOWN)
                .serverImplementation(server.getName() + " " + server.getVersion())
                .minecraftVersion(resolveMinecraftVersion(server))
                .javaVersion(System.getProperty("java.version", "unknown"))
                .javaVendor(System.getProperty("java.vendor", "unknown"))
                .components(components)
                .configs(configs)
                .build();
    }

    /** Paper exposes getMinecraftVersion(); Spigot/Bukkit fall back to bukkit version string. */
    private static String resolveMinecraftVersion(org.bukkit.Server server) {
        try {
            var method = server.getClass().getMethod("getMinecraftVersion");
            Object value = method.invoke(server);
            if (value != null) {
                String text = String.valueOf(value).trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // Spigot / older Bukkit
        }
        String bukkit = server.getBukkitVersion();
        if (bukkit != null && !bukkit.isBlank()) {
            int dash = bukkit.indexOf('-');
            return dash > 0 ? bukkit.substring(0, dash) : bukkit;
        }
        return server.getVersion() == null ? "unknown" : server.getVersion();
    }

    private void collectConfigHashes(String owner, Path root, Path current, List<ConfigSnapshot> configs) {
        try (var stream = Files.list(current)) {
            stream.limit(200).forEach(path -> {
                try {
                    if (Files.isDirectory(path)) {
                        if (path.getFileName().toString().equalsIgnoreCase("cache")
                                || path.getFileName().toString().equalsIgnoreCase("logs")) {
                            return;
                        }
                        collectConfigHashes(owner, root, path, configs);
                        return;
                    }
                    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (!(name.endsWith(".yml")
                            || name.endsWith(".yaml")
                            || name.endsWith(".toml")
                            || name.endsWith(".properties")
                            || name.endsWith(".json")
                            || name.endsWith(".conf"))) {
                        return;
                    }
                    if (Files.size(path) > 1_000_000) {
                        return;
                    }
                    String relative = root.relativize(path).toString().replace('\\', '/');
                    List<String> lines = Files.readAllLines(path);
                    configs.add(new ConfigSnapshot(owner, relative, Sha256Hasher.hashFile(path), "structure",
                            Files.size(path), countStructuralKeys(lines)));
                } catch (Exception ignored) {
                    // Skip unreadable configs.
                }
            });
        } catch (Exception ignored) {
            // Skip unreadable folders.
        }
    }

    static int countStructuralKeys(List<String> lines) {
        int count = 0;
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("-")
                    || trimmed.startsWith("//")) continue;
            int colon = trimmed.indexOf(':');
            int equals = trimmed.indexOf('=');
            if (colon > 0 || equals > 0) count++;
        }
        return count;
    }

    private void collectUnloadedJars(Path pluginsDirectory, Set<Path> loaded, List<ComponentSnapshot> components) {
        try (var stream = Files.list(pluginsDirectory)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .filter(path -> !loaded.contains(path.toAbsolutePath().normalize()))
                    .limit(500)
                    .forEach(path -> components.add(unloadedJar(path, pluginsDirectory)));
        } catch (Exception ignored) {
            // A partial inventory is explicitly preferable to failing startup.
        }
    }

    private ComponentSnapshot unloadedJar(Path path, Path pluginsDirectory) {
        String name = path.getFileName().toString().replaceFirst("(?i)\\.jar$", "");
        String version = "unknown";
        String main = null;
        List<String> authors = List.of();
        List<String> depend = List.of();
        List<String> softDepend = List.of();
        String apiVersion = null;
        try (JarFile jar = new JarFile(path.toFile())) {
            var entry = jar.getJarEntry("plugin.yml");
            if (entry != null) {
                try (var in = jar.getInputStream(entry)) {
                    PluginDescriptionFile description = new PluginDescriptionFile(in);
                    name = description.getName();
                    version = description.getVersion();
                    main = description.getMain();
                    authors = description.getAuthors();
                    depend = description.getDepend();
                    softDepend = description.getSoftDepend();
                    apiVersion = description.getAPIVersion();
                }
            }
        } catch (Exception ignored) {
            // The unreadable JAR is still important evidence.
        }
        try {
            return new ComponentSnapshot(new ComponentIdentity(ComponentType.PLUGIN, name, version,
                    Sha256Hasher.hashFile(path), authors, depend, softDepend, main, apiVersion),
                    "plugins/" + pluginsDirectory.relativize(path).toString().replace('\\', '/'),
                    Files.size(path), false, false, "not-loaded-or-invalid", path.toAbsolutePath().toString());
        } catch (Exception e) {
            return new ComponentSnapshot(new ComponentIdentity(ComponentType.PLUGIN, name, version,
                    "", authors, depend, softDepend, main, apiVersion),
                    "plugins/" + path.getFileName(), 0, false, false, "unreadable", path.toAbsolutePath().toString());
        }
    }

    private void collectServerConfigs(Path root, List<ConfigSnapshot> configs) {
        for (String relative : List.of("server.properties", "bukkit.yml", "spigot.yml", "purpur.yml",
                "pufferfish.yml", "config/paper-global.yml", "config/paper-world-defaults.yml")) {
            Path path = root.resolve(relative);
            try {
                if (Files.isRegularFile(path) && Files.size(path) <= 1_000_000) {
                    configs.add(new ConfigSnapshot("server", relative, Sha256Hasher.hashFile(path), "structure",
                            Files.size(path), countStructuralKeys(Files.readAllLines(path))));
                }
            } catch (Exception ignored) {
                // Skip unreadable config evidence.
            }
        }
    }

    private static String sanitizeJvmArguments(List<String> arguments) {
        return arguments.stream().map(arg -> {
            String lower = arg.toLowerCase(Locale.ROOT);
            if (lower.contains("password") || lower.contains("token") || lower.contains("secret")
                    || lower.contains("key=")) {
                int equals = arg.indexOf('=');
                return equals >= 0 ? arg.substring(0, equals + 1) + "[redacted]" : "[redacted]";
            }
            return arg;
        }).sorted().reduce((a, b) -> a + " " + b).orElse("");
    }
}
