package net.schalker.SMPS.modules.crowns;

import java.util.HashSet;
import java.util.Set;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.DoAPI.core.module.BaseModule;
import net.schalker.DoAPI.core.module.ModuleInfo;
import net.schalker.SMPS.modules.crowns.commands.CrownsCommand;
import net.schalker.SMPS.modules.crowns.listeners.CrownsListener;
import net.schalker.SMPS.modules.crowns.managers.CrownsManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class CrownsModule extends BaseModule {
   private FileConfiguration config;
   private FileConfiguration messages;
   private CrownsManager manager;
   private CrownsListener listener;
   private final Set<String> registeredCommandNames = new HashSet<>();

   public CrownsModule(DoAPI plugin) {
      super(plugin, new ModuleInfo("SM_Crowns", "1.0.0", "MeXaNoBoP", "Короны над именами игроков по группам LuckPerms"));
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.loadConfigs();

      this.manager = new CrownsManager(this.plugin, this);
      this.manager.loadRoles(this.config);

      this.listener = new CrownsListener(this.plugin, this, this.manager);
      this.plugin.getListenerManager().registerListener(this.listener);

      if (this.isCommandEnabled("crowns")) {
         this.registerCommandSafely(new CrownsCommand(this.plugin));
      }

      // Periodic task — checks for role changes (not position tracking)
      long interval = this.config.getLong("settings.update-interval", 200);
      this.plugin.getSchedulerManager().runAsyncTimer("crowns-update", () -> {
         for (Player player : Bukkit.getOnlinePlayers()) {
            this.plugin.getSchedulerManager().runEntityTask(player,
               "crowns-ref-" + player.getUniqueId().toString().substring(0, 8), () -> {
               if (player.isOnline()) {
                  this.manager.updatePlayer(player);
               }
            });
         }
      }, interval, interval);

      // Initialize for existing online players
      for (Player player : Bukkit.getOnlinePlayers()) {
         this.plugin.getSchedulerManager().runEntityTask(player,
            "crowns-init-" + player.getUniqueId().toString().substring(0, 8), () -> {
            if (player.isOnline()) {
               this.manager.handlePlayerJoin(player);
            }
         });
      }

      this.plugin.getDebugSystem().log("Crowns", "Модуль Crowns включён");
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.plugin.getSchedulerManager().cancelTask("crowns-update");
      if (this.listener != null) {
         this.plugin.getListenerManager().unregisterListener(this.listener);
      }
      if (this.manager != null) {
         this.manager.cleanup();
      }
      this.unregisterAllCommands();
      this.plugin.getDebugSystem().log("Crowns", "Модуль Crowns выключен");
   }

   @Override
   public void reload() {
      super.reload();
      this.loadConfigs();
      if (this.manager != null) {
         this.manager.loadRoles(this.config);
         this.manager.rebuildAll();
      }
   }

   private void loadConfigs() {
      this.config = this.plugin.getModuleManager().loadModuleConfig("SM_Crowns");
      this.messages = this.plugin.getModuleManager().loadModuleConfig("SM_Crowns", "messages.yml");
      if (this.config == null) this.config = new YamlConfiguration();
      if (this.messages == null) this.messages = new YamlConfiguration();
   }

   public String getMessage(String key) {
      String message = this.messages.getString(key, "&cMessage not found: " + key);
      return this.plugin.applyColors(message);
   }

   private boolean isCommandEnabled(String key) {
      return this.config.getBoolean("commands." + key + ".enabled", true);
   }

   public CrownsManager getManager() {
      return this.manager;
   }

   public FileConfiguration getModuleConfig() {
      return this.config;
   }

   private void registerCommandSafely(ModuleCommand command) {
      boolean hackEnabled = false;
      try {
         hackEnabled = this.plugin.getPluginReloader().setLifecycleContext();
         this.plugin.getCommandManager().registerModuleCommand(command);
         this.trackCommand(command.getName());
      } catch (Exception e) {
         this.plugin.getDebugSystem().logError("Failed to register command: " + command.getName(), e);
      } finally {
         if (hackEnabled) {
            this.plugin.getPluginReloader().clearLifecycleContext();
         }
      }
   }

   private void trackCommand(String name) {
      this.registeredCommandNames.add(name.toLowerCase());
   }

   private void unregisterAllCommands() {
      this.registeredCommandNames.clear();
   }
}
