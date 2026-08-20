package net.myserver.mechanics;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.entity.EntityDeathEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

public class PlayerHeadDropMechanic {
    public static void register(GlobalEventHandler handler) {
        handler.addListener(EntityDeathEvent.class, event -> {
            // Если умерший - игрок
            if (event.getEntity() instanceof Player player) {
                
                // Создаем предмет-голову с именем игрока
                ItemStack head = ItemStack.of(Material.PLAYER_HEAD)
                        .withCustomName(Component.text("Голова игрока: " + player.getUsername(), NamedTextColor.GOLD));
                
                // Выбрасываем её на землю
                ItemEntity drop = new ItemEntity(head);
                drop.setInstance(player.getInstance(), player.getPosition().add(0, 0.5, 0));
                
                // Оповещаем всех об этом событии
                Component msg = Component.text("💀 С игрока " + player.getUsername() + " выпала голова-трофей!", NamedTextColor.DARK_RED);
                for (Player p : player.getInstance().getPlayers()) {
                    p.sendMessage(msg);
                }
            }
        });
    }
}
