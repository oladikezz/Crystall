package ru.lor.watcher.manager;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.lor.watcher.WatcherPlugin;
import ru.lor.watcher.events.WatcherDespawnEvent;
import ru.lor.watcher.events.WatcherSpawnEvent;
import ru.lor.watcher.model.WatcherLog;
import ru.lor.watcher.model.WatcherSpawnSettings;
import ru.lor.watcher.utils.ColorUtil;
import ru.lor.watcher.watcher.WatcherEntity;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WatcherManager {

    private final WatcherPlugin plugin;
    private final Map<UUID, WatcherEntity> activeWatchers = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledTask> activeTasks = new ConcurrentHashMap<>();

    public WatcherManager(WatcherPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean spawnWatcher(Player target, WatcherSpawnSettings settings, String executorName) {
        if (target == null || !target.isOnline()) {
            return false;
        }

        if (hasWatcher(target)) {
            return false;
        }

        if ("AutonomousStalker".equals(executorName) && plugin.getAutonomousStalkerManager() != null && !plugin.getAutonomousStalkerManager().isEnabled()) {
            return false;
        }

        // Folia: everything below touches the target's inventory, location and entities.
        // Reading that from another region's thread races with the server and can kill the region.
        if (!Bukkit.isOwnedByCurrentRegion(target)) {
            target.getScheduler().run(plugin.getBukkitPlugin(), task -> spawnWatcher(target, settings, executorName), null);
            return true;
        }

        WatcherEntity watcherEntity = new WatcherEntity(plugin, target, settings);

        // Fire WatcherSpawnEvent
        WatcherSpawnEvent spawnEvent = new WatcherSpawnEvent(target, watcherEntity, settings, executorName);
        Bukkit.getPluginManager().callEvent(spawnEvent);

        if (spawnEvent.isCancelled()) {
            watcherEntity.remove();
            return false;
        }

        activeWatchers.put(target.getUniqueId(), watcherEntity);

        // Schedule Folia Region Task directly on player's region thread
        ScheduledTask task = target.getScheduler().runAtFixedRate(plugin.getBukkitPlugin(), scheduledTask -> {
            boolean shouldDespawn = watcherEntity.tick();
            if (shouldDespawn) {
                despawnWatcher(target.getUniqueId(), WatcherDespawnEvent.DespawnReason.EXPIRED);
            }
        }, null, 1L, 1L);

        if (task != null) {
            activeTasks.put(target.getUniqueId(), task);
        }

        // Apply Sound
        if (settings.getSoundName() != null && !settings.getSoundName().isEmpty()) {
            try {
                String sName = settings.getSoundName().toLowerCase(Locale.ROOT);
                target.playSound(target.getLocation(), sName, 1.0f, 1.0f);
            } catch (Throwable ignored) {
            }
        }

        // AI Brain: Auto-generate contextual message based on player situation
        if (plugin.getAiBrainManager() != null) {
            plugin.getAiBrainManager().generateWatcherAction(target, null).thenAccept(aiMessage -> {
                if (aiMessage != null && !aiMessage.isEmpty()) {
                    // Broadcast on main thread
                    target.getScheduler().run(plugin.getBukkitPlugin(), t -> {
                        String broadcastFormat = plugin.getConfigManager().getBroadcastFormat();
                        String formatted = broadcastFormat.replace("{message}", ColorUtil.escape(aiMessage));
                        Bukkit.broadcast(ColorUtil.parse(formatted));
                    }, null);
                }
            });
        }

        // Also apply manual message if set via GUI
        if (settings.getMessageText() != null && !settings.getMessageText().isEmpty()) {
            String broadcastFormat = plugin.getConfigManager().getMessages().getString("watcher-broadcast-format", "<dark_gray>[<purple>Смотрящий</purple>]</dark_gray> <white>{message}</white>");
            String formatted = broadcastFormat.replace("{message}", settings.getMessageText());
            Bukkit.broadcast(ColorUtil.parse(formatted));
        }

        // Apply Title & Subtitle
        if ((settings.getTitleText() != null && !settings.getTitleText().isEmpty()) ||
            (settings.getSubtitleText() != null && !settings.getSubtitleText().isEmpty())) {
            
            Title title = Title.title(
                    settings.getTitleText() != null ? ColorUtil.parse(settings.getTitleText()) : net.kyori.adventure.text.Component.empty(),
                    settings.getSubtitleText() != null ? ColorUtil.parse(settings.getSubtitleText()) : net.kyori.adventure.text.Component.empty(),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
            );
            target.showTitle(title);
        }

        // Apply ActionBar
        if (settings.getActionBarText() != null && !settings.getActionBarText().isEmpty()) {
            target.sendActionBar(ColorUtil.parse(settings.getActionBarText()));
        }

        // Apply Potion Effects
        if (settings.getDarknessDuration() > 0) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, settings.getDarknessDuration() * 20, 0, false, false));
        }
        if (settings.getBlindnessDuration() > 0) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, settings.getBlindnessDuration() * 20, 0, false, false));
        }
        if (settings.getSlowFallingDuration() > 0) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, settings.getSlowFallingDuration() * 20, 0, false, false));
        }
        if (settings.getLevitationDuration() > 0) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, settings.getLevitationDuration() * 20, 0, false, false));
        }

        // Log entry
        WatcherLog log = new WatcherLog(
                System.currentTimeMillis(),
                target.getName(),
                executorName,
                settings.getDurationSeconds(),
                settings.getPositionType().getDisplayName()
        );
        plugin.getLogManager().addLog(log);

        // Discord Webhook Notification
        plugin.getDiscordWebhookManager().sendSpawnNotification(target.getName(), executorName, settings);

        // Telegram Bot Notification
        if (plugin.getTelegramBotManager() != null) {
            plugin.getTelegramBotManager().logSpawn(target, executorName, settings);
        }

        return true;
    }

    public boolean despawnWatcher(UUID playerUuid, WatcherDespawnEvent.DespawnReason reason) {
        WatcherEntity pending = activeWatchers.get(playerUuid);
        if (pending == null) {
            return false;
        }

        // Folia: removing the entity must happen on the region that owns the target.
        Player pendingTarget = pending.getTargetPlayer();
        if (pendingTarget != null && pendingTarget.isOnline() && !Bukkit.isOwnedByCurrentRegion(pendingTarget)) {
            pendingTarget.getScheduler().run(plugin.getBukkitPlugin(), task -> despawnWatcher(playerUuid, reason), null);
            return true;
        }

        ScheduledTask task = activeTasks.remove(playerUuid);
        if (task != null) {
            task.cancel();
        }

        WatcherEntity watcher = activeWatchers.remove(playerUuid);
        if (watcher != null) {
            Player target = watcher.getTargetPlayer();
            watcher.remove();
            Bukkit.getPluginManager().callEvent(new WatcherDespawnEvent(target, watcher, reason));
            return true;
        }
        return false;
    }

    public boolean hasWatcher(Player player) {
        return player != null && activeWatchers.containsKey(player.getUniqueId());
    }

    public WatcherEntity getWatcher(Player player) {
        return activeWatchers.get(player.getUniqueId());
    }

    public void despawnAll() {
        for (ScheduledTask task : activeTasks.values()) {
            try {
                task.cancel();
            } catch (Throwable ignored) {
            }
        }
        activeTasks.clear();

        for (WatcherEntity watcher : activeWatchers.values()) {
            removeSafely(watcher);
        }
        activeWatchers.clear();
    }

    private void removeSafely(WatcherEntity watcher) {
        org.bukkit.entity.Entity anchor = watcher.getArmorStand();

        if (anchor != null && !Bukkit.isOwnedByCurrentRegion(anchor)) {
            try {
                anchor.getScheduler().run(plugin.getBukkitPlugin(), task -> watcher.remove(), null);
                return;
            } catch (Throwable ignored) {
            }
        }

        try {
            watcher.remove();
        } catch (Throwable throwable) {
            plugin.getLogger().warning("[Watcher] Не удалось удалить Смотрящего: " + throwable.getMessage());
        }
    }

    public int getActiveWatchersCount() {
        return activeWatchers.size();
    }

    public Map<UUID, WatcherEntity> getActiveWatchers() {
        return activeWatchers;
    }
}
