package net.myserver.admin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.myserver.permissions.RoleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AntiCheatSystem {
    private static final Logger log = LoggerFactory.getLogger(AntiCheatSystem.class);
    private static final double MAX_SPEED_SQUARED = 2.0; // С запасом на прыжки
    public static final Map<UUID, Integer> airTicks = new ConcurrentHashMap<>();

    public static void register(GlobalEventHandler handler) {
        handler.addListener(PlayerMoveEvent.class, event -> {
            Player player = event.getPlayer();
            if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
            if (player.isFlying()) return;

            Pos oldPos = player.getPosition();
            Pos newPos = event.getNewPosition();
            Instance instance = player.getInstance();
            if (instance == null) return;

            // Проверка Speedhack (расстояние по X и Z за одно событие движения)
            double distanceSq = oldPos.distanceSquared(new Pos(newPos.x(), oldPos.y(), newPos.z()));
            if (distanceSq > MAX_SPEED_SQUARED) {
                event.setCancelled(true);
                alertAdmins(player, "Speedhack (FastMove)");
                return;
            }

            // Проверка Flyhack
            boolean isAirBelow = instance.getBlock(newPos.sub(0, 0.5, 0)).compare(Block.AIR);
            boolean isAirAtLegs = instance.getBlock(newPos).compare(Block.AIR);
            
            if (isAirBelow && isAirAtLegs) {
                if (newPos.y() >= oldPos.y() - 0.05) { 
                    int ticks = airTicks.compute(player.getUuid(), (k, cur) -> (cur == null ? 0 : cur) + 1);

                    if (ticks > 40) { // Игрок висит в воздухе больше ~2 секунд
                        event.setCancelled(true);
                        alertAdmins(player, "Flyhack (Hover)");
                    }
                } else {
                    airTicks.put(player.getUuid(), 0); 
                }
            } else {
                airTicks.put(player.getUuid(), 0); 
            }
        });
    }

    private static void alertAdmins(Player violator, String reason) {
        Component alert = Component.text("[AntiCheat] Подозрение на " + violator.getUsername() + ": " + reason, NamedTextColor.RED);
        for (Player p : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (RoleManager.isStaff(p)) {
                p.sendMessage(alert);
            }
        }
        log.warn("[AntiCheat] {} flagged for {}", violator.getUsername(), reason);
    }
}
