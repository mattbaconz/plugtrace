package dev.pluglabs.plugtrace.fixtures.missingservice;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class MissingServicePlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        RegisteredServiceProvider<?> provider = Bukkit.getServicesManager().getRegistration(
                MissingEconomyService.class);
        if (provider == null) {
            throw new IllegalStateException(
                    "PlugTrace fixture: intentional missing service (MissingEconomyService)");
        }
        getLogger().info("Unexpected: MissingEconomyService was registered");
    }

    /** Marker type that no real plugin registers. */
    public interface MissingEconomyService {
    }
}
