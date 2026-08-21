package ru.lor.watcher.manager;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.lor.watcher.WatcherPlugin;
import ru.lor.watcher.utils.ColorUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class InputSessionManager implements Listener {

    private final WatcherPlugin plugin;
    private final Map<UUID, Consumer<String>> pendingSessions = new ConcurrentHashMap<>();

    public InputSessionManager(WatcherPlugin plugin) {
        this.plugin = plugin;
    }

    public void startSession(Player player, String promptMessage, Consumer<String> onInput) {
        pendingSessions.put(player.getUniqueId(), onInput);
        player.closeInventory();
        player.sendMessage(ColorUtil.parse(promptMessage));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Consumer<String> callback = pendingSessions.remove(player.getUniqueId());

        if (callback != null) {
            event.setCancelled(true);
            String rawMessage = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

            if (rawMessage.equalsIgnoreCase("cancel") || rawMessage.equalsIgnoreCase("отмена")) {
                player.sendMessage(ColorUtil.parse(plugin.getConfigManager().getMessage("chat-input-cancelled")));
                return;
            }

            // Execute callback on Folia player region thread
            player.getScheduler().run(plugin.getBukkitPlugin(), task -> callback.accept(rawMessage), null);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingSessions.remove(event.getPlayer().getUniqueId());
    }

    public boolean hasPendingSession(Player player) {
        return pendingSessions.containsKey(player.getUniqueId());
    }
}
