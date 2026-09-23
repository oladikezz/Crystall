package net.myserver.inventory.recipe;

import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

/**
 * Базовый интерфейс для всех рецептов верстака и инвентаря игрока.
 */
public interface CraftingRecipe {
    /**
     * Проверяет, подходит ли переданная сетка материалов под данный рецепт.
     * @param grid сетка материалов (размером 4 для 2x2 или 9 для 3x3)
     * @param gridWidth ширина сетки (2 или 3)
     * @param gridHeight высота сетки (2 или 3)
     */
    boolean matches(Material[] grid, int gridWidth, int gridHeight);

    /**
     * Возвращает результат крафта для данной сетки.
     */
    ItemStack getResult();

    /**
     * Возвращает предмет-остаток для слота после изъятия ингредиента (например, ведро после ведра с молоком или водой).
     */
    default ItemStack getRemainingItem(ItemStack slotItem) {
        if (slotItem == null || slotItem.isAir()) return ItemStack.AIR;
        Material mat = slotItem.material();
        if (mat == Material.MILK_BUCKET || mat == Material.WATER_BUCKET || mat == Material.LAVA_BUCKET) {
            return ItemStack.of(Material.BUCKET);
        }
        if (mat == Material.HONEY_BOTTLE) {
            return ItemStack.of(Material.GLASS_BOTTLE);
        }
        return ItemStack.AIR;
    }
}
