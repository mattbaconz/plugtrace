package dev.pluglabs.plugtrace.fixtures.wrapperchain;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class WrapperChainPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTask(this, () -> {
            try {
                throwWrapped();
            } catch (RuntimeException ex) {
                throw new CompletionException(
                        "PlugTrace fixture: outer completion wrapper",
                        new ExecutionException("PlugTrace fixture: mid execution wrapper", ex));
            }
        });
        getLogger().info("WrapperChain fixture scheduled nested failure");
    }

    private static void throwWrapped() {
        throw new IllegalStateException("PlugTrace fixture: root intentional failure");
    }
}
