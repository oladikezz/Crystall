package net.schalker.SMPS.modules.hat;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.DoAPI.core.module.BaseModule;
import net.schalker.DoAPI.core.module.ModuleInfo;
import net.schalker.SMPS.modules.hat.commands.HatCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class HatModule extends BaseModule {
   private FileConfiguration config;
   private FileConfiguration messages;
   private final Set<String> registeredCommandNames = new HashSet<>();

   public HatModule(DoAPI plugin) {
      super(plugin, loadModuleInfo());
   }

   private static ModuleInfo loadModuleInfo() {
      try (InputStream stream = HatModule.class.getClassLoader().getResourceAsStream("module.yml")) {
         if (stream != null) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(
               new InputStreamReader(stream, StandardCharsets.UTF_8));
            return new ModuleInfo(
               yml.getString("name", "SM_Hat"),
               yml.getString("version", "1.0.0"),
               yml.getString("author", "Unknown"),
               yml.getString("description", "Команда /hat")
            );
         }
      } catch (Exception ignored) {}
      return new ModuleInfo("SM_Hat", "1.0.0", "Unknown", "Команда /hat");
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.loadConfigs();

      if (this.isCommandEnabled("hat")) {
         this.registerCommandSafely(new HatCommand(this.plugin, this));
      }

      this.plugin.getDebugSystem().log("Hat", "Модуль Hat включен");
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.unregisterAllCommands();
      this.plugin.getDebugSystem().log("Hat", "Модуль Hat выключен");
   }

   @Override
   public void reload() {
      super.reload();
      this.loadConfigs();
   }

   private void loadConfigs() {
      this.config = this.plugin.getModuleManager().loadModuleConfig("SM_Hat");
      this.messages = this.plugin.getModuleManager().loadModuleConfig("SM_Hat", "messages.yml");
      if (this.config == null) {
         this.config = new YamlConfiguration();
      }
      if (this.messages == null) {
         this.messages = new YamlConfiguration();
      }
   }

   private boolean isCommandEnabled(String commandKey) {
      return this.config.getBoolean("commands." + commandKey + ".enabled", true);
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

   private void registerCommandSafely(ModuleCommand command) {
      boolean hackEnabled = false;
      try {
         hackEnabled = this.plugin.getPluginReloader().setLifecycleContext();
         this.plugin.getCommandManager().registerModuleCommand(command);
         this.registeredCommandNames.add(command.getName().toLowerCase());
      } catch (Exception e) {
         this.plugin.getDebugSystem().logError("Hat command registration failed", e);
      } finally {
         if (hackEnabled) {
            this.plugin.getPluginReloader().clearLifecycleContext();
         }
      }
   }

   private void unregisterAllCommands() {
      this.registeredCommandNames.clear();
   }
}

