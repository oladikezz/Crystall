package net.schalker.SMPS.modules.lightcraft;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.inventory.meta.BlockDataMeta;
import org.bukkit.inventory.meta.ItemMeta;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.module.BaseModule;
import net.schalker.DoAPI.core.module.ModuleInfo;
import net.schalker.SMPS.modules.lightcraft.commands.LightCommand;
import net.schalker.SMPS.modules.lightcraft.listeners.LightBlockListener;

public class LightCraftModule extends BaseModule {
   private static final String MODULE_FOLDER_NAME = "SM_Lightcraft";
   private static LightCraftModule instance;
   private final List<NamespacedKey> registeredRecipes = new ArrayList<>();
   private FileConfiguration config;
   private FileConfiguration messages;

   public LightCraftModule(DoAPI plugin) {
      super(plugin, new ModuleInfo("SM_Lightcraft", "2.1.0", "MeXaNoBoP", "Крафт и управление световыми блоками через камнерез"));
      instance = this;
   }

   public static LightCraftModule getInstance() {
      return instance;
   }

   public void onEnable() {
      super.onEnable();
      this.config = this.plugin.getModuleManager().loadModuleConfig(MODULE_FOLDER_NAME);
      this.messages = this.plugin.getModuleManager().loadModuleConfig(MODULE_FOLDER_NAME, "messages.yml");
      if (this.config == null) {
         this.config = new YamlConfiguration();
      }
      if (this.messages == null) {
         this.messages = new YamlConfiguration();
      }

      boolean registerCrafting = this.config.getBoolean("recipe.crafting-table", true);
      boolean registerStonecutter = this.config.getBoolean("recipe.stonecutter", true);

      this.plugin.getSchedulerManager().runGlobalTask("lightcraft-recipes", () -> {
         if (registerCrafting) {
            this.registerLightBlockRecipe();
         }
         if (registerStonecutter) {
            this.registerStonecutterRecipes();
         }
         this.plugin.getLogger().info("SM_Lightcraft загружен с " + this.registeredRecipes.size() + " рецептами");
      });

      try {
         this.plugin.getCommandManager().registerModuleCommand(new LightCommand(this.plugin));
      } catch (Exception e) {
         this.plugin.getDebugSystem().log("SM_Lightcraft", "Команда /light будет доступна после перезагрузки сервера (hot-load ограничение Paper)");
      }
      this.plugin.getListenerManager().registerListener(new LightBlockListener(this.plugin));
      this.plugin.getDebugSystem().log("SM_Lightcraft", "Модуль включён (v2.1.0 - BlockData)");
   }

   public void onDisable() {
      super.onDisable();
      List<NamespacedKey> toRemove = new ArrayList<>(this.registeredRecipes);
      this.registeredRecipes.clear();
      this.plugin.getSchedulerManager().runGlobalTask("lightcraft-recipes-remove", () -> {
         for (NamespacedKey key : toRemove) {
            Bukkit.removeRecipe(key);
         }
      });
      instance = null;
      this.plugin.getDebugSystem().log("SM_Lightcraft", "Модуль выключен");
   }

   public void reload() {
      super.reload();
      this.config = this.plugin.getModuleManager().loadModuleConfig(MODULE_FOLDER_NAME);
      this.messages = this.plugin.getModuleManager().loadModuleConfig(MODULE_FOLDER_NAME, "messages.yml");
      if (this.config == null) {
         this.config = new YamlConfiguration();
      }
      if (this.messages == null) {
         this.messages = new YamlConfiguration();
      }
      this.plugin.getDebugSystem().log("SM_Lightcraft", "Модуль перезагружен");
   }

   public FileConfiguration getModuleConfig() {
      return this.config;
   }

   public FileConfiguration getMessages() {
      return this.messages;
   }

   public String getMessage(String key, String fallback) {
      String message = this.messages != null ? this.messages.getString(key) : null;
      return message != null ? message : fallback;
   }

   private void registerLightBlockRecipe() {
      int craftAmount = this.config.getInt("recipe.craft-amount", 8);
      ItemStack lightBlock = createLightBlock(15);
      lightBlock.setAmount(craftAmount);
      NamespacedKey key = new NamespacedKey(this.plugin, "light_block_craft");
      ShapedRecipe recipe = new ShapedRecipe(key, lightBlock);
      recipe.shape(new String[]{"BG ", "DI ", "   "});
      recipe.setIngredient('B', Material.GLASS_BOTTLE);
      recipe.setIngredient('G', Material.GLOW_BERRIES);
      recipe.setIngredient('D', Material.GLOWSTONE_DUST);
      recipe.setIngredient('I', Material.GLOW_INK_SAC);
      try {
         Bukkit.removeRecipe(key);
         Bukkit.addRecipe(recipe);
         this.registeredRecipes.add(key);
         this.plugin.getDebugSystem().log("SM_Lightcraft", "Базовый крафт светового блока зарегистрирован (кол-во: " + craftAmount + ")");
      } catch (Exception exception) {
         this.plugin.getDebugSystem().logError("Ошибка регистрации рецепта крафта светового блока", exception);
      }
   }

   private void registerStonecutterRecipes() {
      for (int targetLevel = 0; targetLevel <= 15; ++targetLevel) {
         ItemStack result = createLightBlock(targetLevel);
         NamespacedKey key = new NamespacedKey(this.plugin, "light_stonecutter_" + targetLevel);

         try {
            Bukkit.removeRecipe(key);
            StonecuttingRecipe stonecuttingRecipe = new StonecuttingRecipe(key, result, Material.LIGHT);
            Bukkit.addRecipe(stonecuttingRecipe);
            this.registeredRecipes.add(key);
            this.plugin.getDebugSystem().log("SM_Lightcraft", "Зарегистрирован рецепт камнереза для уровня " + targetLevel);
         } catch (Exception exception) {
            this.plugin.getDebugSystem().logError("Ошибка регистрации рецепта камнереза для уровня " + targetLevel, exception);
         }
      }

      this.plugin.getLogger().info("Зарегистрировано 16 рецептов камнереза для световых блоков (0-15)");
   }

   public ItemStack createLightBlock(int level) {
      if (level < 0) {
         level = 0;
      }

      if (level > 15) {
         level = 15;
      }

      String nameTemplate = getMessage("light-block.name", "&eСвет &7[Уровень: &6{level}&7]");
      List<String> loreTemplates = this.messages != null
         ? this.messages.getStringList("light-block.lore")
         : List.of();
      if (loreTemplates.isEmpty()) {
         loreTemplates = List.of(
            "&7Невидимый источник света",
            "&7Яркость: &6{level}&7/&615",
            "",
            "&7Поставьте блок, чтобы активировать",
            "&7Используйте камнерез для изменения яркости"
         );
      }

      ItemStack item = new ItemStack(Material.LIGHT);
      ItemMeta itemMeta = item.getItemMeta();
      if (itemMeta instanceof BlockDataMeta) {
         BlockDataMeta meta = (BlockDataMeta) itemMeta;
         String blockDataString = "minecraft:light[level=" + level + ",waterlogged=false]";
         BlockData blockData = Bukkit.createBlockData(blockDataString);
         meta.setBlockData(blockData);

         String name = nameTemplate.replace("{level}", String.valueOf(level));
         meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(name)
            .decoration(TextDecoration.ITALIC, false));

         List<Component> lore = new ArrayList<>();
         for (String line : loreTemplates) {
            String parsed = line.replace("{level}", String.valueOf(level));
            if (parsed.isEmpty()) {
               lore.add(Component.empty());
            } else {
               lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(parsed)
                  .decoration(TextDecoration.ITALIC, false));
            }
         }
         meta.lore(lore);
         item.setItemMeta(meta);
      }

      return item;
   }
}
