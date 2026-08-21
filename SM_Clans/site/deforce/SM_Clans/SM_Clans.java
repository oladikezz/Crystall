package site.deforce.SM_Clans;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.api.IModule;
import net.schalker.DoAPI.core.command.CommandManager;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.DoAPI.core.debug.DebugSystem;
import net.schalker.DoAPI.core.module.ModuleInfo;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import site.deforce.SM_Clans.commands.ClanCommand;
import site.deforce.SM_Clans.gui.ClanDialogManager;
import site.deforce.SM_Clans.gui.ClanMenuManager;
import site.deforce.SM_Clans.listeners.ClanChatListener;
import site.deforce.SM_Clans.listeners.ClanCreationListener;
import site.deforce.SM_Clans.listeners.ClanGUIListener;
import site.deforce.SM_Clans.listeners.ClanUpkeepListener;
import site.deforce.SM_Clans.listeners.FriendlyFireListener;
import site.deforce.SM_Clans.logging.ClanAuditLogger;
import site.deforce.SM_Clans.logging.ClanLogDatabase;
import site.deforce.SM_Clans.managers.ClanAdminManager;
import site.deforce.SM_Clans.managers.ClanEconomyManager;
import site.deforce.SM_Clans.managers.ClanInfoManager;
import site.deforce.SM_Clans.managers.ClanInviteManager;
import site.deforce.SM_Clans.managers.ClanManager;
import site.deforce.SM_Clans.managers.ClanRentManager;
import site.deforce.SM_Clans.managers.ClanSettingsManager;
import site.deforce.SM_Clans.managers.ClanTaxManager;
import site.deforce.SM_Clans.managers.DatabaseManager;
import site.deforce.SM_Clans.managers.RoleManager;
import site.deforce.SM_Clans.models.Clan;
import site.deforce.SM_Clans.models.PendingPurchase;

public class SM_Clans implements IModule {
   private static SM_Clans instance;
   private final DoAPI plugin;
   private final ModuleInfo info;
   private boolean enabled = false;
   private FileConfiguration config;
   private FileConfiguration messages;
   private FileConfiguration gui;
   private DatabaseManager databaseManager;
   private ClanManager clanManager;
   private RoleManager roleManager;
   private ClanInviteManager clanInviteManager;
   private ClanInfoManager clanInfoManager;
   private ClanSettingsManager clanSettingsManager;
   private ClanAdminManager clanAdminManager;
   private ClanEconomyManager clanEconomyManager;
   private ClanTaxManager clanTaxManager;
   private ClanRentManager clanRentManager;
   private ClanLogDatabase logDatabase;
   private ClanAuditLogger auditLogger;
   private ClanMenuManager menuManager;
   private ClanDialogManager dialogManager;
   private ClanCreationListener creationListener;
   private ClanGUIListener guiListener;
   private ClanChatListener chatListener;
   private FriendlyFireListener friendlyFireListener;
   private ClanUpkeepListener upkeepListener;
   private ModuleCommand clanCommand;
   private final Map<UUID, PendingConfirm> pendingConfirms = new ConcurrentHashMap();
   private final Map<UUID, PendingPurchase> pendingPurchases = new ConcurrentHashMap();
   private final Set<UUID> chatSpies = ConcurrentHashMap.newKeySet();

   public SM_Clans(DoAPI plugin) {
      super();
      this.plugin = plugin;
      this.info = new ModuleInfo("SM_Clans", "3.3.0", "deforce_", "Clan module for SMPS, adapted by deforce_");
      instance = this;
   }

   public void onEnable() {
      this.enabled = true;
      this.plugin.getDebugSystem().log("SM_Clans", "Starting module enable...");
      DebugSystem var10000 = this.plugin.getDebugSystem();
      String var10002 = this.info.getName();
      var10000.log("SM_Clans", "Module name: " + var10002);
      this.config = this.loadModuleConfig(this.info.getName());
      this.messages = this.loadModuleConfig(this.info.getName(), "messages.yml");
      this.gui = this.loadModuleConfig(this.info.getName(), "gui.yml");
      var10000 = this.plugin.getDebugSystem();
      boolean var8 = this.config != null;
      var10000.log("SM_Clans", "Config loaded: " + var8);
      var10000 = this.plugin.getDebugSystem();
      var8 = this.messages != null;
      var10000.log("SM_Clans", "Messages loaded: " + var8);
      this.plugin.getDebugSystem().log("SM_Clans", "GUI loaded: " + (this.gui != null));
      this.plugin.getDebugSystem().log("Clans", "Loading clans module...");

      try {
         this.databaseManager = new DatabaseManager(this.plugin);
         this.databaseManager.initialize();
         this.plugin.getDebugSystem().log("Clans", "Database initialized");
      } catch (Exception exception) {
         this.plugin.getDebugSystem().logError("Failed to initialize database", exception);
         this.plugin.getLogger().severe("Failed to initialize clans database! Module disabled.");
         this.enabled = false;
         return;
      }

      this.clanManager = new ClanManager(this.plugin, this, this.databaseManager);
      this.roleManager = new RoleManager(this.clanManager);
      this.clanInviteManager = new ClanInviteManager(this.plugin, this, this.clanManager, this.roleManager);
      this.clanInfoManager = new ClanInfoManager(this.plugin, this, this.clanManager);
      this.clanSettingsManager = new ClanSettingsManager(this, this.clanManager, this.roleManager);
      this.clanAdminManager = new ClanAdminManager(this, this.clanManager);
      this.clanEconomyManager = new ClanEconomyManager(this, this.clanManager);
      this.clanTaxManager = new ClanTaxManager(this, this.databaseManager);
      this.clanRentManager = new ClanRentManager(this, this.clanManager, this.databaseManager, this.clanEconomyManager);
      this.logDatabase = new ClanLogDatabase(this);
      this.auditLogger = new ClanAuditLogger(this, this.logDatabase);
      this.menuManager = new ClanMenuManager(this, this.clanManager, this.roleManager);
      this.dialogManager = new ClanDialogManager(this);
      this.logDatabase.initialize();
      this.clanManager.loadClans();
      this.clanTaxManager.load();
      this.clanRentManager.start();
      this.auditLogger.start();
      this.creationListener = new ClanCreationListener(this.plugin, this.clanManager);
      this.creationListener.setModule(this);
      this.guiListener = new ClanGUIListener(this.plugin, this);
      this.chatListener = new ClanChatListener(this.plugin, this);
      this.friendlyFireListener = new FriendlyFireListener(this.plugin, this);
      this.upkeepListener = new ClanUpkeepListener(this.plugin, this);
      this.plugin.getListenerManager().registerListener(this.creationListener);
      this.plugin.getListenerManager().registerListener(this.guiListener);
      this.plugin.getListenerManager().registerListener(this.chatListener);
      this.plugin.getListenerManager().registerListener(this.friendlyFireListener);
      this.plugin.getListenerManager().registerListener(this.upkeepListener);
      this.cleanupSmpsCommandCache("clan");
      this.clanCommand = new ClanCommand(this.plugin, this);

      try {
         this.plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, (event) -> {
            this.cleanupSmpsCommandCache("clan");
            this.plugin.getCommandManager().registerModuleCommand(this.clanCommand);
         });
      } catch (IllegalStateException var4) {
         this.plugin.getLogger().warning("Lifecycle registration not available, using Bukkit CommandMap fallback");
         this.registerBukkitFallbackCommand();
      }

      this.registerSpyCommand();
      if (this.isPlaceholderApiAvailable()) {
         try {
            this.registerPlaceholders();
            this.plugin.getDebugSystem().log("Clans", "PlaceholderAPI integration enabled");
         } catch (NoClassDefFoundError var2) {
            this.plugin.getDebugSystem().log("Clans", "PlaceholderAPI classes not found, integration disabled");
         } catch (Exception exception) {
            this.plugin.getDebugSystem().logError("Failed to register PlaceholderAPI", exception);
         }
      }

      this.plugin.getLogger().info("Clans module enabled!");
   }

   public void onDisable() {
      this.enabled = false;
      if (this.clanTaxManager != null) {
         try {
            this.clanTaxManager.flush();
         } catch (Exception e) {
            this.plugin.getLogger().warning("Failed to flush tax pool on disable: " + e.getMessage());
         }
      }

      if (this.clanRentManager != null) {
         try {
            this.clanRentManager.stop();
         } catch (Exception e) {
            this.plugin.getLogger().warning("Failed to stop rent task on disable: " + e.getMessage());
         }
      }

      if (this.auditLogger != null) {
         try {
            this.auditLogger.shutdown();
         } catch (Exception e) {
            this.plugin.getLogger().warning("Failed to stop audit logger on disable: " + e.getMessage());
         }
      }

      if (this.logDatabase != null) {
         try {
            this.logDatabase.shutdown();
         } catch (Exception e) {
            this.plugin.getLogger().warning("Failed to close log database on disable: " + e.getMessage());
         }
      }

      try {
         this.closeOpenMenus();
      } catch (Exception e) {
         this.plugin.getLogger().warning("Failed to close menus on disable: " + e.getMessage());
      }

      if (this.creationListener != null) {
         this.plugin.getListenerManager().unregisterListener(this.creationListener);
         HandlerList.unregisterAll(this.creationListener);
         this.creationListener = null;
      }

      if (this.guiListener != null) {
         this.plugin.getListenerManager().unregisterListener(this.guiListener);
         HandlerList.unregisterAll(this.guiListener);
         this.guiListener = null;
      }

      if (this.chatListener != null) {
         this.plugin.getListenerManager().unregisterListener(this.chatListener);
         HandlerList.unregisterAll(this.chatListener);
         this.chatListener = null;
      }

      if (this.friendlyFireListener != null) {
         this.plugin.getListenerManager().unregisterListener(this.friendlyFireListener);
         HandlerList.unregisterAll(this.friendlyFireListener);
         this.friendlyFireListener = null;
      }

      if (this.upkeepListener != null) {
         this.plugin.getListenerManager().unregisterListener(this.upkeepListener);
         HandlerList.unregisterAll(this.upkeepListener);
         this.upkeepListener = null;
      }

      if (this.clanCommand != null) {
         try {
            CommandManager cm = this.plugin.getCommandManager();
            boolean unregistered = false;

            try {
               Method unregisterByName = cm.getClass().getMethod("unregisterModuleCommand", String.class);
               unregisterByName.invoke(cm, "clan");
               unregistered = true;
               this.plugin.getLogger().info("Clan command unregistered via DoAPI (by name)");
            } catch (NoSuchMethodException var15) {
            }

            if (!unregistered) {
               try {
                  Method unregisterByInstance = cm.getClass().getMethod("unregisterModuleCommand", ModuleCommand.class);
                  unregisterByInstance.invoke(cm, this.clanCommand);
                  unregistered = true;
                  this.plugin.getLogger().info("Clan command unregistered via DoAPI (by instance)");
               } catch (NoSuchMethodException var14) {
               }
            }

            this.cleanupSmpsCommandCache("clan");
            if (!unregistered) {
               this.plugin.getLogger().warning("Could not unregister command from DoAPI - command may not re-register on reload");
            }
         } catch (Exception e) {
            this.plugin.getLogger().warning("Failed to unregister command via SMPS: " + e.getMessage());
         }
      }

      try {
         Object server = this.plugin.getServer();
         Method getCommandMap = server.getClass().getMethod("getCommandMap");
         Object commandMap = getCommandMap.invoke(server);
         Field knownCommandsField = null;
         Class<?> mapClass = commandMap.getClass();

         while(mapClass != null && knownCommandsField == null) {
            try {
               knownCommandsField = mapClass.getDeclaredField("knownCommands");
            } catch (NoSuchFieldException var13) {
               mapClass = mapClass.getSuperclass();
            }
         }

         if (knownCommandsField != null) {
            knownCommandsField.setAccessible(true);
            Map<String, Object> knownCommands = (Map)knownCommandsField.get(commandMap);
            if (knownCommands != null) {
               String[] toRemove = new String[]{"clan", "clans", "c", "clanspy", "guild", "guilds", "smps:clan", "smps:clans", "smps:c", "smps:clanspy", "smps:guild", "smps:guilds", "sm_clans:clan", "sm_clans:clans", "sm_clans:c", "sm_clans:clanspy", "sm_clans:guild", "sm_clans:guilds"};

               for(String key : toRemove) {
                  if (knownCommands.containsKey(key)) {
                     knownCommands.remove(key);
                     this.plugin.getLogger().info("Force removed command from CommandMap: " + key);
                  }
               }

               if (this.clanCommand != null) {
                  knownCommands.values().removeIf((cmd) -> cmd == this.clanCommand);
                  knownCommands.values().removeIf((cmd) -> cmd instanceof Command && (((Command)cmd).getName().equalsIgnoreCase("clan") || ((Command)cmd).getAliases().contains("clans") || ((Command)cmd).getAliases().contains("guild") || ((Command)cmd).getAliases().contains("guilds") || ((Command)cmd).getName().equalsIgnoreCase("clanspy")));
               }
            }
         }

         try {
            Method syncCommands = server.getClass().getMethod("syncCommands");
            syncCommands.invoke(server);
         } catch (Exception var12) {
         }
      } catch (Throwable e) {
         this.plugin.getLogger().warning("Failed to clean Bukkit CommandMap: " + e.getMessage());
      }

      this.clanCommand = null;

      try {
         for(Player p : this.plugin.getServer().getOnlinePlayers()) {
            p.updateCommands();
         }
      } catch (Exception var22) {
      }

      this.databaseManager = null;
      this.clanManager = null;
      this.roleManager = null;
      this.clanInviteManager = null;
      this.clanInfoManager = null;
      this.clanSettingsManager = null;
      this.clanEconomyManager = null;
      this.clanTaxManager = null;
      this.clanRentManager = null;
      this.menuManager = null;
      this.plugin.getLogger().info("Clans module disabled!");
      instance = null;
   }

   public void reload() {
      this.config = this.loadModuleConfig(this.info.getName());
      this.messages = this.loadModuleConfig(this.info.getName(), "messages.yml");
      this.gui = this.loadModuleConfig(this.info.getName(), "gui.yml");
      if (this.menuManager != null) {
         this.menuManager.clearSkinCache();
      }

      this.plugin.getLogger().info("Clans module reloaded!");
   }

   public ModuleInfo getModuleInfo() {
      return this.info;
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public static SM_Clans getInstance() {
      return instance;
   }

   public FileConfiguration getConfig() {
      return this.config;
   }

   public FileConfiguration getMessages() {
      return this.messages;
   }

   public FileConfiguration getGui() {
      return this.gui;
   }

   public DoAPI getPlugin() {
      return this.plugin;
   }

   public void requestDisbandConfirm(UUID playerId) {
      this.pendingConfirms.put(playerId, new PendingConfirm(SM_Clans.ConfirmType.DISBAND, (String)null));
   }

   public void requestPromoteConfirm(UUID playerId, String targetName) {
      this.pendingConfirms.put(playerId, new PendingConfirm(SM_Clans.ConfirmType.PROMOTE, targetName));
   }

   public boolean handleConfirmChat(Player player, String message) {
      PendingConfirm confirm = (PendingConfirm)this.pendingConfirms.remove(player.getUniqueId());
      if (confirm == null) {
         return false;
      } else if (!"confirm".equalsIgnoreCase(message)) {
         player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&cAction cancelled."));
         return true;
      } else {
         this.plugin.getSchedulerManager().runEntityTask(player, "clan-confirm-action", () -> {
            if (this.clanInviteManager != null && this.clanSettingsManager != null) {
               switch (confirm.type.ordinal()) {
                  case 0 -> this.clanInviteManager.disbandClan(player);
                  case 1 -> this.clanSettingsManager.transferOwnership(player, confirm.getTargetName());
               }

            }
         });
         return true;
      }
   }

   public void clearConfirm(UUID playerId) {
      this.pendingConfirms.remove(playerId);
   }

   public void requestPurchase(Player player, PendingPurchase purchase) {
      if (purchase != null) {
         if (this.clanEconomyManager != null && this.clanEconomyManager.isEnabled() && purchase.getCost() > 0L) {
            Clan clan = this.clanManager != null ? this.clanManager.getPlayerClan(player.getUniqueId()) : null;
            if (clan == null) {
               this.executePurchase(player, purchase);
            } else if (!this.clanEconomyManager.canAfford(clan, purchase.getCost())) {
               if (this.menuManager != null) {
                  this.menuManager.playErrorSound(player);
               }

               this.clanEconomyManager.notifyNotEnoughTreasury(player, clan, purchase.getCost());
            } else {
               this.pendingPurchases.put(player.getUniqueId(), purchase);
               if (this.menuManager != null) {
                  this.menuManager.openConfirmPurchase(player, purchase);
               }

            }
         } else {
            this.executePurchase(player, purchase);
         }
      }
   }

   public PendingPurchase peekPendingPurchase(UUID playerId) {
      return (PendingPurchase)this.pendingPurchases.get(playerId);
   }

   public void confirmPurchase(Player player) {
      PendingPurchase purchase = (PendingPurchase)this.pendingPurchases.remove(player.getUniqueId());
      if (purchase != null) {
         this.executePurchase(player, purchase);
      }

   }

   public void cancelPurchase(UUID playerId) {
      this.pendingPurchases.remove(playerId);
   }

   private void executePurchase(Player player, PendingPurchase purchase) {
      if (this.clanSettingsManager != null && this.clanEconomyManager != null) {
         switch (purchase.getType()) {
            case BUY_SLOTS -> this.clanEconomyManager.buySlots(player);
            case CHANGE_NAME -> this.clanSettingsManager.changeClanName(player, purchase.getParam());
            case CHANGE_TAG -> this.clanSettingsManager.changeClanTag(player, purchase.getParam());
            case CHANGE_DESCRIPTION -> this.clanSettingsManager.changeClanDescription(player, purchase.getParam());
            case CHANGE_BANNER_COLOR -> this.clanSettingsManager.changeBannerColor(player, purchase.getParam());
            case SET_FLAG -> this.clanSettingsManager.setClanFlag(player);
         }

      }
   }

   private void registerPlaceholders() throws Exception {
      Class<?> expansionClass = Class.forName("site.deforce.SM_Clans.ClanPlaceholders");
      Object expansion = expansionClass.getConstructor(SM_Clans.class).newInstance(this);
      expansionClass.getMethod("register").invoke(expansion);
   }

   private boolean isPlaceholderApiAvailable() {
      Plugin placeholderAPI = this.plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI");
      if (placeholderAPI != null && placeholderAPI.isEnabled()) {
         try {
            Class.forName("me.clip.placeholderapi.expansion.PlaceholderExpansion", false, this.plugin.getClass().getClassLoader());
            return true;
         } catch (NoClassDefFoundError | ClassNotFoundException var3) {
            this.plugin.getDebugSystem().log("Clans", "PlaceholderAPI not in classpath, integration disabled");
            return false;
         }
      } else {
         return false;
      }
   }

   public DatabaseManager getDatabaseManager() {
      return this.databaseManager;
   }

   public ClanManager getClanManager() {
      return this.clanManager;
   }

   public RoleManager getRoleManager() {
      return this.roleManager;
   }

   public ClanInviteManager getClanInviteManager() {
      return this.clanInviteManager;
   }

   public ClanInfoManager getClanInfoManager() {
      return this.clanInfoManager;
   }

   public ClanSettingsManager getClanSettingsManager() {
      return this.clanSettingsManager;
   }

   public ClanAdminManager getClanAdminManager() {
      return this.clanAdminManager;
   }

   public ClanEconomyManager getClanEconomyManager() {
      return this.clanEconomyManager;
   }

   public ClanTaxManager getTaxManager() {
      return this.clanTaxManager;
   }

   public ClanRentManager getClanRentManager() {
      return this.clanRentManager;
   }

   public ClanAuditLogger getAuditLogger() {
      return this.auditLogger;
   }

   public ClanLogDatabase getLogDatabase() {
      return this.logDatabase;
   }

   public ClanMenuManager getMenuManager() {
      return this.menuManager;
   }

   public ClanDialogManager getDialogManager() {
      return this.dialogManager;
   }

   public ClanCreationListener getCreationListener() {
      return this.creationListener;
   }

   private void closeOpenMenus() {
      for(Player player : this.plugin.getServer().getOnlinePlayers()) {
         String title = PlainTextComponentSerializer.plainText().serialize(player.getOpenInventory().title());
         if (this.guiListener != null && this.guiListener.isClanGUI(title)) {
            player.closeInventory();
            player.sendMessage(Component.text("§c[Clans] Menu closed due to reload."));
         }
      }

   }

   private FileConfiguration loadModuleConfig(String moduleName) {
      return this.loadModuleConfig(moduleName, "config.yml");
   }

   private FileConfiguration loadModuleConfig(String moduleName, String fileName) {
      FileConfiguration config = null;

      try {
         Object moduleManager = this.plugin.getModuleManager();
         Method loadMethod = moduleManager.getClass().getMethod("loadModuleConfig", String.class, String.class);
         config = (FileConfiguration)loadMethod.invoke(moduleManager, moduleName, fileName);
         if (config != null) {
            this.plugin.getDebugSystem().log("SM_Clans", "Loaded config " + fileName + " via SMPS");
            return config;
         }
      } catch (Exception var7) {
         this.plugin.getDebugSystem().log("SM_Clans", "DoAPI config loading failed for " + fileName + ", trying fallback");
      }

      try {
         config = this.loadConfigFromResources(moduleName, fileName);
         if (config != null) {
            this.plugin.getDebugSystem().log("SM_Clans", "Loaded config " + fileName + " via fallback");
         } else {
            this.plugin.getDebugSystem().log("SM_Clans", "Config " + fileName + " returned null from fallback");
         }
      } catch (Exception exception) {
         this.plugin.getDebugSystem().logError("Failed to load module config: " + fileName, exception);
      }

      return config;
   }

   private FileConfiguration loadConfigFromResources(String moduleName, String fileName) {
      try {
         File dataFolder = new File(this.plugin.getDataFolder(), "modules/" + moduleName);
         if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            this.plugin.getDebugSystem().log("SM_Clans", "Could not create module data folder: " + dataFolder.getPath());
         }

         File configFile = new File(dataFolder, fileName);
         if (!configFile.exists()) {
            InputStream resourceStream = this.getClass().getClassLoader().getResourceAsStream(fileName);

            Object var6;
            label66: {
               try {
                  if (resourceStream == null) {
                     this.plugin.getDebugSystem().log("SM_Clans", "Resource " + fileName + " not found in JAR");
                     var6 = null;
                     break label66;
                  }

                  Files.copy(resourceStream, configFile.toPath(), new CopyOption[0]);
                  this.plugin.getDebugSystem().log("SM_Clans", "Extracted default " + fileName + " to " + configFile.getPath());
               } catch (Throwable var9) {
                  if (resourceStream != null) {
                     try {
                        resourceStream.close();
                     } catch (Throwable var8) {
                        var9.addSuppressed(var8);
                     }
                  }

                  throw var9;
               }

               if (resourceStream != null) {
                  resourceStream.close();
               }

               return YamlConfiguration.loadConfiguration(configFile);
            }

            if (resourceStream != null) {
               resourceStream.close();
            }

            return (FileConfiguration)var6;
         } else {
            return YamlConfiguration.loadConfiguration(configFile);
         }
      } catch (Exception exception) {
         this.plugin.getDebugSystem().logError("Failed to load config from resources: " + fileName, exception);
         return null;
      }
   }

   private void cleanupSmpsCommandCache(String commandName) {
      try {
         CommandManager cm = this.plugin.getCommandManager();
         boolean removed = false;

         for(Field field : cm.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            Object value = field.get(cm);
            if (value instanceof Set<?> set) {
               if (set.contains(commandName)) {
                  set.remove(commandName);
                  removed = true;
                  this.plugin.getLogger().info("Removed '" + commandName + "' from DoAPI set field: " + field.getName());
               }
            }

            if (value instanceof Map<?, ?> map) {
               if (map.containsKey(commandName)) {
                  map.remove(commandName);
                  removed = true;
                  this.plugin.getLogger().info("Removed '" + commandName + "' from DoAPI map field: " + field.getName());
               }
            }
         }

         if (!removed) {
            this.plugin.getLogger().warning("DoAPI command cache cleanup did not find '" + commandName + "' in any field");
         }
      } catch (Exception ex) {
         this.plugin.getLogger().warning("Failed to clean DoAPI command cache: " + ex.getMessage());
      }

   }

   private void registerBukkitFallbackCommand() {
      try {
         Object server = this.plugin.getServer();
         Method getCommandMap = server.getClass().getMethod("getCommandMap");
         Object commandMap = getCommandMap.invoke(server);
         Command fallback = new Command("clan") {
            public boolean execute(CommandSender sender, String label, String[] args) {
               ModuleCommand var5 = SM_Clans.this.clanCommand;
               if (var5 instanceof ClanCommand cc) {
                  cc.executeSender(sender, args);
                  return true;
               } else {
                  return false;
               }
            }

            public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
               ModuleCommand var5 = SM_Clans.this.clanCommand;
               if (var5 instanceof ClanCommand cc) {
                  return new ArrayList(cc.suggestSender(sender, args));
               } else {
                  return Collections.emptyList();
               }
            }
         };
         fallback.setAliases(Arrays.asList("clans", "c", "guild", "guilds"));
         fallback.setDescription("Clan command");
         fallback.setPermission("smclans.clan");
         Method register = commandMap.getClass().getMethod("register", String.class, Command.class);
         register.invoke(commandMap, "smps", fallback);
         this.plugin.getLogger().info("Registered /clan via Bukkit CommandMap fallback");
      } catch (Exception e) {
         this.plugin.getLogger().warning("Failed to register Bukkit fallback command: " + e.getMessage());
      }

   }

   private void registerSpyCommand() {
      try {
         Object server = this.plugin.getServer();
         Method getCommandMap = server.getClass().getMethod("getCommandMap");
         Object commandMap = getCommandMap.invoke(server);
         Command spyCmd = new Command("clanspy") {
            public boolean execute(CommandSender sender, String label, String[] args) {
               if (sender instanceof Player player) {
                  if (!player.hasPermission("smclans.admin.spy")) {
                     player.sendMessage("§cNo permission.");
                     return true;
                  } else {
                     SM_Clans.this.toggleSpy(player.getUniqueId());
                     boolean spying = SM_Clans.this.isSpying(player.getUniqueId());
                     player.sendMessage(spying ? "§aClan spy enabled." : "§cClan spy disabled.");
                     return true;
                  }
               } else {
                  sender.sendMessage("Only players.");
                  return true;
               }
            }
         };
         spyCmd.setDescription("Toggle clan chat spy");
         spyCmd.setPermission("smclans.admin.spy");
         Method register = commandMap.getClass().getMethod("register", String.class, Command.class);
         register.invoke(commandMap, "smps", spyCmd);
         this.plugin.getLogger().info("Registered /clanspy");
      } catch (Exception e) {
         this.plugin.getLogger().warning("Failed to register /clanspy: " + e.getMessage());
      }

   }

   public boolean isSpying(UUID uuid) {
      return this.chatSpies.contains(uuid);
   }

   public void toggleSpy(UUID uuid) {
      if (this.chatSpies.contains(uuid)) {
         this.chatSpies.remove(uuid);
      } else {
         this.chatSpies.add(uuid);
      }

   }

   private static enum ConfirmType {
      DISBAND,
      PROMOTE;

      private ConfirmType() {
      }
   }

   private static final class PendingConfirm {
      private final ConfirmType type;
      private final String targetName;

      private PendingConfirm(ConfirmType type, String targetName) {
         super();
         this.type = type;
         this.targetName = targetName;
      }

      private String getTargetName() {
         return this.targetName;
      }
   }
}
