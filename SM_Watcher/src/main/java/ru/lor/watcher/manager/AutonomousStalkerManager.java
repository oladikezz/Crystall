package ru.lor.watcher.manager;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.lor.watcher.WatcherPlugin;
import ru.lor.watcher.model.WatcherPositionType;
import ru.lor.watcher.model.WatcherSpawnSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public class AutonomousStalkerManager {

    private final WatcherPlugin plugin;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile ScheduledTask scheduledTask;
    private volatile boolean enabled = false;
    private volatile boolean mediaTriggerEnabled = true;
    private volatile double chatAnswerChance = 0.15;

    // Per-player cooldown to prevent spamming the same player
    private final Map<UUID, Long> lastPlayerStalkMap = new ConcurrentHashMap<>();

    private static final Pattern MEDIA_URL_PATTERN = Pattern.compile(
            "(?i)(https?://)?(www\\.)?(twitch\\.tv|youtube\\.com|youtu\\.be|kick\\.com|trovo\\.live|vkplay\\.live|t\\.me)/\\S+"
    );

    public AutonomousStalkerManager(WatcherPlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfigManager().getConfig().getBoolean("autonomous.enabled", false);
        this.mediaTriggerEnabled = plugin.getConfigManager().getConfig().getBoolean("streamer-trigger.enabled", true);
        this.chatAnswerChance = plugin.getConfigManager().getConfig().getDouble("ai.chat-answer-chance", 0.15);
    }

    public void start() {
        this.enabled = plugin.getConfigManager().getConfig().getBoolean("autonomous.enabled", false);
        this.mediaTriggerEnabled = plugin.getConfigManager().getConfig().getBoolean("streamer-trigger.enabled", true);
        this.chatAnswerChance = plugin.getConfigManager().getConfig().getDouble("ai.chat-answer-chance", 0.15);

        if (!running.compareAndSet(false, true)) {
            return;
        }

        if (isEnabled()) {
            long minDelay = plugin.getConfigManager().getConfig().getLong("autonomous.min-delay-seconds", 7200L);
            scheduleNextAutonomousSpawn(minDelay);
        }
    }

    public void stop() {
        running.set(false);
        ScheduledTask task = this.scheduledTask;
        this.scheduledTask = null;
        if (task != null) {
            try {
                task.cancel();
            } catch (Throwable ignored) {
            }
        }
    }

    public boolean isEnabled() {
        return enabled && plugin.getConfigManager().getConfig().getBoolean("autonomous.enabled", false);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            ScheduledTask task = this.scheduledTask;
            this.scheduledTask = null;
            if (task != null) {
                try {
                    task.cancel();
                } catch (Throwable ignored) {
                }
            }
        } else if (running.get() && this.scheduledTask == null) {
            long minDelay = plugin.getConfigManager().getConfig().getLong("autonomous.min-delay-seconds", 7200L);
            scheduleNextAutonomousSpawn(minDelay);
        }
    }

    public boolean isMediaTriggerEnabled() {
        return mediaTriggerEnabled && plugin.getConfigManager().getConfig().getBoolean("streamer-trigger.enabled", true);
    }

    public void setMediaTriggerEnabled(boolean mediaTriggerEnabled) {
        this.mediaTriggerEnabled = mediaTriggerEnabled;
    }

    public double getChatAnswerChance() {
        return chatAnswerChance;
    }

    public void setChatAnswerChance(double chatAnswerChance) {
        this.chatAnswerChance = chatAnswerChance;
    }

    private void scheduleNextAutonomousSpawn(long delaySeconds) {
        if (!running.get() || !isEnabled()) {
            return;
        }
        
        long minDelay = Math.max(30L, plugin.getConfigManager().getConfig().getLong("autonomous.min-delay-seconds", 7200L));
        long maxDelay = Math.max(minDelay + 10L, plugin.getConfigManager().getConfig().getLong("autonomous.max-delay-seconds", 21600L));
        long targetDelay = ThreadLocalRandom.current().nextLong(minDelay, maxDelay + 1L);

        this.scheduledTask = Bukkit.getAsyncScheduler().runDelayed(plugin.getBukkitPlugin(), task -> {
            if (!running.get() || !isEnabled()) {
                return;
            }

            attemptAutonomousSpawn();

            if (isEnabled()) {
                scheduleNextAutonomousSpawn(0); // Reschedule with new random delay
            }
        }, Math.max(5L, delaySeconds > 0 ? delaySeconds : targetDelay), TimeUnit.SECONDS);
    }

    private void attemptAutonomousSpawn() {
        if (!isEnabled()) {
            return;
        }

        long now = System.currentTimeMillis();
        long perPlayerCooldownMs = plugin.getConfigManager().getConfig().getLong("autonomous.per-player-cooldown-seconds", 14400L) * 1000L;

        List<Player> candidates = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.isOnline() && !plugin.getWatcherManager().hasWatcher(online)) {
                long lastTime = lastPlayerStalkMap.getOrDefault(online.getUniqueId(), 0L);
                if (now - lastTime >= perPlayerCooldownMs) {
                    candidates.add(online);
                }
            }
        }

        if (candidates.isEmpty()) {
            return;
        }

        Player target = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        target.getScheduler().run(plugin.getBukkitPlugin(), task -> {
            if (!target.isOnline() || target.isDead() || !isEnabled()) {
                return;
            }
            if (plugin.getWatcherManager().hasWatcher(target)) {
                return;
            }

            lastPlayerStalkMap.put(target.getUniqueId(), System.currentTimeMillis());

            WatcherSpawnSettings settings = new WatcherSpawnSettings();
            settings.setSpawnDistance(5.0);
            settings.setDurationSeconds(25);
            settings.setInfiniteDuration(false);
            settings.setPositionType(WatcherPositionType.BEHIND);
            settings.setFreezingEnabled(true);
            settings.setJumpscareEnabled(true);
            settings.setAiMessageEnabled(true);
            settings.setSoundPreset("ANCIENT_HORROR");

            plugin.getWatcherManager().spawnWatcher(target, settings, "AutonomousStalker");
        }, null);
    }

    public boolean checkMediaTrigger(Player player, String message) {
        if (!mediaTriggerEnabled) {
            return false;
        }

        String lower = message.toLowerCase();
        boolean isMedia = MEDIA_URL_PATTERN.matcher(message).find()
                || lower.contains("twitch.tv")
                || lower.contains("youtube.com")
                || lower.contains("youtu.be")
                || lower.contains("kick.com")
                || lower.contains("trovo.live")
                || lower.contains("vkplay.live")
                || lower.startsWith("/stream")
                || lower.startsWith("/media");

        if (isMedia) {
            triggerStreamContentWatcher(player, message);
            return true;
        }

        return false;
    }

    public void triggerStreamContentWatcher(Player streamer, String mediaUrl) {
        streamer.getScheduler().run(plugin.getBukkitPlugin(), task -> {
            if (!streamer.isOnline() || streamer.isDead()) {
                return;
            }
            if (plugin.getWatcherManager().hasWatcher(streamer)) {
                return;
            }

            WatcherSpawnSettings settings = new WatcherSpawnSettings();
            settings.setSpawnDistance(3.5);
            settings.setDurationSeconds(30);
            settings.setInfiniteDuration(false);
            settings.setPositionType(WatcherPositionType.BEHIND);
            settings.setFreezingEnabled(true);
            settings.setJumpscareEnabled(true);
            settings.setAiMessageEnabled(true);
            settings.setSoundPreset("ANCIENT_HORROR");

            plugin.getWatcherManager().spawnWatcher(streamer, settings, "STREAM_TRIGGER");

            if (plugin.getTelegramBotManager() != null) {
                plugin.getTelegramBotManager().logMediaTrigger(streamer, mediaUrl, mediaUrl);
            }
        }, null);
    }
}
