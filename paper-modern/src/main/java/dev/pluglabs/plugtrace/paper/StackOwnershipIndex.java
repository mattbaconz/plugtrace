package dev.pluglabs.plugtrace.paper;

import dev.pluglabs.plugtrace.domain.ComponentSnapshot;
import dev.pluglabs.plugtrace.domain.ComponentType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

/** Immutable class/JAR ownership index built off-thread from observed plugin artifacts. */
final class StackOwnershipIndex {
    private static final int MAX_CLASSES_PER_JAR = 200_000;
    private final Map<String, String> classOwners;
    private final Map<String, String> packageOwners;
    private final WrapperRegistry wrappers;

    private StackOwnershipIndex(
            Map<String, String> classOwners, Map<String, String> packageOwners, WrapperRegistry wrappers) {
        this.classOwners = Map.copyOf(classOwners);
        this.packageOwners = Map.copyOf(packageOwners);
        this.wrappers = wrappers;
    }

    static StackOwnershipIndex empty() {
        return new StackOwnershipIndex(Map.of(), Map.of(), WrapperRegistry.packaged());
    }

    static StackOwnershipIndex build(List<ComponentSnapshot> components, WrapperRegistry wrappers) {
        Map<String, String> classes = new LinkedHashMap<>();
        Map<String, String> packages = new LinkedHashMap<>();
        if (components != null) {
            components.stream()
                    .filter(component -> component.identity().type() == ComponentType.PLUGIN)
                    .forEach(component -> indexComponent(component, classes, packages));
        }
        return new StackOwnershipIndex(classes, packages,
                wrappers == null ? WrapperRegistry.packaged() : wrappers);
    }

    private static void indexComponent(
            ComponentSnapshot component, Map<String, String> classes, Map<String, String> packages) {
        String owner = component.identity().normalizedName();
        String main = component.identity().mainClass();
        if (main != null && !main.isBlank()) {
            classes.putIfAbsent(main, owner);
            String mainPackage = packagePrefix(main);
            if (!mainPackage.isBlank()) packages.putIfAbsent(mainPackage, owner);
        }
        String absolute = component.absolutePath();
        if (absolute == null || absolute.isBlank()) return;
        Path jarPath;
        try {
            jarPath = Path.of(absolute);
        } catch (Exception ignored) {
            return;
        }
        if (!Files.isRegularFile(jarPath)) return;
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            jar.stream().filter(entry -> !entry.isDirectory() && entry.getName().endsWith(".class"))
                    .limit(MAX_CLASSES_PER_JAR)
                    .map(entry -> entry.getName().substring(0, entry.getName().length() - 6).replace('/', '.'))
                    .forEach(className -> classes.putIfAbsent(className, owner));
        } catch (Exception ignored) {
            // The snapshot already records unreadable JARs; ownership remains unknown here.
        }
    }

    List<String> resolve(String stackTrace, List<String> loggerHints) {
        LinkedHashSet<String> evidence = new LinkedHashSet<>();
        if (stackTrace != null) {
            for (String line : stackTrace.split("\\R")) {
                String className = frameClass(line);
                if (className == null) continue;
                wrappers.ownerFor(className).ifPresent(owner -> evidence.add("wrapper:" + owner));
                String owner = classOwners.get(className);
                if (owner == null) owner = ownerFromPackage(className);
                if (owner != null) evidence.add("frame:" + owner);
            }
        }
        if (loggerHints != null) {
            loggerHints.stream().filter(value -> value != null && !value.isBlank())
                    .map(value -> "logger:" + value).forEach(evidence::add);
        }
        return List.copyOf(evidence);
    }

    private String ownerFromPackage(String className) {
        return packageOwners.entrySet().stream()
                .filter(entry -> className.startsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst().orElse(null);
    }

    private static String packagePrefix(String className) {
        int last = className.lastIndexOf('.');
        return last < 0 ? "" : className.substring(0, last + 1);
    }

    private static String frameClass(String line) {
        if (line == null) return null;
        String trimmed = line.trim();
        if (!trimmed.startsWith("at ")) return null;
        int open = trimmed.indexOf('(');
        String method = open < 0 ? trimmed.substring(3) : trimmed.substring(3, open);
        // Paper/Java 9+ frames: "Shop.jar//com.acme.shop.Menu.open" or "Shop.jar/module/com.acme..."
        int slash = method.lastIndexOf('/');
        if (slash >= 0 && slash < method.length() - 1) {
            method = method.substring(slash + 1);
        }
        int lastDot = method.lastIndexOf('.');
        return lastDot <= 0 ? null : method.substring(0, lastDot);
    }
}
