package net.myserver.mechanics;

import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerEntityInteractEvent;
import net.minestom.server.item.Material;

public class ItemFrameMechanics {
    public static void register(GlobalEventHandler handler) {
        handler.addListener(PlayerEntityInteractEvent.class, event -> {
            Player player = event.getPlayer();
            Entity target = event.getTarget();

            // Если кликаем по рамке
            if (target.getEntityType() == EntityType.ITEM_FRAME || target.getEntityType() == EntityType.GLOW_ITEM_FRAME) {
                // Если игрок сидит на Shift и держит ножницы
                if (player.isSneaking() && player.getItemInMainHand().material() == Material.SHEARS) {
                    boolean isInvisible = target.isInvisible();
                    target.setInvisible(!isInvisible); // Переключаем невидимость
                }
            }
        });
    }
}
