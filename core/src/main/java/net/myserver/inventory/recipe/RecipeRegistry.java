package net.myserver.inventory.recipe;

import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

import java.util.*;

/**
 * Центральный реестр рецептов крафта Crystall Core.
 * Содержит полную базу ванильных рецептов (деревообработка, инструменты, броня, механизмы, руды, еда и др.).
 */
public class RecipeRegistry {
    private static final List<CraftingRecipe> recipes = new ArrayList<>();

    public static void init() {
        if (!recipes.isEmpty()) return;

        registerWoodworking();
        registerToolsAndWeapons();
        registerArmor();
        registerStationsAndUtility();
        registerRedstoneAndTransport();
        registerCompactingAndOres();
        registerFoodAndFarming();
        registerMiscAndSurvival();
    }

    public static void register(CraftingRecipe recipe) {
        recipes.add(recipe);
    }

    public static CraftingRecipe findMatchingRecipe(Material[] grid, int width, int height) {
        for (CraftingRecipe recipe : recipes) {
            if (recipe.matches(grid, width, height)) {
                return recipe;
            }
        }
        return null;
    }

    public static List<CraftingRecipe> getRecipes() {
        return Collections.unmodifiableList(recipes);
    }

    // ==========================================
    // Вспомогательные методы регистрации
    // ==========================================

    private static void registerShaped(int width, int height, ItemStack result, Ingredient... ingredients) {
        Ingredient[][] matrix = new Ingredient[height][width];
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                matrix[r][c] = ingredients[r * width + c];
            }
        }
        recipes.add(new ShapedRecipe(width, height, matrix, result, true));
    }

    private static void registerShapeless(ItemStack result, Ingredient... ingredients) {
        recipes.add(new ShapelessRecipe(Arrays.asList(ingredients), result));
    }

    // ==========================================
    // 1. Деревообработка и строительство
    // ==========================================
    private static void registerWoodworking() {
        // Бревна -> Доски (все типы)
        Material[][] woodPairs = {
                {Material.OAK_LOG, Material.STRIPPED_OAK_LOG, Material.OAK_WOOD, Material.STRIPPED_OAK_WOOD, Material.OAK_PLANKS},
                {Material.SPRUCE_LOG, Material.STRIPPED_SPRUCE_LOG, Material.SPRUCE_WOOD, Material.STRIPPED_SPRUCE_WOOD, Material.SPRUCE_PLANKS},
                {Material.BIRCH_LOG, Material.STRIPPED_BIRCH_LOG, Material.BIRCH_WOOD, Material.STRIPPED_BIRCH_WOOD, Material.BIRCH_PLANKS},
                {Material.JUNGLE_LOG, Material.STRIPPED_JUNGLE_LOG, Material.JUNGLE_WOOD, Material.STRIPPED_JUNGLE_WOOD, Material.JUNGLE_PLANKS},
                {Material.ACACIA_LOG, Material.STRIPPED_ACACIA_LOG, Material.ACACIA_WOOD, Material.STRIPPED_ACACIA_WOOD, Material.ACACIA_PLANKS},
                {Material.DARK_OAK_LOG, Material.STRIPPED_DARK_OAK_LOG, Material.DARK_OAK_WOOD, Material.STRIPPED_DARK_OAK_WOOD, Material.DARK_OAK_PLANKS},
                {Material.MANGROVE_LOG, Material.STRIPPED_MANGROVE_LOG, Material.MANGROVE_WOOD, Material.STRIPPED_MANGROVE_WOOD, Material.MANGROVE_PLANKS},
                {Material.CHERRY_LOG, Material.STRIPPED_CHERRY_LOG, Material.CHERRY_WOOD, Material.STRIPPED_CHERRY_WOOD, Material.CHERRY_PLANKS},
                {Material.CRIMSON_STEM, Material.STRIPPED_CRIMSON_STEM, Material.CRIMSON_HYPHAE, Material.STRIPPED_CRIMSON_HYPHAE, Material.CRIMSON_PLANKS},
                {Material.WARPED_STEM, Material.STRIPPED_WARPED_STEM, Material.WARPED_HYPHAE, Material.STRIPPED_WARPED_HYPHAE, Material.WARPED_PLANKS},
        };

        for (Material[] pair : woodPairs) {
            Material planks = pair[4];
            for (int i = 0; i < 4; i++) {
                registerShapeless(ItemStack.of(planks, 4), Ingredient.of(pair[i]));
            }
        }
        registerShapeless(ItemStack.of(Material.BAMBOO_PLANKS, 2), Ingredient.of(Material.BAMBOO_BLOCK));

        // Доски -> Палки (2 вертикально)
        registerShaped(1, 2, ItemStack.of(Material.STICK, 4), Ingredient.PLANKS, Ingredient.PLANKS);
        registerShaped(1, 2, ItemStack.of(Material.STICK, 1), Ingredient.of(Material.BAMBOO), Ingredient.of(Material.BAMBOO));

        // Верстак (4 доски 2x2)
        registerShaped(2, 2, ItemStack.of(Material.CRAFTING_TABLE),
                Ingredient.PLANKS, Ingredient.PLANKS,
                Ingredient.PLANKS, Ingredient.PLANKS);

        // Миски (3 доски V-образно)
        registerShaped(3, 2, ItemStack.of(Material.BOWL, 4),
                Ingredient.PLANKS, Ingredient.AIR, Ingredient.PLANKS,
                Ingredient.AIR, Ingredient.PLANKS, Ingredient.AIR);

        // Сундук (8 досок)
        registerShaped(3, 3, ItemStack.of(Material.CHEST),
                Ingredient.PLANKS, Ingredient.PLANKS, Ingredient.PLANKS,
                Ingredient.PLANKS, Ingredient.AIR, Ingredient.PLANKS,
                Ingredient.PLANKS, Ingredient.PLANKS, Ingredient.PLANKS);

        // Бочка (6 досок + 2 плиты)
        registerShaped(3, 3, ItemStack.of(Material.BARREL),
                Ingredient.PLANKS, Ingredient.of(Material.OAK_SLAB, Material.SPRUCE_SLAB, Material.BIRCH_SLAB), Ingredient.PLANKS,
                Ingredient.PLANKS, Ingredient.AIR, Ingredient.PLANKS,
                Ingredient.PLANKS, Ingredient.of(Material.OAK_SLAB, Material.SPRUCE_SLAB, Material.BIRCH_SLAB), Ingredient.PLANKS);

        // Лестница (7 палок H-образно)
        registerShaped(3, 3, ItemStack.of(Material.LADDER, 3),
                Ingredient.of(Material.STICK), Ingredient.AIR, Ingredient.of(Material.STICK),
                Ingredient.of(Material.STICK), Ingredient.of(Material.STICK), Ingredient.of(Material.STICK),
                Ingredient.of(Material.STICK), Ingredient.AIR, Ingredient.of(Material.STICK));

        // Забор (4 доски + 2 палки)
        registerShaped(3, 2, ItemStack.of(Material.OAK_FENCE, 3),
                Ingredient.PLANKS, Ingredient.of(Material.STICK), Ingredient.PLANKS,
                Ingredient.PLANKS, Ingredient.of(Material.STICK), Ingredient.PLANKS);

        // Калитка (2 доски + 4 палки)
        registerShaped(3, 2, ItemStack.of(Material.OAK_FENCE_GATE),
                Ingredient.of(Material.STICK), Ingredient.PLANKS, Ingredient.of(Material.STICK),
                Ingredient.of(Material.STICK), Ingredient.PLANKS, Ingredient.of(Material.STICK));

        // Двери (6 досок вертикально 2x3)
        registerShaped(2, 3, ItemStack.of(Material.OAK_DOOR, 3),
                Ingredient.PLANKS, Ingredient.PLANKS,
                Ingredient.PLANKS, Ingredient.PLANKS,
                Ingredient.PLANKS, Ingredient.PLANKS);
        registerShaped(2, 3, ItemStack.of(Material.IRON_DOOR, 3),
                Ingredient.of(Material.IRON_INGOT), Ingredient.of(Material.IRON_INGOT),
                Ingredient.of(Material.IRON_INGOT), Ingredient.of(Material.IRON_INGOT),
                Ingredient.of(Material.IRON_INGOT), Ingredient.of(Material.IRON_INGOT));

        // Люки (6 досок 3x2)
        registerShaped(3, 2, ItemStack.of(Material.OAK_TRAPDOOR, 2),
                Ingredient.PLANKS, Ingredient.PLANKS, Ingredient.PLANKS,
                Ingredient.PLANKS, Ingredient.PLANKS, Ingredient.PLANKS);
        registerShaped(2, 2, ItemStack.of(Material.IRON_TRAPDOOR),
                Ingredient.of(Material.IRON_INGOT), Ingredient.of(Material.IRON_INGOT),
                Ingredient.of(Material.IRON_INGOT), Ingredient.of(Material.IRON_INGOT));

        // Плиты (3 блока горизонтально)
        registerShaped(3, 1, ItemStack.of(Material.OAK_SLAB, 6), Ingredient.of(Material.OAK_PLANKS), Ingredient.of(Material.OAK_PLANKS), Ingredient.of(Material.OAK_PLANKS));
        registerShaped(3, 1, ItemStack.of(Material.SPRUCE_SLAB, 6), Ingredient.of(Material.SPRUCE_PLANKS), Ingredient.of(Material.SPRUCE_PLANKS), Ingredient.of(Material.SPRUCE_PLANKS));
        registerShaped(3, 1, ItemStack.of(Material.BIRCH_SLAB, 6), Ingredient.of(Material.BIRCH_PLANKS), Ingredient.of(Material.BIRCH_PLANKS), Ingredient.of(Material.BIRCH_PLANKS));
        registerShaped(3, 1, ItemStack.of(Material.STONE_SLAB, 6), Ingredient.of(Material.STONE), Ingredient.of(Material.STONE), Ingredient.of(Material.STONE));
        registerShaped(3, 1, ItemStack.of(Material.COBBLESTONE_SLAB, 6), Ingredient.of(Material.COBBLESTONE), Ingredient.of(Material.COBBLESTONE), Ingredient.of(Material.COBBLESTONE));
        registerShaped(3, 1, ItemStack.of(Material.SMOOTH_STONE_SLAB, 6), Ingredient.of(Material.SMOOTH_STONE), Ingredient.of(Material.SMOOTH_STONE), Ingredient.of(Material.SMOOTH_STONE));

        // Ступени (6 блоков ступеньками)
        registerShaped(3, 3, ItemStack.of(Material.OAK_STAIRS, 4),
                Ingredient.of(Material.OAK_PLANKS), Ingredient.AIR, Ingredient.AIR,
                Ingredient.of(Material.OAK_PLANKS), Ingredient.of(Material.OAK_PLANKS), Ingredient.AIR,
                Ingredient.of(Material.OAK_PLANKS), Ingredient.of(Material.OAK_PLANKS), Ingredient.of(Material.OAK_PLANKS));
        registerShaped(3, 3, ItemStack.of(Material.COBBLESTONE_STAIRS, 4),
                Ingredient.of(Material.COBBLESTONE), Ingredient.AIR, Ingredient.AIR,
                Ingredient.of(Material.COBBLESTONE), Ingredient.of(Material.COBBLESTONE), Ingredient.AIR,
                Ingredient.of(Material.COBBLESTONE), Ingredient.of(Material.COBBLESTONE), Ingredient.of(Material.COBBLESTONE));
        registerShaped(3, 3, ItemStack.of(Material.STONE_STAIRS, 4),
                Ingredient.of(Material.STONE), Ingredient.AIR, Ingredient.AIR,
                Ingredient.of(Material.STONE), Ingredient.of(Material.STONE), Ingredient.AIR,
                Ingredient.of(Material.STONE), Ingredient.of(Material.STONE), Ingredient.of(Material.STONE));

        // Лодки (5 досок U-образно)
        registerShaped(3, 2, ItemStack.of(Material.OAK_BOAT),
                Ingredient.of(Material.OAK_PLANKS), Ingredient.AIR, Ingredient.of(Material.OAK_PLANKS),
                Ingredient.of(Material.OAK_PLANKS), Ingredient.of(Material.OAK_PLANKS), Ingredient.of(Material.OAK_PLANKS));
        registerShaped(3, 2, ItemStack.of(Material.SPRUCE_BOAT),
                Ingredient.of(Material.SPRUCE_PLANKS), Ingredient.AIR, Ingredient.of(Material.SPRUCE_PLANKS),
                Ingredient.of(Material.SPRUCE_PLANKS), Ingredient.of(Material.SPRUCE_PLANKS), Ingredient.of(Material.SPRUCE_PLANKS));
        registerShaped(3, 2, ItemStack.of(Material.BIRCH_BOAT),
                Ingredient.of(Material.BIRCH_PLANKS), Ingredient.AIR, Ingredient.of(Material.BIRCH_PLANKS),
                Ingredient.of(Material.BIRCH_PLANKS), Ingredient.of(Material.BIRCH_PLANKS), Ingredient.of(Material.BIRCH_PLANKS));

        // Стеклянные панели (6 стеклянных блоков)
        registerShaped(3, 2, ItemStack.of(Material.GLASS_PANE, 16),
                Ingredient.of(Material.GLASS), Ingredient.of(Material.GLASS), Ingredient.of(Material.GLASS),
                Ingredient.of(Material.GLASS), Ingredient.of(Material.GLASS), Ingredient.of(Material.GLASS));

        // Железные решетки (6 слитков железа)
        registerShaped(3, 2, ItemStack.of(Material.IRON_BARS, 16),
                Ingredient.of(Material.IRON_INGOT), Ingredient.of(Material.IRON_INGOT), Ingredient.of(Material.IRON_INGOT),
                Ingredient.of(Material.IRON_INGOT), Ingredient.of(Material.IRON_INGOT), Ingredient.of(Material.IRON_INGOT));
    }

    // ==========================================
    // 2. Инструменты и оружие
    // ==========================================
    private static void registerToolsAndWeapons() {
        Object[][] tiers = {
                {Ingredient.PLANKS, Material.WOODEN_SWORD, Material.WOODEN_PICKAXE, Material.WOODEN_AXE, Material.WOODEN_SHOVEL, Material.WOODEN_HOE},
                {Ingredient.COBBLESTONE, Material.STONE_SWORD, Material.STONE_PICKAXE, Material.STONE_AXE, Material.STONE_SHOVEL, Material.STONE_HOE},
                {Ingredient.of(Material.IRON_INGOT), Material.IRON_SWORD, Material.IRON_PICKAXE, Material.IRON_AXE, Material.IRON_SHOVEL, Material.IRON_HOE},
                {Ingredient.of(Material.GOLD_INGOT), Material.GOLDEN_SWORD, Material.GOLDEN_PICKAXE, Material.GOLDEN_AXE, Material.GOLDEN_SHOVEL, Material.GOLDEN_HOE},
                {Ingredient.of(Material.DIAMOND), Material.DIAMOND_SWORD, Material.DIAMOND_PICKAXE, Material.DIAMOND_AXE, Material.DIAMOND_SHOVEL, Material.DIAMOND_HOE}
        };

        Ingredient stick = Ingredient.of(Material.STICK);

        for (Object[] tier : tiers) {
            Ingredient mat = (Ingredient) tier[0];

            // Меч (2 материала + 1 палка)
            registerShaped(1, 3, ItemStack.of((Material) tier[1]), mat, mat, stick);

            // Кирка (3 материала по верху + 2 палки)
            registerShaped(3, 3, ItemStack.of((Material) tier[2]),
                    mat, mat, mat,
                    Ingredient.AIR, stick, Ingredient.AIR,
                    Ingredient.AIR, stick, Ingredient.AIR);

            // Топор (3 материала уголком + 2 палки)
            registerShaped(2, 3, ItemStack.of((Material) tier[3]),
                    mat, mat,
                    mat, stick,
                    Ingredient.AIR, stick);

            // Лопата (1 материал + 2 палки)
            registerShaped(1, 3, ItemStack.of((Material) tier[4]), mat, stick, stick);

            // Мотыга (2 материала + 2 палки)
            registerShaped(2, 3, ItemStack.of((Material) tier[5]),
                    mat, mat,
                    Ingredient.AIR, stick,
                    Ingredient.AIR, stick);
        }

        // Лук (3 палки + 3 нити)
        registerShaped(3, 3, ItemStack.of(Material.BOW),
                Ingredient.AIR, stick, Ingredient.of(Material.STRING),
                stick, Ingredient.AIR, Ingredient.of(Material.STRING),
                Ingredient.AIR, stick, Ingredient.of(Material.STRING));

        // Арбалет
        registerShaped(3, 3, ItemStack.of(Material.CROSSBOW),
                stick, Ingredient.of(Material.IRON_INGOT), stick,
                Ingredient.of(Material.STRING), Ingredient.of(Material.TRIPWIRE_HOOK), Ingredient.of(Material.STRING),
                Ingredient.AIR, stick, Ingredient.AIR);

        // Стрелы (Кремень + Палка + Перо) -> 4 стрелы
        registerShaped(1, 3, ItemStack.of(Material.ARROW, 4),
                Ingredient.of(Material.FLINT),
                stick,
                Ingredient.of(Material.FEATHER));

        // Щит (6 досок + 1 слиток железа)
        registerShaped(3, 3, ItemStack.of(Material.SHIELD),
                Ingredient.PLANKS, Ingredient.of(Material.IRON_INGOT), Ingredient.PLANKS,
                Ingredient.PLANKS, Ingredient.PLANKS, Ingredient.PLANKS,
                Ingredient.AIR, Ingredient.PLANKS, Ingredient.AIR);

        // Ножницы (2 слитка железа по диагонали)
        registerShaped(2, 2, ItemStack.of(Material.SHEARS),
                Ingredient.AIR, Ingredient.of(Material.IRON_INGOT),
                Ingredient.of(Material.IRON_INGOT), Ingredient.AIR);

        // Удочка (3 палки + 2 нити)
        registerShaped(3, 3, ItemStack.of(Material.FISHING_ROD),
                Ingredient.AIR, Ingredient.AIR, stick,
                Ingredient.AIR, stick, Ingredient.of(Material.STRING),
                stick, Ingredient.AIR, Ingredient.of(Material.STRING));

        // Огниво (Железный слиток + кремень)
        registerShapeless(ItemStack.of(Material.FLINT_AND_STEEL),
                Ingredient.of(Material.IRON_INGOT), Ingredient.of(Material.FLINT));
    }

    // ==========================================
    // 3. Броня
    // ==========================================
    private static void registerArmor() {
        Object[][] armorTiers = {
                {Ingredient.of(Material.LEATHER), Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS},
                {Ingredient.of(Material.IRON_INGOT), Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS},
                {Ingredient.of(Material.GOLD_INGOT), Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS},
                {Ingredient.of(Material.DIAMOND), Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS}
        };

        for (Object[] tier : armorTiers) {
            Ingredient mat = (Ingredient) tier[0];

            // Шлем (5 материалов перевернутой U)
            registerShaped(3, 2, ItemStack.of((Material) tier[1]),
                    mat, mat, mat,
                    mat, Ingredient.AIR, mat);

            // Нагрудник (8 материалов)
            registerShaped(3, 3, ItemStack.of((Material) tier[2]),
                    mat, Ingredient.AIR, mat,
                    mat, mat, mat,
                    mat, mat, mat);

            // Поножи (7 материалов)
            registerShaped(3, 3, ItemStack.of((Material) tier[3]),
                    mat, mat, mat,
                    mat, Ingredient.AIR, mat,
                    mat, Ingredient.AIR, mat);

            // Ботинки (4 материала)
            registerShaped(3, 2, ItemStack.of((Material) tier[4]),
                    mat, Ingredient.AIR, mat,
                    mat, Ingredient.AIR, mat);
        }
    }

    // ==========================================
    // 4. Станки, утилиты и декор
    // ==========================================
    private static void registerStationsAndUtility() {
        // Печь (8 булыжников)
        registerShaped(3, 3, ItemStack.of(Material.FURNACE),
                Ingredient.COBBLESTONE, Ingredient.COBBLESTONE, Ingredient.COBBLESTONE,
                Ingredient.COBBLESTONE, Ingredient.AIR, Ingredient.COBBLESTONE,
                Ingredient.COBBLESTONE, Ingredient.COBBLESTONE, Ingredient.COBBLESTONE);

        // Плавильня (Печь + 5 слитков железа + 3 гладких камня)
        registerShaped(3, 3, ItemStack.of(Material.BLAST_FURNACE),
                Ingredient.of(Material.IRON_INGOT), Ingredient.of(Material.IRON_INGOT), Ingredient.of(Material.IRON_INGOT),
                Ingredient.of(Material.IRON_INGOT), Ingredient.of(Material.FURNACE), Ingredient.of(Material.IRON_INGOT),
                Ingredient.SMOOTH_STONE, Ingredient.SMOOTH_STONE, Ingredient.SMOOTH_STONE);

        // Коптильня (Печь + 4 бревна)
        registerShaped(3, 3, ItemStack.of(Material.SMOKER),
                Ingredient.AIR, Ingredient.LOGS, Ingredient.AIR,
                Ingredient.LOGS, Ingredient.of(Material.FURNACE), Ingredient.LOGS,
                Ingredient.AIR, Ingredient.LOGS, Ingredient.AIR);

        // Наковальня (3 железных блока + 4 слитка железа)
        registerShaped(3, 3, ItemStack.of(Material.ANVIL),
                Ingredient.of(Material.IRON_BLOCK), Ingredient.of(Material.IRON_BLOCK), Ingredient.of(Material.IRON_BLOCK),
                Ingredient.AIR, Ingredient.of(Material.IRON_INGOT), Ingredient.AIR,
                Ingredient.of(Material.IRON_INGOT), Ingredient.of(Material.IRON_INGOT), Ingredient.of(Material.IRON_INGOT));

        // Точило (Grindstone)
        registerShaped(3, 2, ItemStack.of(Material.GRINDSTONE),
                Ingredient.of(Material.STICK), Ingredient.of(Material.STONE_SLAB), Ingredient.of(Material.STICK),
                Ingredient.PLANKS, Ingredient.AIR, Ingredient.PLANKS);

        // Кузнечный стол (Smithing Table)
        registerShaped(2, 3, ItemStack.of(Material.SMITHING_TABLE),
                Ingredient.of(Material.IRON_INGOT), Ingredient.of(Material.IRON_INGOT),
                Ingredient.PLANKS, Ingredient.PLANKS,
                Ingredient.PLANKS, Ingredient.PLANKS);

        // Стол лучника (Fletching Table)
        registerShaped(2, 3, ItemStack.of(Material.FLETCHING_TABLE),
                Ingredient.of(Material.FLINT), Ingredient.of(Material.FLINT),
                Ingredient.PLANKS, Ingredient.PLANKS,
                Ingredient.PLANKS, Ingredient.PLANKS);

        // Ткацкий станок (Loom)
        registerShaped(2, 2, ItemStack.of(Material.LOOM),
                Ingredient.of(Material.STRING), Ingredient.of(Material.STRING),
                Ingredient.PLANKS, Ingredient.PLANKS);

        // Стол картографа (Cartography Table)
        registerShaped(2, 3, ItemStack.of(Material.CARTOGRAPHY_TABLE),
                Ingredient.of(Material.PAPER), Ingredient.of(Material.PAPER),
                Ingredient.PLANKS, Ingredient.PLANKS,
                Ingredient.PLANKS, Ingredient.PLANKS);

        // Книжная полка (6 досок + 3 книги)
        registerShaped(3, 3, ItemStack.of(Material.BOOKSHELF),
                Ingredient.PLANKS, Ingredient.PLANKS, Ingredient.PLANKS,
                Ingredient.of(Material.BOOK), Ingredient.of(Material.BOOK), Ingredient.of(Material.BOOK),
                Ingredient.PLANKS, Ingredient.PLANKS, Ingredient.PLANKS);

        // Факел (Уголь + Палка) -> 4 факела
        registerShaped(1, 2, ItemStack.of(Material.TORCH, 4), Ingredient.COAL, Ingredient.of(Material.STICK));

        // Факел душ
        registerShaped(1, 3, ItemStack.of(Material.SOUL_TORCH, 4),
                Ingredient.COAL,
                Ingredient.of(Material.STICK),
                Ingredient.SOUL_FIRE_BASE);

        // Фонарь (8 самородков железа + факел)
        registerShaped(3, 3, ItemStack.of(Material.LANTERN),
                Ingredient.of(Material.IRON_NUGGET), Ingredient.of(Material.IRON_NUGGET), Ingredient.of(Material.IRON_NUGGET),
                Ingredient.of(Material.IRON_NUGGET), Ingredient.of(Material.TORCH), Ingredient.of(Material.IRON_NUGGET),
                Ingredient.of(Material.IRON_NUGGET), Ingredient.of(Material.IRON_NUGGET), Ingredient.of(Material.IRON_NUGGET));

        // Костер (3 бревна + 3 палки + 1 уголь)
        registerShaped(3, 3, ItemStack.of(Material.CAMPFIRE),
                Ingredient.AIR, Ingredient.of(Material.STICK), Ingredient.AIR,
                Ingredient.of(Material.STICK), Ingredient.COAL, Ingredient.of(Material.STICK),
                Ingredient.LOGS, Ingredient.LOGS, Ingredient.LOGS);

        // Ведро (3 слитка железа V-образно)
        registerShaped(3, 2, ItemStack.of(Material.BUCKET),
                Ingredient.of(Material.IRON_INGOT), Ingredient.AIR, Ingredient.of(Material.IRON_INGOT),
                Ingredient.AIR, Ingredient.of(Material.IRON_INGOT), Ingredient.AIR);

        // Стеклянная колба (3 стекла) -> 3 колбы
        registerShaped(3, 2, ItemStack.of(Material.GLASS_BOTTLE, 3),
                Ingredient.of(Material.GLASS), Ingredient.AIR, Ingredient.of(Material.GLASS),
                Ingredient.AIR, Ingredient.of(Material.GLASS), Ingredient.AIR);

        // Котел (7 слитков железа U-образно)
        registerShaped(3, 3, ItemStack.of(Material.CAULDRON),
                Ingredient.of(Material.IRON_INGOT), Ingredient.AIR, Ingredient.of(Material.IRON_INGOT),
                Ingredient.of(Material.IRON_INGOT), Ingredient.AIR, Ingredient.of(Material.IRON_INGOT),
                Ingredient.of(Material.IRON_INGOT), Ingredient.of(Material.IRON_INGOT), Ingredient.of(Material.IRON_INGOT));

        // Варочная стойка (Огненный стержень + 3 булыжника)
        registerShaped(3, 2, ItemStack.of(Material.BREWING_STAND),
                Ingredient.AIR, Ingredient.of(Material.BLAZE_ROD), Ingredient.AIR,
                Ingredient.COBBLESTONE, Ingredient.COBBLESTONE, Ingredient.COBBLESTONE);

        // Эндер сундук (8 обсидиана + Око Края)
        registerShaped(3, 3, ItemStack.of(Material.ENDER_CHEST),
                Ingredient.of(Material.OBSIDIAN), Ingredient.of(Material.OBSIDIAN), Ingredient.of(Material.OBSIDIAN),
                Ingredient.of(Material.OBSIDIAN), Ingredient.of(Material.ENDER_EYE), Ingredient.of(Material.OBSIDIAN),
                Ingredient.of(Material.OBSIDIAN), Ingredient.of(Material.OBSIDIAN), Ingredient.of(Material.OBSIDIAN));

        // Маяк (Beacon)
        registerShaped(3, 3, ItemStack.of(Material.BEACON),
                Ingredient.of(Material.GLASS), Ingredient.of(Material.GLASS), Ingredient.of(Material.GLASS),
                Ingredient.of(Material.GLASS), Ingredient.of(Material.NETHER_STAR), Ingredient.of(Material.GLASS),
                Ingredient.of(Material.OBSIDIAN), Ingredient.of(Material.OBSIDIAN), Ingredient.of(Material.OBSIDIAN));

        // Громоотвод (3 медных слитка)
        registerShaped(1, 3, ItemStack.of(Material.LIGHTNING_ROD),
                Ingredient.of(Material.COPPER_INGOT), Ingredient.of(Material.COPPER_INGOT), Ingredient.of(Material.COPPER_INGOT));

        // Подзорная труба
        registerShaped(1, 3, ItemStack.of(Material.SPYGLASS),
                Ingredient.of(Material.AMETHYST_SHARD), Ingredient.of(Material.COPPER_INGOT), Ingredient.of(Material.COPPER_INGOT));
    }

    // ==========================================
    // 5. Редстоун и механизмы
    // ==========================================
    private static void registerRedstoneAndTransport() {
        Ingredient redstone = Ingredient.of(Material.REDSTONE);
        Ingredient stick = Ingredient.of(Material.STICK);

        // Редстоун факел
        registerShaped(1, 2, ItemStack.of(Material.REDSTONE_TORCH), redstone, stick);

        // Повторитель (Repeater)
        registerShaped(3, 2, ItemStack.of(Material.REPEATER),
                Ingredient.of(Material.REDSTONE_TORCH), redstone, Ingredient.of(Material.REDSTONE_TORCH),
                Ingredient.STONE, Ingredient.STONE, Ingredient.STONE);

        // Компаратор (Comparator)
        registerShaped(3, 3, ItemStack.of(Material.COMPARATOR),
                Ingredient.AIR, Ingredient.of(Material.REDSTONE_TORCH), Ingredient.AIR,
                Ingredient.of(Material.REDSTONE_TORCH), Ingredient.of(Material.QUARTZ), Ingredient.of(Material.REDSTONE_TORCH),
                Ingredient.STONE, Ingredient.STONE, Ingredient.STONE);

        // Поршень (3 доски + 4 булыжника + 1 железо + 1 редстоун)
        registerShaped(3, 3, ItemStack.of(Material.PISTON),
                Ingredient.PLANKS, Ingredient.PLANKS, Ingredient.PLANKS,
                Ingredient.COBBLESTONE, Ingredient.of(Material.IRON_INGOT), Ingredient.COBBLESTONE,
                Ingredient.COBBLESTONE, redstone, Ingredient.COBBLESTONE);

        // Липкий поршень
        registerShapeless(ItemStack.of(Material.STICKY_PISTON), Ingredient.of(Material.PISTON), Ingredient.of(Material.SLIME_BALL));

        // Наблюдатель (Observer)
        registerShaped(3, 3, ItemStack.of(Material.OBSERVER),
                Ingredient.COBBLESTONE, Ingredient.COBBLESTONE, Ingredient.COBBLESTONE,
                redstone, redstone, Ingredient.of(Material.QUARTZ),
                Ingredient.COBBLESTONE, Ingredient.COBBLESTONE, Ingredient.COBBLESTONE);

        // Раздатчик (Dispenser)
        registerShaped(3, 3, ItemStack.of(Material.DISPENSER),
                Ingredient.COBBLESTONE, Ingredient.COBBLESTONE, Ingredient.COBBLESTONE,
                Ingredient.COBBLESTONE, Ingredient.of(Material.BOW), Ingredient.COBBLESTONE,
                Ingredient.COBBLESTONE, redstone, Ingredient.COBBLESTONE);

        // Выбрасыватель (Dropper)
        registerShaped(3, 3, ItemStack.of(Material.DROPPER),
                Ingredient.COBBLESTONE, Ingredient.COBBLESTONE, Ingredient.COBBLESTONE,
                Ingredient.COBBLESTONE, Ingredient.AIR, Ingredient.COBBLESTONE,
                Ingredient.COBBLESTONE, redstone, Ingredient.COBBLESTONE);

        // Воронка (Hopper: 5 железа + сундук)
        registerShaped(3, 3, ItemStack.of(Material.HOPPER),
                Ingredient.of(Material.IRON_INGOT), Ingredient.AIR, Ingredient.of(Material.IRON_INGOT),
                Ingredient.of(Material.IRON_INGOT), Ingredient.of(Material.CHEST), Ingredient.of(Material.IRON_INGOT),
                Ingredient.AIR, Ingredient.of(Material.IRON_INGOT), Ingredient.AIR);

        // Рычаг
        registerShaped(1, 2, ItemStack.of(Material.LEVER), stick, Ingredient.COBBLESTONE);

        // Натяжной датчик (Tripwire hook: железо + палка + доска)
        registerShaped(1, 3, ItemStack.of(Material.TRIPWIRE_HOOK, 2), Ingredient.of(Material.IRON_INGOT), stick, Ingredient.PLANKS);

        // Кнопки и нажимные плиты
        registerShapeless(ItemStack.of(Material.OAK_BUTTON), Ingredient.of(Material.OAK_PLANKS));
        registerShapeless(ItemStack.of(Material.STONE_BUTTON), Ingredient.of(Material.STONE));
        registerShaped(2, 1, ItemStack.of(Material.OAK_PRESSURE_PLATE), Ingredient.PLANKS, Ingredient.PLANKS);
        registerShaped(2, 1, ItemStack.of(Material.STONE_PRESSURE_PLATE), Ingredient.of(Material.STONE), Ingredient.of(Material.STONE));
        registerShaped(2, 1, ItemStack.of(Material.HEAVY_WEIGHTED_PRESSURE_PLATE), Ingredient.of(Material.IRON_INGOT), Ingredient.of(Material.IRON_INGOT));
        registerShaped(2, 1, ItemStack.of(Material.LIGHT_WEIGHTED_PRESSURE_PLATE), Ingredient.of(Material.GOLD_INGOT), Ingredient.of(Material.GOLD_INGOT));

        // Динамит (5 пороха + 4 песка в шахматном порядке)
        registerShaped(3, 3, ItemStack.of(Material.TNT),
                Ingredient.of(Material.GUNPOWDER), Ingredient.SAND, Ingredient.of(Material.GUNPOWDER),
                Ingredient.SAND, Ingredient.of(Material.GUNPOWDER), Ingredient.SAND,
                Ingredient.of(Material.GUNPOWDER), Ingredient.SAND, Ingredient.of(Material.GUNPOWDER));

        // Вагонетка (5 слитков железа U-образно)
        registerShaped(3, 2, ItemStack.of(Material.MINECART),
                Ingredient.of(Material.IRON_INGOT), Ingredient.AIR, Ingredient.of(Material.IRON_INGOT),
                Ingredient.of(Material.IRON_INGOT), Ingredient.of(Material.IRON_INGOT), Ingredient.of(Material.IRON_INGOT));

        // Рельсы обычные (6 железа + 1 палка) -> 16 шт.
        registerShaped(3, 3, ItemStack.of(Material.RAIL, 16),
                Ingredient.of(Material.IRON_INGOT), Ingredient.AIR, Ingredient.of(Material.IRON_INGOT),
                Ingredient.of(Material.IRON_INGOT), stick, Ingredient.of(Material.IRON_INGOT),
                Ingredient.of(Material.IRON_INGOT), Ingredient.AIR, Ingredient.of(Material.IRON_INGOT));

        // Энергорельсы (6 золота + 1 палка + 1 редстоун) -> 6 шт.
        registerShaped(3, 3, ItemStack.of(Material.POWERED_RAIL, 6),
                Ingredient.of(Material.GOLD_INGOT), Ingredient.AIR, Ingredient.of(Material.GOLD_INGOT),
                Ingredient.of(Material.GOLD_INGOT), stick, Ingredient.of(Material.GOLD_INGOT),
                Ingredient.of(Material.GOLD_INGOT), redstone, Ingredient.of(Material.GOLD_INGOT));
    }

    // ==========================================
    // 6. Сжатие блоков руд и раскрафт (Compacting / Decompacting)
    // ==========================================
    private static void registerCompactingAndOres() {
        Material[][] compactPairs = {
                {Material.IRON_INGOT, Material.IRON_BLOCK},
                {Material.GOLD_INGOT, Material.GOLD_BLOCK},
                {Material.DIAMOND, Material.DIAMOND_BLOCK},
                {Material.EMERALD, Material.EMERALD_BLOCK},
                {Material.NETHERITE_INGOT, Material.NETHERITE_BLOCK},
                {Material.COPPER_INGOT, Material.COPPER_BLOCK},
                {Material.COAL, Material.COAL_BLOCK},
                {Material.REDSTONE, Material.REDSTONE_BLOCK},
                {Material.LAPIS_LAZULI, Material.LAPIS_BLOCK},
                {Material.RAW_IRON, Material.RAW_IRON_BLOCK},
                {Material.RAW_GOLD, Material.RAW_GOLD_BLOCK},
                {Material.RAW_COPPER, Material.RAW_COPPER_BLOCK},
                {Material.SLIME_BALL, Material.SLIME_BLOCK},
                {Material.WHEAT, Material.HAY_BLOCK},
                {Material.BONE_MEAL, Material.BONE_BLOCK}
        };

        for (Material[] pair : compactPairs) {
            Material item = pair[0];
            Material block = pair[1];

            // 9 предметов -> 1 блок
            registerShaped(3, 3, ItemStack.of(block),
                    Ingredient.of(item), Ingredient.of(item), Ingredient.of(item),
                    Ingredient.of(item), Ingredient.of(item), Ingredient.of(item),
                    Ingredient.of(item), Ingredient.of(item), Ingredient.of(item));

            // 1 блок -> 9 предметов
            registerShapeless(ItemStack.of(item, 9), Ingredient.of(block));
        }

        // Самородки <-> Слитки
        registerShaped(3, 3, ItemStack.of(Material.IRON_INGOT),
                Ingredient.of(Material.IRON_NUGGET), Ingredient.of(Material.IRON_NUGGET), Ingredient.of(Material.IRON_NUGGET),
                Ingredient.of(Material.IRON_NUGGET), Ingredient.of(Material.IRON_NUGGET), Ingredient.of(Material.IRON_NUGGET),
                Ingredient.of(Material.IRON_NUGGET), Ingredient.of(Material.IRON_NUGGET), Ingredient.of(Material.IRON_NUGGET));
        registerShapeless(ItemStack.of(Material.IRON_NUGGET, 9), Ingredient.of(Material.IRON_INGOT));

        registerShaped(3, 3, ItemStack.of(Material.GOLD_INGOT),
                Ingredient.of(Material.GOLD_NUGGET), Ingredient.of(Material.GOLD_NUGGET), Ingredient.of(Material.GOLD_NUGGET),
                Ingredient.of(Material.GOLD_NUGGET), Ingredient.of(Material.GOLD_NUGGET), Ingredient.of(Material.GOLD_NUGGET),
                Ingredient.of(Material.GOLD_NUGGET), Ingredient.of(Material.GOLD_NUGGET), Ingredient.of(Material.GOLD_NUGGET));
        registerShapeless(ItemStack.of(Material.GOLD_NUGGET, 9), Ingredient.of(Material.GOLD_INGOT));

        // 4 предмета 2x2 -> Блок
        registerShaped(2, 2, ItemStack.of(Material.CLAY), Ingredient.of(Material.CLAY_BALL), Ingredient.of(Material.CLAY_BALL), Ingredient.of(Material.CLAY_BALL), Ingredient.of(Material.CLAY_BALL));
        registerShaped(2, 2, ItemStack.of(Material.SNOW_BLOCK), Ingredient.of(Material.SNOWBALL), Ingredient.of(Material.SNOWBALL), Ingredient.of(Material.SNOWBALL), Ingredient.of(Material.SNOWBALL));
        registerShaped(2, 2, ItemStack.of(Material.AMETHYST_BLOCK), Ingredient.of(Material.AMETHYST_SHARD), Ingredient.of(Material.AMETHYST_SHARD), Ingredient.of(Material.AMETHYST_SHARD), Ingredient.of(Material.AMETHYST_SHARD));
        registerShaped(2, 2, ItemStack.of(Material.QUARTZ_BLOCK), Ingredient.of(Material.QUARTZ), Ingredient.of(Material.QUARTZ), Ingredient.of(Material.QUARTZ), Ingredient.of(Material.QUARTZ));
        registerShaped(2, 2, ItemStack.of(Material.GLOWSTONE), Ingredient.of(Material.GLOWSTONE_DUST), Ingredient.of(Material.GLOWSTONE_DUST), Ingredient.of(Material.GLOWSTONE_DUST), Ingredient.of(Material.GLOWSTONE_DUST));
        registerShaped(2, 2, ItemStack.of(Material.SANDSTONE), Ingredient.of(Material.SAND), Ingredient.of(Material.SAND), Ingredient.of(Material.SAND), Ingredient.of(Material.SAND));
        registerShaped(2, 2, ItemStack.of(Material.RED_SANDSTONE), Ingredient.of(Material.RED_SAND), Ingredient.of(Material.RED_SAND), Ingredient.of(Material.RED_SAND), Ingredient.of(Material.RED_SAND));
        registerShaped(2, 2, ItemStack.of(Material.HONEYCOMB_BLOCK), Ingredient.of(Material.HONEYCOMB), Ingredient.of(Material.HONEYCOMB), Ingredient.of(Material.HONEYCOMB), Ingredient.of(Material.HONEYCOMB));

        // Незеритовый слиток (4 золота + 4 незеритовых обломка)
        registerShapeless(ItemStack.of(Material.NETHERITE_INGOT),
                Ingredient.of(Material.NETHERITE_SCRAP), Ingredient.of(Material.NETHERITE_SCRAP),
                Ingredient.of(Material.NETHERITE_SCRAP), Ingredient.of(Material.NETHERITE_SCRAP),
                Ingredient.of(Material.GOLD_INGOT), Ingredient.of(Material.GOLD_INGOT),
                Ingredient.of(Material.GOLD_INGOT), Ingredient.of(Material.GOLD_INGOT));

        // Костная мука из кости (1 кость -> 3 муки)
        registerShapeless(ItemStack.of(Material.BONE_MEAL, 3), Ingredient.of(Material.BONE));
    }

    // ==========================================
    // 7. Еда и кулинария
    // ==========================================
    private static void registerFoodAndFarming() {
        // Хлеб (3 пшеницы горизонтально)
        registerShaped(3, 1, ItemStack.of(Material.BREAD), Ingredient.of(Material.WHEAT), Ingredient.of(Material.WHEAT), Ingredient.of(Material.WHEAT));

        // Печенье (2 пшеницы + какао) -> 8 шт.
        registerShaped(3, 1, ItemStack.of(Material.COOKIE, 8), Ingredient.of(Material.WHEAT), Ingredient.of(Material.COCOA_BEANS), Ingredient.of(Material.WHEAT));

        // Торт (3 ведра молока + 2 сахара + 1 яйцо + 3 пшеницы)
        registerShaped(3, 3, ItemStack.of(Material.CAKE),
                Ingredient.of(Material.MILK_BUCKET), Ingredient.of(Material.MILK_BUCKET), Ingredient.of(Material.MILK_BUCKET),
                Ingredient.of(Material.SUGAR), Ingredient.of(Material.EGG), Ingredient.of(Material.SUGAR),
                Ingredient.of(Material.WHEAT), Ingredient.of(Material.WHEAT), Ingredient.of(Material.WHEAT));

        // Тыквенный пирог (Тыква + Сахар + Яйцо)
        registerShapeless(ItemStack.of(Material.PUMPKIN_PIE), Ingredient.of(Material.PUMPKIN), Ingredient.of(Material.SUGAR), Ingredient.of(Material.EGG));

        // Золотое яблоко (8 слитков золота + яблоко)
        registerShaped(3, 3, ItemStack.of(Material.GOLDEN_APPLE),
                Ingredient.of(Material.GOLD_INGOT), Ingredient.of(Material.GOLD_INGOT), Ingredient.of(Material.GOLD_INGOT),
                Ingredient.of(Material.GOLD_INGOT), Ingredient.of(Material.APPLE), Ingredient.of(Material.GOLD_INGOT),
                Ingredient.of(Material.GOLD_INGOT), Ingredient.of(Material.GOLD_INGOT), Ingredient.of(Material.GOLD_INGOT));

        // Зачарованное золотое яблоко (8 блоков золота + яблоко)
        registerShaped(3, 3, ItemStack.of(Material.ENCHANTED_GOLDEN_APPLE),
                Ingredient.of(Material.GOLD_BLOCK), Ingredient.of(Material.GOLD_BLOCK), Ingredient.of(Material.GOLD_BLOCK),
                Ingredient.of(Material.GOLD_BLOCK), Ingredient.of(Material.APPLE), Ingredient.of(Material.GOLD_BLOCK),
                Ingredient.of(Material.GOLD_BLOCK), Ingredient.of(Material.GOLD_BLOCK), Ingredient.of(Material.GOLD_BLOCK));

        // Золотая морковь (8 самородков золота + морковь)
        registerShaped(3, 3, ItemStack.of(Material.GOLDEN_CARROT),
                Ingredient.of(Material.GOLD_NUGGET), Ingredient.of(Material.GOLD_NUGGET), Ingredient.of(Material.GOLD_NUGGET),
                Ingredient.of(Material.GOLD_NUGGET), Ingredient.of(Material.CARROT), Ingredient.of(Material.GOLD_NUGGET),
                Ingredient.of(Material.GOLD_NUGGET), Ingredient.of(Material.GOLD_NUGGET), Ingredient.of(Material.GOLD_NUGGET));

        // Сверкающий ломтик арбуза
        registerShaped(3, 3, ItemStack.of(Material.GLISTERING_MELON_SLICE),
                Ingredient.of(Material.GOLD_NUGGET), Ingredient.of(Material.GOLD_NUGGET), Ingredient.of(Material.GOLD_NUGGET),
                Ingredient.of(Material.GOLD_NUGGET), Ingredient.of(Material.MELON_SLICE), Ingredient.of(Material.GOLD_NUGGET),
                Ingredient.of(Material.GOLD_NUGGET), Ingredient.of(Material.GOLD_NUGGET), Ingredient.of(Material.GOLD_NUGGET));

        // Грибное рагу
        registerShapeless(ItemStack.of(Material.MUSHROOM_STEW),
                Ingredient.of(Material.RED_MUSHROOM), Ingredient.of(Material.BROWN_MUSHROOM), Ingredient.of(Material.BOWL));

        // Сахар из сахарного тростника
        registerShapeless(ItemStack.of(Material.SUGAR), Ingredient.of(Material.SUGAR_CANE));

        // Арбуз из 9 долек
        registerShaped(3, 3, ItemStack.of(Material.MELON),
                Ingredient.of(Material.MELON_SLICE), Ingredient.of(Material.MELON_SLICE), Ingredient.of(Material.MELON_SLICE),
                Ingredient.of(Material.MELON_SLICE), Ingredient.of(Material.MELON_SLICE), Ingredient.of(Material.MELON_SLICE),
                Ingredient.of(Material.MELON_SLICE), Ingredient.of(Material.MELON_SLICE), Ingredient.of(Material.MELON_SLICE));
    }

    // ==========================================
    // 8. Разное, красители и выживание
    // ==========================================
    private static void registerMiscAndSurvival() {
        // Бумага (3 тростника) -> 3 бумаги
        registerShaped(3, 1, ItemStack.of(Material.PAPER, 3), Ingredient.of(Material.SUGAR_CANE), Ingredient.of(Material.SUGAR_CANE), Ingredient.of(Material.SUGAR_CANE));

        // Книга (3 бумаги + 1 кожа)
        registerShapeless(ItemStack.of(Material.BOOK),
                Ingredient.of(Material.PAPER), Ingredient.of(Material.PAPER), Ingredient.of(Material.PAPER), Ingredient.of(Material.LEATHER));

        // Рамка (8 палок + кожа)
        registerShaped(3, 3, ItemStack.of(Material.ITEM_FRAME),
                Ingredient.of(Material.STICK), Ingredient.of(Material.STICK), Ingredient.of(Material.STICK),
                Ingredient.of(Material.STICK), Ingredient.of(Material.LEATHER), Ingredient.of(Material.STICK),
                Ingredient.of(Material.STICK), Ingredient.of(Material.STICK), Ingredient.of(Material.STICK));

        // Картина (8 палок + шерсть)
        registerShaped(3, 3, ItemStack.of(Material.PAINTING),
                Ingredient.of(Material.STICK), Ingredient.of(Material.STICK), Ingredient.of(Material.STICK),
                Ingredient.of(Material.STICK), Ingredient.WOOL, Ingredient.of(Material.STICK),
                Ingredient.of(Material.STICK), Ingredient.of(Material.STICK), Ingredient.of(Material.STICK));

        // Поводок (4 нити + слизь) -> 2 поводка
        registerShaped(3, 3, ItemStack.of(Material.LEAD, 2),
                Ingredient.of(Material.STRING), Ingredient.of(Material.STRING), Ingredient.AIR,
                Ingredient.of(Material.STRING), Ingredient.of(Material.SLIME_BALL), Ingredient.AIR,
                Ingredient.AIR, Ingredient.AIR, Ingredient.of(Material.STRING));

        // Око Края
        registerShapeless(ItemStack.of(Material.ENDER_EYE), Ingredient.of(Material.ENDER_PEARL), Ingredient.of(Material.BLAZE_POWDER));

        // Огненный порошок из огненного стержня (1 стержень -> 2 порошка)
        registerShapeless(ItemStack.of(Material.BLAZE_POWDER, 2), Ingredient.of(Material.BLAZE_ROD));

        // Лавовый крем (Слизь + Огненный порошок)
        registerShapeless(ItemStack.of(Material.MAGMA_CREAM), Ingredient.of(Material.SLIME_BALL), Ingredient.of(Material.BLAZE_POWDER));

        // Маринованный паучий глаз
        registerShapeless(ItemStack.of(Material.FERMENTED_SPIDER_EYE),
                Ingredient.of(Material.SPIDER_EYE), Ingredient.of(Material.SUGAR), Ingredient.of(Material.BROWN_MUSHROOM));

        // Компас (4 железа + редстоун)
        registerShaped(3, 3, ItemStack.of(Material.COMPASS),
                Ingredient.AIR, Ingredient.of(Material.IRON_INGOT), Ingredient.AIR,
                Ingredient.of(Material.IRON_INGOT), Ingredient.of(Material.REDSTONE), Ingredient.of(Material.IRON_INGOT),
                Ingredient.AIR, Ingredient.of(Material.IRON_INGOT), Ingredient.AIR);

        // Часы (4 золота + редстоун)
        registerShaped(3, 3, ItemStack.of(Material.CLOCK),
                Ingredient.AIR, Ingredient.of(Material.GOLD_INGOT), Ingredient.AIR,
                Ingredient.of(Material.GOLD_INGOT), Ingredient.of(Material.REDSTONE), Ingredient.of(Material.GOLD_INGOT),
                Ingredient.AIR, Ingredient.of(Material.GOLD_INGOT), Ingredient.AIR);

        // Кровати (3 шерсти сверху + 3 доски снизу)
        registerShaped(3, 2, ItemStack.of(Material.WHITE_BED),
                Ingredient.of(Material.WHITE_WOOL), Ingredient.of(Material.WHITE_WOOL), Ingredient.of(Material.WHITE_WOOL),
                Ingredient.PLANKS, Ingredient.PLANKS, Ingredient.PLANKS);
        registerShaped(3, 2, ItemStack.of(Material.RED_BED),
                Ingredient.of(Material.RED_WOOL), Ingredient.of(Material.RED_WOOL), Ingredient.of(Material.RED_WOOL),
                Ingredient.PLANKS, Ingredient.PLANKS, Ingredient.PLANKS);
        registerShaped(3, 2, ItemStack.of(Material.BLUE_BED),
                Ingredient.of(Material.BLUE_WOOL), Ingredient.of(Material.BLUE_WOOL), Ingredient.of(Material.BLUE_WOOL),
                Ingredient.PLANKS, Ingredient.PLANKS, Ingredient.PLANKS);
        registerShaped(3, 2, ItemStack.of(Material.BLACK_BED),
                Ingredient.of(Material.BLACK_WOOL), Ingredient.of(Material.BLACK_WOOL), Ingredient.of(Material.BLACK_WOOL),
                Ingredient.PLANKS, Ingredient.PLANKS, Ingredient.PLANKS);

        // Ковры (2 шерсти горизонтально) -> 3 ковра
        registerShaped(2, 1, ItemStack.of(Material.WHITE_CARPET, 3), Ingredient.of(Material.WHITE_WOOL), Ingredient.of(Material.WHITE_WOOL));
        registerShaped(2, 1, ItemStack.of(Material.RED_CARPET, 3), Ingredient.of(Material.RED_WOOL), Ingredient.of(Material.RED_WOOL));
        registerShaped(2, 1, ItemStack.of(Material.BLACK_CARPET, 3), Ingredient.of(Material.BLACK_WOOL), Ingredient.of(Material.BLACK_WOOL));

        // Крафты красителей из растений
        registerShapeless(ItemStack.of(Material.YELLOW_DYE), Ingredient.of(Material.DANDELION));
        registerShapeless(ItemStack.of(Material.RED_DYE), Ingredient.of(Material.POPPY));
        registerShapeless(ItemStack.of(Material.BLUE_DYE), Ingredient.of(Material.CORNFLOWER));
        registerShapeless(ItemStack.of(Material.WHITE_DYE), Ingredient.of(Material.LILY_OF_THE_VALLEY));
        registerShapeless(ItemStack.of(Material.BLACK_DYE), Ingredient.of(Material.WITHER_ROSE));

        // Популярные ванильные дополнения для удобства выживания:
        // Седло (3 кожи + 2 слитка железа + 1 нить)
        registerShaped(3, 2, ItemStack.of(Material.SADDLE),
                Ingredient.of(Material.LEATHER), Ingredient.of(Material.LEATHER), Ingredient.of(Material.LEATHER),
                Ingredient.of(Material.IRON_INGOT), Ingredient.of(Material.STRING), Ingredient.of(Material.IRON_INGOT));

        // Бирка (Железный слиток + Бумага + Нить)
        registerShapeless(ItemStack.of(Material.NAME_TAG),
                Ingredient.of(Material.IRON_INGOT), Ingredient.of(Material.PAPER), Ingredient.of(Material.STRING));
    }
}
