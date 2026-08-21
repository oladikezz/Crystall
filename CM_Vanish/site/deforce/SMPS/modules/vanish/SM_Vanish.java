package site.deforce.SMPS.modules.vanish;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.api.IModule;
import net.schalker.DoAPI.core.module.ModuleInfo;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.scoreboard.Team.Option;
import org.bukkit.scoreboard.Team.OptionStatus;
import site.deforce.SMPS.modules.vanish.commands.HitVanishCommand;
import site.deforce.SMPS.modules.vanish.commands.IncognitoCommand;
import site.deforce.SMPS.modules.vanish.commands.SilentVanishCommand;
import site.deforce.SMPS.modules.vanish.commands.ToggleTabVisibilityCommand;
import site.deforce.SMPS.modules.vanish.commands.VanishCommand;
import site.deforce.SMPS.modules.vanish.commands.VanishListCommand;
import site.deforce.SMPS.modules.vanish.database.VanishDatabase;
import site.deforce.SMPS.modules.vanish.integration.DoChatIntegration;
import site.deforce.SMPS.modules.vanish.integration.InteractiveChatIntegration;
import site.deforce.SMPS.modules.vanish.integration.ServerExpansion;
import site.deforce.SMPS.modules.vanish.integration.SquaremapIntegration;
import site.deforce.SMPS.modules.vanish.integration.TabIntegration;
import site.deforce.SMPS.modules.vanish.integration.VanishExpansion;
import site.deforce.SMPS.modules.vanish.listeners.VanishListener;

public class SM_Vanish implements IModule {
   private static final String MODULE_NAME = "SM_Vanish";
   private static SM_Vanish instance;
   private final DoAPI smps;
   private final ModuleInfo moduleInfo;
   private boolean enabled = false;
   private final Set<UUID> vanishedPlayers = ConcurrentHashMap.newKeySet();
   private final Set<UUID> vanishSeePlayers = ConcurrentHashMap.newKeySet();
   private final Set<UUID> silentVanishPlayers = ConcurrentHashMap.newKeySet();
   private final Set<UUID> incognitoPlayers = ConcurrentHashMap.newKeySet();
   private final Set<UUID> debugHiddenNameTags = ConcurrentHashMap.newKeySet();
   private final Map<UUID, Boolean> tabVisibilityOverrides = new ConcurrentHashMap();
   private final Map<UUID, GameMode> preVanishGameModes = new ConcurrentHashMap();
   private final Map<UUID, HitVanishData> hitVanishPlayers = new ConcurrentHashMap();
   private Team vanishedTeam;
   private VanishDatabase vanishDatabase;
   private boolean placeholderApiAvailable = false;
   private boolean simpleVoiceChatAvailable = false;
   private boolean persistState = true;
   private Object vanishExpansion;
   private Object serverExpansion;
   private TabIntegration tabIntegration;
   private DoChatIntegration doChatIntegration;
   private SquaremapIntegration squaremapIntegration;
   private InteractiveChatIntegration interactiveChatIntegration;
   private FileConfiguration config;
   private final List<String> registeredCommands = new ArrayList();
   private VanishListener vanishListener;
   private final Map<UUID, Object> actionBarTasks = new ConcurrentHashMap();
   private boolean isFolia = false;
   private final Map<UUID, PlayerSnapshot> playerSnapshots = new ConcurrentHashMap();

   public static SM_Vanish getInstance() {
      return instance;
   }

   public SM_Vanish(DoAPI smps) {
      super();
      instance = this;
      this.smps = smps;
      String name = "SM_Vanish";
      String version = "Unknown";
      String author = "Unknown";
      String description = "No description";

      try {
         InputStream is = this.getClass().getResourceAsStream("/module.yml");

         try {
            if (is != null) {
               YamlConfiguration yml = YamlConfiguration.loadConfiguration(new InputStreamReader(is));
               name = yml.getString("name", name);
               version = yml.getString("version", version);
               author = yml.getString("author", author);
               description = yml.getString("description", description);
            }
         } catch (Throwable var12) {
            if (is != null) {
               try {
                  is.close();
               } catch (Throwable var11) {
                  var12.addSuppressed(var11);
               }
            }

            throw var12;
         }

         if (is != null) {
            is.close();
         }
      } catch (Exception e) {
         this.log("Failed to load module.yml: " + e.getMessage());
      }

      this.moduleInfo = new ModuleInfo(name, version, author, description);

      try {
         Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
         this.isFolia = true;
      } catch (ClassNotFoundException var10) {
         try {
            Class.forName("io.papermc.paper.threadedregions.RegionScheduler");
            this.isFolia = true;
         } catch (ClassNotFoundException var9) {
         }
      }

   }

   public void onEnable() {
      this.log("Enabling SM_Vanish module...");
      this.smps.getModuleManager().saveModuleDefaultConfig("SM_Vanish");
      this.loadConfig();
      this.initDatabase();
      this.vanishListener = new VanishListener(this);
      this.smps.getServer().getPluginManager().registerEvents(this.vanishListener, this.smps);
      Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

      try {
         this.vanishedTeam = scoreboard.getTeam("zz_vanished");
         if (this.vanishedTeam == null) {
            this.vanishedTeam = scoreboard.registerNewTeam("zz_vanished");
         }

         this.vanishedTeam.color(NamedTextColor.GRAY);
         this.vanishedTeam.setOption(Option.COLLISION_RULE, OptionStatus.NEVER);
      } catch (Exception e) {
         this.log("Failed to register vanish team: " + e.getMessage());
      }

      this.registerCommands();
      this.checkIntegrations();
      this.refreshVanishSeePlayers();
      this.enabled = true;
      this.log("SM_Vanish module enabled successfully!");
   }

   public void onDisable() {
      this.stopAllActionBarTasks();
      List<Player> vanishedOnline = new ArrayList();

      for(Player player : Bukkit.getOnlinePlayers()) {
         if (this.isVanished(player)) {
            this.saveVanishState(player);
            vanishedOnline.add(player);
         }
      }

      for(Player player : vanishedOnline) {
         this.removeVanish(player, false);
         player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&cModule disabled: Vanish deactivated."));
      }

      this.vanishedPlayers.clear();
      this.vanishSeePlayers.clear();
      this.silentVanishPlayers.clear();
      this.incognitoPlayers.clear();
      this.debugHiddenNameTags.clear();
      this.tabVisibilityOverrides.clear();
      this.preVanishGameModes.clear();
      this.hitVanishPlayers.clear();
      this.unregisterCommands();
      if (this.doChatIntegration != null) {
         this.doChatIntegration.shutdown();
         this.doChatIntegration = null;
      }

      if (this.interactiveChatIntegration != null) {
         HandlerList.unregisterAll(this.interactiveChatIntegration);
         this.interactiveChatIntegration = null;
      }

      if (this.vanishListener != null) {
         HandlerList.unregisterAll(this.vanishListener);
         this.vanishListener = null;
      }

      if (this.placeholderApiAvailable) {
         this.unregisterExpansions();
      }

      if (this.vanishedTeam != null) {
         try {
            this.vanishedTeam.unregister();
         } catch (Exception var4) {
         }

         this.vanishedTeam = null;
      }

      this.enabled = false;
      instance = null;
   }

   public void reload() {
      this.loadConfig();
      this.refreshVanishSeePlayers();
      this.log("SM_Vanish module reloaded!");
   }

   public ModuleInfo getModuleInfo() {
      return this.moduleInfo;
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   private void loadConfig() {
      this.config = this.smps.getModuleManager().loadModuleConfig("SM_Vanish");
      if (this.config == null) {
         this.config = new YamlConfiguration();
      }

      this.persistState = this.config.getBoolean("persist-vanish-state", true);
      this.log("Configuration loaded.");
   }

   public FileConfiguration getModuleConfig() {
      if (this.config == null) {
         this.loadConfig();
      }

      return this.config;
   }

   private void initDatabase() {
      if (!this.persistState) {
         this.log("Vanish state persistence is disabled");
      } else {
         this.vanishDatabase = new VanishDatabase(this);
         if (!this.vanishDatabase.isConnected()) {
            this.log("WARNING: Database is not connected via DoAPI Core.");
            this.log("Persistence will be handled in-memory only (will be lost on server restart).");
         } else {
            this.vanishDatabase.createTables();
            this.log("Database initialized");
         }

      }
   }

   private void registerCommands() {
      VanishCommand vanishCommand = new VanishCommand(this);
      SilentVanishCommand silentVanishCommand = new SilentVanishCommand(this);
      VanishListCommand vanishListCommand = new VanishListCommand(this);
      ToggleTabVisibilityCommand toggleTabVisibilityCommand = new ToggleTabVisibilityCommand(this);

      try {
         Server server = Bukkit.getServer();
         Method commandMapField = server.getClass().getDeclaredMethod("getCommandMap");
         CommandMap commandMap = (CommandMap)commandMapField.invoke(server);
         PluginCommand vanishCmd = this.createPluginCommand("vanish", this.smps);
         if (vanishCmd != null) {
            vanishCmd.setExecutor(vanishCommand);
            vanishCmd.setTabCompleter(vanishCommand);
            vanishCmd.setAliases(Collections.singletonList("v"));
            vanishCmd.setDescription("Toggle vanish mode with fake quit/join messages");
            vanishCmd.setPermission("smvanish.use");
            commandMap.register("smvanish", vanishCmd);
            this.registeredCommands.add("smvanish:vanish");
            this.registeredCommands.add("vanish");
            this.registeredCommands.add("smvanish:v");
            this.registeredCommands.add("v");
         }

         PluginCommand silentVanishCmd = this.createPluginCommand("silentvanish", this.smps);
         if (silentVanishCmd != null) {
            silentVanishCmd.setExecutor(silentVanishCommand);
            silentVanishCmd.setTabCompleter(silentVanishCommand);
            silentVanishCmd.setAliases(Collections.singletonList("sv"));
            silentVanishCmd.setDescription("Toggle vanish mode silently (no fake messages)");
            silentVanishCmd.setPermission("smvanish.use");
            commandMap.register("smvanish", silentVanishCmd);
            this.registeredCommands.add("smvanish:silentvanish");
            this.registeredCommands.add("silentvanish");
            this.registeredCommands.add("smvanish:sv");
            this.registeredCommands.add("sv");
         }

         PluginCommand vanishListCmd = this.createPluginCommand("vanishlist", this.smps);
         if (vanishListCmd != null) {
            vanishListCmd.setExecutor(vanishListCommand);
            vanishListCmd.setTabCompleter(vanishListCommand);
            vanishListCmd.setAliases(Collections.singletonList("vl"));
            vanishListCmd.setDescription("List all vanished players");
            vanishListCmd.setPermission("smvanish.vanishlist");
            commandMap.register("smvanish", vanishListCmd);
            this.registeredCommands.add("smvanish:vanishlist");
            this.registeredCommands.add("vanishlist");
            this.registeredCommands.add("smvanish:vl");
            this.registeredCommands.add("vl");
         }

         PluginCommand toggleTabVisibilityCmd = this.createPluginCommand("toggletabvisibility", this.smps);
         if (toggleTabVisibilityCmd != null) {
            toggleTabVisibilityCmd.setExecutor(toggleTabVisibilityCommand);
            toggleTabVisibilityCmd.setTabCompleter(toggleTabVisibilityCommand);
            toggleTabVisibilityCmd.setDescription("Lock/unlock your tab visibility independent of vanish state");
            toggleTabVisibilityCmd.setPermission("smvanish.use");
            commandMap.register("smvanish", toggleTabVisibilityCmd);
            this.registeredCommands.add("smvanish:toggletabvisibility");
            this.registeredCommands.add("toggletabvisibility");
         }

         IncognitoCommand incognitoCommand = new IncognitoCommand(this);
         PluginCommand incognitoCmd = this.createPluginCommand("incognito", this.smps);
         if (incognitoCmd != null) {
            incognitoCmd.setExecutor(incognitoCommand);
            incognitoCmd.setTabCompleter(incognitoCommand);
            incognitoCmd.setAliases(Collections.singletonList("inc"));
            incognitoCmd.setDescription("Toggle incognito mode (hide from tab, nametag, join/quit messages)");
            incognitoCmd.setPermission("smvanish.incognito");
            commandMap.register("smvanish", incognitoCmd);
            this.registeredCommands.add("smvanish:incognito");
            this.registeredCommands.add("incognito");
            this.registeredCommands.add("smvanish:inc");
            this.registeredCommands.add("inc");
         }

         HitVanishCommand hitVanishCommand = new HitVanishCommand(this);
         PluginCommand hitVanishCmd = this.createPluginCommand("hitvanish", this.smps);
         if (hitVanishCmd != null) {
            hitVanishCmd.setExecutor(hitVanishCommand);
            hitVanishCmd.setTabCompleter(hitVanishCommand);
            hitVanishCmd.setDescription("Toggle hitvanish mode");
            hitVanishCmd.setPermission("smvanish.hitvanish");
            commandMap.register("smvanish", hitVanishCmd);
            this.registeredCommands.add("smvanish:hitvanish");
            this.registeredCommands.add("hitvanish");
         }

         this.log("All commands registered");
      } catch (Exception e) {
         this.log("Failed to register commands: " + e.getMessage());
      }

   }

   private void unregisterCommands() {
      try {
         Server server = Bukkit.getServer();
         Method commandMapMethod = server.getClass().getDeclaredMethod("getCommandMap");
         CommandMap commandMap = (CommandMap)commandMapMethod.invoke(server);
         Map<String, Command> knownCommands = null;
         Field knownCommandsField = null;
         Class<?> clazz = commandMap.getClass();

         while(clazz != null && knownCommandsField == null) {
            try {
               knownCommandsField = clazz.getDeclaredField("knownCommands");
            } catch (NoSuchFieldException var13) {
               clazz = clazz.getSuperclass();
            }
         }

         if (knownCommandsField != null) {
            knownCommandsField.setAccessible(true);
            knownCommands = (Map)knownCommandsField.get(commandMap);
         } else {
            try {
               Method getKnownCommandsMethod = commandMap.getClass().getMethod("getKnownCommands");
               knownCommands = (Map)getKnownCommandsMethod.invoke(commandMap);
            } catch (NoSuchMethodException var12) {
               this.log("Could not find knownCommands field or method");
            }
         }

         if (knownCommands == null) {
            this.log("Failed to get knownCommands map");
            return;
         }

         for(String cmdName : this.registeredCommands) {
            Command cmd = (Command)knownCommands.remove(cmdName);
            if (cmd != null) {
               cmd.unregister(commandMap);
            }
         }

         for(String alias : Arrays.asList("vanish", "v", "silentvanish", "sv", "toggletabvisibility", "vanishlist", "vl", "incognito", "inc", "hitvanish")) {
            if (knownCommands.containsKey(alias)) {
               Command cmd = (Command)knownCommands.get(alias);
               if (cmd instanceof PluginCommand) {
                  PluginCommand pluginCmd = (PluginCommand)cmd;
                  if (pluginCmd.getPlugin().equals(this.smps)) {
                     knownCommands.remove(alias);
                     cmd.unregister(commandMap);
                  }
               }
            }
         }

         this.registeredCommands.clear();
         this.log("All commands unregistered");
      } catch (Exception e) {
         this.log("Failed to unregister commands: " + e.getMessage());
      }

   }

   private PluginCommand createPluginCommand(String name, Plugin plugin) {
      try {
         Constructor<PluginCommand> constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
         constructor.setAccessible(true);
         return (PluginCommand)constructor.newInstance(name, plugin);
      } catch (Exception e) {
         this.log("Failed to create PluginCommand: " + e.getMessage());
         return null;
      }
   }

   private void checkIntegrations() {
      Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
      if (papi != null && papi.isEnabled()) {
         try {
            Class.forName("me.clip.placeholderapi.expansion.PlaceholderExpansion");
            this.placeholderApiAvailable = true;
            this.registerExpansions();
            this.log("PlaceholderAPI integration enabled");
         } catch (ClassNotFoundException var6) {
            this.log("PlaceholderAPI found but expansion class not available");
         }
      }

      Plugin tabPlugin = Bukkit.getPluginManager().getPlugin("TAB");
      if (tabPlugin != null && tabPlugin.isEnabled()) {
         try {
            this.tabIntegration = new TabIntegration(this);
         } catch (Throwable t) {
            this.log("Failed to load TAB integration: " + t.getMessage());
         }
      }

      Plugin voiceChat = Bukkit.getPluginManager().getPlugin("voicechat");
      if (voiceChat != null && voiceChat.isEnabled()) {
         this.simpleVoiceChatAvailable = true;
         this.log("SimpleVoiceChat integration enabled");
      }

      this.doChatIntegration = new DoChatIntegration(this);
      this.squaremapIntegration = new SquaremapIntegration(this);
      Plugin interactiveChatPlugin = Bukkit.getPluginManager().getPlugin("InteractiveChat");
      if (interactiveChatPlugin == null) {
         interactiveChatPlugin = Bukkit.getPluginManager().getPlugin("InteractiveChatDiscordSrvAddon");
      }

      if (interactiveChatPlugin != null && interactiveChatPlugin.isEnabled()) {
         this.interactiveChatIntegration = new InteractiveChatIntegration(this);
         this.smps.getServer().getPluginManager().registerEvents(this.interactiveChatIntegration, this.smps);
         this.log("InteractiveChat integration enabled");
      }

   }

   private void registerExpansions() {
      try {
         this.vanishExpansion = new VanishExpansion(this);
         boolean vanishReg = ((PlaceholderExpansion)this.vanishExpansion).register();
         this.log("Vanish PAPI expansion registered: " + vanishReg);
         VanishExpansion smVanishExpansion = new VanishExpansion(this, "smvanish");
         boolean smVanishReg = ((PlaceholderExpansion)smVanishExpansion).register();
         this.log("SM_Vanish PAPI expansion registered: " + smVanishReg);
         this.serverExpansion = new ServerExpansion(this, "smvanishserver");
         boolean serverReg = ((PlaceholderExpansion)this.serverExpansion).register();
         this.log("Server PAPI expansion (smvanishserver) registered: " + serverReg);
         ServerExpansion smServerExpansion = new ServerExpansion(this, "smserver");
         boolean smServerReg = ((PlaceholderExpansion)smServerExpansion).register();
         this.log("SMServer PAPI expansion (smserver) registered: " + smServerReg);
      } catch (Exception e) {
         this.log("Failed to register PlaceholderAPI expansions: " + e.getMessage());
      }

   }

   private void unregisterExpansions() {
      try {
         if (this.vanishExpansion != null) {
            ((PlaceholderExpansion)this.vanishExpansion).unregister();
         }

         if (this.serverExpansion != null) {
            ((PlaceholderExpansion)this.serverExpansion).unregister();
         }
      } catch (Exception e) {
         this.log("Failed to unregister PlaceholderAPI expansions: " + e.getMessage());
      }

   }

   public void refreshVanishSeePlayers() {
      this.vanishSeePlayers.clear();

      for(Player player : Bukkit.getOnlinePlayers()) {
         this.updateSeePermission(player);
      }

   }

   public void toggleVanish(Player player) {
      this.silentVanishPlayers.remove(player.getUniqueId());
      this.setVanished(player, !this.isVanished(player), true);
   }

   public void toggleSilentVanish(Player player) {
      boolean nowVanished = !this.isVanished(player);
      if (nowVanished) {
         this.silentVanishPlayers.add(player.getUniqueId());
      } else {
         this.silentVanishPlayers.remove(player.getUniqueId());
      }

      this.setVanished(player, nowVanished, false);
   }

   public void setVanished(Player player, boolean vanished, boolean sendFakeMessages) {
      this.log("setVanished(" + player.getName() + ", " + vanished + ", sendFake=" + sendFakeMessages + ")");
      if (!vanished) {
         this.silentVanishPlayers.remove(player.getUniqueId());
      }

      if (vanished) {
         this.applyVanish(player, sendFakeMessages);
      } else {
         this.removeVanish(player, sendFakeMessages);
      }

      this.saveVanishState(player);
   }

   private void applyVanish(Player player, boolean sendFakeQuit) {
      this.preVanishGameModes.put(player.getUniqueId(), player.getGameMode());
      this.playerSnapshots.put(player.getUniqueId(), new PlayerSnapshot(player));
      this.vanishedPlayers.add(player.getUniqueId());
      player.setMetadata("vanished", new FixedMetadataValue(this.smps, true));
      this.hidePlayerFromAll(player);
      if (this.vanishedTeam != null && !this.vanishedTeam.hasEntry(player.getName())) {
         this.vanishedTeam.addEntry(player.getName());
      }

      this.updateTabVisibility(player);
      String enabledMsg = this.config.getString("messages.vanish-enabled", "&aYou are now vanished!");
      player.sendMessage(this.formatMessage(enabledMsg));
      this.startActionBarTask(player);
      if (sendFakeQuit) {
         String quitText = this.config.getString("messages.fake-quit", "&e{player} вышел из игры").replace("{player}", player.getName());
         Component quitMessage = this.formatMessage(quitText);

         for(Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(player) && !online.hasPermission("smvanish.see") && !online.isOp()) {
               online.sendMessage(quitMessage);
            }
         }

         player.sendMessage(quitMessage);
      }

      if (this.squaremapIntegration != null && this.squaremapIntegration.isAvailable()) {
         this.squaremapIntegration.hidePlayer(player);
      }

      Runnable entityWork = () -> {
         if (player.isOnline()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
               this.clearGlowingState(player);
               player.setSilent(true);
               player.setCustomNameVisible(false);
               player.setCanPickupItems(false);
               if (this.simpleVoiceChatAvailable) {
                  this.disableVoiceChat(player);
               }

               this.clearMobTargets(player);
            } else {
               player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, -1, 0, false, false, false));
               this.clearGlowingState(player);
               player.setCollidable(false);
               player.setSilent(true);
               player.setCustomNameVisible(false);
               player.setCanPickupItems(false);
               player.setAllowFlight(true);
               if (this.simpleVoiceChatAvailable) {
                  this.disableVoiceChat(player);
               }

               try {
                  Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "map hide " + player.getName());
               } catch (Exception var3) {
               }

               this.clearMobTargets(player);
            }
         }
      };
      if (this.isFolia) {
         this.runOnEntityScheduler(player, entityWork);
      } else {
         entityWork.run();
      }

      this.scheduleEntityDelayedTask(player, () -> this.hidePlayerFromAll(player), 1L);
      this.scheduleEntityDelayedTask(player, () -> this.hidePlayerFromAll(player), 3L);
      this.scheduleEntityDelayedTask(player, () -> this.updateTabVisibility(player), 2L);
      this.scheduleEntityDelayedTask(player, () -> this.updateTabVisibility(player), 4L);
      this.log(player.getName() + " is now vanished");
   }

   private void hidePlayerFromAll(Player player) {
      if (player.isOnline()) {
         for(Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(player) && !online.hasPermission("smvanish.see") && !online.isOp()) {
               this.hidePlayerFromViewer(online, player);
            }
         }

      }
   }

   public void hidePlayerFromViewer(Player viewer, Player target) {
      if (viewer.isOnline() && target.isOnline()) {
         this.runOnEntityScheduler(viewer, () -> {
            viewer.hidePlayer(this.smps, target);
            if (this.hasTabVisibilityOverride(target.getUniqueId()) && this.isTabVisible(target)) {
               this.sendTabListAddPacket(viewer, target);
            }

         });
      }
   }

   private void showPlayerToAll(Player target) {
      if (target.isOnline()) {
         for(Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(target)) {
               this.runOnEntityScheduler(online, () -> online.showPlayer(this.smps, target));
            }
         }

      }
   }

   public void runOnEntityScheduler(Player viewer, Runnable task) {
      if (viewer.isOnline()) {
         this.smps.getSchedulerManager().runEntityTask(viewer, "vanish_update_viewer_" + String.valueOf(viewer.getUniqueId()), task);
      }
   }

   private void clearMobTargets(Player player) {
      if (player.isOnline()) {
         for(Entity entity : player.getWorld().getEntities()) {
            if (entity instanceof Mob) {
               Mob mob = (Mob)entity;
               if (this.isFolia) {
                  try {
                     Object entityScheduler = mob.getClass().getMethod("getScheduler").invoke(mob);
                     entityScheduler.getClass().getMethod("run", Plugin.class, Consumer.class, Runnable.class).invoke(entityScheduler, this.smps, (Consumer)(t) -> {
                        try {
                           if (mob.getTarget() != null && mob.getTarget().equals(player)) {
                              mob.setTarget((LivingEntity)null);
                           }
                        } catch (Exception var4) {
                        }

                     }, null);
                  } catch (Exception var6) {
                  }
               } else {
                  try {
                     if (mob.getTarget() != null && mob.getTarget().equals(player)) {
                        mob.setTarget((LivingEntity)null);
                     }
                  } catch (Exception var7) {
                  }
               }
            }
         }

      }
   }

   private void clearGlowingState(Player player) {
      player.removePotionEffect(PotionEffectType.GLOWING);
      player.setGlowing(false);
   }

   private void removeVanish(Player player, boolean sendFakeJoin) {
      this.vanishedPlayers.remove(player.getUniqueId());
      this.silentVanishPlayers.remove(player.getUniqueId());
      boolean keepIncognito = this.isIncognito(player);
      if (!keepIncognito) {
         player.removeMetadata("vanished", this.smps);
      } else {
         player.setMetadata("vanished", new FixedMetadataValue(this.smps, true));
      }

      if (this.vanishedTeam != null && this.vanishedTeam.hasEntry(player.getName())) {
         this.vanishedTeam.removeEntry(player.getName());
      }

      this.updateTabVisibility(player);
      this.stopActionBarTask(player);
      String disabledMsg = this.config.getString("messages.vanish-disabled", "&cYou are no longer vanished!");
      player.sendMessage(this.formatMessage(disabledMsg));
      if (sendFakeJoin) {
         String joinText = this.config.getString("messages.fake-join", "&e{player} зашёл в игру").replace("{player}", player.getName());
         Component joinMessage = this.formatMessage(joinText);

         for(Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(player)) {
               online.sendMessage(joinMessage);
            }
         }

         player.sendMessage(joinMessage);
      }

      this.showPlayerToAll(player);
      this.updateTabVisibility(player);
      if (this.squaremapIntegration != null && this.squaremapIntegration.isAvailable()) {
         this.squaremapIntegration.showPlayer(player);
      }

      Runnable entityWork = () -> {
         if (player.isOnline()) {
            player.removePotionEffect(PotionEffectType.SATURATION);
            player.setCustomNameVisible(!this.isDebugNameTagHidden(player));
            if (this.simpleVoiceChatAvailable) {
               this.enableVoiceChat(player);
            }

            PlayerSnapshot snapshot = (PlayerSnapshot)this.playerSnapshots.remove(player.getUniqueId());
            if (snapshot != null) {
               snapshot.restore(player);
               this.log("Restored player state for " + player.getName());
            }

            try {
               Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "map show " + player.getName());
            } catch (Exception var5) {
            }

            if (player.getGameMode() != GameMode.SPECTATOR) {
               if (!keepIncognito) {
                  player.playerListName(Component.text(player.getName()));
               }

               player.setCollidable(true);
               player.setSilent(false);
               player.setCanPickupItems(true);
               if (player.getGameMode() != GameMode.CREATIVE) {
                  player.setAllowFlight(false);
                  player.setFlying(false);
               }

            }
         }
      };
      if (this.isFolia) {
         this.runOnEntityScheduler(player, entityWork);
      } else {
         entityWork.run();
      }

      this.preVanishGameModes.remove(player.getUniqueId());
      this.scheduleEntityDelayedTask(player, () -> this.updateTabVisibility(player), 1L);
      this.scheduleEntityDelayedTask(player, () -> this.updateTabVisibility(player), 5L);
      if (keepIncognito) {
         this.flashPlayerInTab(player, 2L);
      }

      this.log(player.getName() + " is no longer vanished");
   }

   public void applyVanishEffects(Player player) {
      this.playerSnapshots.putIfAbsent(player.getUniqueId(), new PlayerSnapshot(player));
      this.preVanishGameModes.putIfAbsent(player.getUniqueId(), player.getGameMode());
      player.setMetadata("vanished", new FixedMetadataValue(this.smps, true));
      String silentJoinMsg = this.config.getString("messages.silent-join", "&7You've joined the server in vanish, silently.");
      player.sendMessage(this.formatMessage(silentJoinMsg));
      this.startActionBarTask(player);
      this.hidePlayerFromAll(player);
      if (this.vanishedTeam != null && !this.vanishedTeam.hasEntry(player.getName())) {
         this.vanishedTeam.addEntry(player.getName());
      }

      this.updateTabVisibility(player);
      if (this.squaremapIntegration != null && this.squaremapIntegration.isAvailable()) {
         this.squaremapIntegration.hidePlayer(player);
      }

      Runnable entityWork = () -> {
         if (player.isOnline()) {
            try {
               double maxHealth;
               try {
                  AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
                  if (attr != null) {
                     maxHealth = attr.getValue();
                  } else {
                     maxHealth = player.getMaxHealth();
                  }
               } catch (Throwable var5) {
                  maxHealth = player.getMaxHealth();
               }

               player.setHealth(maxHealth);
            } catch (Exception e) {
               String var10001 = player.getName();
               this.log("Error setting max health for " + var10001 + ": " + e.getMessage());
            }

            player.setFoodLevel(20);
            player.setSaturation(20.0F);
            player.setFireTicks(0);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, -1, 0, false, false, false));
            this.clearGlowingState(player);
            player.setCollidable(false);
            player.setSilent(true);
            player.setCustomNameVisible(false);
            player.setCanPickupItems(false);
            player.setAllowFlight(true);
            if (this.simpleVoiceChatAvailable) {
               this.disableVoiceChat(player);
            }

         }
      };
      if (this.isFolia) {
         this.runOnEntityScheduler(player, entityWork);
      } else {
         entityWork.run();
      }

      this.scheduleEntityDelayedTask(player, () -> this.hidePlayerFromAll(player), 2L);
      this.scheduleEntityDelayedTask(player, () -> this.hidePlayerFromAll(player), 5L);
      this.scheduleEntityDelayedTask(player, () -> this.hidePlayerFromAll(player), 15L);
      this.scheduleEntityDelayedTask(player, () -> this.updateTabVisibility(player), 3L);
      this.scheduleEntityDelayedTask(player, () -> this.updateTabVisibility(player), 6L);
      this.scheduleEntityDelayedTask(player, () -> this.updateTabVisibility(player), 16L);
      this.log("Vanish effects applied to " + player.getName());
   }

   public void restoreVanishSettings(Player player) {
      if (this.isVanished(player)) {
         if (this.vanishedTeam != null && !this.vanishedTeam.hasEntry(player.getName())) {
            this.vanishedTeam.addEntry(player.getName());
         }

         this.updateTabVisibility(player);
         if (this.squaremapIntegration != null && this.squaremapIntegration.isAvailable()) {
            this.squaremapIntegration.hidePlayer(player);
         }

         Runnable entityWork = () -> {
            if (player.isOnline()) {
               this.clearGlowingState(player);
               player.setCollidable(false);
               player.setSilent(true);
               player.setCustomNameVisible(false);
               player.setCanPickupItems(false);
               player.setAllowFlight(true);
               if (this.simpleVoiceChatAvailable) {
                  this.disableVoiceChat(player);
               }

            }
         };
         if (this.isFolia) {
            this.runOnEntityScheduler(player, entityWork);
         } else {
            entityWork.run();
         }

         this.hidePlayerFromAll(player);
         this.scheduleEntityDelayedTask(player, () -> this.hidePlayerFromAll(player), 2L);
         this.scheduleEntityDelayedTask(player, () -> this.updateTabVisibility(player), 3L);
         this.log("Vanish settings restored for " + player.getName());
      }
   }

   private void disableVoiceChat(Player player) {
      try {
         player.setMetadata("voicechat:hidden", new FixedMetadataValue(this.smps, true));
      } catch (Exception var3) {
      }

   }

   private void enableVoiceChat(Player player) {
      try {
         player.removeMetadata("voicechat:hidden", this.smps);
      } catch (Exception var3) {
      }

   }

   public void scheduleDelayedTask(Runnable task, long delayTicks) {
      if (this.isFolia) {
         try {
            Object globalRegionScheduler = Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler").invoke(Bukkit.getServer());
            globalRegionScheduler.getClass().getMethod("runDelayed", Plugin.class, Consumer.class, Long.TYPE).invoke(globalRegionScheduler, this.smps, (Consumer)(t) -> task.run(), delayTicks);
         } catch (Exception e) {
            this.log("Failed to schedule Folia delayed task: " + e.getMessage());
            task.run();
         }
      } else {
         Bukkit.getScheduler().runTaskLater(this.smps, task, delayTicks);
      }

   }

   public void scheduleEntityDelayedTask(Player player, Runnable task, long delayTicks) {
      if (player.isOnline()) {
         if (this.isFolia) {
            try {
               String var10000 = String.valueOf(player.getUniqueId());
               String taskName = "vanish_entity_delay_" + var10000 + "_" + System.nanoTime();
               this.smps.getSchedulerManager().runEntityTaskLater(player, taskName, task, Math.max(0L, delayTicks));
            } catch (Exception e) {
               this.log("Failed to schedule Folia entity task: " + e.getMessage());
               task.run();
            }
         } else {
            Bukkit.getScheduler().runTaskLater(this.smps, task, delayTicks);
         }

      }
   }

   private void startActionBarTask(Player player) {
      this.stopActionBarTask(player);

      try {
         String taskName = "vanish_actionbar_" + String.valueOf(player.getUniqueId());
         this.smps.getSchedulerManager().runEntityTaskTimer(player, taskName, () -> {
            if (player.isOnline() && this.isVanished(player)) {
               String actionBarMsg = this.config.getString("messages.actionbar-vanished", "&c⚠ &fYou are &cVANISHED &c⚠");
               Component actionBarComponent = this.formatMessage(actionBarMsg);
               player.sendActionBar(actionBarComponent);
            } else {
               this.stopActionBarTask(player);
            }
         }, 0L, 40L);
         this.actionBarTasks.put(player.getUniqueId(), taskName);
      } catch (Exception e) {
         this.log("Failed to start action bar task: " + e.getMessage());
      }

   }

   private void stopActionBarTask(Player player) {
      Object taskObj = this.actionBarTasks.remove(player.getUniqueId());
      if (taskObj instanceof String taskName) {
         this.smps.getSchedulerManager().cancelTask(taskName);
         if (player.isOnline()) {
            player.sendActionBar(Component.empty());
         }
      } else if (taskObj != null) {
         this.cancelTask(taskObj);
      }

   }

   private void cancelTask(Object task) {
      if (task != null) {
         if (this.isFolia) {
            try {
               Method method = task.getClass().getMethod("cancel");
               method.setAccessible(true);
               method.invoke(task);
            } catch (Exception var3) {
            }
         } else if (task instanceof BukkitTask) {
            ((BukkitTask)task).cancel();
         }

      }
   }

   private void stopAllActionBarTasks() {
      for(Map.Entry<UUID, Object> entry : this.actionBarTasks.entrySet()) {
         Object taskObj = entry.getValue();
         if (taskObj instanceof String) {
            this.smps.getSchedulerManager().cancelTask((String)taskObj);
         } else {
            this.cancelTask(taskObj);
         }

         Player player = Bukkit.getPlayer((UUID)entry.getKey());
         if (player != null && player.isOnline()) {
            player.sendActionBar(Component.empty());
         }
      }

      this.actionBarTasks.clear();
   }

   public boolean isVanished(Player player) {
      return this.vanishedPlayers.contains(player.getUniqueId());
   }

   public boolean isVanished(UUID uuid) {
      return this.vanishedPlayers.contains(uuid);
   }

   public boolean isSilentVanished(Player player) {
      return this.silentVanishPlayers.contains(player.getUniqueId());
   }

   public Set<UUID> getVanishedPlayers() {
      return this.vanishedPlayers;
   }

   public void removeVanishedFromCache(UUID uuid) {
      this.vanishedPlayers.remove(uuid);
   }

   public boolean canSeeVanish(UUID playerId) {
      return this.vanishSeePlayers.contains(playerId);
   }

   public void updateSeePermission(Player player) {
      if (!player.hasPermission("smvanish.see") && !player.isOp()) {
         this.vanishSeePlayers.remove(player.getUniqueId());
      } else {
         this.vanishSeePlayers.add(player.getUniqueId());
      }

   }

   public void removeFromSeeList(UUID playerId) {
      this.vanishSeePlayers.remove(playerId);
   }

   public void saveVanishState(Player player) {
      if (this.persistState && this.vanishDatabase != null) {
         boolean isVanished = this.isVanished(player);
         this.vanishDatabase.setVanished(player.getUniqueId(), isVanished, (UUID)null).thenRun(() -> {
            String var10001 = player.getName();
            this.log("Vanish state saved for " + var10001 + ": " + isVanished);
         });
      }
   }

   public void saveVanishStateSync(Player player) {
      if (this.persistState && this.vanishDatabase != null) {
         boolean isVanished = this.isVanished(player);
         this.vanishDatabase.setVanishedSync(player.getUniqueId(), isVanished, (UUID)null);
         String var10001 = player.getName();
         this.log("Vanish state saved (sync) for " + var10001 + ": " + isVanished);
      }
   }

   public boolean toggleOfflineVanishState(UUID uuid, String playerName) {
      if (this.persistState && this.vanishDatabase != null) {
         boolean currentlyVanished = this.vanishDatabase.isVanishedSync(uuid);
         boolean newState = !currentlyVanished;
         this.vanishDatabase.setVanished(uuid, newState, (UUID)null).thenRun(() -> this.log("Offline vanish state saved for " + playerName + " (" + String.valueOf(uuid) + "): " + newState));
         this.log("Toggled offline vanish for " + playerName + ": " + currentlyVanished + " -> " + newState);
         return newState;
      } else {
         this.log("Cannot toggle offline vanish: persistence disabled or database unavailable");
         return false;
      }
   }

   public boolean toggleOfflineIncognitoState(UUID uuid, String playerName) {
      if (this.persistState && this.vanishDatabase != null) {
         boolean currently = this.vanishDatabase.isIncognitoSync(uuid);
         boolean newState = !currently;
         this.vanishDatabase.setIncognito(uuid, newState).thenRun(() -> this.log("Offline incognito state saved for " + playerName + " (" + String.valueOf(uuid) + "): " + newState));
         this.log("Toggled offline incognito for " + playerName + ": " + currently + " -> " + newState);
         return newState;
      } else {
         this.log("Cannot toggle offline incognito: persistence disabled or database unavailable");
         return false;
      }
   }

   public boolean isVanishedInDatabase(UUID uuid) {
      return this.persistState && this.vanishDatabase != null ? this.vanishDatabase.isVanishedSync(uuid) : false;
   }

   public void loadVanishStateSync(Player player) {
      if (this.persistState && this.vanishDatabase != null) {
         try {
            boolean wasVanished = this.vanishDatabase.isVanishedSync(player.getUniqueId());
            if (wasVanished) {
               this.vanishedPlayers.add(player.getUniqueId());
               this.log("Loaded vanish state for " + player.getName() + ": vanished=true");
            }
         } catch (Exception e) {
            String var10001 = player.getName();
            this.log("Failed to load vanish state for " + var10001 + ": " + e.getMessage());
         }

      }
   }

   public void loadTabVisibilityOverrideSync(Player player) {
      if (this.persistState && this.vanishDatabase != null) {
         try {
            Boolean override = this.vanishDatabase.getTabVisibilityOverrideSync(player.getUniqueId());
            if (override == null) {
               this.tabVisibilityOverrides.remove(player.getUniqueId());
            } else {
               this.tabVisibilityOverrides.put(player.getUniqueId(), override);
            }
         } catch (Exception e) {
            String var10001 = player.getName();
            this.log("Failed to load tab visibility override for " + var10001 + ": " + e.getMessage());
         }

      }
   }

   private void saveTabVisibilityOverride(UUID playerId) {
      if (this.persistState && this.vanishDatabase != null) {
         Boolean override = (Boolean)this.tabVisibilityOverrides.get(playerId);
         this.vanishDatabase.setTabVisibilityOverride(playerId, override);
      }
   }

   public boolean hasTabVisibilityOverride(UUID playerId) {
      return this.tabVisibilityOverrides.containsKey(playerId);
   }

   public void evictTabVisibilityOverrideCache(UUID playerId) {
      this.tabVisibilityOverrides.remove(playerId);
   }

   public boolean isTabVisible(Player player) {
      return this.isTabVisible(player.getUniqueId());
   }

   public boolean isTabVisible(UUID playerId) {
      Boolean override = (Boolean)this.tabVisibilityOverrides.get(playerId);
      if (override != null) {
         return override;
      } else {
         return !this.isVanished(playerId);
      }
   }

   public boolean toggleTabVisibilityLock(Player player) {
      UUID playerId = player.getUniqueId();
      if (this.tabVisibilityOverrides.containsKey(playerId)) {
         this.tabVisibilityOverrides.remove(playerId);
         this.saveTabVisibilityOverride(playerId);
         this.updateTabVisibility(player);
         return false;
      } else {
         this.tabVisibilityOverrides.put(playerId, this.isTabVisible(playerId));
         this.saveTabVisibilityOverride(playerId);
         this.updateTabVisibility(player);
         return true;
      }
   }

   public void setTabVisibilityOverride(Player player, Boolean visibleOverride) {
      UUID playerId = player.getUniqueId();
      if (visibleOverride == null) {
         this.tabVisibilityOverrides.remove(playerId);
      } else {
         this.tabVisibilityOverrides.put(playerId, visibleOverride);
      }

      this.saveTabVisibilityOverride(playerId);
      this.updateTabVisibility(player);
   }

   public void updateTabVisibility(Player player) {
      if (player != null && player.isOnline()) {
         boolean visible = this.isTabVisible(player);
         if (visible) {
            this.applyListedState(player, true);
            player.playerListName((Component)null);

            for(Player viewer : Bukkit.getOnlinePlayers()) {
               if (!viewer.equals(player)) {
                  this.runOnEntityScheduler(viewer, () -> this.sendTabListAddPacket(viewer, player));
               }
            }
         } else if (this.isVanished(player)) {
            this.applyListedState(player, false);
            if (this.tabIntegration != null) {
               this.tabIntegration.hidePlayer(player);
            }

            for(Player viewer : Bukkit.getOnlinePlayers()) {
               if (!viewer.equals(player) && (viewer.hasPermission("smvanish.see") || viewer.isOp())) {
                  this.runOnEntityScheduler(viewer, () -> this.sendTabListAddPacket(viewer, player));
               }
            }
         } else {
            this.applyListedState(player, false);

            for(Player viewer : Bukkit.getOnlinePlayers()) {
               if (!viewer.equals(player) && viewer.hasPermission("smvanish.incognito.see")) {
                  this.runOnEntityScheduler(viewer, () -> this.sendTabListAddPacket(viewer, player));
               }
            }
         }

      }
   }

   public void applyListedState(Player player, boolean listed) {
      try {
         player.getClass().getMethod("setListed", Boolean.TYPE).invoke(player, listed);
      } catch (NoSuchMethodException var6) {
         if (!listed) {
            for(Player viewer : Bukkit.getOnlinePlayers()) {
               if (!viewer.equals(player)) {
                  this.runOnEntityScheduler(viewer, () -> this.sendTabListRemovePacket(viewer, player));
               }
            }
         }
      } catch (Exception e) {
         String var10001 = player.getName();
         this.log("Failed to set listed state for " + var10001 + ": " + e.getMessage());
      }

   }

   public void hideFromTabForViewers(Player player) {
      int viewerCount = 0;

      for(Player viewer : Bukkit.getOnlinePlayers()) {
         if (!viewer.equals(player)) {
            this.runOnEntityScheduler(viewer, () -> this.sendTabListUnlistPacket(viewer, player, false));
            ++viewerCount;
         }
      }

      String var10001 = player.getName();
      this.log("[Debug] hideFromTabForViewers: sent unlist packets for " + var10001 + " to " + viewerCount + " viewers");
   }

   public void showInTabForViewers(Player player) {
      for(Player viewer : Bukkit.getOnlinePlayers()) {
         if (!viewer.equals(player)) {
            this.runOnEntityScheduler(viewer, () -> this.sendTabListUnlistPacket(viewer, player, true));
         }
      }

   }

   public DoAPI getSMPS() {
      return this.smps;
   }

   public Plugin getHostPlugin() {
      return this.smps;
   }

   public Component formatMessage(String text) {
      if (text == null) {
         return Component.empty();
      } else {
         String colored = this.smps.applyColors(text);
         return LegacyComponentSerializer.legacySection().deserialize(colored);
      }
   }

   public void log(String message) {
      try {
         this.smps.getDebugSystem().log("[SM_Vanish] " + message);
      } catch (Exception var3) {
         this.smps.getLogger().info("[SM_Vanish] " + message);
      }

   }

   public void sendFakeQuitOnLeave(Player player) {
      String quitText = this.config.getString("messages.fake-quit", "&e{player} вышел из игры").replace("{player}", player.getName());
      Component quitMessage = this.formatMessage(quitText);

      for(Player online : Bukkit.getOnlinePlayers()) {
         if (!online.equals(player) && !online.hasPermission("smvanish.see") && !online.isOp()) {
            online.sendMessage(quitMessage);
         }
      }

   }

   public boolean isIncognito(Player player) {
      return this.incognitoPlayers.contains(player.getUniqueId());
   }

   public boolean isIncognito(UUID uuid) {
      return this.incognitoPlayers.contains(uuid);
   }

   public boolean isDebugNameTagHidden(Player player) {
      return this.debugHiddenNameTags.contains(player.getUniqueId());
   }

   public boolean isDebugNameTagHidden(UUID uuid) {
      return this.debugHiddenNameTags.contains(uuid);
   }

   public boolean isDebugTabHidden(Player player) {
      return this.isDebugTabHidden(player.getUniqueId());
   }

   public boolean isDebugTabHidden(UUID uuid) {
      return this.hasTabVisibilityOverride(uuid) && !this.isTabVisible(uuid);
   }

   public boolean isDebugBothHidden(Player player) {
      return this.isDebugTabHidden(player) && this.isDebugNameTagHidden(player);
   }

   public boolean isDebugBothHidden(UUID uuid) {
      return this.isDebugTabHidden(uuid) && this.isDebugNameTagHidden(uuid);
   }

   public boolean shouldSuppressPresenceMessages(Player player) {
      return this.isVanished(player) || this.isIncognito(player) || this.isDebugBothHidden(player);
   }

   public boolean setDebugNameTagHidden(Player player, boolean hidden) {
      if (hidden) {
         this.debugHiddenNameTags.add(player.getUniqueId());
         this.enforceHiddenNameTag(player);
         this.saveNameTagVisibilityState(player);
         return true;
      } else {
         this.debugHiddenNameTags.remove(player.getUniqueId());
         if (player.isOnline() && !this.isVanished(player)) {
            String name = player.getName();
            this.runOnPrimaryScheduler(() -> {
               try {
                  Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tab nametag show " + name);
                  this.log("[Debug] Dispatched: /tab nametag show " + name);
               } catch (Exception e) {
                  this.log("[Debug] Failed to dispatch tab nametag show: " + e.getMessage());
               }

            });
         }

         this.saveNameTagVisibilityState(player);
         return false;
      }
   }

   public boolean toggleDebugNameTagHidden(Player player) {
      return this.setDebugNameTagHidden(player, !this.isDebugNameTagHidden(player));
   }

   public boolean isHitVanishEnabled(Player player) {
      return this.hitVanishPlayers.containsKey(player.getUniqueId());
   }

   public HitVanishData getHitVanishData(Player player) {
      return (HitVanishData)this.hitVanishPlayers.get(player.getUniqueId());
   }

   public boolean toggleHitVanish(Player player, double damageThreshold, String particle, String sound) {
      if (this.hitVanishPlayers.containsKey(player.getUniqueId())) {
         this.hitVanishPlayers.remove(player.getUniqueId());
         return false;
      } else {
         this.hitVanishPlayers.put(player.getUniqueId(), new HitVanishData(damageThreshold, particle, sound));
         return true;
      }
   }

   public void triggerHitVanish(Player player) {
      HitVanishData data = (HitVanishData)this.hitVanishPlayers.get(player.getUniqueId());
      if (data != null) {
         if (data.particle != null && !data.particle.isEmpty()) {
            try {
               Particle particleEnum = Particle.valueOf(data.particle.toUpperCase());
               player.getWorld().spawnParticle(particleEnum, player.getLocation().add(0.0, 1.0, 0.0), 20, 0.5, 0.5, 0.5, 0.0);
            } catch (Exception e) {
               String var10001 = player.getName();
               this.log("Failed to spawn hit vanish particle for " + var10001 + ": " + e.getMessage());
            }
         }

         if (data.sound != null && !data.sound.isEmpty()) {
            try {
               Sound soundEnum = Sound.valueOf(data.sound.toUpperCase());
               player.getWorld().playSound(player.getLocation(), soundEnum, 1.0F, 1.0F);
            } catch (Exception e) {
               String var7 = player.getName();
               this.log("Failed to play hit vanish sound for " + var7 + ": " + e.getMessage());
            }
         }
      }

   }

   public void toggleIncognito(Player player) {
      this.setIncognito(player, !this.isIncognito(player), true);
   }

   public void setIncognito(Player player, boolean incognito, boolean sendMessage) {
      if (incognito) {
         this.applyIncognito(player, sendMessage);
      } else {
         this.removeIncognito(player, sendMessage);
      }

   }

   private void applyIncognito(Player player, boolean sendMessage) {
      UUID uuid = player.getUniqueId();
      if (this.incognitoPlayers.contains(uuid)) {
         if (sendMessage) {
            String msg = this.config.getString("messages.incognito-enabled", "&fIncognito - &aenabled");
            if (!msg.isEmpty()) {
               player.sendMessage(this.formatMessage(msg));
            }
         }

         this.applyIncognitoEffects(player);
      } else {
         this.incognitoPlayers.add(uuid);
         player.setMetadata("vanished", new FixedMetadataValue(this.smps, true));
         this.applyIncognitoEffects(player);
         if (sendMessage) {
            String msg = this.config.getString("messages.incognito-enabled", "&fIncognito - &aenabled");
            if (!msg.isEmpty()) {
               player.sendMessage(this.formatMessage(msg));
            }
         }

         this.saveIncognitoState(player);
         this.log(player.getName() + " is now in incognito mode");
      }
   }

   void removeIncognito(Player player, boolean sendMessage) {
      UUID uuid = player.getUniqueId();
      if (!this.incognitoPlayers.contains(uuid)) {
         if (sendMessage) {
            String msg = this.config.getString("messages.incognito-disabled", "&fIncognito - &cdisabled");
            if (!msg.isEmpty()) {
               player.sendMessage(this.formatMessage(msg));
            }
         }

      } else {
         this.incognitoPlayers.remove(uuid);
         this.setTabVisibilityOverride(player, (Boolean)null);
         this.setDebugNameTagHidden(player, false);
         if (this.squaremapIntegration != null && this.squaremapIntegration.isAvailable()) {
            this.squaremapIntegration.showPlayer(player);
         }

         if (sendMessage) {
            String msg = this.config.getString("messages.incognito-disabled", "&fIncognito - &cdisabled");
            if (!msg.isEmpty()) {
               player.sendMessage(this.formatMessage(msg));
            }
         }

         this.saveIncognitoState(player);
         if (this.isVanished(player)) {
            player.setMetadata("vanished", new FixedMetadataValue(this.smps, true));
         } else {
            player.removeMetadata("vanished", this.smps);
         }

         this.log(player.getName() + " left incognito mode");
      }
   }

   public void saveIncognitoState(Player player) {
      if (this.persistState && this.vanishDatabase != null) {
         boolean inc = this.isIncognito(player);
         this.vanishDatabase.setIncognito(player.getUniqueId(), inc).thenRun(() -> {
            String var10001 = player.getName();
            this.log("Incognito state saved for " + var10001 + ": " + inc);
         });
      }
   }

   public void saveIncognitoStateSync(Player player) {
      if (this.persistState && this.vanishDatabase != null) {
         boolean inc = this.isIncognito(player);
         this.vanishDatabase.setIncognitoSync(player.getUniqueId(), inc);
         String var10001 = player.getName();
         this.log("Incognito state saved (sync) for " + var10001 + ": " + inc);
      }
   }

   public void saveNameTagVisibilityState(Player player) {
      if (this.persistState && this.vanishDatabase != null) {
         Boolean visible = !this.isDebugNameTagHidden(player);
         this.vanishDatabase.setNameTagVisibility(player.getUniqueId(), visible).thenRun(() -> {
            String var10001 = player.getName();
            this.log("Nametag visibility saved for " + var10001 + ": " + visible);
         });
      }
   }

   public void saveNameTagVisibilityStateSync(Player player) {
      if (this.persistState && this.vanishDatabase != null) {
         Boolean visible = !this.isDebugNameTagHidden(player);
         this.vanishDatabase.setNameTagVisibilitySync(player.getUniqueId(), visible);
         String var10001 = player.getName();
         this.log("Nametag visibility saved (sync) for " + var10001 + ": " + visible);
      }
   }

   public void loadIncognitoStateSync(Player player) {
      if (this.persistState && this.vanishDatabase != null) {
         try {
            boolean wasIncognito = this.vanishDatabase.isIncognitoSync(player.getUniqueId());
            if (wasIncognito) {
               this.incognitoPlayers.add(player.getUniqueId());
               this.log("Loaded incognito state for " + player.getName() + ": true");
            }
         } catch (Exception e) {
            String var10001 = player.getName();
            this.log("Failed to load incognito state for " + var10001 + ": " + e.getMessage());
         }

      }
   }

   public void loadNameTagVisibilityStateSync(Player player) {
      if (this.persistState && this.vanishDatabase != null) {
         try {
            Boolean visible = this.vanishDatabase.getNameTagVisibilitySync(player.getUniqueId());
            if (visible != null && !visible) {
               this.debugHiddenNameTags.add(player.getUniqueId());
               this.log("Loaded nametag visibility for " + player.getName() + ": hidden");
            }
         } catch (Exception e) {
            String var10001 = player.getName();
            this.log("Failed to load nametag visibility for " + var10001 + ": " + e.getMessage());
         }

      }
   }

   public void applyIncognitoEffects(Player player) {
      player.setMetadata("vanished", new FixedMetadataValue(this.smps, true));
      this.setTabVisibilityOverride(player, false);
      this.setDebugNameTagHidden(player, true);
      if (this.squaremapIntegration != null && this.squaremapIntegration.isAvailable()) {
         this.squaremapIntegration.hidePlayer(player);
      }

      this.log("Incognito effects applied to " + player.getName());
   }

   private void runOnPrimaryScheduler(Runnable task) {
      if (this.isFolia) {
         this.smps.getServer().getGlobalRegionScheduler().execute(this.smps, task);
      } else {
         Bukkit.getScheduler().runTask(this.smps, task);
      }

   }

   public void enforceHiddenNameTag(Player player) {
      if (player.isOnline() && this.isDebugNameTagHidden(player)) {
         String name = player.getName();
         this.log("[Debug] enforceHiddenNameTag called for " + name);
         this.runOnPrimaryScheduler(() -> {
            try {
               Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tab nametag hide " + name);
               this.log("[Debug] Dispatched: /tab nametag hide " + name);
            } catch (Exception e) {
               this.log("[Debug] Failed to dispatch tab nametag hide: " + e.getMessage());
            }

         });
      }
   }

   private void flashPlayerInTab(Player player, long flashTicks) {
      if (player != null && player.isOnline()) {
         for(Player viewer : new ArrayList<Player>(Bukkit.getOnlinePlayers())) {
            this.runOnEntityScheduler(viewer, () -> this.sendTabListAddPacket(viewer, player));
         }

         long restoreDelay = Math.max(1L, flashTicks);
         this.scheduleEntityDelayedTask(player, () -> {
            if (player.isOnline()) {
               this.updateTabVisibility(player);
            }
         }, restoreDelay);
      }
   }

   private void sendTabListAddPacket(Player viewer, Player target) {
      try {
         Object craftTarget = target.getClass().getMethod("getHandle").invoke(target);
         Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
         Class actionEnum = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Action");
         Enum addPlayer = Enum.valueOf(actionEnum, "ADD_PLAYER");
         Enum updateListed = Enum.valueOf(actionEnum, "UPDATE_LISTED");
         Enum updateLatency = Enum.valueOf(actionEnum, "UPDATE_LATENCY");
         Enum updateDisplayName = Enum.valueOf(actionEnum, "UPDATE_DISPLAY_NAME");
         Enum updateGameMode = Enum.valueOf(actionEnum, "UPDATE_GAME_MODE");
         EnumSet actions = EnumSet.of(addPlayer, updateListed, updateLatency, updateDisplayName, updateGameMode);
         Constructor<?> packetCtor = packetClass.getConstructor(EnumSet.class, Collection.class);
         Object packet = packetCtor.newInstance(actions, Collections.singletonList(craftTarget));
         Field entriesField = null;

         for(Field f : packetClass.getDeclaredFields()) {
            if (List.class.isAssignableFrom(f.getType())) {
               entriesField = f;
               break;
            }
         }

         if (entriesField != null) {
            entriesField.setAccessible(true);
            List<?> oldEntries = (List)entriesField.get(packet);
            if (oldEntries != null && !oldEntries.isEmpty()) {
               Object oldEntry = oldEntries.get(0);
               Class<?> entryClass = oldEntry.getClass();
               Object newEntry = null;

               for(Constructor<?> ctor : entryClass.getDeclaredConstructors()) {
                  Class<?>[] params = ctor.getParameterTypes();
                  if (params.length >= 3 && params[0] == UUID.class) {
                     ctor.setAccessible(true);
                     Object[] args = new Object[params.length];
                     RecordComponent[] components = entryClass.getRecordComponents();
                     if (components != null && components.length == params.length) {
                        for(int i = 0; i < components.length; ++i) {
                           Object val = components[i].getAccessor().invoke(oldEntry);
                           if (params[i] == Boolean.TYPE) {
                              args[i] = true;
                           } else {
                              args[i] = val;
                           }
                        }

                        newEntry = ctor.newInstance(args);
                     }
                     break;
                  }
               }

               if (newEntry != null) {
                  entriesField.set(packet, List.of(newEntry));
               }
            }
         }

         this.sendPacketToPlayer(viewer, packet);
      } catch (Exception e) {
         String var10001 = target.getName();
         this.log("Failed to send tab list ADD packet for " + var10001 + " to " + viewer.getName() + ": " + e.getMessage());
      }

   }

   public void sendTabListAddUnlistedPacket(Player viewer, Player target) {
      try {
         Object craftTarget = target.getClass().getMethod("getHandle").invoke(target);
         Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
         Class actionEnum = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Action");
         Enum addPlayerEx = Enum.valueOf(actionEnum, "ADD_PLAYER");
         Enum updateListedEx = Enum.valueOf(actionEnum, "UPDATE_LISTED");
         Enum updateLatencyEx = Enum.valueOf(actionEnum, "UPDATE_LATENCY");
         Enum updateNameEx = Enum.valueOf(actionEnum, "UPDATE_DISPLAY_NAME");
         Enum updateGameModeEx = Enum.valueOf(actionEnum, "UPDATE_GAME_MODE");
         EnumSet actions = EnumSet.of(addPlayerEx, updateListedEx, updateLatencyEx, updateNameEx, updateGameModeEx);
         Constructor<?> packetCtor = packetClass.getConstructor(EnumSet.class, Collection.class);
         Object packet = packetCtor.newInstance(actions, Collections.singletonList(craftTarget));

         try {
            Field entriesField = null;

            for(Field f : packetClass.getDeclaredFields()) {
               if (List.class.isAssignableFrom(f.getType())) {
                  entriesField = f;
                  break;
               }
            }

            if (entriesField != null) {
               entriesField.setAccessible(true);
               List<?> entries = (List)entriesField.get(packet);
               if (entries != null) {
                  for(Object entry : entries) {
                     for(Field f : entry.getClass().getDeclaredFields()) {
                        if (f.getType() == Boolean.TYPE) {
                           f.setAccessible(true);
                           f.setBoolean(entry, false);
                        }
                     }
                  }
               }
            }
         } catch (Exception var24) {
         }

         Object craftViewer = viewer.getClass().getMethod("getHandle").invoke(viewer);

         Field connectionField;
         try {
            connectionField = craftViewer.getClass().getField("connection");
         } catch (NoSuchFieldException var23) {
            connectionField = craftViewer.getClass().getField("c");
         }

         Object connection = connectionField.get(craftViewer);

         Method sendMethod;
         try {
            sendMethod = connection.getClass().getMethod("send", Class.forName("net.minecraft.network.protocol.Packet"));
         } catch (NoSuchMethodException var22) {
            sendMethod = connection.getClass().getMethod("a", Class.forName("net.minecraft.network.protocol.Packet"));
         }

         sendMethod.invoke(connection, packet);
      } catch (Exception e) {
         this.smps.getLogger().warning("Failed to send tab list add-unlisted packet: " + e.getMessage());
      }

   }

   public void sendTabListRemovePacket(Player viewer, Player target) {
      try {
         Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket");
         Constructor<?> packetCtor = packetClass.getConstructor(List.class);
         Object packet = packetCtor.newInstance(Collections.singletonList(target.getUniqueId()));
         this.sendPacketToPlayer(viewer, packet);
      } catch (Exception e) {
         this.smps.getLogger().warning("Failed to send tab list remove packet: " + e.getMessage());
      }

   }

   public void sendTabListUnlistPacket(Player viewer, Player target, boolean listed) {
      try {
         Object craftTarget = target.getClass().getMethod("getHandle").invoke(target);
         Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
         Class actionEnum = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Action");
         Enum updateListedAction = Enum.valueOf(actionEnum, "UPDATE_LISTED");
         EnumSet actions = EnumSet.of(updateListedAction);
         Object gameProfile = null;

         try {
            gameProfile = craftTarget.getClass().getMethod("getGameProfile").invoke(craftTarget);
         } catch (NoSuchMethodException var21) {
            try {
               for(Method m : craftTarget.getClass().getMethods()) {
                  if (m.getReturnType().getName().contains("GameProfile") && m.getParameterCount() == 0) {
                     gameProfile = m.invoke(craftTarget);
                     break;
                  }
               }
            } catch (Exception var20) {
            }
         }

         Class<?> entryClass = null;

         for(Class<?> inner : packetClass.getDeclaredClasses()) {
            if (inner.getSimpleName().equals("Entry") || inner.getSimpleName().equals("b")) {
               entryClass = inner;
               break;
            }
         }

         Object entry = null;
         if (entryClass != null) {
            for(Constructor<?> ctor : entryClass.getDeclaredConstructors()) {
               Class<?>[] params = ctor.getParameterTypes();
               if (params.length >= 3 && params[0] == UUID.class) {
                  ctor.setAccessible(true);
                  Object[] args = new Object[params.length];
                  args[0] = target.getUniqueId();
                  int boolIndex = 0;

                  for(int i = 1; i < params.length; ++i) {
                     if (params[i] == Boolean.TYPE) {
                        args[i] = boolIndex == 0 ? listed : true;
                        ++boolIndex;
                     } else if (params[i] == Integer.TYPE) {
                        args[i] = 0;
                     } else if (gameProfile != null && params[i].isAssignableFrom(gameProfile.getClass())) {
                        args[i] = gameProfile;
                     } else {
                        args[i] = null;
                     }
                  }

                  entry = ctor.newInstance(args);
                  break;
               }
            }
         }

         if (entry == null) {
            this.log("[NMS] Could not construct Entry, falling back to field patching");
            Constructor<?> packetCtor = packetClass.getConstructor(EnumSet.class, Collection.class);
            Object packet = packetCtor.newInstance(actions, Collections.singletonList(craftTarget));
            this.patchAndSendPacket(packet, packetClass, listed, viewer);
            return;
         }

         Constructor<?> packetCtor = packetClass.getConstructor(EnumSet.class, Collection.class);
         Object packet = packetCtor.newInstance(actions, Collections.singletonList(craftTarget));

         for(Field f : packetClass.getDeclaredFields()) {
            if (List.class.isAssignableFrom(f.getType())) {
               f.setAccessible(true);
               f.set(packet, List.of(entry));
               break;
            }
         }

         this.sendPacketToPlayer(viewer, packet);
      } catch (Exception e) {
         String var10001 = target.getName();
         this.log("[NMS] Failed to send UPDATE_LISTED packet for " + var10001 + " to " + viewer.getName() + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
      }

   }

   private void patchAndSendPacket(Object packet, Class<?> packetClass, boolean listed, Player viewer) throws Exception {
      for(Field f : packetClass.getDeclaredFields()) {
         if (List.class.isAssignableFrom(f.getType())) {
            f.setAccessible(true);
            List<?> entries = (List)f.get(packet);
            if (entries != null) {
               for(Object entry : entries) {
                  for(Field ef : entry.getClass().getDeclaredFields()) {
                     if (ef.getType() == Boolean.TYPE) {
                        ef.setAccessible(true);
                        ef.setBoolean(entry, listed);
                     }
                  }
               }
            }
            break;
         }
      }

      this.sendPacketToPlayer(viewer, packet);
   }

   private void sendPacketToPlayer(Player viewer, Object packet) throws Exception {
      Object craftViewer = viewer.getClass().getMethod("getHandle").invoke(viewer);

      Field connectionField;
      try {
         connectionField = craftViewer.getClass().getField("connection");
      } catch (NoSuchFieldException var9) {
         connectionField = craftViewer.getClass().getField("c");
      }

      Object connection = connectionField.get(craftViewer);

      Method sendMethod;
      try {
         sendMethod = connection.getClass().getMethod("send", Class.forName("net.minecraft.network.protocol.Packet"));
      } catch (NoSuchMethodException var8) {
         sendMethod = connection.getClass().getMethod("a", Class.forName("net.minecraft.network.protocol.Packet"));
      }

      sendMethod.invoke(connection, packet);
   }

   public static class HitVanishData {
      public double damageThreshold;
      public String particle;
      public String sound;

      public HitVanishData(double damageThreshold, String particle, String sound) {
         super();
         this.damageThreshold = damageThreshold;
         this.particle = particle;
         this.sound = sound;
      }
   }

   private static class PlayerSnapshot {
      final double health;
      final int foodLevel;
      final float saturation;

      PlayerSnapshot(Player player) {
         super();
         this.health = player.getHealth();
         this.foodLevel = player.getFoodLevel();
         this.saturation = player.getSaturation();
      }

      void restore(Player player) {
         double maxHealth;
         try {
            AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
            if (attr != null) {
               maxHealth = attr.getValue();
            } else {
               maxHealth = player.getMaxHealth();
            }
         } catch (Throwable var5) {
            maxHealth = player.getMaxHealth();
         }

         player.setHealth(Math.min(this.health, maxHealth));
         player.setFoodLevel(this.foodLevel);
         player.setSaturation(this.saturation);
      }
   }
}
