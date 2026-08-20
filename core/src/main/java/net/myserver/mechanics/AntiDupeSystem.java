package net.myserver.mechanics;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.entity.EntityDeathEvent;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerHandAnimationEvent;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AntiDupeSystem {
    private static final Map<UUID, Long> lastClickTime = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> clicksPerSecond = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastSecondWindow = new ConcurrentHashMap<>();

    public static void register(GlobalEventHandler handler) {
        // 1. Защита от FastClick / Макросов и гонки потоков в инвентаре
        handler.addListener(InventoryPreClickEvent.class, event -> {
            Player player = event.getPlayer();
            UUID uuid = player.getUuid();
            long now = System.currentTimeMillis();

            // Кулдаун между кликами (минимум 40 мс)
            long last = lastClickTime.getOrDefault(uuid, 0L);
            if (now - last < 40) {
                event.setCancelled(true);
                return;
            }
            lastClickTime.put(uuid, now);

            // Ограничение кликов в секунду (макс. 18 кликов/сек)
            long windowStart = lastSecondWindow.getOrDefault(uuid, now);
            if (now - windowStart > 1000) {
                lastSecondWindow.put(uuid, now);
                clicksPerSecond.put(uuid, 1);
            } else {
                int count = clicksPerSecond.getOrDefault(uuid, 0) + 1;
                clicksPerSecond.put(uuid, count);
                if (count > 18) {
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

        // 3. Защита от спама анимацией
        handler.addListener(PlayerHandAnimationEvent.class, event -> {
            // Hand animation check
        });

        // 4. Закрытие инвентаря перед смертью или выходом
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
            lastClickTime.remove(player.getUuid());
            clicksPerSecond.remove(player.getUuid());
            lastSecondWindow.remove(player.getUuid());
        });
    }

    private static boolean isShulkerBox(ItemStack item) {
        if (item == null || item.isAir()) return false;
        Material mat = item.material();
        return mat.name().endsWith("_SHULKER_BOX") || mat == Material.SHULKER_BOX;
    }
}
