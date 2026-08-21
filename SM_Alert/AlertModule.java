package net.schalker.SMPS.modules.alert;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.DoAPI.core.module.BaseModule;
import net.schalker.DoAPI.core.module.ModuleInfo;
import net.schalker.SMPS.modules.alert.commands.AToggleCommand;
import net.schalker.SMPS.modules.alert.listeners.AlertListener;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class AlertModule extends BaseModule {
   private static final String CROSS_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTljZGI5YWYzOGNmNDFkYWE1M2JjOGNkYTc2NjVjNTA5NjMyZDE0ZTY3OGYwZjE5ZjI2M2Y0NmU1NDFkOGEzMCJ9fX0=";
   private static final String CHECK_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2UyYTUzMGY0MjcyNmZhN2EzMWVmYWI4ZTQzZGFkZWUxODg5MzdjZjgyNGFmODhlYThlNGM5M2E0OWM1NzI5NCJ9fX0=";
   private static final String ACTIVE_GRADIENT_START = "6EE7F9";
   private static final String ACTIVE_GRADIENT_END = "8C7CFF";
   private static final String DISABLED_GRADIENT_START = "FF6B6B";
   private static final String DISABLED_GRADIENT_END = "FFB86C";

   private static final List<Integer> COMMAND_SLOTS = List.of(
      10, 11, 12, 13, 14, 15, 16,
      19, 20, 21, 22, 23, 24, 25,
      28, 29, 30, 31, 32, 33, 34,
      37, 38, 39, 40, 41, 42, 43
   );

   private static final List<AlertCommand> DEFAULT_COMMANDS = List.of(
      new AlertCommand("tp", "/tp", List.of("tp", "teleport")),
      new AlertCommand("gm", "/gm", List.of("gm", "gamemode")),
      new AlertCommand("check", "/check", List.of("check")),
      new AlertCommand("vanish", "/v", List.of("v", "vanish")),
      new AlertCommand("spectator", "/s", List.of("s", "spectator", "spec")),
      new AlertCommand("ban", "/ban", List.of("ban", "banip", "tempban")),
      new AlertCommand("warn", "/warn", List.of("warn")),
      new AlertCommand("kick", "/kick", List.of("kick")),
      new AlertCommand("fix", "/fix", List.of("fix")),
      new AlertCommand("heal", "/heal", List.of("heal")),
      new AlertCommand("feed", "/feed", List.of("feed")),
      new AlertCommand("god", "/god", List.of("god")),
      new AlertCommand("fly", "/fly", List.of("fly")),
      new AlertCommand("speed", "/speed", List.of("speed")),
      new AlertCommand("back", "/back", List.of("back")),
      new AlertCommand("tphere", "/tphere", List.of("tphere"))
   );

   private final Set<UUID> logEnabled = ConcurrentHashMap.newKeySet();
   private final Map<UUID, Set<String>> disabledCommands = new ConcurrentHashMap<>();
   private final Map<String, String> aliasToKey = new HashMap<>();
   private final Set<String> registeredCommandNames = new HashSet<>();
   private List<AlertCommand> commands = new ArrayList<>();
   private FileConfiguration config;
   private FileConfiguration messages;
   private AlertListener listener;
   private NamespacedKey commandKey;
   private NamespacedKey toggleKey;
   private NamespacedKey toggleAllKey;

   public AlertModule(DoAPI plugin) {
      super(plugin, new ModuleInfo("SM_Alert", "1.0.0", "MeXaNoBoP", "Настройка логов админских команд через меню"));
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.config = this.plugin.getModuleManager().loadModuleConfig("SM_Alert");
      this.messages = this.plugin.getModuleManager().loadModuleConfig("SM_Alert", "messages.yml");
      if (this.config == null) {
         this.config = new YamlConfiguration();
      }
      if (this.messages == null) {
         this.messages = new YamlConfiguration();
      }

      this.commandKey = new NamespacedKey(this.plugin, "alert-command");
      this.toggleKey = new NamespacedKey(this.plugin, "alert-toggle");
      this.toggleAllKey = new NamespacedKey(this.plugin, "alert-toggle-all");
      this.loadCommandsFromConfig();

      this.listener = new AlertListener(this.plugin, this);
      this.plugin.getListenerManager().registerListener(this.listener);

      if (this.isCommandEnabled("atoggle")) {
         this.registerCommandSafely(new AToggleCommand(this.plugin, this));
      }

      this.plugin.getDebugSystem().log("Alert", "Модуль Alert включен");
   }

   @Override
   public void onDisable() {
      super.onDisable();
      if (this.listener != null) {
         this.plugin.getListenerManager().unregisterListener(this.listener);
      }
      this.logEnabled.clear();
      this.disabledCommands.clear();
      this.unregisterAllCommands();
      this.plugin.getDebugSystem().log("Alert", "Модуль Alert выключен");
   }

   @Override
   public void reload() {
      super.reload();
      this.config = this.plugin.getModuleManager().loadModuleConfig("SM_Alert");
      this.messages = this.plugin.getModuleManager().loadModuleConfig("SM_Alert", "messages.yml");
      if (this.config == null) {
         this.config = new YamlConfiguration();
      }
      if (this.messages == null) {
         this.messages = new YamlConfiguration();
      }
      this.loadCommandsFromConfig();
   }

   public FileConfiguration getConfig() {
      return this.config;
   }

   public String getMessage(String key, String fallback) {
      FileConfiguration messages = this.messages;
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

   public boolean isLogEnabled(UUID playerId) {
      return playerId != null && this.logEnabled.contains(playerId);
   }

   public void toggleLog(UUID playerId) {
      if (playerId == null) {
         return;
      }
      if (this.logEnabled.contains(playerId)) {
         this.logEnabled.remove(playerId);
      } else {
         this.logEnabled.add(playerId);
      }
   }

   public void clearPlayerState(UUID playerId) {
      if (playerId == null) {
         return;
      }
      this.logEnabled.remove(playerId);
      this.disabledCommands.remove(playerId);
   }

   public boolean isCommandLogEnabled(UUID playerId, String key) {
      if (playerId == null || key == null) {
         return false;
      }
      Set<String> disabled = this.disabledCommands.get(playerId);
      return disabled == null || !disabled.contains(key);
   }

   public void toggleCommand(UUID playerId, String key) {
      if (playerId == null || key == null) {
         return;
      }
      Set<String> disabled = this.disabledCommands.computeIfAbsent(playerId, id -> ConcurrentHashMap.newKeySet());
      if (disabled.contains(key)) {
         disabled.remove(key);
         if (disabled.isEmpty()) {
            this.disabledCommands.remove(playerId);
         }
      } else {
         disabled.add(key);
      }
   }

   public String resolveCommandKey(String label) {
      if (label == null) {
         return null;
      }
      return this.aliasToKey.get(label.toLowerCase());
   }

   public List<AlertCommand> getCommands() {
      return this.commands;
   }

   public NamespacedKey getCommandKey() {
      return this.commandKey;
   }

   public NamespacedKey getToggleKey() {
      return this.toggleKey;
   }

   public NamespacedKey getToggleAllKey() {
      return this.toggleAllKey;
   }

   public String getLogFormat() {
      FileConfiguration config = this.getConfig();
      String format = config != null ? config.getString("settings.log-format") : null;
      if (format == null || format.isEmpty()) {
         format = "&7[&dALERT&7] &e{sender}&7: &f{command}";
      }
      return format;
   }

   public String formatLog(String senderName, String commandLine) {
      String format = this.getLogFormat();
      String safeSender = senderName != null ? senderName : "";
      String safeCommand = commandLine != null ? commandLine : "";
      return this.plugin.applyColors(format
         .replace("{sender}", safeSender)
         .replace("{command}", safeCommand));
   }

   public void openMenu(Player player) {
      if (player == null) {
         return;
      }
      String title = this.getMenuTitle();
      Inventory inventory = Bukkit.createInventory(new AlertMenuHolder(player.getUniqueId()), 54, title);
      this.fillMenu(inventory, player);
      player.openInventory(inventory);
   }

   public void refreshMenu(Inventory inventory, Player player) {
      if (inventory == null || player == null) {
         return;
      }
      inventory.clear();
      this.fillMenu(inventory, player);
   }

   private void buildAliasMap() {
      this.aliasToKey.clear();
      for (AlertCommand command : this.commands) {
         String key = command.key().toLowerCase();
         this.aliasToKey.put(key, command.key());
         for (String alias : command.aliases()) {
            if (alias == null || alias.isEmpty()) {
               continue;
            }
            this.aliasToKey.put(alias.toLowerCase(), command.key());
         }
      }
   }

   private String getMenuTitle() {
      FileConfiguration config = this.getConfig();
      String title = config != null ? config.getString("settings.menu-title") : null;
      if (title == null || title.isEmpty()) {
         title = "&d&lALERT &7• &fЛоги админ-команд";
      }
      return this.plugin.applyColors(title);
   }

   private void fillMenu(Inventory inventory, Player player) {
      if (player != null && !player.hasPermission("smalert.atoggle")) {
         this.clearPlayerState(player.getUniqueId());
      }
      this.fillBorders(inventory);

      int index = 0;
      for (AlertCommand command : this.commands) {
         if (index >= COMMAND_SLOTS.size()) {
            break;
         }
         ItemStack item = this.createCommandItem(player, command);
         inventory.setItem(COMMAND_SLOTS.get(index), item);
         index++;
      }

      inventory.setItem(50, this.createToggleItem(player));
      inventory.setItem(48, this.createToggleAllItem(player));
   }

   private void fillBorders(Inventory inventory) {
      ItemStack glass = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
      ItemMeta meta = glass.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(" ");
         glass.setItemMeta(meta);
      }
      int size = inventory.getSize();
      for (int i = 0; i < size; i++) {
         int row = i / 9;
         int col = i % 9;
         if (row == 0 || row == 5 || col == 0 || col == 8) {
            inventory.setItem(i, glass);
         }
      }
   }

   private ItemStack createCommandItem(Player player, AlertCommand command) {
      boolean hasPermission = player != null && player.hasPermission("smalert.atoggle");
      boolean enabled = hasPermission && this.isCommandLogEnabled(player.getUniqueId(), command.key());
      ItemStack head = this.createStatusHead(enabled);
      ItemMeta meta = head.getItemMeta();
      if (meta != null) {
         String titleText = enabled ? "Состояние включено" : "Состояние выключено";
         String titleGradient = enabled
            ? this.gradientText(titleText, ACTIVE_GRADIENT_START, ACTIVE_GRADIENT_END)
            : this.gradientText(titleText, DISABLED_GRADIENT_START, DISABLED_GRADIENT_END);
         meta.setDisplayName(this.plugin.applyColors("&l" + titleGradient));
         List<String> lore = new ArrayList<>();
         lore.add(this.plugin.applyColors("&7Команда: &f" + command.label()));
         lore.add(this.plugin.applyColors(enabled ? "&aНажмите, чтобы отключить" : "&cНажмите, чтобы включить"));
         meta.setLore(lore);
         PersistentDataContainer container = meta.getPersistentDataContainer();
         container.set(this.commandKey, PersistentDataType.STRING, command.key());
         head.setItemMeta(meta);
      }
      return head;
   }

   private ItemStack createToggleItem(Player player) {
      boolean hasPermission = player != null && player.hasPermission("smalert.atoggle");
      boolean enabled = hasPermission && this.isLogEnabled(player.getUniqueId());
      Material material = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         String titleText = enabled ? "Логи включены" : "Логи выключены";
         String titleGradient = enabled
            ? this.gradientText(titleText, ACTIVE_GRADIENT_START, ACTIVE_GRADIENT_END)
            : this.gradientText(titleText, DISABLED_GRADIENT_START, DISABLED_GRADIENT_END);
         meta.setDisplayName(this.plugin.applyColors("&l" + titleGradient));
         List<String> lore = new ArrayList<>();
         lore.add(this.plugin.applyColors("&7Нажмите, чтобы переключить"));
         meta.setLore(lore);
         meta.getPersistentDataContainer().set(this.toggleKey, PersistentDataType.BYTE, (byte) 1);
         item.setItemMeta(meta);
      }
      return item;
   }

   private ItemStack createToggleAllItem(Player player) {
      boolean hasPermission = player != null && player.hasPermission("smalert.atoggle");
      boolean allEnabled = hasPermission && this.areAllCommandsEnabled(player.getUniqueId());
      Material material = allEnabled ? Material.LIME_DYE : Material.GRAY_DYE;
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         String titleText = allEnabled ? "Все алерты включены" : "Все алерты выключены";
         String titleGradient = allEnabled
            ? this.gradientText(titleText, ACTIVE_GRADIENT_START, ACTIVE_GRADIENT_END)
            : this.gradientText(titleText, DISABLED_GRADIENT_START, DISABLED_GRADIENT_END);
         meta.setDisplayName(this.plugin.applyColors("&l" + titleGradient));
         List<String> lore = new ArrayList<>();
         lore.add(this.plugin.applyColors(allEnabled ? "&cНажмите, чтобы выключить все" : "&aНажмите, чтобы включить все"));
         meta.setLore(lore);
         meta.getPersistentDataContainer().set(this.toggleAllKey, PersistentDataType.BYTE, (byte) 1);
         item.setItemMeta(meta);
      }
      return item;
   }

   private ItemStack createStatusHead(boolean enabled) {
      ItemStack head = new ItemStack(Material.PLAYER_HEAD);
      SkullMeta meta = (SkullMeta) head.getItemMeta();
      if (meta == null) {
         return head;
      }
      PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), "alert");
      String texture = enabled ? CHECK_TEXTURE : CROSS_TEXTURE;
      profile.setProperty(new ProfileProperty("textures", texture));
      meta.setPlayerProfile(profile);
      head.setItemMeta(meta);
      return head;
   }

   private String gradientText(String text, String startHex, String endHex) {
      if (text == null || text.isEmpty()) {
         return "";
      }
      String start = normalizeHex(startHex);
      String end = normalizeHex(endHex);
      if (start == null || end == null) {
         return text;
      }
      int startColor = Integer.parseInt(start, 16);
      int endColor = Integer.parseInt(end, 16);
      int sr = (startColor >> 16) & 0xFF;
      int sg = (startColor >> 8) & 0xFF;
      int sb = startColor & 0xFF;
      int er = (endColor >> 16) & 0xFF;
      int eg = (endColor >> 8) & 0xFF;
      int eb = endColor & 0xFF;

      int length = text.length();
      if (length == 1) {
         return "&#" + start + text;
      }

      StringBuilder builder = new StringBuilder(length * 10);
      for (int i = 0; i < length; i++) {
         float ratio = (float) i / (float) (length - 1);
         int r = Math.round(sr + (er - sr) * ratio);
         int g = Math.round(sg + (eg - sg) * ratio);
         int b = Math.round(sb + (eb - sb) * ratio);
         builder.append("&#");
         builder.append(String.format("%02X%02X%02X", r, g, b));
         builder.append(text.charAt(i));
      }
      return builder.toString();
   }

   private String normalizeHex(String hex) {
      if (hex == null) {
         return null;
      }
      String value = hex.trim();
      if (value.startsWith("#")) {
         value = value.substring(1);
      }
      if (value.startsWith("&")) {
         value = value.substring(1);
      }
      if (value.length() != 6) {
         return null;
      }
      return value.toUpperCase();
   }

   public void sendCommandLog(Player sender, String commandLine, String commandKey) {
      if (sender == null) {
         return;
      }
      String message = this.formatLog(sender.getName(), commandLine);
      for (Player player : this.plugin.getServer().getOnlinePlayers()) {
         if (!player.hasPermission("smalert.atoggle")) {
            this.clearPlayerState(player.getUniqueId());
            continue;
         }
         if (!this.isLogEnabled(player.getUniqueId())) {
            continue;
         }
         if (!this.isCommandLogEnabled(player.getUniqueId(), commandKey)) {
            continue;
         }
         String taskName = "alert-log-" + player.getUniqueId() + "-" + UUID.randomUUID();
         this.plugin.getSchedulerManager().runEntityTask(player, taskName, () -> {
            if (player.isOnline()) {
               player.sendMessage(message);
            }
         });
      }
   }

   public boolean areAllCommandsEnabled(UUID playerId) {
      if (playerId == null) {
         return false;
      }
      Set<String> disabled = this.disabledCommands.get(playerId);
      return disabled == null || disabled.isEmpty();
   }

   public void toggleAllCommands(UUID playerId) {
      if (playerId == null) {
         return;
      }
      if (this.areAllCommandsEnabled(playerId)) {
         Set<String> disabled = ConcurrentHashMap.newKeySet();
         for (AlertCommand command : this.commands) {
            disabled.add(command.key());
         }
         this.disabledCommands.put(playerId, disabled);
      } else {
         this.disabledCommands.remove(playerId);
      }
   }

   private void loadCommandsFromConfig() {
      List<String> rawList = this.config != null ? this.config.getStringList("tracked-commands") : null;
      if (rawList == null || rawList.isEmpty()) {
         this.commands = new ArrayList<>(DEFAULT_COMMANDS);
         this.buildAliasMap();
         return;
      }

      List<AlertCommand> loaded = new ArrayList<>();
      Set<String> seenKeys = new HashSet<>();
      for (String entry : rawList) {
         if (entry == null) {
            continue;
         }
         String trimmed = entry.trim();
         if (trimmed.isEmpty()) {
            continue;
         }
         String[] parts = trimmed.split("\\|");
         List<String> aliases = new ArrayList<>();
         for (String part : parts) {
            String alias = this.normalizeAlias(part);
            if (alias != null && !alias.isEmpty()) {
               aliases.add(alias);
            }
         }
         if (aliases.isEmpty()) {
            continue;
         }
         String key = aliases.get(0).toLowerCase();
         if (!seenKeys.add(key)) {
            continue;
         }
         String label = "/" + aliases.get(0);
         loaded.add(new AlertCommand(key, label, aliases));
      }

      if (loaded.isEmpty()) {
         this.commands = new ArrayList<>(DEFAULT_COMMANDS);
      } else {
         this.commands = loaded;
      }
      this.buildAliasMap();
   }

   private String normalizeAlias(String alias) {
      if (alias == null) {
         return null;
      }
      String trimmed = alias.trim();
      if (trimmed.startsWith("/")) {
         trimmed = trimmed.substring(1);
      }
      if (trimmed.isEmpty()) {
         return null;
      }
      return trimmed;
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
         this.plugin.getDebugSystem().logError("Alert command registration failed", e);
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

   public record AlertCommand(String key, String label, List<String> aliases) {
   }
}
