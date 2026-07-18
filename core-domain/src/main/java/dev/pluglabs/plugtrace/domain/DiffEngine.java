package dev.pluglabs.plugtrace.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DiffEngine {
    public List<Change> diff(Deployment baseline, Deployment current) {
        Objects.requireNonNull(current, "current");
        List<Change> changes = new ArrayList<>();
        if (baseline == null) {
            changes.add(new Change(
                    ChangeType.COMPONENT_ADDED,
                    "deployment",
                    null,
                    "#" + current.localSequence(),
                    "No baseline deployment exists yet.",
                    0
            ));
            return changes;
        }

        if (!Objects.equals(baseline.serverImplementation(), current.serverImplementation())
                || !Objects.equals(baseline.minecraftVersion(), current.minecraftVersion())) {
            changes.add(new Change(
                    ChangeType.RUNTIME_CHANGED,
                    "SERVER:server",
                    baseline.serverImplementation() + " / " + baseline.minecraftVersion(),
                    current.serverImplementation() + " / " + current.minecraftVersion(),
                    "Server software or Minecraft version changed.",
                    80
            ));
        }

        if (!Objects.equals(baseline.javaVersion(), current.javaVersion())
                || !Objects.equals(baseline.javaVendor(), current.javaVendor())) {
            changes.add(new Change(
                    ChangeType.RUNTIME_CHANGED,
                    "RUNTIME:java",
                    baseline.javaVendor() + " " + baseline.javaVersion(),
                    current.javaVendor() + " " + current.javaVersion(),
                    "Java runtime changed.",
                    70
            ));
        }

        Map<String, ComponentSnapshot> before = indexComponents(baseline);
        Map<String, ComponentSnapshot> after = indexComponents(current);

        for (Map.Entry<String, ComponentSnapshot> entry : after.entrySet()) {
            String key = entry.getKey();
            ComponentSnapshot currentComponent = entry.getValue();
            ComponentSnapshot previous = before.get(key);
            if (previous == null) {
                changes.add(new Change(
                        ChangeType.COMPONENT_ADDED,
                        key,
                        null,
                        describe(currentComponent),
                        "Component added: " + currentComponent.identity().normalizedName(),
                        60
                ));
                continue;
            }

            ComponentIdentity prevId = previous.identity();
            ComponentIdentity currId = currentComponent.identity();

            if (!Objects.equals(prevId.declaredVersion(), currId.declaredVersion())) {
                changes.add(new Change(
                        ChangeType.VERSION_CHANGED,
                        key,
                        prevId.declaredVersion(),
                        currId.declaredVersion(),
                        "Declared version changed for " + currId.normalizedName(),
                        75
                ));
            } else if (!Objects.equals(prevId.binaryHash(), currId.binaryHash())
                    && !currId.binaryHash().isBlank()
                    && !prevId.binaryHash().isBlank()) {
                changes.add(new Change(
                        ChangeType.BINARY_CHANGED_SAME_VERSION,
                        key,
                        prevId.binaryHash(),
                        currId.binaryHash(),
                        "Version remains " + currId.declaredVersion() + ", but binary hash changed.",
                        90
                ));
            }

            if (previous.enabled() != currentComponent.enabled()
                    || previous.loaded() != currentComponent.loaded()) {
                changes.add(new Change(
                        ChangeType.LOAD_OUTCOME_CHANGED,
                        key,
                        "loaded=" + previous.loaded() + ",enabled=" + previous.enabled(),
                        "loaded=" + currentComponent.loaded() + ",enabled=" + currentComponent.enabled(),
                        "Load/enable outcome changed for " + currId.normalizedName(),
                        85
                ));
            }
        }

        for (Map.Entry<String, ComponentSnapshot> entry : before.entrySet()) {
            if (!after.containsKey(entry.getKey())) {
                changes.add(new Change(
                        ChangeType.COMPONENT_REMOVED,
                        entry.getKey(),
                        describe(entry.getValue()),
                        null,
                        "Component removed: " + entry.getValue().identity().normalizedName(),
                        70
                ));
            }
        }

        Map<String, ConfigSnapshot> beforeConfigs = indexConfigs(baseline);
        Map<String, ConfigSnapshot> afterConfigs = indexConfigs(current);
        for (Map.Entry<String, ConfigSnapshot> entry : afterConfigs.entrySet()) {
            ConfigSnapshot previous = beforeConfigs.get(entry.getKey());
            ConfigSnapshot currentConfig = entry.getValue();
            if (previous == null) {
                changes.add(new Change(
                        ChangeType.CONFIG_HASH_CHANGED,
                        entry.getKey(),
                        null,
                        currentConfig.sha256(),
                        "Config appeared: " + currentConfig.relativePath(),
                        40
                ));
            } else if (!Objects.equals(previous.sha256(), currentConfig.sha256())) {
                changes.add(new Change(
                        ChangeType.CONFIG_HASH_CHANGED,
                        entry.getKey(),
                        previous.sha256(),
                        currentConfig.sha256(),
                        "Config hash changed: " + currentConfig.relativePath(),
                        55
                ));
            }
        }

        changes.sort((a, b) -> Integer.compare(b.significance(), a.significance()));
        return changes;
    }

    private static Map<String, ComponentSnapshot> indexComponents(Deployment deployment) {
        Map<String, ComponentSnapshot> map = new HashMap<>();
        for (ComponentSnapshot component : deployment.components()) {
            map.put(component.identity().identityKey(), component);
        }
        return map;
    }

    private static Map<String, ConfigSnapshot> indexConfigs(Deployment deployment) {
        Map<String, ConfigSnapshot> map = new HashMap<>();
        for (ConfigSnapshot config : deployment.configs()) {
            map.put(config.key(), config);
        }
        return map;
    }

    private static String describe(ComponentSnapshot snapshot) {
        ComponentIdentity id = snapshot.identity();
        return id.normalizedName() + " " + id.declaredVersion() + " #" + shortHash(id.binaryHash());
    }

    private static String shortHash(String hash) {
        if (hash == null || hash.length() < 12) {
            return hash == null ? "" : hash;
        }
        return hash.substring(0, 12);
    }
}
