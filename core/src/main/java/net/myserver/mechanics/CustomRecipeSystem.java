package net.myserver.mechanics;

import net.myserver.inventory.recipe.RecipeRegistry;

/**
 * Система регистрации кастомных рецептов сервера Crystall.
 */
public class CustomRecipeSystem {
    public static void register() {
        RecipeRegistry.init();
    }
}
