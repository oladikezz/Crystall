package net.schalker.SMPS.modules.keepinventory;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.DoAPI.core.module.BaseModule;
import net.schalker.DoAPI.core.module.ModuleInfo;
import net.schalker.SMPS.modules.keepinventory.commands.KeepInventoryCommand;
import net.schalker.SMPS.modules.keepinventory.listeners.KeepInventoryListener;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class KeepInventoryModule extends BaseModule {
   private FileConfiguration config;
   private FileConfiguration messages;
   private KeepInventoryListener listener;
   private final Set<UUID> enabledPlayers = ConcurrentHashMap.newKeySet();
   private final Set<String> registeredCommandNames = new HashSet<>();

   public KeepInventoryModule(DoAPI plugin) {
      super(plugin, new ModuleInfo("SM_KeepInventory", "1.0.0", "MeXaNoBoP", "Переключаемое сохранение инвентаря при смерти"));
   }

   @Override
   public void onEnable() {
      super.onEnable();
      loadConfigs();

      this.listener = new KeepInventoryListener(this.plugin, this);
      this.plugin.getListenerManager().registerListener(this.listener);

      if (this.isCommandEnabled("keepinv")) {
         registerCommandSafely(new KeepInventoryCommand(this.plugin, this));
      }

      this.plugin.getDebugSystem().log("KeepInventory", "Модуль KeepInventory включен");
   }

   @Override
   public void onDisable() {
      super.onDisable();
      if (this.listener != null) {
         this.plugin.getListenerManager().unregisterListener(this.listener);
      }
      this.enabledPlayers.clear();
      this.registeredCommandNames.clear();
      this.plugin.getDebugSystem().log("KeepInventory", "Модуль KeepInventory выключен");
   }

   @Override
   public void reload() {
      super.reload();
      loadConfigs();
   }

   private void loadConfigs() {
      this.config = this.plugin.getModuleManager().loadModuleConfig("SM_KeepInventory");
      this.messages = this.plugin.getModuleManager().loadModuleConfig("SM_KeepInventory", "messages.yml");
      if (this.config == null) {
         this.config = new YamlConfiguration();
      }
      if (this.messages == null) {
         this.messages = new YamlConfiguration();
      }
   }

   public boolean isCommandEnabled(String commandKey) {
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

   public boolean toggleKeepInventory(UUID playerId) {
      if (playerId == null) {
         return false;
      }
      if (this.enabledPlayers.contains(playerId)) {
         this.enabledPlayers.remove(playerId);
         return false;
      }
      this.enabledPlayers.add(playerId);
      return true;
   }

   public boolean isKeepInventoryEnabled(UUID playerId) {
      return playerId != null && this.enabledPlayers.contains(playerId);
   }

   private void registerCommandSafely(ModuleCommand command) {
      boolean hackEnabled = false;
      try {
         hackEnabled = this.plugin.getPluginReloader().setLifecycleContext();
         this.plugin.getCommandManager().registerModuleCommand(command);
         this.registeredCommandNames.add(command.getName().toLowerCase());
         if (command.getAliases() != null) {
            for (String alias : command.getAliases()) {
               if (alias != null) {
                  this.registeredCommandNames.add(alias.toLowerCase());
               }
            }
         }
      } catch (Exception e) {
         this.plugin.getDebugSystem().logError("KeepInventory command registration failed", e);
      } finally {
         if (hackEnabled) {
            this.plugin.getPluginReloader().clearLifecycleContext();
         }
      }
   }
}
