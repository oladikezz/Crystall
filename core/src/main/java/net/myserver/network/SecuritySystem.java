package net.myserver.network;

import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SecuritySystem {
    private static final Map<UUID, PacketData> packetCounts = new ConcurrentHashMap<>();
    private static final int MAX_PACKETS_PER_SECOND = 300;

    public static void register(GlobalEventHandler handler) {
        handler.addListener(PlayerPacketEvent.class, event -> {
            Player player = event.getPlayer();
            UUID uuid = player.getUuid();
            
            long currentTime = System.currentTimeMillis();
            PacketData data = packetCounts.computeIfAbsent(uuid, k -> new PacketData(currentTime, 0));
            
            if (currentTime - data.lastTime > 1000) {
                data.lastTime = currentTime;
                data.count = 0;
            }
            
            data.count++;
            
            if (data.count > MAX_PACKETS_PER_SECOND) {
                event.setCancelled(true);
                
                // Кикаем игрока в следующем тике во избежание конфликтов потока обработки пакетов
                net.minestom.server.MinecraftServer.getSchedulerManager().scheduleNextTick(() -> {
                    if (player.isOnline()) {
                        player.kick(Component.text("Слишком много пакетов (Flood/Spam)."));
                        packetCounts.remove(uuid);
                    }
                });
            }
        });
        
        handler.addListener(PlayerDisconnectEvent.class, event -> {
            packetCounts.remove(event.getPlayer().getUuid());
        });
    }

    private static class PacketData {
        long lastTime;
        int count;

        PacketData(long lastTime, int count) {
            this.lastTime = lastTime;
            this.count = count;
        }
    }
}
