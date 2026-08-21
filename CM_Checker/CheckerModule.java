package net.schalker.SMPS.modules.checker;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Bukkit;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.module.BaseModule;
import net.schalker.DoAPI.core.module.ModuleInfo;
import net.schalker.SMPS.modules.checker.commands.CheckCommand;
import net.schalker.SMPS.modules.checker.listeners.CheckListener;
import net.schalker.SMPS.modules.checker.managers.CheckManager;

public class CheckerModule extends BaseModule {
   private CheckManager checkManager;
   private CheckListener checkListener;
   private FileConfiguration config;
   private FileConfiguration messages;
   private FileConfiguration effects;
   private final Set<String> registeredCommandNames = new HashSet<>();

   public CheckerModule(DoAPI plugin) {
      super(plugin, new ModuleInfo("SM_Checker", "2.0.0", "MeXaNoBoP", "Система проверок игроков"));
   }

   public void onEnable() {
      super.onEnable();
      this.config = this.plugin.getModuleManager().loadModuleConfig("SM_Checker");
      this.messages = this.plugin.getModuleManager().loadModuleConfig("SM_Checker", "messages.yml");
      this.effects = this.plugin.getModuleManager().loadModuleConfig("SM_Checker", "effects.yml");
      if (this.config == null) {
         this.config = new YamlConfiguration();
      }
      if (this.messages == null) {
         this.messages = new YamlConfiguration();
      }
      if (this.effects == null) {
         this.effects = new YamlConfiguration();
      }

      this.checkManager = new CheckManager(this.plugin, this);
      this.checkListener = new CheckListener(this.plugin, this.checkManager, this);
      this.plugin.getListenerManager().registerListener(this.checkListener);
      if (this.isCommandEnabled("check")) {
         this.registerCommandSafely(new CheckCommand(this.plugin, this.checkManager, this));
      }
      this.plugin.getDebugSystem().log("Checker", "Модуль Checker выключен");
   }

   public void onDisable() {
      super.onDisable();
      if (this.checkListener != null) {
         this.plugin.getListenerManager().unregisterListener(this.checkListener);
      }

      if (this.checkManager != null) {
         this.checkManager.clearAll();
      }

      this.unregisterAllCommands();

      this.plugin.getDebugSystem().log("Checker", "Модуль Checker выключен");
   }

   public void reload() {
      super.reload();
      this.config = this.plugin.getModuleManager().loadModuleConfig("SM_Checker");
      this.messages = this.plugin.getModuleManager().loadModuleConfig("SM_Checker", "messages.yml");
      this.effects = this.plugin.getModuleManager().loadModuleConfig("SM_Checker", "effects.yml");
      if (this.config == null) {
         this.config = new YamlConfiguration();
      }
      if (this.messages == null) {
         this.messages = new YamlConfiguration();
      }
      if (this.effects == null) {
         this.effects = new YamlConfiguration();
      }
   }

   public FileConfiguration getConfig() {
      return this.config;
   }

   public FileConfiguration getMessages() {
      return this.messages;
   }

   public FileConfiguration getEffectsConfig() {
      return this.effects;
   }

   public String getMessage(String key) {
      FileConfiguration messages = this.getMessages();
      if (messages == null) {
         return "&cMessage not found: " + key;
      }

      String message = messages.getString(key, "&cMessage not found: " + key);
      return this.plugin.applyColors(message);
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

   public boolean isCommandEnabled(String commandKey) {
      FileConfiguration config = this.getConfig();
      if (config == null) {
         return true;
      }
      return config.getBoolean("commands." + commandKey + ".enabled", true);
   }

   public boolean isFeatureEnabled(String featureKey, boolean defaultValue) {
      FileConfiguration config = this.getConfig();
      if (config == null) {
         return defaultValue;
      }
      return config.getBoolean("features." + featureKey, defaultValue);
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
         this.plugin.getDebugSystem().logError("Checker command registration failed", e);
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


