package dev.pluglabs.plugtrace.fixtures.eventthrow;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class EventThrowPlugin extends JavaPlugin implements Listener {
    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("EventThrow fixture enabled — will throw on PlayerJoinEvent");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        throw new RuntimeException("PlugTrace fixture: intentional event handler failure for "
                + event.getPlayer().getUniqueId());
    }
}
