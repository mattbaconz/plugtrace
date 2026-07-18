package dev.pluglabs.plugtrace.platform;

import java.util.Locale;

/** Runtime Folia detection without requiring Folia classes on the compile classpath of callers. */
public final class FoliaDetect {
    private FoliaDetect() {
    }

    public static boolean isFolia(String serverName, String versionString) {
        String haystack = ((serverName == null ? "" : serverName) + " " + (versionString == null ? "" : versionString))
                .toLowerCase(Locale.ROOT);
        if (haystack.contains("folia")) {
            return true;
        }
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
