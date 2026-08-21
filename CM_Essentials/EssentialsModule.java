package net.schalker.SMPS.modules.essentials;

import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.module.BaseModule;
import net.schalker.DoAPI.core.module.ModuleInfo;
import net.schalker.SMPS.modules.essentials.commands.BackCommand;
import net.schalker.SMPS.modules.essentials.commands.FlyCommand;
import net.schalker.SMPS.modules.essentials.commands.FeedCommand;
import net.schalker.SMPS.modules.essentials.commands.GodCommand;
import net.schalker.SMPS.modules.essentials.commands.FixCommand;
import net.schalker.SMPS.modules.essentials.commands.FreezeCommand;
import net.schalker.SMPS.modules.essentials.commands.GameModeCommand;
import net.schalker.SMPS.modules.essentials.commands.HealCommand;
import net.schalker.SMPS.modules.essentials.commands.ExtCommand;
import net.schalker.SMPS.modules.essentials.commands.SpeedCommand;
import net.schalker.SMPS.modules.essentials.commands.TpHereCommand;
import net.schalker.SMPS.modules.essentials.listeners.BackListener;
import net.schalker.SMPS.modules.essentials.listeners.FreezeListener;
import net.schalker.SMPS.modules.essentials.listeners.GodModeListener;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EssentialsModule extends BaseModule {
   private FileConfiguration config;
   private FileConfiguration messages;
   private BackListener backListener;
   private GodModeListener godModeListener;
   private FreezeListener freezeListener;
   private final Set<UUID> godPlayers = ConcurrentHashMap.newKeySet();
   private final Set<UUID> flyPlayers = ConcurrentHashMap.newKeySet();
   private final Set<UUID> frozenPlayers = ConcurrentHashMap.newKeySet();
   private final Set<UUID> adminLogPlayers = ConcurrentHashMap.newKeySet();
   private final Set<String> registeredCommandNames = new HashSet<>();

   public EssentialsModule(DoAPI plugin) {
      super(plugin, new ModuleInfo("SM_Essentials", "2.0.0", "MeXaNoBoP", "Базовые команды сервера (gm, heal, feed и т.д.)"));
   }

   public void onEnable() {
      super.onEnable();
      this.config = this.plugin.getModuleManager().loadModuleConfig("SM_Essentials");
      this.messages = this.plugin.getModuleManager().loadModuleConfig("SM_Essentials", "messages.yml");
      if (this.config == null) {
         this.config = new YamlConfiguration();
      }
      if (this.messages == null) {
         this.messages = new YamlConfiguration();
      }

      this.backListener = new BackListener(this.plugin);
      this.plugin.getListenerManager().registerListener(this.backListener);
      this.godModeListener = new GodModeListener(this.plugin, this);
      this.plugin.getListenerManager().registerListener(this.godModeListener);
      this.freezeListener = new FreezeListener(this.plugin, this);
      this.plugin.getListenerManager().registerListener(this.freezeListener);

      if (this.isCommandEnabled("tphere")) {
         this.registerCommandSafely(new TpHereCommand(this.plugin, this));
      }
      if (this.isCommandEnabled("gm")) {
         this.registerCommandSafely(new GameModeCommand(this.plugin, this));
      }
      if (this.isCommandEnabled("heal")) {
         this.registerCommandSafely(new HealCommand(this.plugin, this));
      }
      if (this.isCommandEnabled("feed")) {
         this.registerCommandSafely(new FeedCommand(this.plugin, this));
      }
      if (this.isCommandEnabled("fix")) {
         this.registerCommandSafely(new FixCommand(this.plugin, this));
      }
      if (this.isCommandEnabled("back")) {
         this.registerCommandSafely(new BackCommand(this.plugin, this));
      }
      if (this.isCommandEnabled("god")) {
         this.registerCommandSafely(new GodCommand(this.plugin, this));
      }
      if (this.isCommandEnabled("fly")) {
         this.registerCommandSafely(new FlyCommand(this.plugin, this));
      }
      if (this.isCommandEnabled("speed")) {
         this.registerCommandSafely(new SpeedCommand(this.plugin, this));
      }
      if (this.isCommandEnabled("ext")) {
         this.registerCommandSafely(new ExtCommand(this.plugin, this));
      }
      if (this.isCommandEnabled("freeze")) {
         this.registerCommandSafely(new FreezeCommand(this.plugin, this));
      }
      this.plugin.getDebugSystem().log("Essentials", "Модуль Essentials включен");
   }

   public void onDisable() {
      super.onDisable();
      if (this.backListener != null) {
         this.plugin.getListenerManager().unregisterListener(this.backListener);
      }
      if (this.godModeListener != null) {
         this.plugin.getListenerManager().unregisterListener(this.godModeListener);
      }
      if (this.freezeListener != null) {
         this.plugin.getListenerManager().unregisterListener(this.freezeListener);
      }
      this.clearGodMode();
      this.clearFlyMode();
      this.clearFrozen();
      this.clearAdminLogs();
      this.unregisterAllCommands();
      this.plugin.getDebugSystem().log("Essentials", "Модуль Essentials выключен");
   }

   public void reload() {
      super.reload();
      this.config = this.plugin.getModuleManager().loadModuleConfig("SM_Essentials");
      this.messages = this.plugin.getModuleManager().loadModuleConfig("SM_Essentials", "messages.yml");
      if (this.config == null) {
         this.config = new YamlConfiguration();
      }
      if (this.messages == null) {
         this.messages = new YamlConfiguration();
      }
   }

   public FileConfiguration getConfig() {
      return this.config;
   }

   public FileConfiguration getMessages() {
      return this.messages;
   }

   public String getMessage(String key, String fallback) {
      FileConfiguration messages = this.getMessages();
      String message = messages != null ? messages.getString(key) : null;
      if (message == null) {
         message = fallback;
      }
      if (message == null || message.isEmpty()) {
         return "";
      }
      return this.plugin.applyColors(message);
   }

   public boolean isCommandEnabled(String commandKey) {
      FileConfiguration config = this.getConfig();
      if (config == null) {
         return true;
      }
      return config.getBoolean("commands." + commandKey + ".enabled", true);
   }

   public boolean isAdminHighlightEnabled() {
      return false;
   }

   public String formatAdminLog(String senderName, String commandLine) {
      FileConfiguration config = this.getConfig();
      String format = config != null ? config.getString("settings.admin-log-format") : null;
      if (format == null || format.isEmpty()) {
         format = "&7[&6CMD&7] &e{sender} &7> &f{command}";
      }
      String safeSender = senderName != null ? senderName : "";
      String safeCommand = commandLine != null ? commandLine : "";
      return this.plugin.applyColors(format
         .replace("{sender}", safeSender)
         .replace("{command}", safeCommand));
   }

   public boolean toggleGod(UUID playerId) {
      if (playerId == null) {
         return false;
      }
      if (this.godPlayers.contains(playerId)) {
         this.godPlayers.remove(playerId);
         return false;
      }
      this.godPlayers.add(playerId);
      return true;
   }

   public boolean isGod(UUID playerId) {
      return playerId != null && this.godPlayers.contains(playerId);
   }

   public void clearGodMode() {
      this.godPlayers.clear();
   }

   public boolean toggleFly(UUID playerId) {
      if (playerId == null) {
         return false;
      }
      if (this.flyPlayers.contains(playerId)) {
         this.flyPlayers.remove(playerId);
         return false;
      }
      this.flyPlayers.add(playerId);
      return true;
   }

   public boolean isFlyEnabled(UUID playerId) {
      return playerId != null && this.flyPlayers.contains(playerId);
   }

   public void applyFlyState(Player player, boolean enabled) {
      if (player == null) {
         return;
      }
      if (enabled) {
         player.setAllowFlight(true);
         player.setFlying(true);
         return;
      }
      if (this.isCreativeOrSpectator(player)) {
         player.setAllowFlight(true);
         return;
      }
      player.setFlying(false);
      player.setAllowFlight(false);
   }

   public void clearFlyMode() {
      for (UUID playerId : this.flyPlayers) {
         Player player = Bukkit.getPlayer(playerId);
         if (player == null) {
            continue;
         }
         this.plugin.getSchedulerManager().runEntityTask(player, "fly-disable-" + player.getUniqueId(), () -> {
            this.applyFlyState(player, false);
         });
      }
      this.flyPlayers.clear();
   }

   public boolean toggleFreeze(UUID playerId) {
      if (playerId == null) {
         return false;
      }
      if (this.frozenPlayers.contains(playerId)) {
         this.frozenPlayers.remove(playerId);
         return false;
      }
      this.frozenPlayers.add(playerId);
      return true;
   }

   public boolean isFrozen(UUID playerId) {
      return playerId != null && this.frozenPlayers.contains(playerId);
   }

   public void clearFrozen() {
      this.frozenPlayers.clear();
   }

   public boolean toggleAdminLog(UUID playerId) {
      if (playerId == null) {
         return false;
      }
      if (this.adminLogPlayers.contains(playerId)) {
         this.adminLogPlayers.remove(playerId);
         return false;
      }
      this.adminLogPlayers.add(playerId);
      return true;
   }

   public boolean isAdminLogEnabled(UUID playerId) {
      return playerId != null && this.adminLogPlayers.contains(playerId);
   }

   public void clearAdminLogs() {
      this.adminLogPlayers.clear();
   }

   private boolean isCreativeOrSpectator(Player player) {
      GameMode mode = player.getGameMode();
      return mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
   }

   private void registerCommandSafely(net.schalker.DoAPI.core.command.ModuleCommand command) {
      boolean hackEnabled = false;
      try {
         hackEnabled = this.plugin.getPluginReloader().setLifecycleContext();
         this.unregisterCommandName(command.getName());
         this.unregisterAliases(command.getAliases());
         this.plugin.getCommandManager().registerModuleCommand(command);
         this.trackCommand(command.getName(), command.getAliases());
      } catch (Exception e) {
         this.plugin.getDebugSystem().logError("Essentials command registration failed", e);
      } finally {
         if (hackEnabled) {
            this.plugin.getPluginReloader().clearLifecycleContext();
         }
      }
   }

   private void trackCommand(String name, Collection<String> aliases) {
      if (name != null) {
         this.registeredCommandNames.add(name.toLowerCase());
      }
      if (aliases != null) {
         for (String alias : aliases) {
            if (alias != null) {
               this.registeredCommandNames.add(alias.toLowerCase());
            }
         }
      }
   }

   private void unregisterAliases(Collection<String> aliases) {
      if (aliases == null) {
         return;
      }
      for (String alias : aliases) {
         this.unregisterCommandName(alias);
      }
   }

   private void unregisterAllCommands() {
      for (String name : this.registeredCommandNames) {
         this.unregisterCommandName(name);
      }
      this.registeredCommandNames.clear();
   }

   private void unregisterCommandName(String name) {
      if (name == null) {
         return;
      }
      String key = name.toLowerCase();

      try {
         var commandManager = this.plugin.getCommandManager();
         this.removeFromSet(commandManager, "registeredCommands", key);
         this.removeFromMap(commandManager, "moduleCommands", key);
         Object registrar = this.getField(commandManager, "commandsRegistrar");
         this.tryUnregisterFromRegistrar(registrar, key);
      } catch (Exception e) {
         this.plugin.getDebugSystem().logError("Failed to unregister command (manager): " + name, e);
      }
      this.removeFromCommandMap(key, name);
   }

   private void removeFromSet(Object target, String fieldName, String value) throws Exception {
      Object fieldValue = this.getField(target, fieldName);
      if (fieldValue instanceof Set<?> set) {
         @SuppressWarnings("unchecked")
         Set<String> stringSet = (Set<String>) set;
         stringSet.remove(value);
      }
   }

   private void removeFromMap(Object target, String fieldName, String key) throws Exception {
      Object fieldValue = this.getField(target, fieldName);
      if (fieldValue instanceof Map<?, ?> map) {
         @SuppressWarnings("unchecked")
         Map<String, Object> stringMap = (Map<String, Object>) map;
         stringMap.remove(key);
      }
   }

   private Object getField(Object target, String fieldName) throws Exception {
      Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      return field.get(target);
   }

   private void tryUnregisterFromRegistrar(Object registrar, String name) {
      if (registrar == null || name == null) {
         return;
      }
      Method[] methods = registrar.getClass().getMethods();
      for (Method method : methods) {
         if (method.getParameterCount() != 1) {
            continue;
         }
         if (!method.getParameterTypes()[0].equals(String.class)) {
            continue;
         }
         String methodName = method.getName().toLowerCase();
         if (methodName.contains("unregister") || methodName.equals("remove") || methodName.equals("removecommand")) {
            try {
               method.invoke(registrar, name);
               return;
            } catch (Exception ignored) {
               // keep trying other methods
            }
         }
      }
   }

   private void removeFromCommandMap(String key, String name) {
      try {
         Object commandMap = Bukkit.getServer().getCommandMap();
         if (commandMap == null) {
            return;
         }
         String pluginName = this.plugin.getName();
         String namespaced = pluginName == null ? null : pluginName.toLowerCase() + ":" + key;
         if (this.tryRemoveFromKnownCommands(commandMap, key, namespaced)) {
            return;
         }
         this.tryRemoveFromAnyMap(commandMap, key, namespaced);
      } catch (Exception ignored) {
         // Ignore: command map internals vary across server versions.
      }
   }

   private boolean tryRemoveFromKnownCommands(Object commandMap, String key, String namespaced) {
      Field field = this.findField(commandMap.getClass(), "knownCommands");
      if (field == null) {
         return false;
      }
      try {
         field.setAccessible(true);
         Object value = field.get(commandMap);
         if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> stringMap = (Map<String, Object>) map;
            boolean removed = stringMap.remove(key) != null;
            if (namespaced != null) {
               removed |= stringMap.remove(namespaced) != null;
            }
            return removed;
         }
      } catch (Exception ignored) {
         // keep silent
      }
      return false;
   }

   private void tryRemoveFromAnyMap(Object commandMap, String key, String namespaced) {
      for (Class<?> type = commandMap.getClass(); type != null; type = type.getSuperclass()) {
         for (Field field : type.getDeclaredFields()) {
            if (!Map.class.isAssignableFrom(field.getType())) {
               continue;
            }
            try {
               field.setAccessible(true);
               Object value = field.get(commandMap);
               if (value instanceof Map<?, ?> map) {
                  @SuppressWarnings("unchecked")
                  Map<String, Object> stringMap = (Map<String, Object>) map;
                  boolean removed = false;
                  if (stringMap.containsKey(key)) {
                     stringMap.remove(key);
                     removed = true;
                  }
                  if (namespaced != null && stringMap.containsKey(namespaced)) {
                     stringMap.remove(namespaced);
                     removed = true;
                  }
                  if (removed) {
                     return;
                  }
               }
            } catch (Exception ignored) {
               // keep scanning other fields
            }
         }
      }
   }

   private Field findField(Class<?> type, String fieldName) {
      for (Class<?> current = type; current != null; current = current.getSuperclass()) {
         try {
            return current.getDeclaredField(fieldName);
         } catch (NoSuchFieldException ignored) {
            // keep searching
         }
      }
      return null;
   }
}
