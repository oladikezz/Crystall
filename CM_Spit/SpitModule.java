package net.schalker.SMPS.modules.spit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.DoAPI.core.module.BaseModule;
import net.schalker.DoAPI.core.module.ModuleInfo;
import net.schalker.SMPS.modules.spit.commands.SpitCommand;
import net.schalker.SMPS.modules.spit.listeners.SpitListener;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class SpitModule extends BaseModule {
   private FileConfiguration config;
   private FileConfiguration messages;
   private SpitListener listener;
   private NamespacedKey spitKey;
   private final Set<String> registeredCommandNames = new HashSet<>();

   public SpitModule(DoAPI plugin) {
      super(plugin, new ModuleInfo("SM_Spit", "1.0.0", "MeXaNoBoP", "Команда /spit для плевка ламы без урона"));
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.loadConfigs();
      this.spitKey = new NamespacedKey(this.plugin, "spit-no-damage");

      this.listener = new SpitListener(this);
      this.plugin.getListenerManager().registerListener(this.listener);

      if (this.isCommandEnabled("spit")) {
         this.registerCommandSafely(new SpitCommand(this.plugin, this));
      }

      this.plugin.getDebugSystem().log("Spit", "Модуль Spit включен");
   }

   @Override
   public void onDisable() {
      super.onDisable();
      if (this.listener != null) {
         this.plugin.getListenerManager().unregisterListener(this.listener);
      }
      this.unregisterAllCommands();
      this.plugin.getDebugSystem().log("Spit", "Модуль Spit выключен");
   }

   @Override
   public void reload() {
      super.reload();
      this.loadConfigs();
   }

   private void loadConfigs() {
      this.config = this.plugin.getModuleManager().loadModuleConfig("SM_Spit");
      this.messages = this.plugin.getModuleManager().loadModuleConfig("SM_Spit", "messages.yml");
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

   public FileConfiguration getConfig() {
      return this.config;
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

   public NamespacedKey getSpitKey() {
      return this.spitKey;
   }

   private void registerCommandSafely(ModuleCommand command) {
      boolean hackEnabled = false;
      try {
         hackEnabled = this.plugin.getPluginReloader().setLifecycleContext();
         this.unregisterCommandName(command.getName());
         this.unregisterAliases(command.getAliases());
         this.plugin.getCommandManager().registerModuleCommand(command);
         this.trackCommand(command.getName(), command.getAliases());
      } catch (Exception e) {
         this.plugin.getDebugSystem().logError("Spit command registration failed", e);
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
      this.removeFromCommandMap(key);
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
            }
         }
      }
   }

   private void removeFromCommandMap(String key) {
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
            }
         }
      }
   }

   private Field findField(Class<?> type, String fieldName) {
      for (Class<?> current = type; current != null; current = current.getSuperclass()) {
         try {
            return current.getDeclaredField(fieldName);
         } catch (NoSuchFieldException ignored) {
         }
      }
      return null;
   }
}
