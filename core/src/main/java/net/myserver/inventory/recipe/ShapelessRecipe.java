package net.myserver.inventory.recipe;

import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * Бесформенный (Shapeless) рецепт крафта.
 * Ингредиенты могут находиться в любых слотах сетки в произвольном порядке.
 */
public class ShapelessRecipe implements CraftingRecipe {
    private final List<Ingredient> ingredients;
    private final ItemStack result;

    public ShapelessRecipe(List<Ingredient> ingredients, ItemStack result) {
        this.ingredients = ingredients;
        this.result = result;
    }

    @Override
    public boolean matches(Material[] grid, int gridWidth, int gridHeight) {
        // 1. Собираем все непустые материалы из сетки
        List<Material> itemsInGrid = new ArrayList<>(grid.length);
        for (Material m : grid) {
            if (m != null && m != Material.AIR) {
                itemsInGrid.add(m);
            }
        }

        // Количество непустых предметов должно строго совпадать с числом ингредиентов
        if (itemsInGrid.size() != ingredients.size()) {
            return false;
        }

        // 2. Проверяем соответствие мультимножеств
        boolean[] matched = new boolean[itemsInGrid.size()];
        for (Ingredient ingredient : ingredients) {
            boolean found = false;
            for (int i = 0; i < itemsInGrid.size(); i++) {
                if (!matched[i] && ingredient.matches(itemsInGrid.get(i))) {
                    matched[i] = true;
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }

        return true;
    }

    @Override
    public ItemStack getResult() {
        return result;
    }
}
