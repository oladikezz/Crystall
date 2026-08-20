package net.myserver;

import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.myserver.admin.AntiCheatSystem;
import net.myserver.combat.CombatSystem;
import net.myserver.engine.SpatialGrid;
import net.myserver.mechanics.DimensionManager;

import java.util.UUID;

/**
 * Очистка структур памяти и кулдаунов ядра при выходе игрока.
 */
public class CleanupSystem {
    public static void register(GlobalEventHandler handler) {
        handler.addListener(PlayerDisconnectEvent.class, event -> {
            Player player = event.getPlayer();
            UUID uuid = player.getUuid();

            // 1. Очистка игровых кулдаунов и боевой системы
            DimensionManager.lastPortalTime.remove(uuid);
            CombatSystem.lastHitTimes.remove(uuid);

            // 2. Очистка структур античита
            AntiCheatSystem.airTicks.remove(uuid);

            // 3. Очистка сетки пространственного индекса
            if (player.getInstance() != null) {
                SpatialGrid.removeEntity(player.getInstance(), player, player.getPosition());
            }
        });
    }
}
