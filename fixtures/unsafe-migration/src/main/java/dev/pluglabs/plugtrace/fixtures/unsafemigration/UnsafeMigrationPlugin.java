package dev.pluglabs.plugtrace.fixtures.unsafemigration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.plugin.java.JavaPlugin;

public final class UnsafeMigrationPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        Path data = getDataFolder().toPath().resolve("store.v1.json");
        try {
            Files.createDirectories(getDataFolder().toPath());
            if (Files.exists(data)) {
                // Destructive rewrite with incompatible schema — no backup.
                Files.writeString(data, "{\"schema\":2,\"wiped\":true,\"legacy\":null}", StandardCharsets.UTF_8);
                getLogger().warning("UnsafeMigration fixture destroyed v1 store without backup");
            } else {
                Files.writeString(data, "{\"schema\":1,\"value\":\"seed\"}", StandardCharsets.UTF_8);
                getLogger().info("UnsafeMigration fixture wrote seed v1 store");
            }
        } catch (IOException ex) {
            throw new IllegalStateException("PlugTrace fixture: unsafe migration I/O failure", ex);
        }
    }
}
