package net.schalker.SMPS.modules.autoreplenish;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.module.BaseModule;
import net.schalker.DoAPI.core.module.ModuleInfo;
import net.schalker.SMPS.modules.autoreplenish.listeners.HarvestListener;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class AutoReplenishModule extends BaseModule {
   private FileConfiguration config;
   private FileConfiguration messages;
   private HarvestListener listener;

   public AutoReplenishModule(DoAPI plugin) {
      super(plugin, loadModuleInfo());
   }

   private static ModuleInfo loadModuleInfo() {
      try (InputStream stream = AutoReplenishModule.class.getClassLoader().getResourceAsStream("module.yml")) {
         if (stream != null) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(
               new InputStreamReader(stream, StandardCharsets.UTF_8));
            return new ModuleInfo(
               yml.getString("name", "SM_AutoReplenish"),
               yml.getString("version", "1.0.0"),
               yml.getString("author", "Unknown"),
               yml.getString("description", "Автосбор и пересадка урожая мотыгой")
            );
         }
      } catch (Exception ignored) {}
      return new ModuleInfo("SM_AutoReplenish", "1.0.0", "Unknown", "Автосбор и пересадка урожая мотыгой");
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.loadConfigs();

      this.listener = new HarvestListener(this.plugin, this);
      this.plugin.getListenerManager().registerListener(this.listener);

      this.plugin.getDebugSystem().log("AutoReplenish", "Модуль AutoReplenish включен");
   }

   @Override
   public void onDisable() {
      super.onDisable();
      if (this.listener != null) {
         this.plugin.getListenerManager().unregisterListener(this.listener);
      }
      this.plugin.getDebugSystem().log("AutoReplenish", "Модуль AutoReplenish выключен");
   }

   @Override
   public void reload() {
      super.reload();
      this.loadConfigs();
   }

   private void loadConfigs() {
      this.config = this.plugin.getModuleManager().loadModuleConfig("SM_AutoReplenish");
      this.messages = this.plugin.getModuleManager().loadModuleConfig("SM_AutoReplenish", "messages.yml");
      if (this.config == null) {
         this.config = new YamlConfiguration();
      }
      if (this.messages == null) {
         this.messages = new YamlConfiguration();
      }
   }

   public boolean isFeatureEnabled() {
      return this.config.getBoolean("enabled", true);
   }

   public int getDurabilityCost() {
      return Math.max(0, this.config.getInt("durability-cost", 3));
   }

   public String getMessage(String key, String fallback) {
      String message = this.messages != null ? this.messages.getString(key) : null;
      if (message == null) {
         message = fallback;
      }
      if (message == null || message.isEmpty()) {
         return "";
      }
      return this.plugin.applyColors(message);
   }
}
