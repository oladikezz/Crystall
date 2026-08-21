package net.schalker.SMPS.modules.flags;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.DoAPI.core.module.BaseModule;
import net.schalker.DoAPI.core.module.ModuleInfo;
import net.schalker.SMPS.modules.flags.commands.FlagsCommand;
import net.schalker.SMPS.modules.flags.listeners.FlagsListener;
import net.schalker.SMPS.modules.flags.managers.AutoBanManager;
import net.schalker.SMPS.modules.flags.managers.FlagsManager;
import net.schalker.SMPS.modules.flags.managers.LowPlaytimeReminderManager;
import net.schalker.SMPS.modules.flags.managers.PlaytimeSensitivityManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class FlagsModule extends BaseModule {
   private static final int[] FLAG_SLOTS = {
      10, 11, 12, 13, 14, 15, 16,
      19, 20, 21, 22, 23, 24, 25,
      28, 29, 30, 31, 32, 33, 34,
      37, 38, 39, 40, 41, 42, 43
   };
   private static final int ITEMS_PER_PAGE = FLAG_SLOTS.length;
   private static final int HISTORY_ITEMS_PER_PAGE = 21;

   private FileConfiguration config;
   private FileConfiguration messages;
   private FileConfiguration gui;
   private FlagsDatabase database;
   private FlagsManager manager;
   private FlagsListener listener;
   private NamespacedKey actionKey;
   private NamespacedKey flagTypeKey;
   private NamespacedKey pageKey;
   private NamespacedKey historyWorldKey;
   private NamespacedKey historyXKey;
   private NamespacedKey historyYKey;
   private NamespacedKey historyZKey;
   private LowPlaytimeReminderManager lowPlaytimeReminderManager;
   private boolean databaseReady;
   private final Set<String> registeredCommandNames = new HashSet<>();

   public FlagsModule(DoAPI plugin) {
      super(plugin, loadModuleInfo());
   }

   private static ModuleInfo loadModuleInfo() {
      try (InputStream stream = FlagsModule.class.getClassLoader().getResourceAsStream("module.yml")) {
         if (stream != null) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(
               new InputStreamReader(stream, StandardCharsets.UTF_8));
            return new ModuleInfo(
               yml.getString("name", "SM_Flags"),
               yml.getString("version", "1.0.0"),
               yml.getString("author", "Unknown"),
               yml.getString("description", "Система отслеживания флагов")
            );
         }
      } catch (Exception ignored) {}
      return new ModuleInfo("SM_Flags", "1.0.0", "Unknown", "Система отслеживания флагов");
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.loadConfigs();
      
      this.actionKey = new NamespacedKey(this.plugin, "flags-action");
      this.flagTypeKey = new NamespacedKey(this.plugin, "flags-type");
      this.pageKey = new NamespacedKey(this.plugin, "flags-page");
      this.historyWorldKey = new NamespacedKey(this.plugin, "flags-history-world");
      this.historyXKey = new NamespacedKey(this.plugin, "flags-history-x");
      this.historyYKey = new NamespacedKey(this.plugin, "flags-history-y");
      this.historyZKey = new NamespacedKey(this.plugin, "flags-history-z");

      this.closeOpenMenus();

      this.database = new FlagsDatabase(this.plugin);
      this.databaseReady = this.database.createTables();

      if (!this.databaseReady) {
         this.plugin.getDebugSystem().logError("Flags", new IllegalStateException("Failed to initialize Flags database"));
         return;
      }

      int historySize = this.config.getInt("settings.history-size", 200);

      WebhookGroupManager webhookGroupManager = new WebhookGroupManager(this.config, this.plugin.getLogger());
      FlagSeverityResolver severityResolver = new FlagSeverityResolver(this.config);
      AutoBanManager autoBanManager = new AutoBanManager(this.plugin, this.config);
      PlaytimeSensitivityManager playtimeSensitivity = new PlaytimeSensitivityManager(this.plugin, this.config);

      this.manager = new FlagsManager(this.plugin, this.database, historySize,
         webhookGroupManager, severityResolver, autoBanManager, playtimeSensitivity);
      this.lowPlaytimeReminderManager = new LowPlaytimeReminderManager(this.plugin, this, this.manager);

      long cooldownLowMs = this.config.getLong("settings.cooldown-low-seconds", 60) * 1000;
      long cooldownMediumMs = this.config.getLong("settings.cooldown-medium-seconds", 30) * 1000;
      long cooldownHighMs = this.config.getLong("settings.cooldown-high-seconds", 2) * 1000;
      int actionBarDuration = this.config.getInt("settings.actionbar-duration-seconds", 5);
      boolean chatNotifications = this.config.getBoolean("settings.chat-notifications", false);
      this.manager.setCooldowns(cooldownLowMs, cooldownMediumMs, cooldownHighMs);
      this.manager.setActionBarDuration(actionBarDuration);
      this.manager.setChatNotifications(chatNotifications);

      this.listener = new FlagsListener(this.plugin, this, this.manager);
      this.plugin.getListenerManager().registerListener(this.listener);

      if (this.isCommandEnabled("flags")) {
         this.registerCommandSafely(new FlagsCommand(this.plugin));
      }

      // Pre-load playtime data for all online players
      if (playtimeSensitivity.isEnabled()) {
         for (Player p : this.plugin.getServer().getOnlinePlayers()) {
            playtimeSensitivity.preloadAsync(p.getUniqueId());
         }
      }

      // Start cleanup task (every 10 minutes)
      this.plugin.getSchedulerManager().runAsyncTimer("flags-cleanup", () -> {
         this.manager.cleanup();
      }, 12000L, 12000L);

      this.lowPlaytimeReminderManager.restart();

      this.plugin.getDebugSystem().log("Flags", "Модуль Flags включен");
   }

   @Override
   public void onDisable() {
      super.onDisable();
      if (this.listener != null) {
         this.plugin.getListenerManager().unregisterListener(this.listener);
      }
      this.closeOpenMenus();
      this.plugin.getSchedulerManager().cancelTask("flags-cleanup");
      if (this.lowPlaytimeReminderManager != null) {
         this.lowPlaytimeReminderManager.stop();
      }
      if (this.manager != null) {
         this.manager.clearSettingsCache();
      }
      this.unregisterAllCommands();
      this.plugin.getDebugSystem().log("Flags", "Модуль Flags выключен");
   }

   @Override
   public void reload() {
      super.reload();
      this.loadConfigs();
      
      if (this.manager != null) {
         long cooldownLowMs = this.config.getLong("settings.cooldown-low-seconds", 60) * 1000;
         long cooldownMediumMs = this.config.getLong("settings.cooldown-medium-seconds", 30) * 1000;
         long cooldownHighMs = this.config.getLong("settings.cooldown-high-seconds", 2) * 1000;
         int actionBarDuration = this.config.getInt("settings.actionbar-duration-seconds", 5);
         boolean chatNotifications = this.config.getBoolean("settings.chat-notifications", false);
         this.manager.setCooldowns(cooldownLowMs, cooldownMediumMs, cooldownHighMs);
         this.manager.setActionBarDuration(actionBarDuration);
         this.manager.setChatNotifications(chatNotifications);
         this.manager.setWebhookGroupManager(new WebhookGroupManager(this.config, this.plugin.getLogger()));
         this.manager.setSeverityResolver(new FlagSeverityResolver(this.config));
         this.manager.setAutoBanManager(new AutoBanManager(this.plugin, this.config));
         this.manager.setPlaytimeSensitivity(new PlaytimeSensitivityManager(this.plugin, this.config));
      }

      if (this.lowPlaytimeReminderManager == null && this.manager != null) {
         this.lowPlaytimeReminderManager = new LowPlaytimeReminderManager(this.plugin, this, this.manager);
      }
      if (this.lowPlaytimeReminderManager != null) {
         this.lowPlaytimeReminderManager.restart();
      }

      if (this.listener != null) {
         this.listener.reloadRareItems();
      }
   }

   private void loadConfigs() {
      this.config = this.plugin.getModuleManager().loadModuleConfig("SM_Flags");
      this.messages = this.plugin.getModuleManager().loadModuleConfig("SM_Flags", "messages.yml");
      this.gui = this.plugin.getModuleManager().loadModuleConfig("SM_Flags", "gui.yml");
      
      if (this.config == null) {
         this.config = new YamlConfiguration();
      }
      if (this.messages == null) {
         this.messages = new YamlConfiguration();
      }
      if (this.gui == null) {
         this.gui = new YamlConfiguration();
      }

      // Merge defaults from JAR so any new config keys are always present
      this.mergeDefaults(this.config, "config.yml");
      this.mergeDefaults(this.messages, "messages.yml");
      this.mergeDefaults(this.gui, "gui.yml");
   }

   /**
    * Load the embedded resource from the module JAR and deep-merge any
    * missing keys into the given config. This ensures that new keys added
    * in updates are immediately available even if the server copy is old.
    *
    * Unlike Bukkit's setDefaults/copyDefaults, this writes missing keys
    * directly into the config so that getConfigurationSection() and
    * getKeys() work correctly for new sections.
    */
   private void mergeDefaults(FileConfiguration config, String resourceName) {
      try (InputStream stream = this.getClass().getClassLoader().getResourceAsStream(resourceName)) {
         if (stream == null) return;
         YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
            new InputStreamReader(stream, StandardCharsets.UTF_8));
         this.deepMerge(defaults, config);
      } catch (Exception e) {
         this.plugin.getDebugSystem().logError("Failed to merge defaults for " + resourceName, e);
      }
   }

    /**
     * Recursively copy keys from source into target if they are missing in target.
     * Existing keys in target are never overwritten.
     * Sections in SKIP_MERGE_SECTIONS are never merged — they are 100% user-controlled.
     */
    private static final java.util.Set<String> SKIP_MERGE_SECTIONS = java.util.Set.of("webhook_groups");

    private void deepMerge(org.bukkit.configuration.ConfigurationSection source,
                            org.bukkit.configuration.ConfigurationSection target) {
       for (String key : source.getKeys(false)) {
          // Never merge user-controlled sections from defaults
          if (SKIP_MERGE_SECTIONS.contains(key) && target.contains(key)) {
             continue;
          }
          if (!target.contains(key)) {
             // For sections, copy the entire map structure (not MemorySection references)
             if (source.isConfigurationSection(key)) {
                target.createSection(key, source.getConfigurationSection(key).getValues(true));
             } else {
                target.set(key, source.get(key));
             }
          } else if (source.isConfigurationSection(key) && target.isConfigurationSection(key)) {
             this.deepMerge(source.getConfigurationSection(key), target.getConfigurationSection(key));
          }
       }
    }

   public String getMessage(String key) {
      String message = this.messages.getString(key, "&[SECONDARY]Message not found: " + key);
      return this.plugin.applyColors(message);
   }

   private boolean isCommandEnabled(String key) {
      return this.config.getBoolean("commands." + key + ".enabled", true);
   }

   public FileConfiguration getConfig() {
      return this.config;
   }

   public FlagsManager getManager() {
      return this.manager;
   }

   public NamespacedKey getActionKey() {
      return this.actionKey;
   }

   public NamespacedKey getFlagTypeKey() {
      return this.flagTypeKey;
   }

   public NamespacedKey getPageKey() {
      return this.pageKey;
   }

   public NamespacedKey getHistoryWorldKey() {
      return this.historyWorldKey;
   }

   public NamespacedKey getHistoryXKey() {
      return this.historyXKey;
   }

   public NamespacedKey getHistoryYKey() {
      return this.historyYKey;
   }

   public NamespacedKey getHistoryZKey() {
      return this.historyZKey;
   }

   // ================================================================
   // Command handling — delegated from FlagsCommand for hot-swap safety
   // ================================================================

   public void handleCommand(Player player, String[] args) {
      if (!player.hasPermission("smflags.menu")) {
         player.sendMessage(this.getMessage("no-permission"));
         return;
      }

      // No args — open menu
      if (args.length == 0) {
         this.openFlagsMenu(player);
         return;
      }

      String sub = args[0].toLowerCase();

      switch (sub) {
         case "help" -> handleHelp(player);
         case "reload" -> handleReload(player);
         case "history" -> handleHistory(player, args);
         case "mute" -> handleMute(player, args);
         case "unmute" -> handleUnmute(player, args);
         case "clearplayer" -> handleClearPlayer(player, args);
         case "clearhistory" -> handleClearHistory(player, args);
         default -> player.sendMessage("§cНеизвестная подкоманда. Используйте §f/flags help §cдля списка команд.");
      }
   }

   private void handleHelp(Player player) {
      player.sendMessage("§6§l▎ §eКоманды системы флагов:");
      player.sendMessage("§f  /flags §7— открыть меню флагов");
      player.sendMessage("§f  /flags help §7— список команд");
      player.sendMessage("§f  /flags history §e[игрок] §7— история флагов");
      if (player.hasPermission("smflags.reload")) {
         player.sendMessage("§f  /flags reload §7— перезагрузить конфигурацию");
      }
      if (player.hasPermission("smflags.mute")) {
         player.sendMessage("§f  /flags mute §e<игрок> <время> §7— отключить флаги");
         player.sendMessage("§f  /flags unmute §e<игрок> §7— включить флаги обратно");
      }
      if (player.hasPermission("smflags.clearplayer")) {
         player.sendMessage("§f  /flags clearplayer §e<игрок> §c--confirm §7— удалить все данные игрока");
      }
      if (player.hasPermission("smflags.clearhistory")) {
         player.sendMessage("§f  /flags clearhistory §c--confirm §7— удалить §cВСЮ §7историю флагов");
      }
   }

   private void handleReload(Player player) {
      if (!player.hasPermission("smflags.reload")) {
         player.sendMessage(this.getMessage("no-permission"));
         return;
      }
      this.reload();
      player.sendMessage(this.getMessage("reload-done"));
   }

   private void handleHistory(Player player, String[] args) {
      String targetPlayer = args.length >= 2 ? args[1] : null;
      this.openHistoryMenu(player, targetPlayer);
   }

   private void handleMute(Player player, String[] args) {
      if (!player.hasPermission("smflags.mute")) {
         player.sendMessage(this.getMessage("no-permission"));
         return;
      }
      if (args.length < 3) {
         player.sendMessage("§cИспользование: /flags mute <игрок> <время>");
         player.sendMessage("§7Время: 10m, 1h, 2d, и т.д. (m=минуты, h=часы, d=дни)");
         return;
      }
      String targetName = args[1];
      long durationMs = parseDuration(args[2]);
      if (durationMs <= 0) {
         player.sendMessage("§cНеверный формат времени. Примеры: 10m, 1h, 2d");
         return;
      }

      Player target = Bukkit.getPlayerExact(targetName);
      UUID targetUuid;
      if (target != null) {
         targetUuid = target.getUniqueId();
         targetName = target.getName();
      } else {
         @SuppressWarnings("deprecation")
         OfflinePlayer offline = Bukkit.getOfflinePlayer(targetName);
         if (!offline.hasPlayedBefore() && !offline.isOnline()) {
            player.sendMessage("§cИгрок " + targetName + " не найден.");
            return;
         }
         targetUuid = offline.getUniqueId();
         if (offline.getName() != null) targetName = offline.getName();
      }

      this.manager.mutePlayer(targetUuid, durationMs);
      player.sendMessage("§aФлаги игрока §f" + targetName + " §aотключены на §f" + formatDuration(durationMs));
   }

   private void handleUnmute(Player player, String[] args) {
      if (!player.hasPermission("smflags.mute")) {
         player.sendMessage(this.getMessage("no-permission"));
         return;
      }
      if (args.length < 2) {
         player.sendMessage("§cИспользование: /flags unmute <игрок>");
         return;
      }
      String targetName = args[1];
      Player target = Bukkit.getPlayerExact(targetName);
      UUID targetUuid;
      if (target != null) {
         targetUuid = target.getUniqueId();
         targetName = target.getName();
      } else {
         @SuppressWarnings("deprecation")
         OfflinePlayer offline = Bukkit.getOfflinePlayer(targetName);
         targetUuid = offline.getUniqueId();
         if (offline.getName() != null) targetName = offline.getName();
      }

      this.manager.unmutePlayer(targetUuid);
      player.sendMessage("§aФлаги игрока §f" + targetName + " §aвключены обратно.");
   }

   private void handleClearPlayer(Player player, String[] args) {
      if (!player.hasPermission("smflags.clearplayer")) {
         player.sendMessage(this.getMessage("no-permission"));
         return;
      }
      if (args.length < 2) {
         player.sendMessage("§cИспользование: /flags clearplayer <игрок> --confirm");
         return;
      }
      String targetName = args[1];

      boolean confirmed = false;
      for (int i = 2; i < args.length; i++) {
         if (args[i].equalsIgnoreCase("--confirm")) {
            confirmed = true;
            break;
         }
      }
      if (!confirmed) {
         player.sendMessage("§cВнимание: §fЭта команда удалит §cВСЮ §fисторию флагов и настройки игрока §e" + targetName + "§f.");
         player.sendMessage("§fДобавьте §a--confirm §fдля подтверждения:");
         player.sendMessage("§7/flags clearplayer " + targetName + " --confirm");
         return;
      }

      Player target = Bukkit.getPlayerExact(targetName);
      UUID targetUuid;
      if (target != null) {
         targetUuid = target.getUniqueId();
         targetName = target.getName();
      } else {
         @SuppressWarnings("deprecation")
         OfflinePlayer offline = Bukkit.getOfflinePlayer(targetName);
         if (!offline.hasPlayedBefore() && !offline.isOnline()) {
            player.sendMessage("§cИгрок " + targetName + " не найден.");
            return;
         }
         targetUuid = offline.getUniqueId();
         if (offline.getName() != null) targetName = offline.getName();
      }

      String finalTargetName = targetName;
      String senderName = player.getName();
      this.manager.clearPlayerData(targetUuid, targetName, (int deleted) -> {
         player.sendMessage("§aДанные игрока §f" + finalTargetName + " §aочищены. Удалено записей: §f" + deleted);
         this.plugin.getDebugSystem().log("Flags",
            senderName + " cleared all flag data for " + finalTargetName + " (" + deleted + " history entries)");
      });
   }

   private void handleClearHistory(Player player, String[] args) {
      if (!player.hasPermission("smflags.clearhistory")) {
         player.sendMessage(this.getMessage("no-permission"));
         return;
      }

      boolean confirmed = false;
      for (int i = 1; i < args.length; i++) {
         if (args[i].equalsIgnoreCase("--confirm")) {
            confirmed = true;
            break;
         }
      }
      if (!confirmed) {
         player.sendMessage("§c§lВнимание: §fЭта команда удалит §c§lВСЮ §fисторию флагов §c§lВСЕХ §fигроков!");
         player.sendMessage("§fЭто действие §cнеобратимо§f. Добавьте §a--confirm §fдля подтверждения:");
         player.sendMessage("§7/flags clearhistory --confirm");
         return;
      }

      String senderName = player.getName();
      this.manager.clearAllHistory(() -> {
         player.sendMessage("§aВся история флагов очищена.");
         this.plugin.getDebugSystem().log("Flags", senderName + " cleared ALL flags history");
      });
   }

   @SuppressWarnings("unused")
   public java.util.Collection<String> handleSuggest(org.bukkit.command.CommandSender sender, String[] args) {
      java.util.List<String> suggestions = new java.util.ArrayList<>();

      if (args.length <= 1) {
         String input = args.length > 0 ? args[0].toLowerCase() : "";
         if ("help".startsWith(input)) suggestions.add("help");
         if ("reload".startsWith(input) && sender.hasPermission("smflags.reload")) suggestions.add("reload");
         if ("history".startsWith(input)) suggestions.add("history");
         if ("mute".startsWith(input) && sender.hasPermission("smflags.mute")) suggestions.add("mute");
         if ("unmute".startsWith(input) && sender.hasPermission("smflags.mute")) suggestions.add("unmute");
         if ("clearplayer".startsWith(input) && sender.hasPermission("smflags.clearplayer")) suggestions.add("clearplayer");
         if ("clearhistory".startsWith(input) && sender.hasPermission("smflags.clearhistory")) suggestions.add("clearhistory");
         return suggestions;
      }

      if (args.length == 2 && (args[0].equalsIgnoreCase("history")
            || args[0].equalsIgnoreCase("mute")
            || args[0].equalsIgnoreCase("unmute")
            || args[0].equalsIgnoreCase("clearplayer"))) {
         String input = args[1].toLowerCase();
         for (Player p : this.plugin.getServer().getOnlinePlayers()) {
            if (p.getName().toLowerCase().startsWith(input)) {
               suggestions.add(p.getName());
            }
         }
         return suggestions;
      }

      if (args.length == 3 && args[0].equalsIgnoreCase("mute")) {
         String input = args[2].toLowerCase();
         for (String example : java.util.List.of("10m", "30m", "1h", "2h", "6h", "12h", "1d", "7d")) {
            if (example.startsWith(input)) suggestions.add(example);
         }
         return suggestions;
      }

      if (args.length == 3 && args[0].equalsIgnoreCase("clearplayer")) {
         if ("--confirm".startsWith(args[2].toLowerCase())) suggestions.add("--confirm");
         return suggestions;
      }

      if (args.length == 2 && args[0].equalsIgnoreCase("clearhistory")) {
         if ("--confirm".startsWith(args[1].toLowerCase())) suggestions.add("--confirm");
         return suggestions;
      }

      return suggestions;
   }

   private static long parseDuration(String input) {
      if (input == null || input.length() < 2) return -1;
      try {
         char unit = input.charAt(input.length() - 1);
         long amount = Long.parseLong(input.substring(0, input.length() - 1));
         if (amount <= 0) return -1;
         return switch (Character.toLowerCase(unit)) {
            case 's' -> amount * 1000L;
            case 'm' -> amount * 60_000L;
            case 'h' -> amount * 3_600_000L;
            case 'd' -> amount * 86_400_000L;
            default -> -1;
         };
      } catch (NumberFormatException e) {
         return -1;
      }
   }

   private static String formatDuration(long ms) {
      long totalSeconds = ms / 1000;
      if (totalSeconds < 60) return totalSeconds + " сек.";
      long minutes = totalSeconds / 60;
      if (minutes < 60) return minutes + " мин.";
      long hours = minutes / 60;
      long remainingMinutes = minutes % 60;
      if (hours < 24) {
         return remainingMinutes > 0
            ? hours + " ч. " + remainingMinutes + " мин."
            : hours + " ч.";
      }
      long days = hours / 24;
      long remainingHours = hours % 24;
      return remainingHours > 0
         ? days + " д. " + remainingHours + " ч."
         : days + " д.";
   }

   public void openFlagsMenu(Player player) {
      this.openFlagsMenu(player, 1);
   }

   public void openFlagsMenu(Player player, int page) {
      if (!this.databaseReady) {
         player.sendMessage(this.getMessage("db-not-ready"));
         return;
      }

      FlagType[] allFlags = FlagType.values();
      int totalPages = (int) Math.ceil((double) allFlags.length / ITEMS_PER_PAGE);
      int safePage = Math.max(1, Math.min(page, totalPages));

      String title = this.gui.getString("menus.flags.title", "&d&lФЛАГИ &7• &fСтраница {page}")
         .replace("{page}", safePage + "/" + totalPages);
      
      Inventory inventory = Bukkit.createInventory(
         new FlagsMenuHolder(player.getUniqueId(), safePage), 
         54, 
         this.plugin.applyColors(title)
      );

      this.fillBorder(inventory);
      Map<FlagType, Boolean> settings = this.manager.getPlayerSettings(player.getUniqueId());

      int startIndex = (safePage - 1) * ITEMS_PER_PAGE;
      int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, allFlags.length);

      for (int i = startIndex; i < endIndex; i++) {
         FlagType flagType = allFlags[i];
         int slotIndex = i - startIndex;
         if (slotIndex < FLAG_SLOTS.length) {
            ItemStack item = this.createFlagItem(flagType, settings.getOrDefault(flagType, true));
            inventory.setItem(FLAG_SLOTS[slotIndex], item);
         }
      }

      // Navigation buttons
      if (safePage > 1) {
         inventory.setItem(45, this.createNavButton("prev", Material.ARROW));
      }
      
      // Display toggle buttons
      inventory.setItem(47, this.createActionBarToggle(player));
      inventory.setItem(49, this.createToggleAllButton());
      inventory.setItem(51, this.createChatToggle(player));

      if (safePage < totalPages) {
         inventory.setItem(53, this.createNavButton("next", Material.ARROW));
      }

      player.openInventory(inventory);
   }

   public void openHistoryMenu(Player player, String targetPlayer) {
      this.openHistoryMenu(player, targetPlayer, 1);
   }

   public void openHistoryMenu(Player player, String targetPlayer, int page) {
      List<FlagEvent> rawEvents = targetPlayer == null
         ? this.manager.getHistory()
         : this.manager.getHistory(targetPlayer);

      if (rawEvents.isEmpty()) {
         player.sendMessage(this.getMessage("history-empty"));
         return;
      }

      // Group consecutive duplicate flags (same player + same flag type)
      List<GroupedFlagEvent> events = this.groupHistory(rawEvents);

      int totalPages = (int) Math.ceil((double) events.size() / HISTORY_ITEMS_PER_PAGE);
      int safePage = Math.max(1, Math.min(page, totalPages));

      String title = this.gui.getString("menus.history.title", "&d&lИСТОРИЯ &7• &f{player}")
         .replace("{player}", targetPlayer == null ? "Все" : targetPlayer)
         .replace("{page}", safePage + "/" + totalPages);

      Inventory inventory = Bukkit.createInventory(
         new FlagsHistoryMenuHolder(safePage, targetPlayer),
         54,
         this.plugin.applyColors(title)
      );

      this.fillBorder(inventory);

      int startIndex = (safePage - 1) * HISTORY_ITEMS_PER_PAGE;
      int endIndex = Math.min(startIndex + HISTORY_ITEMS_PER_PAGE, events.size());

      int[] historySlots = {
         10, 11, 12, 13, 14, 15, 16,
         19, 20, 21, 22, 23, 24, 25,
         28, 29, 30, 31, 32, 33, 34
      };

      for (int i = startIndex; i < endIndex; i++) {
         GroupedFlagEvent grouped = events.get(i);
         int slotIndex = i - startIndex;
         if (slotIndex < historySlots.length) {
            ItemStack item = this.createHistoryItem(grouped);
            inventory.setItem(historySlots[slotIndex], item);
         }
      }

      // Navigation
      if (safePage > 1) {
         inventory.setItem(45, this.createNavButton("history-prev", Material.ARROW));
      }
      if (safePage < totalPages) {
         inventory.setItem(53, this.createNavButton("history-next", Material.ARROW));
      }

      player.openInventory(inventory);
   }

   /**
    * Open an expanded (unstacked) view for a specific player + flag type group.
    * Shows individual events without grouping, with a back button to return.
    */
   public void openExpandedHistoryMenu(Player player, String targetPlayer,
                                        String expandPlayerName, String expandFlagTypeKey,
                                        int page, int parentPage) {
      List<FlagEvent> rawEvents = targetPlayer == null
         ? this.manager.getHistory()
         : this.manager.getHistory(targetPlayer);

      // Filter to only matching events
      List<FlagEvent> filtered = new ArrayList<>();
      for (FlagEvent event : rawEvents) {
         if (event.getPlayerName().equals(expandPlayerName)
               && event.getFlagType().getKey().equals(expandFlagTypeKey)) {
            filtered.add(event);
         }
      }

      if (filtered.isEmpty()) {
         player.sendMessage(this.getMessage("history-empty"));
         return;
      }

      FlagType flagType = FlagType.fromKey(expandFlagTypeKey);
      String flagName = flagType != null ? flagType.getDisplayName() : expandFlagTypeKey;

      int totalPages = (int) Math.ceil((double) filtered.size() / HISTORY_ITEMS_PER_PAGE);
      int safePage = Math.max(1, Math.min(page, totalPages));

      String title = this.gui.getString("menus.history.title", "&d&lИСТОРИЯ &7• &f{player}")
         .replace("{player}", expandPlayerName + " — " + flagName)
         .replace("{page}", safePage + "/" + totalPages);

      Inventory inventory = Bukkit.createInventory(
         new FlagsHistoryMenuHolder(safePage, targetPlayer, expandPlayerName, expandFlagTypeKey, parentPage),
         54,
         this.plugin.applyColors(title)
      );

      this.fillBorder(inventory);

      int startIndex = (safePage - 1) * HISTORY_ITEMS_PER_PAGE;
      int endIndex = Math.min(startIndex + HISTORY_ITEMS_PER_PAGE, filtered.size());

      int[] historySlots = {
         10, 11, 12, 13, 14, 15, 16,
         19, 20, 21, 22, 23, 24, 25,
         28, 29, 30, 31, 32, 33, 34
      };

      for (int i = startIndex; i < endIndex; i++) {
         FlagEvent event = filtered.get(i);
         int slotIndex = i - startIndex;
         if (slotIndex < historySlots.length) {
            inventory.setItem(historySlots[slotIndex], this.createSingleHistoryItem(event));
         }
      }

      // Back button (slot 45)
      ItemStack backBtn = new ItemStack(Material.BARRIER);
      ItemMeta backMeta = backBtn.getItemMeta();
      if (backMeta != null) {
         backMeta.setDisplayName(this.plugin.applyColors("&c← Назад"));
         backMeta.getPersistentDataContainer().set(this.actionKey, PersistentDataType.STRING, "expand-back");
         backBtn.setItemMeta(backMeta);
      }
      inventory.setItem(45, backBtn);

      // Pagination within expanded view
      if (safePage > 1) {
         inventory.setItem(48, this.createNavButton("expand-prev", Material.ARROW));
      }
      if (safePage < totalPages) {
         inventory.setItem(50, this.createNavButton("expand-next", Material.ARROW));
      }

      player.openInventory(inventory);
   }

   /**
    * Create a history item for a single (non-grouped) event.
    */
   private ItemStack createSingleHistoryItem(FlagEvent event) {
      ItemStack item = new ItemStack(event.getFlagType().getIcon());
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(this.plugin.applyColors("&[MAIN]" + event.getFlagType().getDisplayName()));

         List<String> lore = new ArrayList<>();
         lore.add(this.plugin.applyColors("&[SECONDARY]Игрок: &[MAIN]" + event.getPlayerName()));

         if (event.getLocation() != null) {
            lore.add(this.plugin.applyColors("&[SECONDARY]Координаты: &[MAIN]" + event.getCoordinates()));
            lore.add(this.plugin.applyColors("&[SECONDARY]Мир: &[MAIN]" + event.getWorld()));
         }

         if (event.getValue() > 0) {
            lore.add(this.plugin.applyColors("&[SECONDARY]Значение: &[MAIN]" + event.getValue()));
         }

         if (event.getDetails() != null && !event.getDetails().isEmpty()) {
            lore.add(this.plugin.applyColors("&[SECONDARY]Детали: &[MAIN]" + event.getDetails()));
         }

         lore.add(this.plugin.applyColors("&[SECONDARY]Время: &[MAIN]" + formatTimestamp(event.getTimestamp())));

         if (event.getLocation() != null) {
            lore.add("");
            lore.add(this.plugin.applyColors("&7Клик: &fТелепорт к событию"));
            meta.getPersistentDataContainer().set(this.actionKey, PersistentDataType.STRING, "history-teleport");
            this.writeHistoryLocation(meta.getPersistentDataContainer(), event.getLocation());
         }

         meta.setLore(lore);
         item.setItemMeta(meta);
      }
      return item;
   }

   /**
    * Group consecutive events with the same player + flag type into a single entry.
    * Events are already sorted DESC by timestamp from the database.
    */
   private List<GroupedFlagEvent> groupHistory(List<FlagEvent> events) {
      List<GroupedFlagEvent> grouped = new ArrayList<>();
      if (events.isEmpty()) return grouped;

      FlagEvent current = events.get(0);
      int count = 1;
      int totalValue = current.getValue();
      long oldestTimestamp = current.getTimestamp();

      for (int i = 1; i < events.size(); i++) {
         FlagEvent next = events.get(i);
         if (next.getPlayerName().equals(current.getPlayerName())
               && next.getFlagType() == current.getFlagType()) {
            count++;
            totalValue += next.getValue();
            oldestTimestamp = next.getTimestamp();
         } else {
            grouped.add(new GroupedFlagEvent(current, count, totalValue, oldestTimestamp));
            current = next;
            count = 1;
            totalValue = next.getValue();
            oldestTimestamp = next.getTimestamp();
         }
      }
      grouped.add(new GroupedFlagEvent(current, count, totalValue, oldestTimestamp));
      return grouped;
   }

   private static class GroupedFlagEvent {
      final FlagEvent event;
      final int count;
      final int totalValue;
      final long oldestTimestamp;

      GroupedFlagEvent(FlagEvent event, int count, int totalValue, long oldestTimestamp) {
         this.event = event;
         this.count = count;
         this.totalValue = totalValue;
         this.oldestTimestamp = oldestTimestamp;
      }
   }

   private ItemStack createFlagItem(FlagType flagType, boolean enabled) {
      ItemStack item = new ItemStack(enabled ? flagType.getIcon() : Material.GRAY_DYE);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         String severityColor = switch (flagType.getSeverity()) {
            case LOW -> "&a";
            case MEDIUM -> "&e";
            case HIGH -> "&c";
         };
         String status = enabled ? "&aВключен" : "&7Выключен";
         meta.setDisplayName(this.plugin.applyColors(severityColor + flagType.getDisplayName() + " &7• " + status));
         
         List<String> lore = new ArrayList<>();
         lore.add(this.plugin.applyColors("&7Уровень: " + severityColor + flagType.getSeverity().getName()));
         if (this.manager != null && this.manager.getSeverityResolver() != null
               && this.manager.getSeverityResolver().hasLevels(flagType)) {
            lore.add(this.plugin.applyColors("&7Динамические уровни: &aНастроены"));
         }
         lore.add("");
         lore.add(this.plugin.applyColors(enabled 
            ? "&7Клик: &cВыключить" 
            : "&7Клик: &aВключить"));
         meta.setLore(lore);
         
         PersistentDataContainer container = meta.getPersistentDataContainer();
         container.set(this.actionKey, PersistentDataType.STRING, "toggle-flag");
         container.set(this.flagTypeKey, PersistentDataType.STRING, flagType.getKey());
         item.setItemMeta(meta);
      }
      return item;
   }

   private ItemStack createHistoryItem(GroupedFlagEvent grouped) {
      FlagEvent event = grouped.event;
      ItemStack item = new ItemStack(event.getFlagType().getIcon());
      if (grouped.count > 1) {
         item.setAmount(Math.min(grouped.count, 64));
      }
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         String name = event.getFlagType().getDisplayName();
         if (grouped.count > 1) {
            meta.setDisplayName(this.plugin.applyColors("&[MAIN]" + name + " &7x" + grouped.count));
         } else {
            meta.setDisplayName(this.plugin.applyColors("&[MAIN]" + name));
         }

         List<String> lore = new ArrayList<>();
         lore.add(this.plugin.applyColors("&[SECONDARY]Игрок: &[MAIN]" + event.getPlayerName()));
         
         if (event.getLocation() != null) {
            lore.add(this.plugin.applyColors("&[SECONDARY]Координаты: &[MAIN]" + event.getCoordinates()));
            lore.add(this.plugin.applyColors("&[SECONDARY]Мир: &[MAIN]" + event.getWorld()));
         }
         
         if (grouped.count > 1 && grouped.totalValue > 0) {
            lore.add(this.plugin.applyColors("&[SECONDARY]Сумма значений: &[MAIN]" + grouped.totalValue));
         } else if (event.getValue() > 0) {
            lore.add(this.plugin.applyColors("&[SECONDARY]Значение: &[MAIN]" + event.getValue()));
         }
         
         if (event.getDetails() != null && !event.getDetails().isEmpty()) {
            lore.add(this.plugin.applyColors("&[SECONDARY]Детали: &[MAIN]" + event.getDetails()));
         }

         if (grouped.count > 1) {
            lore.add(this.plugin.applyColors("&[SECONDARY]Последний: &[MAIN]" + formatTimestamp(event.getTimestamp())));
            lore.add(this.plugin.applyColors("&[SECONDARY]Первый: &[MAIN]" + formatTimestamp(grouped.oldestTimestamp)));
            lore.add("");
            lore.add(this.plugin.applyColors("&7ЛКМ: &fРазвернуть все " + grouped.count + " событий"));
            if (event.getLocation() != null) {
               lore.add(this.plugin.applyColors("&7ПКМ: &fТелепорт к последнему событию"));
            }
         } else {
            lore.add(this.plugin.applyColors("&[SECONDARY]Время: &[MAIN]" + formatTimestamp(event.getTimestamp())));
            if (event.getLocation() != null) {
               lore.add("");
               lore.add(this.plugin.applyColors("&7Клик: &fТелепорт к событию"));
            }
         }

         // For grouped items, store expand data so clicking opens the individual events
         if (grouped.count > 1) {
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(this.actionKey, PersistentDataType.STRING, "expand-group");
            container.set(this.flagTypeKey, PersistentDataType.STRING, event.getFlagType().getKey());
            // Store the player name using a NamespacedKey
            container.set(this.pageKey, PersistentDataType.STRING, event.getPlayerName());
            this.writeHistoryLocation(container, event.getLocation());
         } else if (event.getLocation() != null) {
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(this.actionKey, PersistentDataType.STRING, "history-teleport");
            this.writeHistoryLocation(container, event.getLocation());
         }

         meta.setLore(lore);
         item.setItemMeta(meta);
      }
      return item;
   }

   private ItemStack createNavButton(String action, Material material) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         String name = action.contains("next") ? "&[MAIN]Следующая" : "&[MAIN]Предыдущая";
         meta.setDisplayName(this.plugin.applyColors(name));
         meta.getPersistentDataContainer().set(this.actionKey, PersistentDataType.STRING, action);
         item.setItemMeta(meta);
      }
      return item;
   }

   private void writeHistoryLocation(PersistentDataContainer container, org.bukkit.Location location) {
      if (container == null || location == null || location.getWorld() == null) {
         return;
      }
      container.set(this.historyWorldKey, PersistentDataType.STRING, location.getWorld().getName());
      container.set(this.historyXKey, PersistentDataType.DOUBLE, location.getX());
      container.set(this.historyYKey, PersistentDataType.DOUBLE, location.getY());
      container.set(this.historyZKey, PersistentDataType.DOUBLE, location.getZ());
   }

   private ItemStack createToggleAllButton() {
      ItemStack item = new ItemStack(Material.COMPARATOR);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(this.plugin.applyColors("&[MAIN]Переключить всё"));
         List<String> lore = new ArrayList<>();
         lore.add(this.plugin.applyColors("&[SECONDARY]ЛКМ: &aВключить всё"));
         lore.add(this.plugin.applyColors("&[SECONDARY]ПКМ: &cВыключить всё"));
         meta.setLore(lore);
         meta.getPersistentDataContainer().set(this.actionKey, PersistentDataType.STRING, "toggle-all");
         item.setItemMeta(meta);
      }
      return item;
   }

   private ItemStack createActionBarToggle(Player player) {
      boolean enabled = this.manager != null && this.manager.isAdminActionBarEnabled(player.getUniqueId());
      ItemStack item = new ItemStack(enabled ? Material.EXPERIENCE_BOTTLE : Material.GLASS_BOTTLE);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         String status = enabled ? "&aВключен" : "&cВыключен";
         meta.setDisplayName(this.plugin.applyColors("&b\u2588 ActionBar &7• " + status));
         List<String> lore = new ArrayList<>();
         lore.add(this.plugin.applyColors("&7Отображение флагов"));
         lore.add(this.plugin.applyColors("&7в ActionBar"));
         lore.add("");
         lore.add(this.plugin.applyColors(enabled ? "&7Клик: &cВыключить" : "&7Клик: &aВключить"));
         meta.setLore(lore);
         meta.getPersistentDataContainer().set(this.actionKey, PersistentDataType.STRING, "toggle-actionbar");
         item.setItemMeta(meta);
      }
      return item;
   }

   private ItemStack createChatToggle(Player player) {
      boolean enabled = this.manager != null && this.manager.isAdminChatEnabled(player.getUniqueId());
      ItemStack item = new ItemStack(enabled ? Material.WRITABLE_BOOK : Material.BOOK);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         String status = enabled ? "&aВключен" : "&cВыключен";
         meta.setDisplayName(this.plugin.applyColors("&b\u2709 Чат &7• " + status));
         List<String> lore = new ArrayList<>();
         lore.add(this.plugin.applyColors("&7Отображение флагов"));
         lore.add(this.plugin.applyColors("&7в чате с кнопкой [TP]"));
         lore.add("");
         lore.add(this.plugin.applyColors(enabled ? "&7Клик: &cВыключить" : "&7Клик: &aВключить"));
         meta.setLore(lore);
         meta.getPersistentDataContainer().set(this.actionKey, PersistentDataType.STRING, "toggle-chat");
         item.setItemMeta(meta);
      }
      return item;
   }

   private void fillBorder(Inventory inventory) {
      Material borderMaterial = Material.getMaterial(
         this.gui.getString("border-material", "LIGHT_GRAY_STAINED_GLASS_PANE")
      );
      if (borderMaterial == null) {
         borderMaterial = Material.LIGHT_GRAY_STAINED_GLASS_PANE;
      }
      
      ItemStack border = new ItemStack(borderMaterial);
      ItemMeta meta = border.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(" ");
         border.setItemMeta(meta);
      }

      for (int i = 0; i < 9; i++) {
         inventory.setItem(i, border);
      }
      for (int i = 45; i < 54; i++) {
         if (inventory.getItem(i) == null) {
            inventory.setItem(i, border);
         }
      }
      for (int i = 9; i < 45; i += 9) {
         inventory.setItem(i, border);
         inventory.setItem(i + 8, border);
      }
   }

   private String formatTimestamp(long timestamp) {
      long seconds = (System.currentTimeMillis() - timestamp) / 1000;
      if (seconds < 60) return seconds + "с назад";
      if (seconds < 3600) return (seconds / 60) + "м назад";
      if (seconds < 86400) return (seconds / 3600) + "ч назад";
      return (seconds / 86400) + "д назад";
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

   private void closeOpenMenus() {
      // Capture class names as strings BEFORE the lambda executes,
      // so the classloader doesn't need to be alive when Folia runs the task
      final String flagsMenuClass = FlagsMenuHolder.class.getName();
      final String historyMenuClass = FlagsHistoryMenuHolder.class.getName();

      for (Player player : this.plugin.getServer().getOnlinePlayers()) {
         this.plugin.getSchedulerManager().runEntityTask(player, "flags-close-menu-" + player.getUniqueId(), () -> {
            if (!player.isOnline()) {
               return;
            }
            InventoryView view = player.getOpenInventory();
            if (view == null) {
               return;
            }
            InventoryHolder holder = view.getTopInventory().getHolder();
            if (holder != null) {
               String holderClass = holder.getClass().getName();
               if (holderClass.equals(flagsMenuClass) || holderClass.equals(historyMenuClass)) {
                  player.closeInventory();
               }
            }
         });
      }
   }

   private boolean isMenuHolder(InventoryHolder holder, Class<?> type) {
      if (holder == null) {
         return false;
      }
      if (type.isInstance(holder)) {
         return true;
      }
      return holder.getClass().getName().equals(type.getName());
   }
}
