package net.schalker.SMPS.modules.debugstick;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.module.BaseModule;
import net.schalker.DoAPI.core.module.ModuleInfo;
import net.schalker.SMPS.modules.debugstick.listeners.DebugStickListener;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;

public class DebugStickModule extends BaseModule {
   private FileConfiguration config;
   private DebugStickListener listener;
   private NamespacedKey recipeKey;
   private final Set<Material> blacklist = new HashSet<>();
   private String blacklistMessage;
   private boolean protectSlabs = true;
   private boolean recipeRegistered = false;

   public DebugStickModule(DoAPI plugin) {
      super(plugin, loadModuleInfo());
   }

   private static ModuleInfo loadModuleInfo() {
      try (InputStream stream = DebugStickModule.class.getClassLoader().getResourceAsStream("module.yml")) {
         if (stream != null) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(
               new InputStreamReader(stream, StandardCharsets.UTF_8));
            return new ModuleInfo(
               yml.getString("name", "SM_DebugStick"),
               yml.getString("version", "1.0.0"),
               yml.getString("author", "Unknown"),
               yml.getString("description", "Крафт и ограничения Debug Stick")
            );
         }
      } catch (Exception ignored) {}
      return new ModuleInfo("SM_DebugStick", "1.0.0", "Unknown", "Крафт и ограничения Debug Stick");
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.loadConfigs();

      this.recipeKey = new NamespacedKey(this.plugin, "debug_stick");

      if (this.config.getBoolean("craft.enabled", true)) {
         // Defer to next tick — Bukkit.addRecipe() triggers recipe reload
         // which causes ConcurrentModificationException on Folia if called
         // from an entity task during module enable.
         this.plugin.getSchedulerManager().runTaskLater("debugstick-recipe-register", this::registerRecipe, 1L);
      }

      this.listener = new DebugStickListener(this);
      this.plugin.getListenerManager().registerListener(this.listener);

      this.plugin.getDebugSystem().log("DebugStick", "Модуль DebugStick включен");
   }

   @Override
   public void onDisable() {
      super.onDisable();
      if (this.listener != null) {
         this.plugin.getListenerManager().unregisterListener(this.listener);
      }
      // Do NOT call Bukkit.removeRecipe() — on Folia/Canvas it can corrupt the recipe registry.
      // The recipe will simply be ignored after the module is unloaded.
      this.plugin.getDebugSystem().log("DebugStick", "Модуль DebugStick выключен");
   }

   @Override
   public void reload() {
      super.reload();
      this.loadConfigs();
      // Recipe persists across reloads — no need to re-register or remove
   }

   private void loadConfigs() {
      this.config = this.plugin.getModuleManager().loadModuleConfig("SM_DebugStick");
      if (this.config == null) {
         this.config = new YamlConfiguration();
      }

      this.protectSlabs = this.config.getBoolean("protect-slabs", true);

      this.blacklist.clear();
      List<String> list = this.config.getStringList("blacklist");
      for (String name : list) {
         Material mat = Material.getMaterial(name.toUpperCase());
         if (mat != null) {
            this.blacklist.add(mat);
         }
      }

      this.blacklistMessage = this.plugin.applyColors(
         this.config.getString("blacklist-message", "&cЭтот блок нельзя изменить с помощью Debug Stick!"));
   }

   /**
    * Register the debug stick shaped recipe:
    * CCB
    * CAC
    * ACC
    * A = Breeze Rod, B = Nether Star, C = nothing
    * Result: Debug Stick with breeze_rod item model
    */
   private void registerRecipe() {
      if (this.recipeRegistered) {
         return;
      }
      // Check if recipe already exists (e.g. from a previous load)
      if (Bukkit.getRecipe(this.recipeKey) != null) {
         this.recipeRegistered = true;
         return;
      }
      ItemStack result = new ItemStack(Material.DEBUG_STICK, 1);
      result.editMeta(meta -> {
         meta.setItemModel(NamespacedKey.minecraft("breeze_rod"));
      });
      ShapedRecipe recipe = new ShapedRecipe(this.recipeKey, result);
      recipe.shape("  B", " A ", "A  ");
      recipe.setIngredient('A', Material.BREEZE_ROD);
      recipe.setIngredient('B', Material.NETHER_STAR);

      try {
         Bukkit.addRecipe(recipe);
         this.recipeRegistered = true;
      } catch (Exception e) {
         this.plugin.getDebugSystem().logError("Failed to register debug stick recipe", e);
      }
   }

   public boolean isBlacklisted(Material material) {
      return this.blacklist.contains(material);
   }

   public String getBlacklistMessage() {
      return this.blacklistMessage;
   }

   public boolean isProtectSlabs() {
      return this.protectSlabs;
   }

   public DoAPI getSmps() {
      return this.plugin;
   }
}
