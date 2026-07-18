package dev.pluglabs.plugtrace.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;

/** Builds a stable state fingerprint excluding timestamps and runtime noise. */
public final class StateFingerprint {
    private StateFingerprint() {
    }

    public static String compute(Deployment deployment) {
        StringJoiner joiner = new StringJoiner("|");
        joiner.add("server=" + nullToEmpty(deployment.serverImplementation()));
        joiner.add("mc=" + nullToEmpty(deployment.minecraftVersion()));
        joiner.add("java=" + nullToEmpty(deployment.javaVendor()) + "/" + nullToEmpty(deployment.javaVersion()));

        List<ComponentSnapshot> components = new ArrayList<>(deployment.components());
        components.sort(Comparator.comparing(c -> c.identity().identityKey()));
        for (ComponentSnapshot component : components) {
            ComponentIdentity id = component.identity();
            joiner.add(id.identityKey()
                    + "@" + id.declaredVersion()
                    + "#" + id.binaryHash()
                    + ":L" + component.loaded()
                    + ":E" + component.enabled());
        }

        List<ConfigSnapshot> configs = new ArrayList<>(deployment.configs());
        configs.sort(Comparator.comparing(ConfigSnapshot::key));
        for (ConfigSnapshot config : configs) {
            joiner.add("cfg:" + config.key() + "#" + config.sha256());
        }

        return Sha256Hasher.hashString(joiner.toString());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
