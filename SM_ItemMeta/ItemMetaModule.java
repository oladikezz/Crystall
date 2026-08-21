package net.schalker.SMPS.modules.itemmeta;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.module.BaseModule;
import net.schalker.DoAPI.core.module.ModuleInfo;
import net.schalker.SMPS.modules.itemmeta.commands.ItemLoreCommand;
import net.schalker.SMPS.modules.itemmeta.commands.ItemNameCommand;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class ItemMetaModule extends BaseModule {
   private static final Pattern MINI_GRADIENT_PATTERN = Pattern.compile("(?i)<gradient:(#?[a-f0-9]{6}):(#?[a-f0-9]{6})>(.*?)</gradient>");
   private static final Pattern SHORT_GRADIENT_PATTERN = Pattern.compile("(?i)<(#?[a-f0-9]{6}):(#?[a-f0-9]{6})>(.*?)</>");
   private static final Pattern HEX_PATTERN = Pattern.compile("(?i)#([a-f0-9]{6})");
   private FileConfiguration config;
   private FileConfiguration messages;
   private final Set<String> registeredCommandNames = new HashSet<>();

   public ItemMetaModule(DoAPI plugin) {
      super(plugin, new ModuleInfo("SM_ItemMeta", "1.0.0", "MeXaNoBoP", "Изменение названия и лора предметов"));
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.config = this.plugin.getModuleManager().loadModuleConfig("SM_ItemMeta");
      this.messages = this.plugin.getModuleManager().loadModuleConfig("SM_ItemMeta", "messages.yml");
      if (this.config == null) {
         this.config = new YamlConfiguration();
      }
      if (this.messages == null) {
         this.messages = new YamlConfiguration();
      }

      if (this.isCommandEnabled("itemname")) {
         this.registerCommandSafely(new ItemNameCommand(this.plugin, this));
      }
      if (this.isCommandEnabled("itemlore")) {
         this.registerCommandSafely(new ItemLoreCommand(this.plugin, this));
      }

      this.plugin.getDebugSystem().log("ItemMeta", "Модуль ItemMeta включен");
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.unregisterAllCommands();
      this.plugin.getDebugSystem().log("ItemMeta", "Модуль ItemMeta выключен");
   }

   @Override
   public void reload() {
      super.reload();
      this.config = this.plugin.getModuleManager().loadModuleConfig("SM_ItemMeta");
      this.messages = this.plugin.getModuleManager().loadModuleConfig("SM_ItemMeta", "messages.yml");
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
      String message = this.messages != null ? this.messages.getString(key) : null;
      if (message == null) {
         message = fallback;
      }
      if (message == null || message.isEmpty()) {
         return "";
      }
      return this.plugin.applyColors(message);
   }

   public boolean isCommandEnabled(String commandKey) {
      if (this.config == null) {
         return true;
      }
      return this.config.getBoolean("commands." + commandKey + ".enabled", true);
   }

   public String formatMetaText(String input) {
      if (input == null || input.isEmpty()) {
         return "";
      }

      String processed = this.applyGradientTags(input);
      processed = this.applyHexColors(processed);
      return this.plugin.applyColors(processed);
   }

   private String applyGradientTags(String input) {
      String result = input;
      result = this.replaceGradientPattern(result, MINI_GRADIENT_PATTERN);
      result = this.replaceGradientPattern(result, SHORT_GRADIENT_PATTERN);
      return result;
   }

   private String replaceGradientPattern(String input, Pattern pattern) {
      String current = input;

      while (true) {
         Matcher matcher = pattern.matcher(current);
         if (!matcher.find()) {
            return current;
         }

         StringBuffer sb = new StringBuffer();
         do {
            String start = matcher.group(1);
            String end = matcher.group(2);
            String text = matcher.group(3);
            String replacement = this.gradientText(text, start, end);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
         } while (matcher.find());
         matcher.appendTail(sb);
         current = sb.toString();
      }
   }

   private String applyHexColors(String input) {
      Matcher matcher = HEX_PATTERN.matcher(input);
      StringBuffer sb = new StringBuffer();
      while (matcher.find()) {
         String replacement = this.toLegacyHex(matcher.group(1));
         matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
      }
      matcher.appendTail(sb);
      return sb.toString();
   }

   private String gradientText(String text, String startHex, String endHex) {
      String plain = text == null ? "" : text;
      if (plain.isEmpty()) {
         return "";
      }

      int[] codePoints = plain.codePoints().toArray();
      int[] start = this.hexToRgb(startHex);
      int[] end = this.hexToRgb(endHex);
      StringBuilder builder = new StringBuilder();

      for (int i = 0; i < codePoints.length; i++) {
         double ratio = codePoints.length == 1 ? 0.0D : (double) i / (double) (codePoints.length - 1);
         int red = this.interpolate(start[0], end[0], ratio);
         int green = this.interpolate(start[1], end[1], ratio);
         int blue = this.interpolate(start[2], end[2], ratio);
         String hex = String.format("%02X%02X%02X", red, green, blue);
         builder.append(this.toLegacyHex(hex));
         builder.append(Character.toChars(codePoints[i]));
      }

      return builder.toString();
   }

   private int interpolate(int start, int end, double ratio) {
      return (int) Math.round(start + (end - start) * ratio);
   }

   private int[] hexToRgb(String hex) {
      String normalized = this.normalizeHex(hex);
      return new int[] {
         Integer.parseInt(normalized.substring(0, 2), 16),
         Integer.parseInt(normalized.substring(2, 4), 16),
         Integer.parseInt(normalized.substring(4, 6), 16)
      };
   }

   private String normalizeHex(String hex) {
      if (hex == null) {
         return "FFFFFF";
      }
      String normalized = hex.startsWith("#") ? hex.substring(1) : hex;
      if (normalized.length() != 6) {
         return "FFFFFF";
      }
      return normalized.toUpperCase();
   }

   private String toLegacyHex(String hex) {
      String normalized = this.normalizeHex(hex);
      StringBuilder builder = new StringBuilder("§x");
      for (char c : normalized.toCharArray()) {
         builder.append('§').append(c);
      }
      return builder.toString();
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
         this.plugin.getDebugSystem().logError("ItemMeta command registration failed", e);
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
      if (target == null) {
         return null;
      }
      Field field = findField(target.getClass(), fieldName);
      if (field == null) {
         return null;
      }
      field.setAccessible(true);
      return field.get(target);
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

   private void tryUnregisterFromRegistrar(Object registrar, String key) {
      if (registrar == null || key == null) {
         return;
      }
      try {
         Method unregister = registrar.getClass().getMethod("unregister", String.class);
         unregister.invoke(registrar, key);
      } catch (NoSuchMethodException ignored) {
      } catch (Exception e) {
         this.plugin.getDebugSystem().logError("Failed to unregister command via registrar: " + key, e);
      }
   }

   private void removeFromCommandMap(String key, String originalName) {
      try {
         var commandMap = Bukkit.getCommandMap();
         Object knownCommandsObj = this.getField(commandMap, "knownCommands");
         if (!(knownCommandsObj instanceof Map<?, ?> knownCommands)) {
            return;
         }
         @SuppressWarnings("unchecked")
         Map<String, Object> commandMapEntries = (Map<String, Object>) knownCommands;
         commandMapEntries.remove(key);
         String pluginPrefix = this.plugin.getName().toLowerCase();
         commandMapEntries.remove(pluginPrefix + ":" + key);
         if (originalName != null) {
            String originalLower = originalName.toLowerCase();
            commandMapEntries.remove(originalLower);
            commandMapEntries.remove(pluginPrefix + ":" + originalLower);
         }
      } catch (Exception e) {
         this.plugin.getDebugSystem().logError("Failed to unregister command (BukkitMap): " + key, e);
      }
   }
}
