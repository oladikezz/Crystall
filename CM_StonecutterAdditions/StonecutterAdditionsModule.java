package net.schalker.SMPS.modules.stonecutter;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.module.BaseModule;
import net.schalker.DoAPI.core.module.ModuleInfo;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.StonecuttingRecipe;

/**
 * SM_StonecutterAdditions — adds missing stonecutter recipes for
 * deepslate, all wood types, and various stone variants.
 *
 * Uses batched global-task registration to avoid blocking the main tick
 * on Folia/Canvas (where every addRecipe/removeRecipe triggers
 * finalizeRecipeLoading + advancement reload).
 */
public class StonecutterAdditionsModule extends BaseModule {
   private FileConfiguration config;
   private final List<NamespacedKey> registeredRecipes = new ArrayList<>();

   /** How many recipes to add/remove per tick to avoid watchdog timeout */
   private static final int BATCH_SIZE = 5;

   public StonecutterAdditionsModule(DoAPI plugin) {
      super(plugin, loadModuleInfo());
   }

   private static ModuleInfo loadModuleInfo() {
      try (InputStream stream = StonecutterAdditionsModule.class.getClassLoader()
              .getResourceAsStream("module.yml")) {
         if (stream != null) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(
               new InputStreamReader(stream, StandardCharsets.UTF_8));
            return new ModuleInfo(
               yml.getString("name", "SM_StonecutterAdditions"),
               yml.getString("version", "1.0.0"),
               yml.getString("author", "Unknown"),
               yml.getString("description", "")
            );
         }
      } catch (Exception ignored) {}
      return new ModuleInfo("SM_StonecutterAdditions", "1.0.0", "Unknown", "");
   }

   @Override
   public void onEnable() {
      super.onEnable();
      loadConfigs();
      collectAndRegisterAllRecipes();
      this.plugin.getDebugSystem().log("StonecutterAdditions", "Module enabling...");
   }

   @Override
   public void onDisable() {
      super.onDisable();
      batchUnregisterAllRecipes();
      this.plugin.getDebugSystem().log("StonecutterAdditions", "Module disabling...");
   }

   @Override
   public void reload() {
      super.reload();
      // Synchronous unregister then re-register with batching
      batchUnregisterAllRecipes();
      loadConfigs();
      collectAndRegisterAllRecipes();
      this.plugin.getDebugSystem().log("StonecutterAdditions", "Module reloading...");
   }

   private void loadConfigs() {
      this.config = this.plugin.getModuleManager()
         .loadModuleConfig("SM_StonecutterAdditions");
      if (this.config == null) {
         this.config = new YamlConfiguration();
      }
   }

   // ================================================================
   // Batched recipe registration
   // ================================================================

   /** Pending recipes to be registered across multiple ticks */
   private final List<StonecuttingRecipe> pendingAdd = new ArrayList<>();

   private static final String TASK_REGISTER = "stonecutter-batch-register";
   private static final String TASK_UNREGISTER = "stonecutter-batch-unregister";

   /**
    * Collect all recipes that need to be registered, then process them
    * in batches of BATCH_SIZE per tick using a global timer task.
    */
   private void collectAndRegisterAllRecipes() {
      pendingAdd.clear();

      if (config.getBoolean("deepslate.enabled", true)) {
         collectDeepslateRecipes();
      }
      if (config.getBoolean("wood.enabled", true)) {
         collectWoodRecipes();
      }
      if (config.getBoolean("stone-variants.enabled", true)) {
         collectStoneVariantRecipes();
      }

      if (pendingAdd.isEmpty()) {
         this.plugin.getDebugSystem().log("StonecutterAdditions",
            "No recipes to register (all categories disabled)");
         return;
      }

      final List<StonecuttingRecipe> toAdd = new ArrayList<>(pendingAdd);
      pendingAdd.clear();
      final AtomicInteger index = new AtomicInteger(0);
      final int total = toAdd.size();

      this.plugin.getDebugSystem().log("StonecutterAdditions",
         "Queued " + total + " recipes for batched registration (" + BATCH_SIZE + "/tick)");

      this.plugin.getSchedulerManager().runTaskTimer(
         TASK_REGISTER,
         () -> {
            int i = index.get();
            if (i >= total) {
               this.plugin.getSchedulerManager().cancelTask(TASK_REGISTER);
               try { Bukkit.updateRecipes(); } catch (Exception ignored) {}
               this.plugin.getDebugSystem().log("StonecutterAdditions",
                  "Batch registration complete: " + registeredRecipes.size() + " recipes");
               return;
            }
            int end = Math.min(i + BATCH_SIZE, total);
            for (int j = i; j < end; j++) {
               StonecuttingRecipe recipe = toAdd.get(j);
               try {
                  Bukkit.addRecipe(recipe);
                  registeredRecipes.add(recipe.getKey());
               } catch (Exception e) {
                  this.plugin.getDebugSystem().log("StonecutterAdditions",
                     "Failed to register: " + recipe.getKey().getKey() + " (" + e.getMessage() + ")");
               }
            }
            index.set(end);
         },
         1L, 1L
      );
   }

   /**
    * Unregister all recipes in batches across multiple ticks.
    * For onDisable we do it synchronously since the module is shutting down
    * and we can't schedule more tasks. We accept the lag on disable as a
    * trade-off — disabling happens much less frequently than enabling.
    *
    * Actually, we use the same batched approach for disable too.
    */
   private void batchUnregisterAllRecipes() {
      if (registeredRecipes.isEmpty()) return;

      final List<NamespacedKey> toRemove = new ArrayList<>(registeredRecipes);
      registeredRecipes.clear();
      final AtomicInteger index = new AtomicInteger(0);
      final int total = toRemove.size();

      this.plugin.getDebugSystem().log("StonecutterAdditions",
         "Queued " + total + " recipes for batched removal (" + BATCH_SIZE + "/tick)");

      this.plugin.getSchedulerManager().runTaskTimer(
         TASK_UNREGISTER,
         () -> {
            int i = index.get();
            if (i >= total) {
               this.plugin.getSchedulerManager().cancelTask(TASK_UNREGISTER);
               try { Bukkit.updateRecipes(); } catch (Exception ignored) {}
               this.plugin.getDebugSystem().log("StonecutterAdditions",
                  "Batch removal complete: " + total + " recipes removed");
               return;
            }
            int end = Math.min(i + BATCH_SIZE, total);
            for (int j = i; j < end; j++) {
               try {
                  Bukkit.removeRecipe(toRemove.get(j));
               } catch (Exception ignored) {}
            }
            index.set(end);
         },
         1L, 1L
      );
   }

   /**
    * Queue a stonecutting recipe for batched registration.
    */
   private void queueRecipe(String id, Material input, Material output, int count) {
      if (input == null || output == null) return;
      NamespacedKey key = new NamespacedKey("smps", "stonecutter_" + id);
      StonecuttingRecipe recipe = new StonecuttingRecipe(key, new ItemStack(output, count), input);
      pendingAdd.add(recipe);
   }

   // ================================================================
   // Category 1: Deepslate
   // ================================================================
   private void collectDeepslateRecipes() {
      Material D = Material.DEEPSLATE;

      // Deepslate -> cobbled deepslate family
      queueRecipe("deepslate_to_cobbled", D, Material.COBBLED_DEEPSLATE, 1);
      queueRecipe("deepslate_to_cobbled_slab", D, Material.COBBLED_DEEPSLATE_SLAB, 2);
      queueRecipe("deepslate_to_cobbled_stairs", D, Material.COBBLED_DEEPSLATE_STAIRS, 1);
      queueRecipe("deepslate_to_cobbled_wall", D, Material.COBBLED_DEEPSLATE_WALL, 1);

      // Deepslate -> polished deepslate family
      queueRecipe("deepslate_to_polished", D, Material.POLISHED_DEEPSLATE, 1);
      queueRecipe("deepslate_to_polished_slab", D, Material.POLISHED_DEEPSLATE_SLAB, 2);
      queueRecipe("deepslate_to_polished_stairs", D, Material.POLISHED_DEEPSLATE_STAIRS, 1);
      queueRecipe("deepslate_to_polished_wall", D, Material.POLISHED_DEEPSLATE_WALL, 1);

      // Deepslate -> deepslate bricks family
      queueRecipe("deepslate_to_bricks", D, Material.DEEPSLATE_BRICKS, 1);
      queueRecipe("deepslate_to_brick_slab", D, Material.DEEPSLATE_BRICK_SLAB, 2);
      queueRecipe("deepslate_to_brick_stairs", D, Material.DEEPSLATE_BRICK_STAIRS, 1);
      queueRecipe("deepslate_to_brick_wall", D, Material.DEEPSLATE_BRICK_WALL, 1);

      // Deepslate -> deepslate tiles family
      queueRecipe("deepslate_to_tiles", D, Material.DEEPSLATE_TILES, 1);
      queueRecipe("deepslate_to_tile_slab", D, Material.DEEPSLATE_TILE_SLAB, 2);
      queueRecipe("deepslate_to_tile_stairs", D, Material.DEEPSLATE_TILE_STAIRS, 1);
      queueRecipe("deepslate_to_tile_wall", D, Material.DEEPSLATE_TILE_WALL, 1);

      // Deepslate -> chiseled deepslate
      queueRecipe("deepslate_to_chiseled", D, Material.CHISELED_DEEPSLATE, 1);
   }

   // ================================================================
   // Category 2: Wood types
   // ================================================================
   private void collectWoodRecipes() {
      collectWoodFamily("oak", Material.OAK_LOG, Material.OAK_WOOD,
         Material.STRIPPED_OAK_LOG, Material.STRIPPED_OAK_WOOD,
         Material.OAK_PLANKS, Material.OAK_STAIRS, Material.OAK_SLAB,
         Material.OAK_FENCE, Material.OAK_FENCE_GATE,
         Material.OAK_SIGN, Material.OAK_HANGING_SIGN,
         Material.OAK_BUTTON, Material.OAK_PRESSURE_PLATE,
         Material.OAK_DOOR, Material.OAK_TRAPDOOR, Material.OAK_BOAT);

      collectWoodFamily("spruce", Material.SPRUCE_LOG, Material.SPRUCE_WOOD,
         Material.STRIPPED_SPRUCE_LOG, Material.STRIPPED_SPRUCE_WOOD,
         Material.SPRUCE_PLANKS, Material.SPRUCE_STAIRS, Material.SPRUCE_SLAB,
         Material.SPRUCE_FENCE, Material.SPRUCE_FENCE_GATE,
         Material.SPRUCE_SIGN, Material.SPRUCE_HANGING_SIGN,
         Material.SPRUCE_BUTTON, Material.SPRUCE_PRESSURE_PLATE,
         Material.SPRUCE_DOOR, Material.SPRUCE_TRAPDOOR, Material.SPRUCE_BOAT);

      collectWoodFamily("birch", Material.BIRCH_LOG, Material.BIRCH_WOOD,
         Material.STRIPPED_BIRCH_LOG, Material.STRIPPED_BIRCH_WOOD,
         Material.BIRCH_PLANKS, Material.BIRCH_STAIRS, Material.BIRCH_SLAB,
         Material.BIRCH_FENCE, Material.BIRCH_FENCE_GATE,
         Material.BIRCH_SIGN, Material.BIRCH_HANGING_SIGN,
         Material.BIRCH_BUTTON, Material.BIRCH_PRESSURE_PLATE,
         Material.BIRCH_DOOR, Material.BIRCH_TRAPDOOR, Material.BIRCH_BOAT);

      collectWoodFamily("jungle", Material.JUNGLE_LOG, Material.JUNGLE_WOOD,
         Material.STRIPPED_JUNGLE_LOG, Material.STRIPPED_JUNGLE_WOOD,
         Material.JUNGLE_PLANKS, Material.JUNGLE_STAIRS, Material.JUNGLE_SLAB,
         Material.JUNGLE_FENCE, Material.JUNGLE_FENCE_GATE,
         Material.JUNGLE_SIGN, Material.JUNGLE_HANGING_SIGN,
         Material.JUNGLE_BUTTON, Material.JUNGLE_PRESSURE_PLATE,
         Material.JUNGLE_DOOR, Material.JUNGLE_TRAPDOOR, Material.JUNGLE_BOAT);

      collectWoodFamily("acacia", Material.ACACIA_LOG, Material.ACACIA_WOOD,
         Material.STRIPPED_ACACIA_LOG, Material.STRIPPED_ACACIA_WOOD,
         Material.ACACIA_PLANKS, Material.ACACIA_STAIRS, Material.ACACIA_SLAB,
         Material.ACACIA_FENCE, Material.ACACIA_FENCE_GATE,
         Material.ACACIA_SIGN, Material.ACACIA_HANGING_SIGN,
         Material.ACACIA_BUTTON, Material.ACACIA_PRESSURE_PLATE,
         Material.ACACIA_DOOR, Material.ACACIA_TRAPDOOR, Material.ACACIA_BOAT);

      collectWoodFamily("dark_oak", Material.DARK_OAK_LOG, Material.DARK_OAK_WOOD,
         Material.STRIPPED_DARK_OAK_LOG, Material.STRIPPED_DARK_OAK_WOOD,
         Material.DARK_OAK_PLANKS, Material.DARK_OAK_STAIRS, Material.DARK_OAK_SLAB,
         Material.DARK_OAK_FENCE, Material.DARK_OAK_FENCE_GATE,
         Material.DARK_OAK_SIGN, Material.DARK_OAK_HANGING_SIGN,
         Material.DARK_OAK_BUTTON, Material.DARK_OAK_PRESSURE_PLATE,
         Material.DARK_OAK_DOOR, Material.DARK_OAK_TRAPDOOR, Material.DARK_OAK_BOAT);

      collectWoodFamily("mangrove", Material.MANGROVE_LOG, Material.MANGROVE_WOOD,
         Material.STRIPPED_MANGROVE_LOG, Material.STRIPPED_MANGROVE_WOOD,
         Material.MANGROVE_PLANKS, Material.MANGROVE_STAIRS, Material.MANGROVE_SLAB,
         Material.MANGROVE_FENCE, Material.MANGROVE_FENCE_GATE,
         Material.MANGROVE_SIGN, Material.MANGROVE_HANGING_SIGN,
         Material.MANGROVE_BUTTON, Material.MANGROVE_PRESSURE_PLATE,
         Material.MANGROVE_DOOR, Material.MANGROVE_TRAPDOOR, Material.MANGROVE_BOAT);

      collectWoodFamily("cherry", Material.CHERRY_LOG, Material.CHERRY_WOOD,
         Material.STRIPPED_CHERRY_LOG, Material.STRIPPED_CHERRY_WOOD,
         Material.CHERRY_PLANKS, Material.CHERRY_STAIRS, Material.CHERRY_SLAB,
         Material.CHERRY_FENCE, Material.CHERRY_FENCE_GATE,
         Material.CHERRY_SIGN, Material.CHERRY_HANGING_SIGN,
         Material.CHERRY_BUTTON, Material.CHERRY_PRESSURE_PLATE,
         Material.CHERRY_DOOR, Material.CHERRY_TRAPDOOR, Material.CHERRY_BOAT);

      // Pale oak (1.21.4)
      collectWoodFamilySafe("pale_oak", "PALE_OAK_LOG", "PALE_OAK_WOOD",
         "STRIPPED_PALE_OAK_LOG", "STRIPPED_PALE_OAK_WOOD",
         "PALE_OAK_PLANKS", "PALE_OAK_STAIRS", "PALE_OAK_SLAB",
         "PALE_OAK_FENCE", "PALE_OAK_FENCE_GATE",
         "PALE_OAK_SIGN", "PALE_OAK_HANGING_SIGN",
         "PALE_OAK_BUTTON", "PALE_OAK_PRESSURE_PLATE",
         "PALE_OAK_DOOR", "PALE_OAK_TRAPDOOR", "PALE_OAK_BOAT");

      // Bamboo (special: BAMBOO_BLOCK instead of logs, BAMBOO_RAFT instead of boat)
      collectWoodFamily("bamboo", Material.BAMBOO_BLOCK, null,
         Material.STRIPPED_BAMBOO_BLOCK, null,
         Material.BAMBOO_PLANKS, Material.BAMBOO_STAIRS, Material.BAMBOO_SLAB,
         Material.BAMBOO_FENCE, Material.BAMBOO_FENCE_GATE,
         Material.BAMBOO_SIGN, Material.BAMBOO_HANGING_SIGN,
         Material.BAMBOO_BUTTON, Material.BAMBOO_PRESSURE_PLATE,
         Material.BAMBOO_DOOR, Material.BAMBOO_TRAPDOOR, Material.BAMBOO_RAFT);
      // Bamboo mosaic
      queueRecipe("bamboo_planks_to_mosaic", Material.BAMBOO_PLANKS, Material.BAMBOO_MOSAIC, 1);
      queueRecipe("bamboo_planks_to_mosaic_slab", Material.BAMBOO_PLANKS, Material.BAMBOO_MOSAIC_SLAB, 2);
      queueRecipe("bamboo_planks_to_mosaic_stairs", Material.BAMBOO_PLANKS, Material.BAMBOO_MOSAIC_STAIRS, 1);
      queueRecipe("bamboo_mosaic_to_slab", Material.BAMBOO_MOSAIC, Material.BAMBOO_MOSAIC_SLAB, 2);
      queueRecipe("bamboo_mosaic_to_stairs", Material.BAMBOO_MOSAIC, Material.BAMBOO_MOSAIC_STAIRS, 1);

      // Crimson (nether -- no boats, uses stems/hyphae)
      collectWoodFamily("crimson", Material.CRIMSON_STEM, Material.CRIMSON_HYPHAE,
         Material.STRIPPED_CRIMSON_STEM, Material.STRIPPED_CRIMSON_HYPHAE,
         Material.CRIMSON_PLANKS, Material.CRIMSON_STAIRS, Material.CRIMSON_SLAB,
         Material.CRIMSON_FENCE, Material.CRIMSON_FENCE_GATE,
         Material.CRIMSON_SIGN, Material.CRIMSON_HANGING_SIGN,
         Material.CRIMSON_BUTTON, Material.CRIMSON_PRESSURE_PLATE,
         Material.CRIMSON_DOOR, Material.CRIMSON_TRAPDOOR, null);

      // Warped (nether -- no boats)
      collectWoodFamily("warped", Material.WARPED_STEM, Material.WARPED_HYPHAE,
         Material.STRIPPED_WARPED_STEM, Material.STRIPPED_WARPED_HYPHAE,
         Material.WARPED_PLANKS, Material.WARPED_STAIRS, Material.WARPED_SLAB,
         Material.WARPED_FENCE, Material.WARPED_FENCE_GATE,
         Material.WARPED_SIGN, Material.WARPED_HANGING_SIGN,
         Material.WARPED_BUTTON, Material.WARPED_PRESSURE_PLATE,
         Material.WARPED_DOOR, Material.WARPED_TRAPDOOR, null);
   }

   private void collectWoodFamily(String prefix, Material log, Material wood,
                                   Material strippedLog, Material strippedWood,
                                   Material planks, Material stairs, Material slab,
                                   Material fence, Material fenceGate,
                                   Material sign, Material hangingSign,
                                   Material button, Material pressurePlate,
                                   Material door, Material trapdoor, Material boat) {
      if (log != null && planks != null) {
         queueRecipe(prefix + "_log_to_planks", log, planks, 4);
         if (strippedLog != null)
            queueRecipe(prefix + "_log_to_stripped", log, strippedLog, 1);
         if (wood != null)
            queueRecipe(prefix + "_log_to_wood", log, wood, 1);
      }
      if (wood != null && planks != null) {
         queueRecipe(prefix + "_wood_to_planks", wood, planks, 4);
         if (strippedWood != null)
            queueRecipe(prefix + "_wood_to_stripped", wood, strippedWood, 1);
      }
      if (strippedLog != null && planks != null) {
         queueRecipe(prefix + "_stripped_log_to_planks", strippedLog, planks, 4);
      }
      if (strippedWood != null && planks != null) {
         queueRecipe(prefix + "_stripped_wood_to_planks", strippedWood, planks, 4);
      }
      if (planks != null) {
         if (stairs != null) queueRecipe(prefix + "_planks_to_stairs", planks, stairs, 1);
         if (slab != null) queueRecipe(prefix + "_planks_to_slab", planks, slab, 2);
         if (fence != null) queueRecipe(prefix + "_planks_to_fence", planks, fence, 1);
         if (fenceGate != null) queueRecipe(prefix + "_planks_to_fence_gate", planks, fenceGate, 1);
         if (sign != null) queueRecipe(prefix + "_planks_to_sign", planks, sign, 2);
         if (hangingSign != null) queueRecipe(prefix + "_planks_to_hanging_sign", planks, hangingSign, 2);
         if (button != null) queueRecipe(prefix + "_planks_to_button", planks, button, 1);
         if (pressurePlate != null) queueRecipe(prefix + "_planks_to_pressure_plate", planks, pressurePlate, 1);
         if (door != null) queueRecipe(prefix + "_planks_to_door", planks, door, 1);
         if (trapdoor != null) queueRecipe(prefix + "_planks_to_trapdoor", planks, trapdoor, 1);
         if (boat != null) queueRecipe(prefix + "_planks_to_boat", planks, boat, 1);
      }
   }

   private void collectWoodFamilySafe(String prefix, String... names) {
      if (names.length < 16) return;
      Material[] mats = new Material[names.length];
      for (int i = 0; i < names.length; i++) {
         try {
            mats[i] = Material.valueOf(names[i]);
         } catch (IllegalArgumentException ignored) {
            mats[i] = null;
         }
      }
      if (mats[4] == null) return;
      collectWoodFamily(prefix, mats[0], mats[1], mats[2], mats[3],
         mats[4], mats[5], mats[6], mats[7], mats[8],
         mats[9], mats[10], mats[11], mats[12], mats[13], mats[14], mats[15]);
   }

   // ================================================================
   // Category 3: Stone variants
   // ================================================================
   private void collectStoneVariantRecipes() {
      queueRecipe("prismarine_to_bricks", Material.PRISMARINE, Material.PRISMARINE_BRICKS, 1);
      queueRecipe("prismarine_to_brick_slab", Material.PRISMARINE, Material.PRISMARINE_BRICK_SLAB, 2);
      queueRecipe("prismarine_to_brick_stairs", Material.PRISMARINE, Material.PRISMARINE_BRICK_STAIRS, 1);
      queueRecipe("prismarine_to_dark", Material.PRISMARINE, Material.DARK_PRISMARINE, 1);
      queueRecipe("prismarine_to_dark_slab", Material.PRISMARINE, Material.DARK_PRISMARINE_SLAB, 2);
      queueRecipe("prismarine_to_dark_stairs", Material.PRISMARINE, Material.DARK_PRISMARINE_STAIRS, 1);

      queueRecipe("purpur_pillar_to_slab", Material.PURPUR_PILLAR, Material.PURPUR_SLAB, 2);
      queueRecipe("purpur_pillar_to_stairs", Material.PURPUR_PILLAR, Material.PURPUR_STAIRS, 1);

      queueRecipe("quartz_pillar_to_slab", Material.QUARTZ_PILLAR, Material.QUARTZ_SLAB, 2);
      queueRecipe("quartz_pillar_to_stairs", Material.QUARTZ_PILLAR, Material.QUARTZ_STAIRS, 1);
      queueRecipe("quartz_bricks_to_slab", Material.QUARTZ_BRICKS, Material.QUARTZ_SLAB, 2);
      queueRecipe("quartz_bricks_to_stairs", Material.QUARTZ_BRICKS, Material.QUARTZ_STAIRS, 1);

      queueRecipe("packed_mud_to_bricks", Material.PACKED_MUD, Material.MUD_BRICKS, 1);
      queueRecipe("packed_mud_to_brick_slab", Material.PACKED_MUD, Material.MUD_BRICK_SLAB, 2);
      queueRecipe("packed_mud_to_brick_stairs", Material.PACKED_MUD, Material.MUD_BRICK_STAIRS, 1);
      queueRecipe("packed_mud_to_brick_wall", Material.PACKED_MUD, Material.MUD_BRICK_WALL, 1);

      queueRecipe("basalt_to_polished", Material.BASALT, Material.POLISHED_BASALT, 1);
      queueRecipe("basalt_to_smooth", Material.BASALT, Material.SMOOTH_BASALT, 1);
      queueRecipe("polished_basalt_to_smooth", Material.POLISHED_BASALT, Material.SMOOTH_BASALT, 1);

      queueRecipe("polished_blackstone_to_button", Material.POLISHED_BLACKSTONE,
         Material.POLISHED_BLACKSTONE_BUTTON, 1);
      queueRecipe("polished_blackstone_to_plate", Material.POLISHED_BLACKSTONE,
         Material.POLISHED_BLACKSTONE_PRESSURE_PLATE, 1);

      queueRecipe("gilded_to_blackstone", Material.GILDED_BLACKSTONE, Material.BLACKSTONE, 1);
      queueRecipe("gilded_to_blackstone_slab", Material.GILDED_BLACKSTONE, Material.BLACKSTONE_SLAB, 2);
      queueRecipe("gilded_to_blackstone_stairs", Material.GILDED_BLACKSTONE, Material.BLACKSTONE_STAIRS, 1);
      queueRecipe("gilded_to_blackstone_wall", Material.GILDED_BLACKSTONE, Material.BLACKSTONE_WALL, 1);

      queueRecipe("dripstone_to_pointed", Material.DRIPSTONE_BLOCK, Material.POINTED_DRIPSTONE, 4);

      queueRecipe("cobblestone_to_stone_bricks", Material.COBBLESTONE, Material.STONE_BRICKS, 1);
      queueRecipe("cobblestone_to_stone_brick_slab", Material.COBBLESTONE, Material.STONE_BRICK_SLAB, 2);
      queueRecipe("cobblestone_to_stone_brick_stairs", Material.COBBLESTONE, Material.STONE_BRICK_STAIRS, 1);
      queueRecipe("cobblestone_to_stone_brick_wall", Material.COBBLESTONE, Material.STONE_BRICK_WALL, 1);
      queueRecipe("cobblestone_to_chiseled_stone_bricks", Material.COBBLESTONE, Material.CHISELED_STONE_BRICKS, 1);
   }
}

