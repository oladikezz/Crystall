package net.myserver.social;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerChatEvent;
import net.myserver.permissions.RoleManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChatManager {
    // Храним UUID последнего отправителя для команды /reply
    public static final Map<UUID, UUID> lastMessageSenders = new HashMap<>();

    public static void register(GlobalEventHandler handler) {
        handler.addListener(PlayerChatEvent.class, event -> {
            Player player = event.getPlayer();
            String message = event.getMessage();
            event.setCancelled(true); // Отменяем ванильный чат

            String role = RoleManager.getRole(player.getUuid());
            String prefix = role.equals("admin") ? "[Admin] " : (role.equals("moderator") ? "[Mod] " : "");

            if (message.startsWith("!")) {
                // Глобальный чат
                String msg = message.substring(1).trim();
                if (msg.isEmpty()) return;
                
                Component comp = Component.text("[G] " + prefix + player.getUsername() + ": " + msg, NamedTextColor.GOLD);
                for (Player p : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                    p.sendMessage(comp);
                }
            } else {
                // Локальный чат (радиус 100 блоков)
                Component comp = Component.text("[L] " + prefix + player.getUsername() + ": " + message, NamedTextColor.WHITE);
                for (Player p : player.getInstance().getPlayers()) {
                    if (p.getPosition().distance(player.getPosition()) <= 100) {
                        p.sendMessage(comp);
                    }
                }
            }
        });
    }
}
