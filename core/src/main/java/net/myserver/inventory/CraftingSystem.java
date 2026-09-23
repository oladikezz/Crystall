package net.myserver.inventory;

import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.inventory.InventoryCloseEvent;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.inventory.click.Click;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.myserver.inventory.recipe.CraftingRecipe;
import net.myserver.inventory.recipe.RecipeRegistry;

/**
 * Полноценная модульная система крафта Crystall Core.
 * Поддерживает сетки 2x2 и 3x3, Shift-клик крафт, возврат остатков (ведра, колбы),
 * автоматический возврат ресурсов при закрытии верстака и сотни ванильных рецептов.
 */
public class CraftingSystem {

    public static void register(GlobalEventHandler handler) {
        RecipeRegistry.init();

        // 1. Обработка кликов по сетке крафта и результату
        handler.addListener(InventoryPreClickEvent.class, event -> {
            Player player = event.getPlayer();
            int slot = event.getSlot();

            boolean isCraftingTable = event.getInventory() instanceof Inventory inv && inv.getInventoryType() == InventoryType.CRAFTING;
            boolean isPlayerInv = event.getInventory() == null;

            if (!isCraftingTable && !isPlayerInv) return;

            Inventory customInv = isCraftingTable ? (Inventory) event.getInventory() : null;

            // Слот 0 — это слот результата крафта
            if (slot == 0) {
                ItemStack resultItem = isPlayerInv ? player.getInventory().getItemStack(0) : customInv.getItemStack(0);
                if (resultItem.isAir()) return;

                int endSlot = isPlayerInv ? 4 : 9;
                int gridWidth = isPlayerInv ? 2 : 3;
                int gridHeight = isPlayerInv ? 2 : 3;

                Material[] gridMaterials = new Material[endSlot];
                for (int i = 0; i < endSlot; i++) {
                    ItemStack item = isPlayerInv ? player.getInventory().getItemStack(i + 1) : customInv.getItemStack(i + 1);
                    gridMaterials[i] = item.material();
                }

                CraftingRecipe currentRecipe = RecipeRegistry.findMatchingRecipe(gridMaterials, gridWidth, gridHeight);
                if (currentRecipe == null) return;

                boolean isShiftClick = event.getClick() instanceof Click.LeftShift || event.getClick() instanceof Click.RightShift;

                if (isShiftClick) {
                    // ==========================================
                    // Быстрый крафт пачки через Shift-Click
                    // ==========================================
                    int maxCrafts = 64;
                    for (int i = 1; i <= endSlot; i++) {
                        ItemStack item = isPlayerInv ? player.getInventory().getItemStack(i) : customInv.getItemStack(i);
                        if (!item.isAir()) {
                            maxCrafts = Math.min(maxCrafts, item.amount());
                        }
                    }

                    if (maxCrafts <= 0) return;

                    int craftedCount = 0;
                    for (int step = 0; step < maxCrafts; step++) {
                        ItemStack singleResult = currentRecipe.getResult();
                        if (!player.getInventory().addItemStack(singleResult)) {
                            // Если инвентарь полон, прекращаем крафт
                            break;
                        }

                        // Уменьшаем предметы в сетке на 1
                        for (int i = 1; i <= endSlot; i++) {
                            ItemStack gridItem = isPlayerInv ? player.getInventory().getItemStack(i) : customInv.getItemStack(i);
                            if (!gridItem.isAir()) {
                                ItemStack remainder = currentRecipe.getRemainingItem(gridItem);
                                ItemStack newGrid = gridItem.amount() > 1 ? gridItem.withAmount(gridItem.amount() - 1) : remainder;

                                if (isPlayerInv) {
                                    player.getInventory().setItemStack(i, newGrid);
                                } else {
                                    customInv.setItemStack(i, newGrid);
                                }

                                // Если есть остаток и он не поместился в сетку
                                if (!remainder.isAir() && gridItem.amount() > 1) {
                                    if (!player.getInventory().addItemStack(remainder)) {
                                        spawnDrop(player, remainder);
                                    }
                                }
                            }
                        }
                        craftedCount++;
                    }

                    if (craftedCount > 0) {
                        MinecraftServer.getSchedulerManager().scheduleNextTick(() -> updateCraftingGrid(player, customInv));
                    }
                    event.setCancelled(true);
                } else {
                    // ==========================================
                    // Обычный единичный крафт
                    // ==========================================
                    ItemStack cursor = player.getInventory().getCursorItem();

                    if (cursor.isAir() || (cursor.material() == resultItem.material() && cursor.amount() + resultItem.amount() <= 64)) {
                        // Забираем ресурсы из сетки
                        for (int i = 1; i <= endSlot; i++) {
                            ItemStack gridItem = isPlayerInv ? player.getInventory().getItemStack(i) : customInv.getItemStack(i);
                            if (!gridItem.isAir()) {
                                ItemStack remainder = currentRecipe.getRemainingItem(gridItem);
                                ItemStack newGrid = gridItem.amount() > 1 ? gridItem.withAmount(gridItem.amount() - 1) : remainder;

                                if (isPlayerInv) {
                                    player.getInventory().setItemStack(i, newGrid);
                                } else {
                                    customInv.setItemStack(i, newGrid);
                                }

                                if (!remainder.isAir() && gridItem.amount() > 1) {
                                    if (!player.getInventory().addItemStack(remainder)) {
                                        spawnDrop(player, remainder);
                                    }
                                }
                            }
                        }

                        // Помещаем результат в курсор
                        int newCursorAmount = cursor.isAir() ? resultItem.amount() : cursor.amount() + resultItem.amount();
                        player.getInventory().setCursorItem(resultItem.withAmount(newCursorAmount));

                        // Очищаем слот результата
                        if (isPlayerInv) {
                            player.getInventory().setItemStack(0, ItemStack.AIR);
                        } else {
                            customInv.setItemStack(0, ItemStack.AIR);
                        }

                        MinecraftServer.getSchedulerManager().scheduleNextTick(() -> updateCraftingGrid(player, customInv));
                        event.setCancelled(true);
                    } else {
                        event.setCancelled(true);
                    }
                }
            } else if ((isPlayerInv && slot >= 1 && slot <= 4) || (isCraftingTable && slot >= 1 && slot <= 9)) {
                // Изменение слотов сетки крафта -> обновляем результат на следующем тике
                MinecraftServer.getSchedulerManager().scheduleNextTick(() -> updateCraftingGrid(player, customInv));
            }
        });

        // 2. Открытие инвентаря Верстака
        handler.addListener(PlayerBlockInteractEvent.class, event -> {
            if (event.getBlock().compare(Block.CRAFTING_TABLE)) {
                Inventory craftingInventory = new Inventory(InventoryType.CRAFTING, Component.text("Верстак"));
                event.getPlayer().openInventory(craftingInventory);
                event.setCancelled(true);
            }
        });

        // 3. Возврат оставшихся в верстаке предметов при закрытии инвентаря
        handler.addListener(InventoryCloseEvent.class, event -> {
            if (event.getInventory() instanceof Inventory inv && inv.getInventoryType() == InventoryType.CRAFTING) {
                Player player = event.getPlayer();
                refundCraftingTable(player, inv);
            }
        });

        // 4. Очистка и возврат при отключении игрока
        handler.addListener(PlayerDisconnectEvent.class, event -> {
            Player player = event.getPlayer();
            if (player.getOpenInventory() instanceof Inventory inv && inv.getInventoryType() == InventoryType.CRAFTING) {
                refundCraftingTable(player, inv);
            }
        });
    }

    private static void refundCraftingTable(Player player, Inventory inv) {
        for (int i = 1; i <= 9; i++) {
            ItemStack item = inv.getItemStack(i);
            if (!item.isAir()) {
                inv.setItemStack(i, ItemStack.AIR);
                if (!player.getInventory().addItemStack(item)) {
                    spawnDrop(player, item);
                }
            }
        }
        inv.setItemStack(0, ItemStack.AIR);
    }

    private static void spawnDrop(Player player, ItemStack item) {
        if (player.getInstance() != null && !item.isAir()) {
            ItemEntity drop = new ItemEntity(item);
            drop.setInstance(player.getInstance(), player.getPosition().add(0, 0.5, 0));
        }
    }

    private static void updateCraftingGrid(Player player, Inventory inventory) {
        boolean isPlayerInv = (inventory == null);
        int size = isPlayerInv ? 4 : 9;
        int width = isPlayerInv ? 2 : 3;
        int height = isPlayerInv ? 2 : 3;

        Material[] grid = new Material[size];
        for (int i = 0; i < size; i++) {
            ItemStack item = isPlayerInv ? player.getInventory().getItemStack(i + 1) : inventory.getItemStack(i + 1);
            grid[i] = item.material();
        }

        CraftingRecipe matched = RecipeRegistry.findMatchingRecipe(grid, width, height);
        ItemStack result = (matched != null) ? matched.getResult() : ItemStack.AIR;

        if (isPlayerInv) {
            player.getInventory().setItemStack(0, result);
        } else {
            inventory.setItemStack(0, result);
        }
    }
}
