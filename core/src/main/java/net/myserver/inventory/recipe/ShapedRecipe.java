package net.myserver.inventory.recipe;

import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

/**
 * Форменный (Shaped) рецепт крафта.
 * Поддерживает свободное смещение внутри сетки 2x2 и 3x3, а также зеркальное отражение (Mirrored).
 */
public class ShapedRecipe implements CraftingRecipe {
    private final int width;
    private final int height;
    private final Ingredient[][] pattern;
    private final ItemStack result;
    private final boolean mirrored;

    public ShapedRecipe(int width, int height, Ingredient[][] pattern, ItemStack result, boolean mirrored) {
        this.width = width;
        this.height = height;
        this.pattern = pattern;
        this.result = result;
        this.mirrored = mirrored;
    }

    public ShapedRecipe(int width, int height, Ingredient[][] pattern, ItemStack result) {
        this(width, height, pattern, result, true);
    }

    @Override
    public boolean matches(Material[] grid, int gridWidth, int gridHeight) {
        // 1. Находим минимальный ограничивающий прямоугольник (Bounding Box) непустых слотов сетки
        int minX = gridWidth;
        int maxX = -1;
        int minY = gridHeight;
        int maxY = -1;

        for (int y = 0; y < gridHeight; y++) {
            for (int x = 0; x < gridWidth; x++) {
                int index = y * gridWidth + x;
                Material m = (index < grid.length) ? grid[index] : Material.AIR;
                if (m != null && m != Material.AIR) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }

        // Если сетка пуста
        if (maxX == -1) {
            return false;
        }

        int actualWidth = maxX - minX + 1;
        int actualHeight = maxY - minY + 1;

        // Если размерности не совпадают с рецептом, совпадение невозможно
        if (actualWidth != width || actualHeight != height) {
            return false;
        }

        // 2. Проверяем обычное положение
        boolean normalMatch = true;
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                int gridX = minX + c;
                int gridY = minY + r;
                int index = gridY * gridWidth + gridX;
                Material m = (index < grid.length && grid[index] != null) ? grid[index] : Material.AIR;
                Ingredient expected = pattern[r][c];

                if (!expected.matches(m)) {
                    normalMatch = false;
                    break;
                }
            }
            if (!normalMatch) break;
        }

        if (normalMatch) return true;

        // 3. Если рецепт зеркальный, проверяем горизонтально отраженное положение
        if (mirrored && width > 1) {
            boolean mirrorMatch = true;
            for (int r = 0; r < height; r++) {
                for (int c = 0; c < width; c++) {
                    int gridX = minX + c;
                    int gridY = minY + r;
                    int index = gridY * gridWidth + gridX;
                    Material m = (index < grid.length && grid[index] != null) ? grid[index] : Material.AIR;
                    Ingredient expected = pattern[r][width - 1 - c]; // Отражение по горизонтали

                    if (!expected.matches(m)) {
                        mirrorMatch = false;
                        break;
                    }
                }
                if (!mirrorMatch) break;
            }
            return mirrorMatch;
        }

        return false;
    }

    @Override
    public ItemStack getResult() {
        return result;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
