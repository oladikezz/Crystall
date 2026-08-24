package net.myserver.mechanics;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.entity.EntityDeathEvent;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Оптимизированная система защиты от дюпов и FastClick кликеров с компактной структурой памяти.
 */
public class AntiDupeSystem {
    private static class ClickRate {
        long lastClickTime = 0;
        long windowStart = 0;
        int count = 0;
    }

    private static final Map<UUID, ClickRate> clickRates = new ConcurrentHashMap<>();

    public static void register(GlobalEventHandler handler) {
        // 1. Защита от FastClick / Макросов и гонки потоков в инвентаре
        handler.addListener(InventoryPreClickEvent.class, event -> {
            Player player = event.getPlayer();
            UUID uuid = player.getUuid();
            long now = System.currentTimeMillis();

            ClickRate rate = clickRates.computeIfAbsent(uuid, k -> new ClickRate());

            // Кулдаун между кликами (минимум 40 мс)
            if (now - rate.lastClickTime < 40) {
                event.setCancelled(true);
                return;
            }
            rate.lastClickTime = now;

            // Ограничение кликов в секунду (макс. 18 кликов/сек)
            if (now - rate.windowStart > 1000) {
                rate.windowStart = now;
                rate.count = 1;
            } else {
                rate.count++;
                if (rate.count > 18) {
                    event.setCancelled(true);
                    player.sendActionBar(Component.text("Защита: Слишком частые клики в инвентаре!", NamedTextColor.RED));
                    return;
                }
            }

            // 2. Запрет вложенных шалкеровых ящиков (Nested Shulkers)
            ItemStack cursor = player.getInventory().getCursorItem();
            ItemStack clicked = event.getClickedItem();
            
            if (isShulkerBox(cursor) || isShulkerBox(clicked)) {
                if (event.getInventory() instanceof Inventory customInv) {
                    Component titleComp = customInv.getTitle();
                    String title = PlainTextComponentSerializer.plainText().serialize(titleComp).toLowerCase();
                    if (title.contains("рюкзак") || title.contains("shulker") || title.contains("backpack")) {
                        if (isShulkerBox(cursor)) {
                            event.setCancelled(true);
                            player.sendMessage(Component.text("⛔ Запрещено помещать шалкеры внутрь других рюкзаков!", NamedTextColor.RED));
                        }
                    }
                }
            }
        });

        // 3. Закрытие инвентаря перед смертью или выходом
        handler.addListener(EntityDeathEvent.class, event -> {
            if (event.getEntity() instanceof Player player) {
                if (player.getOpenInventory() != null) {
                    player.closeInventory();
                }
            }
        });

        handler.addListener(PlayerDisconnectEvent.class, event -> {
            Player player = event.getPlayer();
            if (player.getOpenInventory() != null) {
                player.closeInventory();
            }
            clickRates.remove(player.getUuid());
        });
    }

    public static void clearPlayer(UUID uuid) {
        clickRates.remove(uuid);
    }

    private static boolean isShulkerBox(ItemStack item) {
        if (item == null || item.isAir()) return false;
        Material mat = item.material();
        return mat.name().endsWith("_SHULKER_BOX") || mat == Material.SHULKER_BOX;
    }
}
