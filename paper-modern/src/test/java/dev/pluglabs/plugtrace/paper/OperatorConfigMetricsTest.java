package dev.pluglabs.plugtrace.paper;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperatorConfigMetricsTest {
    @Test
    void metricsEnabledDefaultsTrue() {
        OperatorConfig cfg = OperatorConfig.from(new YamlConfiguration());
        assertTrue(cfg.metricsEnabled);
    }

    @Test
    void metricsEnabledFalseSkipsInitGate() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("metrics.enabled", false);
        OperatorConfig cfg = OperatorConfig.from(yaml);
        assertFalse(cfg.metricsEnabled);
        assertTrue(BStatsMetrics.startIfEnabled(null, null, cfg.metricsEnabled) == null);
    }

    @Test
    void serverFamilyPieMapsForkFamilies() {
        assertTrue("paper".equals(BStatsMetrics.serverFamily(
                new dev.pluglabs.plugtrace.domain.PlatformInfo("paper", "t", "paper-modern"))));
        assertTrue("spigot".equals(BStatsMetrics.serverFamily(
                new dev.pluglabs.plugtrace.domain.PlatformInfo("bukkit-family", "t", "paper-modern"))));
        assertTrue("other".equals(BStatsMetrics.serverFamily(
                new dev.pluglabs.plugtrace.domain.PlatformInfo("pufferfish", "t", "paper-modern"))));
    }
}
