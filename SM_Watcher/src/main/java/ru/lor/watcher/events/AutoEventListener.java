package ru.lor.watcher.events;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.lor.watcher.WatcherPlugin;

public class AutoEventListener implements Listener {

    private final WatcherPlugin plugin;

    public AutoEventListener(WatcherPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.getWatcherManager().hasWatcher(event.getPlayer())) {
            plugin.getWatcherManager().despawnWatcher(event.getPlayer().getUniqueId(), WatcherDespawnEvent.DespawnReason.PLAYER_QUIT);
        }
    }
}
