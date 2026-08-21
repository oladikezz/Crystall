package ru.lor.watcher.events;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChatEvent;
import ru.lor.watcher.WatcherPlugin;
import ru.lor.watcher.model.WatcherSpawnSettings;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("deprecation")
public class StreamerChatListener implements Listener {

    private final WatcherPlugin plugin;
    private Pattern pattern;

    public StreamerChatListener(WatcherPlugin plugin) {
        this.plugin = plugin;
        updatePattern();
    }

    public void updatePattern() {
        String regex = plugin.getConfigManager().getConfig().getString(
                "streamer-trigger.regex",
                "(?:Игрок|Player)?\\s*([A-Za-z0-9_]{3,16})?\\s*запустил\\s*стрим"
        );
        try {
            this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        } catch (Throwable t) {
            this.pattern = Pattern.compile("запустил\\s*стрим", Pattern.CASE_INSENSITIVE);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        if (!plugin.getConfigManager().getConfig().getBoolean("streamer-trigger.enabled", true)) {
            return;
        }

        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        checkAndTriggerStreamerWatcher(event.getPlayer(), message);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLegacyChat(PlayerChatEvent event) {
        if (!plugin.getConfigManager().getConfig().getBoolean("streamer-trigger.enabled", true)) {
            return;
        }

        checkAndTriggerStreamerWatcher(event.getPlayer(), event.getMessage());
    }

    private void checkAndTriggerStreamerWatcher(Player sender, String message) {
        if (message == null || message.isEmpty()) return;

        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            Player targetPlayer = sender;

            if (matcher.groupCount() >= 1 && matcher.group(1) != null) {
                String extractedName = matcher.group(1);
                Player p = Bukkit.getPlayerExact(extractedName);
                if (p != null && !p.equals(sender)) {
                    // Naming somebody else in chat must not be enough to summon a Watcher on them.
                    if (!sender.hasPermission("watcher.spawn")) {
                        return;
                    }
                    targetPlayer = p;
                } else if (p != null) {
                    targetPlayer = p;
                }
            }

            final Player finalTarget = targetPlayer;
            // Schedule Watcher spawn on region thread of target player
            finalTarget.getScheduler().run(plugin.getBukkitPlugin(), task -> {
                if (finalTarget.isOnline()) {
                    WatcherSpawnSettings settings = new WatcherSpawnSettings();
                    settings.setMessageText("§8[§5Смотрящий§8] §fЯ смотрю твой эфир...");
                    plugin.getWatcherManager().spawnWatcher(finalTarget, settings, "STREAMER_CHAT_TRIGGER");
                }
            }, null);
        }
    }
}
