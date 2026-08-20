package net.myserver.inventory;

import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

public class CraftingSystem {

    public static void register(GlobalEventHandler handler) {
        handler.addListener(InventoryPreClickEvent.class, event -> {
            Player player = event.getPlayer();
            int slot = event.getSlot();
            
            boolean isCraftingTable = event.getInventory() instanceof Inventory inv && inv.getInventoryType() == InventoryType.CRAFTING;
            boolean isPlayerInv = event.getInventory() == null;
            
            if (!isCraftingTable && !isPlayerInv) return;
            
            Inventory customInv = isCraftingTable ? (Inventory) event.getInventory() : null;

            // Слот 0 — это результат крафта
            if (slot == 0) {
                ItemStack resultItem = isPlayerInv ? player.getInventory().getItemStack(0) : customInv.getItemStack(0);
                
                if (!resultItem.isAir()) {
                    ItemStack cursor = player.getInventory().getCursorItem();
                    
                    if (cursor.isAir() || (cursor.material() == resultItem.material() && cursor.amount() + resultItem.amount() <= 64)) {
                        
                        // Забираем ресурсы из сетки
                        int endSlot = isPlayerInv ? 4 : 9;
                        for (int i = 1; i <= endSlot; i++) {
                            ItemStack gridItem = isPlayerInv ? player.getInventory().getItemStack(i) : customInv.getItemStack(i);
                            if (!gridItem.isAir()) {
                                ItemStack newGrid = gridItem.withAmount(gridItem.amount() - 1);
                                if (isPlayerInv) {
                                    player.getInventory().setItemStack(i, newGrid.amount() == 0 ? ItemStack.AIR : newGrid);
                                } else {
                                    customInv.setItemStack(i, newGrid.amount() == 0 ? ItemStack.AIR : newGrid);
                                }
                            }
                        }
                        
                        // Даем результат в курсор
                        player.getInventory().setCursorItem(cursor.isAir() ? resultItem : cursor.withAmount(cursor.amount() + resultItem.amount()));
                        
                        // Очищаем результат
                        if (isPlayerInv) {
                            player.getInventory().setItemStack(0, ItemStack.AIR);
                        } else {
                            customInv.setItemStack(0, ItemStack.AIR);
                        }
                        
                        net.minestom.server.MinecraftServer.getSchedulerManager().scheduleNextTick(() -> {
                             updateCraftingGrid(player, customInv);
                        });
                        
                        event.setCancelled(true);
                    } else {
                        event.setCancelled(true);
                    }
                }
            } else if ((isPlayerInv && slot >= 1 && slot <= 4) || (isCraftingTable && slot >= 1 && slot <= 9)) {
                net.minestom.server.MinecraftServer.getSchedulerManager().scheduleNextTick(() -> {
                     updateCraftingGrid(player, customInv);
                });
            }
        });

        // Открытие инвентаря Верстака
        handler.addListener(PlayerBlockInteractEvent.class, event -> {
            if (event.getBlock().compare(Block.CRAFTING_TABLE)) {
                Inventory craftingInventory = new Inventory(InventoryType.CRAFTING, Component.text("Верстак"));
                event.getPlayer().openInventory(craftingInventory);
                event.setCancelled(true);
            }
        });
    }
    
    private static void updateCraftingGrid(Player player, Inventory inventory) {
        boolean isPlayerInv = inventory == null;
        int size = isPlayerInv ? 4 : 9;
        Material[] grid = new Material[size];
        
        for (int i = 0; i < size; i++) {
            ItemStack item = isPlayerInv ? player.getInventory().getItemStack(i + 1) : inventory.getItemStack(i + 1);
            grid[i] = item.material();
        }
        
        ItemStack result = checkRecipes(grid, isPlayerInv);
        
        if (isPlayerInv) {
            player.getInventory().setItemStack(0, result);
        } else {
            inventory.setItemStack(0, result);
        }
    }
    
    public static ItemStack checkRecipes(Material[] grid, boolean is2x2) {
        int logCount = 0;
        int otherCount = 0;
        for (Material m : grid) {
            if (m == Material.OAK_LOG) logCount++;
            else if (m != Material.AIR) otherCount++;
        }
        
        // 1. Бревно -> Доски
        if (logCount == 1 && otherCount == 0) {
            return ItemStack.of(Material.OAK_PLANKS, 4);
        }
        
        // 2. Доски -> Палки (в 2x2)
        if (is2x2) {
            if (grid[0] == Material.OAK_PLANKS && grid[2] == Material.OAK_PLANKS && grid[1] == Material.AIR && grid[3] == Material.AIR) {
                 return ItemStack.of(Material.STICK, 4);
            }
            if (grid[1] == Material.OAK_PLANKS && grid[3] == Material.OAK_PLANKS && grid[0] == Material.AIR && grid[2] == Material.AIR) {
                 return ItemStack.of(Material.STICK, 4);
            }
        }
        
        // 3. Верстак (3x3)
        if (!is2x2) {
            // Деревянная кирка
            if (grid[0] == Material.OAK_PLANKS && grid[1] == Material.OAK_PLANKS && grid[2] == Material.OAK_PLANKS &&
                grid[4] == Material.STICK && grid[7] == Material.STICK) {
                if (grid[3] == Material.AIR && grid[5] == Material.AIR && grid[6] == Material.AIR && grid[8] == Material.AIR) {
                    return ItemStack.of(Material.WOODEN_PICKAXE, 1);
                }
            }
            
            // Палки (в 3x3) по центру
            if (grid[1] == Material.OAK_PLANKS && grid[4] == Material.OAK_PLANKS && 
                grid[0]==Material.AIR && grid[2]==Material.AIR && grid[3]==Material.AIR && grid[5]==Material.AIR && grid[6]==Material.AIR && grid[7]==Material.AIR && grid[8]==Material.AIR) {
                return ItemStack.of(Material.STICK, 4);
            }
        }
        
        return ItemStack.AIR;
    }
}
