package net.myserver.mechanics;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ванильная механика рыбалки (Vanilla Fishing Mechanics).
 */
public class CustomFishingSystem {
    private static final Map<UUID, Long> fishingCooldowns = new ConcurrentHashMap<>();

    public static void register(GlobalEventHandler handler) {
        handler.addListener(PlayerUseItemEvent.class, event -> {
            Player player = event.getPlayer();
            if (event.getItemStack().material() == Material.FISHING_ROD) {
                long now = System.currentTimeMillis();
                long last = fishingCooldowns.getOrDefault(player.getUuid(), 0L);
                if (now - last < 3000) return; // 3 сек кулдаун заброса
                fishingCooldowns.put(player.getUuid(), now);

                catchFish(player);
            }
        });
    }

    private static void catchFish(Player player) {
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        float roll = rand.nextFloat();
        ItemStack loot;

        if (roll < 0.05f) {
            // Сокровище
            loot = ItemStack.of(Material.NAUTILUS_SHELL, 1);
            player.sendMessage(Component.text("🌟 [Сокровище] Вы выловили Раковину наутилуса!", NamedTextColor.AQUA));
        } else if (roll < 0.20f) {
            // Редкая рыба
            loot = ItemStack.of(Material.PUFFERFISH, 1);
            player.sendActionBar(Component.text("🎣 Поймана рыба-фугу!", NamedTextColor.GOLD));
        } else if (roll < 0.50f) {
            // Лосось
            loot = ItemStack.of(Material.SALMON, 1);
            player.sendActionBar(Component.text("🎣 Пойман сырой лосось!", NamedTextColor.GREEN));
        } else {
            // Треска
            loot = ItemStack.of(Material.COD, 1);
            player.sendActionBar(Component.text("🎣 Поймана сырая треска!", NamedTextColor.WHITE));
        }

        if (player.getInstance() != null) {
            ItemEntity itemEntity = new ItemEntity(loot);
            itemEntity.setInstance(player.getInstance(), player.getPosition().add(0, 1, 0));
        }
    }
}
