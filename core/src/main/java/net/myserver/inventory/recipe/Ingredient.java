package net.myserver.inventory.recipe;

import net.minestom.server.item.Material;

import java.util.*;
import java.util.function.Predicate;

/**
 * Обобщенный ингредиент рецепта крафта.
 * Поддерживает конкретные материалы, наборы материалов (тэги) и предикаты.
 */
public class Ingredient implements Predicate<Material> {
    private final Set<Material> matchingMaterials;

    public static final Ingredient AIR = new Ingredient(Collections.singleton(Material.AIR));

    public Ingredient(Set<Material> matchingMaterials) {
        this.matchingMaterials = matchingMaterials;
    }

    public static Ingredient of(Material material) {
        return new Ingredient(Collections.singleton(material));
    }

    public static Ingredient of(Material... materials) {
        return new Ingredient(new HashSet<>(Arrays.asList(materials)));
    }

    public static Ingredient of(Collection<Material> materials) {
        return new Ingredient(new HashSet<>(materials));
    }

    @Override
    public boolean test(Material material) {
        if (matchingMaterials.isEmpty()) return material == Material.AIR;
        return matchingMaterials.contains(material);
    }

    public boolean matches(Material material) {
        return test(material);
    }

    public boolean isAir() {
        return matchingMaterials.contains(Material.AIR);
    }

    public Set<Material> getMatchingMaterials() {
        return matchingMaterials;
    }

    // ==========================================
    // Предустановленные группы материалов (Тэги)
    // ==========================================

    public static final Ingredient PLANKS = Ingredient.of(
            Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.BIRCH_PLANKS,
            Material.JUNGLE_PLANKS, Material.ACACIA_PLANKS, Material.DARK_OAK_PLANKS,
            Material.MANGROVE_PLANKS, Material.CHERRY_PLANKS, Material.BAMBOO_PLANKS,
            Material.CRIMSON_PLANKS, Material.WARPED_PLANKS
    );

    public static final Ingredient LOGS = Ingredient.of(
            Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG,
            Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG,
            Material.CRIMSON_STEM, Material.WARPED_STEM,
            Material.STRIPPED_OAK_LOG, Material.STRIPPED_SPRUCE_LOG, Material.STRIPPED_BIRCH_LOG,
            Material.STRIPPED_JUNGLE_LOG, Material.STRIPPED_ACACIA_LOG, Material.STRIPPED_DARK_OAK_LOG,
            Material.STRIPPED_MANGROVE_LOG, Material.STRIPPED_CHERRY_LOG,
            Material.STRIPPED_CRIMSON_STEM, Material.STRIPPED_WARPED_STEM,
            Material.OAK_WOOD, Material.SPRUCE_WOOD, Material.BIRCH_WOOD, Material.JUNGLE_WOOD,
            Material.ACACIA_WOOD, Material.DARK_OAK_WOOD, Material.MANGROVE_WOOD, Material.CHERRY_WOOD,
            Material.CRIMSON_HYPHAE, Material.WARPED_HYPHAE
    );

    public static final Ingredient COBBLESTONE = Ingredient.of(
            Material.COBBLESTONE, Material.COBBLED_DEEPSLATE, Material.BLACKSTONE
    );

    public static final Ingredient STONE = Ingredient.of(
            Material.STONE, Material.DEEPSLATE, Material.ANDESITE, Material.DIORITE, Material.GRANITE
    );

    public static final Ingredient SMOOTH_STONE = Ingredient.of(Material.SMOOTH_STONE);

    public static final Ingredient COAL = Ingredient.of(Material.COAL, Material.CHARCOAL);

    public static final Ingredient SAND = Ingredient.of(Material.SAND, Material.RED_SAND);

    public static final Ingredient WOOL = Ingredient.of(
            Material.WHITE_WOOL, Material.ORANGE_WOOL, Material.MAGENTA_WOOL, Material.LIGHT_BLUE_WOOL,
            Material.YELLOW_WOOL, Material.LIME_WOOL, Material.PINK_WOOL, Material.GRAY_WOOL,
            Material.LIGHT_GRAY_WOOL, Material.CYAN_WOOL, Material.PURPLE_WOOL, Material.BLUE_WOOL,
            Material.BROWN_WOOL, Material.GREEN_WOOL, Material.RED_WOOL, Material.BLACK_WOOL
    );

    public static final Ingredient SOUL_FIRE_BASE = Ingredient.of(Material.SOUL_SAND, Material.SOUL_SOIL);
}
