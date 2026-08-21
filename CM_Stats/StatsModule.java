package net.schalker.SMPS.modules.stats;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.DoAPI.core.module.BaseModule;
import net.schalker.DoAPI.core.module.ModuleInfo;
import net.schalker.SMPS.modules.stats.commands.StatsAdminScanCommand;
import net.schalker.SMPS.modules.stats.commands.StatsCommand;
import net.schalker.SMPS.modules.stats.listeners.StatsListener;
import net.schalker.SMPS.modules.stats.managers.StatsManager;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class StatsModule extends BaseModule {
   private static final int[] STAT_SLOTS = new int[] {
      19, 21, 23, 25,
      29, 31, 33,
      40
   };
   private static final int[] TOP_SLOTS = new int[] {
      10, 11, 12, 13, 14, 15, 16,
      19, 20, 21, 22, 23, 24, 25,
      28, 29, 30, 31, 32, 33, 34
   };

   private FileConfiguration config;
   private FileConfiguration messages;
   private FileConfiguration gui;
   private StatsDatabase database;
   private StatsManager manager;
   private StatsListener listener;
   private NamespacedKey actionKey;
   private NamespacedKey metricKey;
   private NamespacedKey categoryKey;
   private NamespacedKey targetKey;
   private boolean databaseReady;
   private final Set<String> registeredCommandNames = new HashSet<>();
   private List<StatsCategory> categories = new ArrayList<>();
   private Map<String, StatsCategory> categoryMap = new HashMap<>();
   private boolean playerdataScanEnabled;
   private final List<File> playerdataScanFiles = new ArrayList<>();
   private int playerdataScanCursor;
   private int playerdataImportedCount;
   private UUID playerdataScanInitiator;
   private boolean playerdataScanInitiatorConsole;
   private static Set<String> BLOCK_NAMESPACE_KEYS;
   private Map<UUID, String> userNameCache = new HashMap<>();

   public StatsModule(DoAPI plugin) {
      super(plugin, new ModuleInfo("SM_Stats", "1.0.0", "MeXaNoBoP", "Статистика игроков"));
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.config = this.plugin.getModuleManager().loadModuleConfig("SM_Stats");
      this.messages = this.plugin.getModuleManager().loadModuleConfig("SM_Stats", "messages.yml");
      this.gui = this.plugin.getModuleManager().loadModuleConfig("SM_Stats", "gui.yml");
      if (this.config == null) {
         this.config = new YamlConfiguration();
      }
      if (this.messages == null) {
         this.messages = new YamlConfiguration();
      }
      if (this.gui == null) {
         this.gui = new YamlConfiguration();
      }

      this.actionKey = new NamespacedKey(this.plugin, "stats-action");
      this.metricKey = new NamespacedKey(this.plugin, "stats-metric");
      this.categoryKey = new NamespacedKey(this.plugin, "stats-category");
      this.targetKey = new NamespacedKey(this.plugin, "stats-target");
      this.loadCategories();

      if (!this.plugin.isDatabaseConnected()) {
         this.databaseReady = false;
         this.plugin.getDebugSystem().logError("Stats", new IllegalStateException("Database not connected"));
         return;
      }

      this.databaseReady = true;
      this.database = new StatsDatabase(this.plugin);
      this.database.createTables();
      this.database.setAllOffline();
      long moveInterval = this.getMoveIntervalMillis();
      this.manager = new StatsManager(this.plugin, this.database, moveInterval);
      this.listener = new StatsListener(this.plugin, this.manager, this);
      this.plugin.getListenerManager().registerListener(this.listener);

      if (this.isCommandEnabled("stats")) {
         this.registerCommandSafely(new StatsCommand(this.plugin, this));
      }
      if (this.isCommandEnabled("statsadmin")) {
         this.registerCommandSafely(new StatsAdminScanCommand(this.plugin, this));
      }

      this.startSaveTask();
      this.plugin.getDebugSystem().log("Stats", "Модуль Stats включен");
   }

   @Override
   public void onDisable() {
      super.onDisable();
      if (this.listener != null) {
         this.plugin.getListenerManager().unregisterListener(this.listener);
      }
      this.plugin.getSchedulerManager().cancelTask("stats-save");
      if (this.manager != null) {
         this.manager.saveAllSync();
      }
      this.stopPlayerdataScanInternal();
      if (this.database != null) {
         this.database.setAllOffline();
      }
      this.unregisterAllCommands();
      this.plugin.getDebugSystem().log("Stats", "Модуль Stats выключен");
   }

   @Override
   public void reload() {
      super.reload();
      this.config = this.plugin.getModuleManager().loadModuleConfig("SM_Stats");
      this.messages = this.plugin.getModuleManager().loadModuleConfig("SM_Stats", "messages.yml");
      this.gui = this.plugin.getModuleManager().loadModuleConfig("SM_Stats", "gui.yml");
      if (this.config == null) {
         this.config = new YamlConfiguration();
      }
      if (this.messages == null) {
         this.messages = new YamlConfiguration();
      }
      if (this.gui == null) {
         this.gui = new YamlConfiguration();
      }
      this.loadCategories();
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

   public String getGuiMessage(String key, String fallback) {
      String message = this.gui != null ? this.gui.getString(key) : null;
      if (message == null) {
         message = fallback;
      }
      if (message == null || message.isEmpty()) {
         return "";
      }
      return this.plugin.applyColors(this.sanitizeGuiText(message));
   }

   private String getMetricTitle(StatsMetric metric) {
      if (metric == null) {
         return "";
      }
      String title = this.gui != null ? this.gui.getString("metric-titles." + metric.getKey()) : null;
      if (title == null || title.isEmpty()) {
         return this.sanitizeGuiText(metric.getTitle());
      }
      return this.sanitizeGuiText(title);
   }

   public boolean isCommandEnabled(String key) {
      return this.config.getBoolean("commands." + key + ".enabled", true);
   }

   public void togglePlayerdataScan(CommandSender sender) {
      if (sender == null) {
         return;
      }
      if (!this.isPlayerdataScanFeatureEnabled()) {
         sender.sendMessage(this.getMessage("scan.disabled-feature", "&[SECONDARY]Импорт из playerdata выключен в конфиге."));
         return;
      }
      if (!this.databaseReady || this.database == null) {
         sender.sendMessage(this.getMessage("db-not-ready", "&[SECONDARY]База данных недоступна."));
         return;
      }

      if (this.playerdataScanEnabled) {
         this.stopPlayerdataScanInternal();
         sender.sendMessage(this.getMessage("scan.stopped", "&[SECONDARY]Импорт статистики из playerdata остановлен."));
         return;
      }

      this.ensureBlockKeyCache();

      File statsDir = new File(Bukkit.getWorldContainer(), "world/stats");
      if (!statsDir.exists() || !statsDir.isDirectory()) {
         sender.sendMessage(this.getMessage("scan.no-stats-dir", "&[SECONDARY]Папка world/stats не найдена на сервере."));
         return;
      }

      File[] jsonFiles = statsDir.listFiles((dir, name) -> name.endsWith(".json"));
      if (jsonFiles == null || jsonFiles.length == 0) {
         sender.sendMessage(this.getMessage("scan.empty", "&[SECONDARY]Не найдено файлов статистики для импорта."));
         return;
      }

      this.playerdataScanFiles.clear();
      for (File f : jsonFiles) {
         this.playerdataScanFiles.add(f);
      }
      this.userNameCache = this.loadUserCache();
      this.playerdataScanCursor = 0;
      this.playerdataImportedCount = 0;
      this.playerdataScanEnabled = true;
      this.playerdataScanInitiatorConsole = !(sender instanceof Player);
      this.playerdataScanInitiator = sender instanceof Player player ? player.getUniqueId() : null;

      int total = this.playerdataScanFiles.size();
      sender.sendMessage(this.getMessage("scan.started", "&[SECONDARY]Импорт из playerdata запущен. Игроков: &[MAIN]{total}&[SECONDARY].")
         .replace("{total}", Integer.toString(total)));

      long interval = this.getPlayerdataScanTickInterval();
      this.plugin.getSchedulerManager().runAsyncTimer("stats-playerdata-scan", () -> {
         this.runPlayerdataScanBatch();
      }, interval, interval);
   }

   private void ensureBlockKeyCache() {
      if (BLOCK_NAMESPACE_KEYS != null) {
         return;
      }
      Set<String> blocks = new HashSet<>();
      for (Material material : Material.values()) {
         if (material.isLegacy() || material.isAir()) {
            continue;
         }
         if (material.isBlock()) {
            blocks.add(material.getKey().toString());
         }
      }
      BLOCK_NAMESPACE_KEYS = blocks;
   }

   private void runPlayerdataScanBatch() {
      if (!this.playerdataScanEnabled || this.database == null) {
         this.plugin.getSchedulerManager().runGlobalTask("stats-scan-stop", () -> {
            this.stopPlayerdataScanInternal();
         });
         return;
      }

      int batchSize = this.getPlayerdataScanBatchSize();
      List<StatsSnapshot> snapshots = new ArrayList<>();
      int processedNow = 0;

      while (processedNow < batchSize && this.playerdataScanCursor < this.playerdataScanFiles.size()) {
         File statsFile = this.playerdataScanFiles.get(this.playerdataScanCursor++);
         StatsSnapshot snapshot = this.buildSnapshotFromStatsFile(statsFile);
         if (snapshot != null) {
            snapshots.add(snapshot);
         }
         processedNow++;
      }

      if (!snapshots.isEmpty()) {
         this.playerdataImportedCount += snapshots.size();
         for (StatsSnapshot snapshot : snapshots) {
            this.database.saveSnapshot(snapshot);
         }
      }

      if (this.playerdataScanCursor >= this.playerdataScanFiles.size()) {
         int imported = this.playerdataImportedCount;
         this.plugin.getSchedulerManager().runGlobalTask("stats-scan-done", () -> {
            this.stopPlayerdataScanInternal();
         });
         this.notifyScanInitiator(this.getMessage("scan.done", "&[SECONDARY]Импорт из playerdata завершён. Импортировано: &[MAIN]{count}&[SECONDARY].")
            .replace("{count}", Integer.toString(imported)));
      }
   }

   private void stopPlayerdataScanInternal() {
      this.plugin.getSchedulerManager().cancelTask("stats-playerdata-scan");
      this.playerdataScanEnabled = false;
      this.playerdataScanFiles.clear();
      this.playerdataScanCursor = 0;
      this.playerdataImportedCount = 0;
      this.playerdataScanInitiator = null;
      this.playerdataScanInitiatorConsole = false;
      this.userNameCache.clear();
   }

   private Map<UUID, String> loadUserCache() {
      Map<UUID, String> cache = new HashMap<>();
      File usercacheFile = new File(Bukkit.getWorldContainer(), "usercache.json");
      if (!usercacheFile.exists()) {
         usercacheFile = new File("usercache.json");
      }
      if (!usercacheFile.exists()) {
         return cache;
      }
      try (FileReader reader = new FileReader(usercacheFile)) {
         com.google.gson.JsonArray array = JsonParser.parseReader(reader).getAsJsonArray();
         for (JsonElement element : array) {
            JsonObject entry = element.getAsJsonObject();
            if (!entry.has("uuid") || !entry.has("name")) {
               continue;
            }
            try {
               UUID id = UUID.fromString(entry.get("uuid").getAsString());
               String name = entry.get("name").getAsString();
               if (name != null && !name.isEmpty()) {
                  cache.put(id, name);
               }
            } catch (Exception ignored) {
            }
         }
      } catch (Exception ex) {
         this.plugin.getLogger().warning("[SM_Stats] Failed to read usercache.json: " + ex.getMessage());
      }
      return cache;
   }

   private StatsSnapshot buildSnapshotFromStatsFile(File statsFile) {
      if (statsFile == null || !statsFile.exists()) {
         return null;
      }

      String fileName = statsFile.getName();
      if (!fileName.endsWith(".json")) {
         return null;
      }
      String uuidStr = fileName.substring(0, fileName.length() - 5);
      UUID uuid;
      try {
         uuid = UUID.fromString(uuidStr);
      } catch (IllegalArgumentException ex) {
         return null;
      }

      JsonObject root;
      try (FileReader reader = new FileReader(statsFile)) {
         root = JsonParser.parseReader(reader).getAsJsonObject();
      } catch (Exception ex) {
         return null;
      }

      if (!root.has("stats")) {
         return null;
      }
      JsonObject stats = root.getAsJsonObject("stats");

      JsonObject custom = stats.has("minecraft:custom") ? stats.getAsJsonObject("minecraft:custom") : null;
      JsonObject mined = stats.has("minecraft:mined") ? stats.getAsJsonObject("minecraft:mined") : null;
      JsonObject used = stats.has("minecraft:used") ? stats.getAsJsonObject("minecraft:used") : null;
      JsonObject crafted = stats.has("minecraft:crafted") ? stats.getAsJsonObject("minecraft:crafted") : null;

      long playtimeTicks = this.readJsonStat(custom, "minecraft:play_one_minute");
      long totalMinutes = Math.max(0L, playtimeTicks / 20L / 60L);

      long deaths = this.readJsonStat(custom, "minecraft:deaths");
      long playerKills = this.readJsonStat(custom, "minecraft:player_kills");
      long mobKills = this.readJsonStat(custom, "minecraft:mob_kills");

      long blocksBroken = this.sumJsonSection(mined, null);
      long blocksPlaced = this.sumJsonSection(used, BLOCK_NAMESPACE_KEYS);
      long itemsCrafted = this.sumJsonSection(crafted, null);

      long distWalk = this.readJsonStat(custom, "minecraft:walk_one_cm")
         + this.readJsonStat(custom, "minecraft:sprint_one_cm")
         + this.readJsonStat(custom, "minecraft:crouch_one_cm")
         + this.readJsonStat(custom, "minecraft:walk_on_water_one_cm")
         + this.readJsonStat(custom, "minecraft:walk_under_water_one_cm");
      long distSwim = this.readJsonStat(custom, "minecraft:swim_one_cm");
      long distFly = this.readJsonStat(custom, "minecraft:fly_one_cm")
         + this.readJsonStat(custom, "minecraft:aviate_one_cm");

      long chatMessages = 0L;
      long achievements = 0L;

      String name = this.userNameCache.getOrDefault(uuid, uuid.toString());
      long lastModified = Math.max(0L, statsFile.lastModified() / 1000L);
      long firstJoin = lastModified;
      long lastJoin = lastModified;
      boolean online = false;

      java.time.LocalDate date = java.time.Instant.now().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
      int monthKey = date.getYear() * 100 + date.getMonthValue();
      int weekKey = date.getYear() * 100 + date.get(java.time.temporal.WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear());

      return new StatsSnapshot(
         uuid,
         name,
         totalMinutes,
         0L,
         0L,
         0L,
         monthKey,
         weekKey,
         lastJoin,
         firstJoin,
         deaths,
         playerKills,
         mobKills,
         blocksBroken,
         blocksPlaced,
         itemsCrafted,
         Math.max(0L, distWalk),
         Math.max(0L, distSwim),
         Math.max(0L, distFly),
         chatMessages,
         achievements,
         online
      );
   }

   private long readJsonStat(JsonObject section, String key) {
      if (section == null || !section.has(key)) {
         return 0L;
      }
      try {
         return Math.max(0L, section.get(key).getAsLong());
      } catch (Exception ex) {
         return 0L;
      }
   }

   private long sumJsonSection(JsonObject section, Set<String> filter) {
      if (section == null) {
         return 0L;
      }
      long total = 0L;
      for (Map.Entry<String, JsonElement> entry : section.entrySet()) {
         if (filter != null && !filter.contains(entry.getKey())) {
            continue;
         }
         try {
            total += Math.max(0L, entry.getValue().getAsLong());
         } catch (Exception ignored) {
         }
      }
      return total;
   }

   private boolean isPlayerdataScanFeatureEnabled() {
      return this.config.getBoolean("import-scan.enabled", true);
   }

   private int getPlayerdataScanBatchSize() {
      int value = this.config.getInt("import-scan.batch-size", 3);
      return Math.max(1, Math.min(100, value));
   }

   private long getPlayerdataScanTickInterval() {
      long value = this.config.getLong("import-scan.tick-interval", 20L);
      return Math.max(1L, value);
   }

   private void notifyScanInitiator(String message) {
      if (message == null || message.isEmpty()) {
         return;
      }
      if (this.playerdataScanInitiatorConsole) {
         Bukkit.getConsoleSender().sendMessage(message);
         return;
      }
      if (this.playerdataScanInitiator == null) {
         return;
      }
      Player player = Bukkit.getPlayer(this.playerdataScanInitiator);
      if (player != null && player.isOnline()) {
         player.sendMessage(message);
      }
   }

   public boolean isTrackEnabled(String key) {
      return this.config.getBoolean("track." + key, true);
   }

   public NamespacedKey getActionKey() {
      return this.actionKey;
   }

   public NamespacedKey getMetricKey() {
      return this.metricKey;
   }

   public NamespacedKey getCategoryKey() {
      return this.categoryKey;
   }

   public NamespacedKey getTargetKey() {
      return this.targetKey;
   }

   public StatsCategory getCategory(String id) {
      if (id == null) {
         return null;
      }
      return this.categoryMap.get(id.toLowerCase());
   }

   public void openStats(Player viewer, String targetName) {
      if (!this.databaseReady) {
         viewer.sendMessage(this.getMessage("db-not-ready", "&[SECONDARY]База данных недоступна."));
         return;
      }
      Player target = this.findOnlinePlayer(targetName);
      if (target != null) {
         if (this.isTrackEnabled("achievements")) {
            this.refreshAchievements(target);
         }
         StatsSnapshot snapshot = this.manager.getSnapshot(target.getUniqueId());
         if (snapshot == null) {
            long nowSec = System.currentTimeMillis() / 1000L;
            snapshot = new StatsSnapshot(
               target.getUniqueId(),
               target.getName(),
               0,
               0,
               0,
               0,
               0,
               0,
               nowSec,
               nowSec,
               0,
               0,
               0,
               0,
               0,
               0,
               0,
               0,
               0,
               0,
               0,
               true
            );
         }
         this.openStatsMenu(viewer, snapshot, true);
         return;
      }

      String taskName = "stats-load-name-" + viewer.getUniqueId();
      this.plugin.getSchedulerManager().runAsync(taskName, () -> {
         StatsSnapshot snapshot = this.database.loadByName(targetName);
         if (snapshot == null) {
            this.plugin.getSchedulerManager().runEntityTask(viewer, "stats-not-found", () -> {
               if (viewer.isOnline()) {
                  viewer.sendMessage(this.getMessage("player-not-found", "&[SECONDARY]Игрок &[MAIN]{player} &[SECONDARY]не найден.")
                     .replace("{player}", targetName));
               }
            });
            return;
         }
         this.plugin.getSchedulerManager().runEntityTask(viewer, "stats-open", () -> {
            if (viewer.isOnline()) {
               this.openStatsMenu(viewer, snapshot, snapshot.isOnline());
            }
         });
      });
   }

   public void openStatsByUuid(Player viewer, UUID targetId) {
      if (!this.databaseReady) {
         viewer.sendMessage(this.getMessage("db-not-ready", "&[SECONDARY]База данных недоступна."));
         return;
      }
      if (targetId == null) {
         return;
      }
      Player target = this.plugin.getServer().getPlayer(targetId);
      if (target != null) {
         StatsSnapshot snapshot = this.manager.getSnapshot(target.getUniqueId());
         if (snapshot == null) {
            long nowSec = System.currentTimeMillis() / 1000L;
            snapshot = new StatsSnapshot(
               target.getUniqueId(),
               target.getName(),
               0,
               0,
               0,
               0,
               0,
               0,
               nowSec,
               nowSec,
               0,
               0,
               0,
               0,
               0,
               0,
               0,
               0,
               0,
               0,
               0,
               true
            );
         }
         this.openStatsMenu(viewer, snapshot, true);
         return;
      }

      String taskName = "stats-load-uuid-" + viewer.getUniqueId();
      this.plugin.getSchedulerManager().runAsync(taskName, () -> {
         StatsSnapshot snapshot = this.database.loadByUuid(targetId);
         if (snapshot == null) {
            this.plugin.getSchedulerManager().runEntityTask(viewer, "stats-not-found", () -> {
               if (viewer.isOnline()) {
                  viewer.sendMessage(this.getMessage("player-not-found", "&[SECONDARY]Игрок &[MAIN]{player} &[SECONDARY]не найден.")
                     .replace("{player}", targetId.toString()));
               }
            });
            return;
         }
         this.plugin.getSchedulerManager().runEntityTask(viewer, "stats-open", () -> {
            if (viewer.isOnline()) {
               this.openStatsMenu(viewer, snapshot, snapshot.isOnline());
            }
         });
      });
   }

   public void openTopSelect(Player viewer) {
      Inventory inventory = Bukkit.createInventory(new TopSelectMenuHolder(), 54, this.getTopCategoriesTitle());
      this.fillBorder(inventory);
      int slotIndex = 0;
      for (StatsCategory category : this.categories) {
         if (this.isAchievementsCategory(category)) {
            continue;
         }
         if (slotIndex >= TOP_SLOTS.length) {
            break;
         }
         ItemStack item = this.createCategoryItem(category);
         inventory.setItem(TOP_SLOTS[slotIndex], item);
         slotIndex++;
      }
      viewer.openInventory(inventory);
   }

   public void openTop(Player viewer, StatsMetric metric, int page) {
      if (!this.databaseReady) {
         viewer.sendMessage(this.getMessage("db-not-ready", "&[SECONDARY]База данных недоступна."));
         return;
      }
      int safePage = Math.max(1, page);
      int limit = this.getTopLimit();
      int offset = (safePage - 1) * limit;

      String taskName = "stats-top-" + metric.getKey() + "-" + safePage + "-" + viewer.getUniqueId();
      this.plugin.getSchedulerManager().runAsync(taskName, () -> {
         List<TopEntry> loaded = this.database.loadTop(metric, limit + 1, offset);
         boolean hasNext = loaded.size() > limit;
         List<TopEntry> entries = hasNext ? new ArrayList<>(loaded.subList(0, limit)) : loaded;
         boolean hasPrev = safePage > 1;
         List<TopEntry> finalEntries = entries;
         boolean finalHasPrev = hasPrev;
         boolean finalHasNext = hasNext;
         this.plugin.getSchedulerManager().runEntityTask(viewer, "stats-top-open", () -> {
            if (!viewer.isOnline()) {
               return;
            }
            Inventory inventory = Bukkit.createInventory(new TopMenuHolder(metric, safePage), 54, this.getTopTitle(metric));
            this.fillBorder(inventory);
            this.fillTopEntries(inventory, metric, finalEntries, safePage, limit, finalHasPrev, finalHasNext);
            viewer.openInventory(inventory);
         });
      });
   }

   public void openCategoryTop(Player viewer, StatsCategory category) {
      if (!this.databaseReady) {
         viewer.sendMessage(this.getMessage("db-not-ready", "&[SECONDARY]База данных недоступна."));
         return;
      }
      if (category == null || category.getEntries().isEmpty()) {
         return;
      }
      if (this.isAchievementsCategory(category)) {
         return;
      }

      String taskName = "stats-category-top-" + category.getId() + "-" + viewer.getUniqueId();
      this.plugin.getSchedulerManager().runAsync(taskName, () -> {
         // Load top entries for each metric in category
         Map<StatsMetric, List<TopEntry>> topData = new HashMap<>();
         for (StatsCategory.Entry entry : category.getEntries()) {
            List<TopEntry> entries = this.database.loadTop(entry.getMetric(), 3, 0); // Top 3 for each
            topData.put(entry.getMetric(), entries);
         }
         
         this.plugin.getSchedulerManager().runEntityTask(viewer, "stats-category-top-open", () -> {
            if (!viewer.isOnline()) {
               return;
            }
            Inventory inventory = Bukkit.createInventory(new TopMenuHolder(null, 1), 54, 
               this.getTopCategoryTitle(category.getTitle()));
            this.fillBorder(inventory);
            this.fillCategoryTopEntries(inventory, category, topData);
            
            // Add back to profile button
            inventory.setItem(49, this.createProfileButton(viewer.getUniqueId()));
            viewer.openInventory(inventory);
         });
      });
   }

   private void fillCategoryTopEntries(Inventory inventory, StatsCategory category, Map<StatsMetric, List<TopEntry>> topData) {
      List<StatsCategory.Entry> entries = category.getEntries();
      int[] headerSlots = {10, 11, 12, 13, 14, 15, 16};
      int[][] playerSlots = {
         {19, 28, 37},
         {20, 29, 38},
         {21, 30, 39},
         {22, 31, 40},
         {23, 32, 41},
         {24, 33, 42},
         {25, 34, 43}
      };

      int visibleColumns = Math.min(entries.size(), headerSlots.length);
      int startOffset = (headerSlots.length - visibleColumns) / 2;

      for (int index = 0; index < visibleColumns; index++) {
         int columnIndex = startOffset + index;
         StatsCategory.Entry entry = entries.get(index);
         if (this.shouldShiftCategoryColumnRight(category, entry)) {
            columnIndex = Math.min(columnIndex + 1, headerSlots.length - 1);
         }
         List<TopEntry> topEntries = topData.get(entry.getMetric());
         if (topEntries == null) {
            topEntries = new ArrayList<>();
         }

         ItemStack header = this.createColumnHeader(entry.getMetric(), entry.getTitle());
         inventory.setItem(headerSlots[columnIndex], header);

         for (int rank = 0; rank < Math.min(topEntries.size(), 3); rank++) {
            TopEntry topEntry = topEntries.get(rank);
            ItemStack item = this.createColumnEntry(entry.getMetric(), topEntry, rank + 1);
            inventory.setItem(playerSlots[columnIndex][rank], item);
         }
      }
   }

   private boolean shouldShiftCategoryColumnRight(StatsCategory category, StatsCategory.Entry entry) {
      if (category == null || entry == null || entry.getMetric() == null) {
         return false;
      }

      String categoryId = category.getId();
      String metricKey = entry.getMetric().getKey();
      if (categoryId == null || metricKey == null) {
         return false;
      }

      String normalizedCategory = categoryId.toLowerCase(Locale.ROOT);
      String normalizedMetric = metricKey.toLowerCase(Locale.ROOT);

      if (normalizedCategory.equals("kills") && normalizedMetric.equals("mob_kills")) {
         return true;
      }
      if (normalizedCategory.equals("blocks") && normalizedMetric.equals("blocks_placed")) {
         return true;
      }
      if (normalizedCategory.equals("distance")
         && (normalizedMetric.equals("dist_fly") || normalizedMetric.equals("total_distance"))) {
         return true;
      }

      return false;
   }

   private ItemStack createColumnHeader(StatsMetric metric, String title) {
      ItemStack item = new ItemStack(metric.getMaterial());
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(this.plugin.applyColors("&[MAIN]§l" + title));
         List<String> lore = new ArrayList<>();
         String description = this.getMetricDescription(metric);
         if (description != null && !description.isEmpty()) {
            lore.add(this.plugin.applyColors("&[SECONDARY]" + description));
         }
         lore.add("");
         lore.add(this.plugin.applyColors("&7&oТоп 3 игроков"));
         lore.add(this.plugin.applyColors("&7&oНажмите: открыть глобальный топ"));
         meta.setLore(lore);
         PersistentDataContainer container = meta.getPersistentDataContainer();
         container.set(this.actionKey, PersistentDataType.STRING, "open-top");
         container.set(this.metricKey, PersistentDataType.STRING, metric.getKey());
         item.setItemMeta(meta);
      }
      return item;
   }

   private ItemStack createColumnEntry(StatsMetric metric, TopEntry entry, int rank) {
      ItemStack item = new ItemStack(Material.PLAYER_HEAD);
      ItemMeta rawMeta = item.getItemMeta();
      if (rawMeta == null) {
         return item;
      }
      
      String name = entry.getName() == null || entry.getName().isEmpty() ? entry.getUuid().toString() : entry.getName();
      String title = this.plugin.applyColors("&[MAIN]#{rank} §f{player}")
         .replace("{rank}", Integer.toString(rank))
         .replace("{player}", name);
      
      if (rawMeta instanceof SkullMeta meta) {
         this.applyTextureToSkull(meta, entry.getUuid(), name);
         meta.setDisplayName(title);
         List<String> loreTemplate = this.gui.getStringList("text.top-column-lore");
         List<String> lore = new ArrayList<>();
         for (String line : loreTemplate) {
            line = line.replace("{metric}", this.getMetricTitle(metric));
            line = line.replace("{value}", this.formatMetricValue(metric, entry.getValue()));
            line = line.replace("{rank}", Integer.toString(rank));
            lore.add(this.plugin.applyColors(line));
         }
         meta.setLore(lore);
         PersistentDataContainer container = meta.getPersistentDataContainer();
         container.set(this.actionKey, PersistentDataType.STRING, "open-stats");
         container.set(this.targetKey, PersistentDataType.STRING, entry.getUuid().toString());
         item.setItemMeta(meta);
         return item;
      }
      
      rawMeta.setDisplayName(title);
      item.setItemMeta(rawMeta);
      return item;
   }

   private ItemStack createProfileButton(UUID viewerUuid) {
      String materialName = this.gui.getString("buttons.profile.material", "PLAYER_HEAD");
      Material material = Material.getMaterial(materialName);
      if (material == null) {
         material = Material.PLAYER_HEAD;
      }
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(this.getGuiMessage("text.profile-button-name", "&[MAIN]Мой профиль"));
         List<String> loreLines = this.gui.getStringList("text.profile-button-lore");
         List<String> lore = new ArrayList<>();
         if (loreLines.isEmpty()) {
            lore.add(this.getGuiMessage("text.profile-button-lore", "&[SECONDARY]Вернуться к статистике"));
         } else {
            for (String line : loreLines) {
               lore.add(this.plugin.applyColors(line));
            }
         }
         meta.setLore(lore);
         PersistentDataContainer container = meta.getPersistentDataContainer();
         container.set(this.actionKey, PersistentDataType.STRING, "open-stats");
         container.set(this.targetKey, PersistentDataType.STRING, viewerUuid.toString());
         item.setItemMeta(meta);
      }
      return item;
   }

   private String getMetricDescription(StatsMetric metric) {
      if (metric == null) {
         return "";
      }
      String desc = this.gui != null ? this.gui.getString("metric-descriptions." + metric.getKey()) : null;
      return desc == null ? "" : desc;
   }

   private String getTopCategoryTitle(String categoryName) {
      String title = this.gui.getString("menus.top.title", "&d&lТОП &7• &f{category}");
      title = this.sanitizeGuiText(title)
         .replace("{category}", categoryName == null ? "" : categoryName)
         .replace("{metric}", categoryName == null ? "" : categoryName);
      return this.plugin.applyColors(title);
   }

   public void openCategoryMenu(Player viewer, StatsCategory category) {
      if (category == null) {
         return;
      }
      Inventory inventory = Bukkit.createInventory(new TopSelectMenuHolder(), 54, this.getTopSubcategoryTitle(category.getTitle()));
      this.fillBorder(inventory);
      int slotIndex = 0;
      for (StatsCategory.Entry entry : category.getEntries()) {
         if (slotIndex >= TOP_SLOTS.length) {
            break;
         }
         ItemStack item = this.createMetricSelectItem(entry.getMetric(), entry.getTitle());
         inventory.setItem(TOP_SLOTS[slotIndex], item);
         slotIndex++;
      }
      inventory.setItem(49, this.createNavItem("back-categories", this.getGuiMessage("text.nav-back", "&[SECONDARY]Назад"), this.getNavMaterial("back-categories")));
      viewer.openInventory(inventory);
   }

   public void openTimePeriodMenu(Player viewer, StatsCategory category, UUID profileUuid) {
      if (category == null) {
         return;
      }
      String title = this.gui.getString("menus.period-select.title", "&d&lТОП &7• &fВыбор периода");
      title = this.sanitizeGuiText(title)
         .replace("{category}", category.getTitle() == null ? "" : category.getTitle())
         .replace("{metric}", this.getMetricTitle(StatsMetric.PLAYTIME));
      Inventory inventory = Bukkit.createInventory(new TimePeriodMenuHolder(category), 54, this.plugin.applyColors(title));
      this.fillBorder(inventory);
      
      // Create period selection items
      int[] slots = {20, 13, 24, 31}; // 4 periods
      StatsMetric[] metrics = {StatsMetric.PLAYTIME, StatsMetric.MONTHLY_MINUTES, StatsMetric.WEEKLY_MINUTES, StatsMetric.DAILY_MINUTES};
      String[] periodKeys = {"period-all-time", "period-month", "period-week", "period-day"};
      
      for (int i = 0; i < metrics.length && i < slots.length; i++) {
         ItemStack item = this.createPeriodSelectItem(metrics[i], periodKeys[i]);
         inventory.setItem(slots[i], item);
      }
      
      // Add back button
      inventory.setItem(49, this.createNavItem("back-to-profile", this.getGuiMessage("text.nav-back", "&[SECONDARY]Назад"), this.getNavMaterial("back-to-profile")));
      if (profileUuid != null) {
         ItemMeta meta = inventory.getItem(49).getItemMeta();
         if (meta != null) {
            meta.getPersistentDataContainer().set(this.targetKey, PersistentDataType.STRING, profileUuid.toString());
            inventory.getItem(49).setItemMeta(meta);
         }
      }
      
      viewer.openInventory(inventory);
   }

   private ItemStack createPeriodSelectItem(StatsMetric metric, String periodKey) {
      ItemStack item = new ItemStack(metric.getMaterial());
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         String displayName = this.getGuiMessage("text." + periodKey, "&[MAIN]" + this.getMetricTitle(metric));
         meta.setDisplayName(this.plugin.applyColors(displayName));
         List<String> lore = new ArrayList<>();
         List<String> loreTemplate = this.gui.getStringList("text.period-select-lore");
         if (loreTemplate == null || loreTemplate.isEmpty()) {
            String hint = this.getGuiMessage("text.period-select-hint", "&7&oВыберите период для просмотра топа");
            lore.add(this.plugin.applyColors(this.sanitizeGuiText(hint)));
         } else {
            for (String line : loreTemplate) {
               String processed = this.sanitizeGuiText(line)
                  .replace("{period}", this.getGuiMessage("text." + periodKey, this.getMetricTitle(metric)))
                  .replace("{metric}", this.getMetricTitle(metric));
               lore.add(this.plugin.applyColors(processed));
            }
         }
         meta.setLore(lore);
         PersistentDataContainer container = meta.getPersistentDataContainer();
         container.set(this.actionKey, PersistentDataType.STRING, "open-top");
         container.set(this.metricKey, PersistentDataType.STRING, metric.getKey());
         item.setItemMeta(meta);
      }
      return item;
   }

   public void resetPlayer(Player sender, String targetName) {
      if (!this.databaseReady) {
         sender.sendMessage(this.getMessage("db-not-ready", "&[SECONDARY]База данных недоступна."));
         return;
      }
      String taskName = "stats-reset-" + targetName + "-" + sender.getUniqueId();
      this.plugin.getSchedulerManager().runAsync(taskName, () -> {
         StatsSnapshot snapshot = this.database.loadByName(targetName);
         if (snapshot == null) {
            this.plugin.getSchedulerManager().runEntityTask(sender, "stats-reset-notfound", () -> {
               if (sender.isOnline()) {
                  sender.sendMessage(this.getMessage("player-not-found", "&[SECONDARY]Игрок &[MAIN]{player} &[SECONDARY]не найден.")
                     .replace("{player}", targetName));
               }
            });
            return;
         }
        this.database.resetPlayer(snapshot.getUuid());
        Player online = this.findOnlinePlayer(snapshot.getName());
        if (online != null && this.manager != null) {
            this.manager.resetData(online.getUniqueId(), online.getName());
        }
         this.plugin.getSchedulerManager().runEntityTask(sender, "stats-reset-done", () -> {
            if (sender.isOnline()) {
               sender.sendMessage(this.getMessage("reset.done", "&[SECONDARY]Статистика игрока &[MAIN]{player} &[SECONDARY]сброшена.")
                  .replace("{player}", snapshot.getName()));
            }
         });
      });
   }

   public void resetAll(Player sender) {
      if (!this.databaseReady) {
         sender.sendMessage(this.getMessage("db-not-ready", "&[SECONDARY]База данных недоступна."));
         return;
      }
      this.plugin.getSchedulerManager().runAsync("stats-reset-all", () -> {
         this.database.resetAll();
         this.plugin.getSchedulerManager().runEntityTask(sender, "stats-reset-all-done", () -> {
            if (sender.isOnline()) {
               sender.sendMessage(this.getMessage("reset.all-done", "&[SECONDARY]Вся статистика сброшена."));
            }
            if (this.manager != null) {
               this.manager.clearAll();
               for (Player player : this.plugin.getServer().getOnlinePlayers()) {
                  this.manager.resetData(player.getUniqueId(), player.getName());
               }
            }
         });
      });
   }

   private void openStatsMenu(Player viewer, StatsSnapshot snapshot, boolean online) {
      Inventory inventory = Bukkit.createInventory(new StatsMenuHolder(snapshot.getUuid()), 54, this.getStatsTitle(snapshot.getName()));
      this.fillBorder(inventory);
      inventory.setItem(this.getHeadSlot(), this.createPlayerHead(snapshot, online));

      int index = 0;
      for (StatsCategory category : this.categories) {
         if (index >= STAT_SLOTS.length) {
            break;
         }
         ItemStack item = this.createCategoryItemForProfile(category, snapshot);
         inventory.setItem(STAT_SLOTS[index], item);
         index++;
      }

      viewer.openInventory(inventory);
   }

   private void fillTopEntries(Inventory inventory, StatsMetric metric, List<TopEntry> entries, int page, int limit, boolean hasPrev, boolean hasNext) {
      int index = 0;
      int rankStart = (page - 1) * limit + 1;
      for (TopEntry entry : entries) {
         if (index >= TOP_SLOTS.length) {
            break;
         }
         int rank = rankStart + index;
         ItemStack item = this.createTopEntryItem(metric, entry, rank);
         inventory.setItem(TOP_SLOTS[index], item);
         index++;
      }

      if (hasPrev) {
         inventory.setItem(45, this.createNavItem("top-prev", this.getGuiMessage("text.nav-prev", "&[SECONDARY]Предыдущая"), this.getNavMaterial("top-prev")));
      }
      inventory.setItem(49, this.createNavItem("top-back", this.getGuiMessage("text.nav-back", "&[SECONDARY]Назад"), this.getNavMaterial("top-back")));
      if (hasNext) {
         inventory.setItem(53, this.createNavItem("top-next", this.getGuiMessage("text.nav-next", "&[SECONDARY]Следующая"), this.getNavMaterial("top-next")));
      }
   }

   private ItemStack createNavItem(String action, String title, Material material) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(this.plugin.applyColors(title));
         meta.getPersistentDataContainer().set(this.actionKey, PersistentDataType.STRING, action);
         item.setItemMeta(meta);
      }
      return item;
   }

   private Material getNavMaterial(String action) {
      String buttonType = "nav-back"; // default
      if (action.contains("prev")) {
         buttonType = "nav-prev";
      } else if (action.contains("next")) {
         buttonType = "nav-next";
      }
      String materialName = this.gui.getString("buttons." + buttonType + ".material");
      if (materialName != null) {
         Material material = Material.getMaterial(materialName);
         if (material != null) {
            return material;
         }
      }
      // Fallback
      if (action.contains("prev") || action.contains("next")) {
         return Material.ARROW;
      }
      return Material.BARRIER;
   }

   private ItemStack createTopButton() {
      ItemStack item = new ItemStack(Material.NETHER_STAR);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(this.getGuiMessage("text.top-button-name", "&[MAIN]Топы"));
         List<String> lore = new ArrayList<>();
         lore.add(this.getGuiMessage("text.top-button-lore", "&[SECONDARY]Открыть рейтинг"));
         meta.setLore(lore);
         meta.getPersistentDataContainer().set(this.actionKey, PersistentDataType.STRING, "top-select");
         item.setItemMeta(meta);
      }
      return item;
   }

   private ItemStack createMetricItem(StatsMetric metric, StatsSnapshot snapshot) {
      ItemStack item = new ItemStack(metric.getMaterial());
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(this.plugin.applyColors("&[MAIN]" + this.getMetricTitle(metric)));
         List<String> lore = new ArrayList<>();
         String valueLine = this.getGuiMessage("text.metric-value", "&[SECONDARY]Значение: &[MAIN]{value}")
            .replace("{value}", this.getMetricValue(metric, snapshot));
         lore.add(valueLine);
         lore.add(this.getGuiMessage("text.metric-click", "&[SECONDARY]Клик: открыть топ"));
         meta.setLore(lore);
         PersistentDataContainer container = meta.getPersistentDataContainer();
         container.set(this.actionKey, PersistentDataType.STRING, "open-top");
         container.set(this.metricKey, PersistentDataType.STRING, metric.getKey());
         item.setItemMeta(meta);
      }
      return item;
   }

   private ItemStack createMetricSelectItem(StatsMetric metric) {
      return this.createMetricSelectItem(metric, this.getMetricTitle(metric));
   }

   private ItemStack createMetricSelectItem(StatsMetric metric, String displayName) {
      ItemStack item = new ItemStack(metric.getMaterial());
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(this.plugin.applyColors("&[MAIN]" + displayName));
         List<String> lore = new ArrayList<>();
         lore.add(this.getGuiMessage("text.metric-open-top", "&[SECONDARY]Открыть топ"));
         meta.setLore(lore);
         PersistentDataContainer container = meta.getPersistentDataContainer();
         container.set(this.actionKey, PersistentDataType.STRING, "open-top");
         container.set(this.metricKey, PersistentDataType.STRING, metric.getKey());
         item.setItemMeta(meta);
      }
      return item;
   }

   private ItemStack createCategoryItem(StatsCategory category) {
      Material material = category.getIcon() != null ? category.getIcon() : Material.BOOK;
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      if (meta == null) {
         return item;
      }
      meta.setDisplayName(this.plugin.applyColors("&[MAIN]" + category.getTitle()));
      List<String> lore = new ArrayList<>();
      lore.add(this.getGuiMessage("text.category-title", "&[MAIN]{category}")
         .replace("{category}", category.getTitle()));
      for (StatsCategory.Entry entry : category.getEntries()) {
         lore.add(this.getGuiMessage("text.category-entry", "&[SECONDARY]{name}:")
            .replace("{name}", entry.getTitle()));
      }
      lore.add("");
      lore.add(this.getGuiMessage("text.category-hint", "&7&oНажмите чтобы открыть топ"));
      meta.setLore(lore);
      PersistentDataContainer container = meta.getPersistentDataContainer();
      if (category.hasSubMenu()) {
         container.set(this.actionKey, PersistentDataType.STRING, "open-category");
         container.set(this.categoryKey, PersistentDataType.STRING, category.getId());
      } else if (!category.getEntries().isEmpty()) {
         container.set(this.actionKey, PersistentDataType.STRING, "open-top");
         container.set(this.metricKey, PersistentDataType.STRING, category.getEntries().get(0).getMetric().getKey());
      }
      item.setItemMeta(meta);
      return item;
   }

   private ItemStack createCategoryItemForProfile(StatsCategory category, StatsSnapshot snapshot) {
      Material material = category.getIcon() != null ? category.getIcon() : Material.BOOK;
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      if (meta == null) {
         return item;
      }
      
      meta.setDisplayName(this.plugin.applyColors("&[MAIN]" + category.getTitle()));
      
      List<String> lore = new ArrayList<>();
      
      // Add subtitle if present
      if (category.getSubtitle() != null && !category.getSubtitle().isEmpty()) {
         String subtitle = this.getGuiMessage("text.category-subtitle", "&[SECONDARY]{subtitle}")
            .replace("{subtitle}", category.getSubtitle());
         lore.add(this.plugin.applyColors(subtitle));
         lore.add("");
      }
      
      // Add metrics from category
      for (StatsCategory.Entry entry : category.getEntries()) {
         String line = "&[SECONDARY]" + entry.getTitle() + ": &[MAIN]" + this.getMetricValue(entry.getMetric(), snapshot);
         lore.add(this.plugin.applyColors(line));
      }
      
      if (!this.isAchievementsCategory(category)) {
         lore.add("");
         lore.add(this.getGuiMessage("text.category-hint", "&7&oНажмите чтобы открыть топ"));
      }
      meta.setLore(lore);
      
      PersistentDataContainer container = meta.getPersistentDataContainer();
      if (!this.isAchievementsCategory(category)) {
         container.set(this.actionKey, PersistentDataType.STRING, "open-category-top");
         container.set(this.categoryKey, PersistentDataType.STRING, category.getId());
      }
      item.setItemMeta(meta);
      return item;
   }

   private ItemStack createTopEntryItem(StatsMetric metric, TopEntry entry, int rank) {
      ItemStack item = new ItemStack(Material.PLAYER_HEAD);
      ItemMeta rawMeta = item.getItemMeta();
      if (rawMeta == null) {
         return item;
      }
      String name = entry.getName() == null || entry.getName().isEmpty() ? entry.getUuid().toString() : entry.getName();
      String title = this.plugin.applyColors("&[MAIN]#{rank} &[SECONDARY]{player}")
         .replace("{rank}", Integer.toString(rank))
         .replace("{player}", name);

      List<String> loreTemplate = this.gui.getStringList("text.top-entry-lore");
      List<String> loreLines = new ArrayList<>();
      if (loreTemplate == null || loreTemplate.isEmpty()) {
         String line = "&[SECONDARY]{metric}: &[MAIN]{value}";
         line = line.replace("{metric}", this.getMetricTitle(metric))
            .replace("{value}", this.formatMetricValue(metric, entry.getValue()));
         loreLines.add(this.plugin.applyColors(line));
      } else {
         for (String tmpl : loreTemplate) {
            String line = tmpl.replace("{metric}", this.getMetricTitle(metric))
               .replace("{value}", this.formatMetricValue(metric, entry.getValue()))
               .replace("{rank}", Integer.toString(rank))
               .replace("{player}", name);
            loreLines.add(this.plugin.applyColors(line));
         }
      }

      if (rawMeta instanceof SkullMeta meta) {
         this.applyTextureToSkull(meta, entry.getUuid(), name);
         meta.setDisplayName(title);
         meta.setLore(loreLines);
         PersistentDataContainer container = meta.getPersistentDataContainer();
         container.set(this.actionKey, PersistentDataType.STRING, "open-stats");
         container.set(this.targetKey, PersistentDataType.STRING, entry.getUuid().toString());
         item.setItemMeta(meta);
         return item;
      }

      rawMeta.setDisplayName(title);
      rawMeta.setLore(loreLines);
      PersistentDataContainer container = rawMeta.getPersistentDataContainer();
      container.set(this.actionKey, PersistentDataType.STRING, "open-stats");
      container.set(this.targetKey, PersistentDataType.STRING, entry.getUuid().toString());
      item.setItemMeta(rawMeta);
      return item;
   }

   private ItemStack createPlayerHead(StatsSnapshot snapshot, boolean online) {
      ItemStack head = new ItemStack(Material.PLAYER_HEAD);
      ItemMeta rawMeta = head.getItemMeta();
      if (!(rawMeta instanceof SkullMeta meta)) {
         return head;
      }
      String name = snapshot.getName() == null ? "" : snapshot.getName();
      
      // Apply head name from config with placeholders
      String headName = this.getGuiMessage("menus.stats.head-name", "&[MAIN]{player}");
      headName = this.replacePlaceholders(headName, snapshot).replace("{player}", name);
      meta.setDisplayName(this.plugin.applyColors(headName));
      
      // Load texture via Mojang API
      this.applyTextureToSkull(meta, snapshot.getUuid(), name);
      
      // Load and apply lore from config with placeholders  
      List<String> loreLines = this.gui.getStringList("menus.stats.head-lore");
      List<String> lore = new ArrayList<>();
      String status = online
         ? this.getGuiMessage("text.online", "&[MAIN]Онлайн")
         : this.getGuiMessage("text.offline", "&[SECONDARY]Оффлайн");
      
      for (String line : loreLines) {
         line = this.replacePlaceholders(line, snapshot);
         line = line.replace("{status}", status);
         line = line.replace("{last_join}", this.formatLastSeen(snapshot.getLastJoin()));
         lore.add(this.plugin.applyColors(line));
      }
      
      meta.setLore(lore);
      head.setItemMeta(meta);
      return head;
   }

   private void applyTextureToSkull(SkullMeta meta, UUID uuid) {
      this.applyTextureToSkull(meta, uuid, null);
   }

   private void applyTextureToSkull(SkullMeta meta, UUID uuid, String playerName) {
      if (meta == null || uuid == null) {
         return;
      }
      try {
         OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
         meta.setOwningPlayer(offlinePlayer);
      } catch (Exception e) {
         this.plugin.getDebugSystem().logError("Failed to apply skull texture", e);
      }
   }

   private boolean isAchievementsCategory(StatsCategory category) {
      return category != null && "achievements".equalsIgnoreCase(category.getId());
   }

   public void refreshAchievements(Player player) {
      if (player == null || this.manager == null) {
         return;
      }
      long count = this.countCompletedAdvancements(player);
      this.manager.setAchievements(player, count);
   }


   private long countCompletedAdvancements(Player player) {
      if (player == null) {
         return 0L;
      }
      long completed = 0L;
      var iterator = Bukkit.advancementIterator();
      while (iterator.hasNext()) {
         Advancement advancement = iterator.next();
         if (advancement == null || advancement.getKey() == null) {
            continue;
         }
         String key = advancement.getKey().getKey();
         if (key != null && key.startsWith("recipes/")) {
            continue;
         }
         AdvancementProgress progress = player.getAdvancementProgress(advancement);
         if (progress != null && progress.isDone()) {
            completed++;
         }
      }
      return completed;
   }

   private String getMetricValue(StatsMetric metric, StatsSnapshot snapshot) {
      return switch (metric) {
         case PLAYTIME -> formatMinutes(snapshot.getTotalMinutes());
         case DAILY_MINUTES -> formatMinutes(snapshot.getDailyMinutes());
         case WEEKLY_MINUTES -> formatMinutes(snapshot.getWeeklyMinutes());
         case MONTHLY_MINUTES -> formatMinutes(snapshot.getMonthlyMinutes());
         case DEATHS -> Long.toString(snapshot.getDeaths());
         case PLAYER_KILLS -> Long.toString(snapshot.getPlayerKills());
         case MOB_KILLS -> Long.toString(snapshot.getMobKills());
         case BLOCKS_BROKEN -> Long.toString(snapshot.getBlocksBroken());
         case BLOCKS_PLACED -> Long.toString(snapshot.getBlocksPlaced());
         case ITEMS_CRAFTED -> Long.toString(snapshot.getItemsCrafted());
         case DIST_WALK -> formatDistance(snapshot.getDistWalkCenti());
         case DIST_SWIM -> formatDistance(snapshot.getDistSwimCenti());
         case DIST_FLY -> formatDistance(snapshot.getDistFlyCenti());
         case CHAT_MESSAGES -> Long.toString(snapshot.getChatMessages());
         case TOTAL_DISTANCE -> formatDistance(snapshot.getTotalDistanceCenti());
         case ACHIEVEMENTS -> Long.toString(snapshot.getAchievements());
      };
   }

   private String formatMetricValue(StatsMetric metric, double value) {
      if (metric.isTime()) {
         return formatMinutes(Math.round(value));
      }
      if (metric.isDistance()) {
         return String.format(Locale.US, "%.2f", value);
      }
      return String.format(Locale.US, "%.0f", value);
   }

   private String formatDistance(long centi) {
      double blocks = centi / 100.0D;
      return String.format(Locale.US, "%.2f", blocks);
   }

   private String formatMinutes(long minutes) {
      long hours = minutes / 60;
      long mins = minutes % 60;
      return String.format(Locale.US, "%d:%02d", hours, mins);
   }

   private String formatLastSeen(long lastSeenSeconds) {
      if (lastSeenSeconds <= 0L) {
         return "-";
      }
      SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US);
      return format.format(new Date(lastSeenSeconds * 1000L));
   }

   private String getStatsTitle(String playerName) {
      String title = this.gui.getString("menus.stats.title", "&d&lСТАТИСТИКА &7• &f{player}");
      title = this.sanitizeGuiText(title).replace("{player}", playerName == null ? "" : playerName);
      return this.plugin.applyColors(title);
   }

   private String getTopTitle(StatsMetric metric) {
      String title = this.gui.getString("menus.top.title", "&d&lТОП &7• &f{metric}");
      String metricTitle = this.getMetricTitle(metric);
      title = this.sanitizeGuiText(title)
         .replace("{metric}", metricTitle)
         .replace("{category}", metricTitle);
      return this.plugin.applyColors(title);
   }

   private String getTopCategoriesTitle() {
      String title = this.gui.getString("menus.categories.title", "&d&lТОП &7• &fКатегории");
      return this.plugin.applyColors(this.sanitizeGuiText(title));
   }

   private String getTopSubcategoryTitle(String category) {
      String title = this.gui.getString("menus.subcategories.title", "&d&lТОП &7• &f{category}");
      title = this.sanitizeGuiText(title)
         .replace("{category}", category == null ? "" : category)
         .replace("{metric}", category == null ? "" : category);
      return this.plugin.applyColors(title);
   }

   private Material getBorderMaterial() {
      String name = this.gui.getString("menus.border-material", "LIGHT_GRAY_STAINED_GLASS_PANE");
      Material material = Material.matchMaterial(name);
      return material != null ? material : Material.LIGHT_GRAY_STAINED_GLASS_PANE;
   }

   private int getHeadSlot() {
      int slot = this.gui.getInt("menus.stats.head-slot", 4);
      return Math.max(0, Math.min(53, slot));
   }

   private void fillBorder(Inventory inventory) {
      ItemStack glass = new ItemStack(this.getBorderMaterial());
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

   private int getTopLimit() {
      int limit = this.config.getInt("settings.top-limit", 10);
      return Math.max(1, Math.min(limit, 50));
   }

   private void loadCategories() {
      this.categories = new ArrayList<>();
      this.categoryMap = new HashMap<>();

      if (this.gui != null) {
         List<Map<?, ?>> list = this.gui.getMapList("categories");
         int index = 0;
         for (Map<?, ?> raw : list) {
            if (raw == null) {
               continue;
            }
            String id = toString(raw.get("id"));
            if (id == null || id.isEmpty()) {
               id = "category-" + index;
            }
            String title = toString(raw.get("title"));
            if (title == null || title.isEmpty()) {
               title = id;
            }
            title = this.sanitizeGuiText(title);
            String subtitle = toString(raw.get("subtitle"));
            subtitle = this.sanitizeGuiText(subtitle);
            Material icon = Material.matchMaterial(toString(raw.get("icon")));
            if (icon == null) {
               icon = Material.BOOK;
            }
            List<StatsCategory.Entry> entries = new ArrayList<>();
            Object metricsObj = raw.get("metrics");
            if (metricsObj instanceof List<?> metricsList) {
               for (Object entryObj : metricsList) {
                  if (entryObj instanceof String key) {
                     StatsMetric metric = StatsMetric.fromKey(key);
                     if (metric != null) {
                        entries.add(new StatsCategory.Entry(metric, this.getMetricTitle(metric)));
                     }
                     continue;
                  }
                  if (entryObj instanceof Map<?, ?> map) {
                     String key = toString(map.get("key"));
                     StatsMetric metric = StatsMetric.fromKey(key);
                     if (metric == null) {
                        continue;
                     }
                     String name = toString(map.get("name"));
                     if (name == null || name.isEmpty()) {
                        name = this.getMetricTitle(metric);
                     }
                     name = this.sanitizeGuiText(name);
                     entries.add(new StatsCategory.Entry(metric, name));
                  }
               }
            }
            if (entries.isEmpty()) {
               continue;
            }
            StatsCategory category = new StatsCategory(id.toLowerCase(), title, subtitle, icon, entries);
            this.categories.add(category);
            this.categoryMap.put(category.getId(), category);
            index++;
         }
      }

      if (this.categories.isEmpty()) {
         this.categories = this.createDefaultCategories();
         for (StatsCategory category : this.categories) {
            this.categoryMap.put(category.getId(), category);
         }
      }
   }

   private List<StatsCategory> createDefaultCategories() {
      List<StatsCategory> list = new ArrayList<>();
      list.add(new StatsCategory("time", "Время в игре", Material.CLOCK, List.of(
         new StatsCategory.Entry(StatsMetric.PLAYTIME, "За все время"),
         new StatsCategory.Entry(StatsMetric.MONTHLY_MINUTES, "За месяц"),
         new StatsCategory.Entry(StatsMetric.WEEKLY_MINUTES, "За неделю"),
         new StatsCategory.Entry(StatsMetric.DAILY_MINUTES, "За день")
      )));
      list.add(new StatsCategory("kills", "Убийства", Material.DIAMOND_SWORD, List.of(
         new StatsCategory.Entry(StatsMetric.PLAYER_KILLS, "Игроков"),
         new StatsCategory.Entry(StatsMetric.MOB_KILLS, "Мобов")
      )));
      list.add(new StatsCategory("deaths", "Смерти", Material.SKELETON_SKULL, List.of(
         new StatsCategory.Entry(StatsMetric.DEATHS, "Смерти")
      )));
      list.add(new StatsCategory("blocks", "Блоки", Material.DIAMOND_PICKAXE, List.of(
         new StatsCategory.Entry(StatsMetric.BLOCKS_BROKEN, "Сломанные"),
         new StatsCategory.Entry(StatsMetric.BLOCKS_PLACED, "Поставленные")
      )));
      list.add(new StatsCategory("crafts", "Крафты", Material.CRAFTING_TABLE, List.of(
         new StatsCategory.Entry(StatsMetric.ITEMS_CRAFTED, "Крафты")
      )));
      list.add(new StatsCategory("messages", "Сообщения", Material.PAPER, List.of(
         new StatsCategory.Entry(StatsMetric.CHAT_MESSAGES, "Сообщения")
      )));
      list.add(new StatsCategory("distance", "Дистанция", Material.COMPASS, List.of(
         new StatsCategory.Entry(StatsMetric.TOTAL_DISTANCE, "Общая дистанция"),
         new StatsCategory.Entry(StatsMetric.DIST_WALK, "Дистанция пешком"),
         new StatsCategory.Entry(StatsMetric.DIST_SWIM, "Дистанция вплавь"),
         new StatsCategory.Entry(StatsMetric.DIST_FLY, "Дистанция в полете")
      )));
      list.add(new StatsCategory("achievements", "Достижения", Material.NETHER_STAR, List.of(
         new StatsCategory.Entry(StatsMetric.ACHIEVEMENTS, "Достижения")
      )));
      return list;
   }

   private String replacePlaceholders(String text, StatsSnapshot snapshot) {
      if (text == null || snapshot == null) {
         return text;
      }
      String result = text;
      for (StatsMetric metric : StatsMetric.values()) {
         String placeholder = "{" + metric.getKey() + "}";
         if (result.contains(placeholder)) {
            result = result.replace(placeholder, getMetricValue(metric, snapshot));
         }
      }
      result = result.replace("{player}", snapshot.getName() == null ? "" : snapshot.getName());
      result = result.replace("{status}", ""); // будет заменено позже
      result = result.replace("{last_join}", formatLastSeen(snapshot.getLastJoin()));
      return result;
   }

   private String toString(Object value) {
      return value == null ? null : value.toString();
   }

   private String sanitizeGuiText(String text) {
      if (text == null || text.isEmpty()) {
         return text;
      }
      String sanitized = text
         .replace("Динамический", "")
         .replace("динамический", "")
         .replace("Dynamic", "")
         .replace("dynamic", "");
      while (sanitized.contains("  ")) {
         sanitized = sanitized.replace("  ", " ");
      }
      return sanitized.trim();
   }

   private List<StatsMetric> getSortedMetrics() {
      List<StatsMetric> metrics = new ArrayList<>(List.of(StatsMetric.values()));
      metrics.sort(Comparator.comparingInt(this::getMetricPriority)
         .thenComparing(this::getMetricTitle, String.CASE_INSENSITIVE_ORDER));
      return metrics;
   }

   private int getMetricPriority(StatsMetric metric) {
      return switch (metric) {
         case PLAYTIME -> 10;
         case DAILY_MINUTES -> 11;
         case WEEKLY_MINUTES -> 12;
         case MONTHLY_MINUTES -> 13;
         case DEATHS -> 20;
         case PLAYER_KILLS -> 21;
         case MOB_KILLS -> 22;
         case BLOCKS_BROKEN -> 30;
         case BLOCKS_PLACED -> 31;
         case ITEMS_CRAFTED -> 32;
         case CHAT_MESSAGES -> 40;
         case TOTAL_DISTANCE -> 50;
         case DIST_WALK -> 51;
         case DIST_SWIM -> 52;
         case DIST_FLY -> 53;
         case ACHIEVEMENTS -> 60;
      };
   }

   private long getMoveIntervalMillis() {
      long value = this.config.getLong("settings.move-interval-ms", 500L);
      return Math.max(100L, value);
   }

   private void startSaveTask() {
      long seconds = this.config.getLong("settings.save-interval-seconds", 120L);
      long period = Math.max(20L, seconds * 20L);
      this.plugin.getSchedulerManager().runAsyncTimer("stats-save", () -> {
         if (this.manager != null) {
            this.manager.saveAllAsync();
         }
      }, period, period);
   }

   private Player findOnlinePlayer(String name) {
      if (name == null || name.isEmpty()) {
         return null;
      }
      Player exact = this.plugin.getServer().getPlayerExact(name);
      if (exact != null) {
         return exact;
      }
      for (Player player : this.plugin.getServer().getOnlinePlayers()) {
         if (player.getName().equalsIgnoreCase(name)) {
            return player;
         }
      }
      return null;
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
         this.plugin.getDebugSystem().logError("Stats command registration failed", e);
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
