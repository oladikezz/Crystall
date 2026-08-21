package ru.lor.watcher.manager;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import ru.lor.watcher.WatcherPlugin;
import ru.lor.watcher.model.AutoEvent;
import ru.lor.watcher.model.WatcherBehaviorType;
import ru.lor.watcher.model.WatcherPositionType;
import ru.lor.watcher.model.WatcherSpawnSettings;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class EventManager {

    private final WatcherPlugin plugin;
    private final Map<String, AutoEvent> events = new ConcurrentHashMap<>();
    private final Map<String, Long> lastTriggeredMap = new ConcurrentHashMap<>();
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask schedulerTask;

    public EventManager(WatcherPlugin plugin) {
        this.plugin = plugin;
        loadEvents();
        startScheduler();
    }

    public void loadEvents() {
        events.clear();
        ConfigurationSection section = plugin.getConfigManager().getEvents().getConfigurationSection("events");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                int interval = section.getInt(id + ".interval-minutes", 40);
                int duration = section.getInt(id + ".duration-seconds", 15);
                String sound = section.getString(id + ".sound", "ENTITY_WARDEN_HEARTBEAT");
                String message = section.getString(id + ".message", "Смотрящий наблюдал за тобой...");

                WatcherSpawnSettings settings = new WatcherSpawnSettings();
                settings.setDurationSeconds(duration);
                settings.setSoundName(sound);
                settings.setMessageText(message);
                settings.setPositionType(WatcherPositionType.BEHIND);
                settings.setBehaviorType(WatcherBehaviorType.STATIC);

                events.put(id, new AutoEvent(id, interval, settings));
            }
        }
    }

    public void saveEvent(AutoEvent event) {
        events.put(event.getId(), event);
        ConfigurationSection section = plugin.getConfigManager().getEvents();
        String path = "events." + event.getId();
        section.set(path + ".interval-minutes", event.getIntervalMinutes());
        section.set(path + ".duration-seconds", event.getSettings().getDurationSeconds());
        section.set(path + ".sound", event.getSettings().getSoundName());
        section.set(path + ".message", event.getSettings().getMessageText());
        plugin.getConfigManager().saveEvents();
    }

    public void deleteEvent(String id) {
        events.remove(id);
        lastTriggeredMap.remove(id);
        plugin.getConfigManager().getEvents().set("events." + id, null);
        plugin.getConfigManager().saveEvents();
    }

    public void stop() {
        if (schedulerTask != null) {
            schedulerTask.cancel();
            schedulerTask = null;
        }
    }

    private void startScheduler() {
        // Folia / Paper GlobalRegionScheduler
        schedulerTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin.getBukkitPlugin(), task -> {
            long now = System.currentTimeMillis();
            for (AutoEvent event : events.values()) {
                long lastTime = lastTriggeredMap.getOrDefault(event.getId(), 0L);
                long intervalMs = event.getIntervalMinutes() * 60 * 1000L;

                if (now - lastTime >= intervalMs) {
                    triggerEvent(event);
                    lastTriggeredMap.put(event.getId(), now);
                }
            }
        }, 1200L, 1200L); // Check every 60 seconds (1200 ticks)
    }

    public void triggerEvent(AutoEvent event) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) return;

        boolean caveOnly = plugin.getConfigManager().getConfig().getBoolean("triggers.cave-only", false);
        boolean nightOnly = plugin.getConfigManager().getConfig().getBoolean("triggers.night-only", false);
        boolean lowHealthOnly = plugin.getConfigManager().getConfig().getBoolean("triggers.low-health-only", false);

        Player target = players.get(ThreadLocalRandom.current().nextInt(players.size()));

        // Trigger conditions read the player's location, world time and health, so they must be
        // evaluated on the region that owns the player, not on the global scheduler thread.
        target.getScheduler().run(plugin.getBukkitPlugin(), task -> {
            if (!target.isOnline()) {
                return;
            }
            if (!matchesTriggers(target, caveOnly, nightOnly, lowHealthOnly)) {
                return;
            }
            plugin.getWatcherManager().spawnWatcher(target, event.getSettings(), "AUTO_EVENT:" + event.getId());
        }, null);
    }

    private boolean matchesTriggers(Player player, boolean caveOnly, boolean nightOnly, boolean lowHealthOnly) {
        if (caveOnly && player.getLocation().getBlockY() >= 55) {
            return false;
        }
        if (nightOnly) {
            long time = player.getWorld().getTime();
            if (time < 13000 || time > 23000) {
                return false;
            }
        }
        return !lowHealthOnly || !(player.getHealth() > 10.0);
    }

    public Map<String, AutoEvent> getEvents() {
        return events;
    }
}
