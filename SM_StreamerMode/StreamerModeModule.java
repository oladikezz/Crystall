package net.schalker.SMPS.modules.streamermode;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.InheritanceNode;
import net.schalker.SMPS.SMPS;
import net.schalker.SMPS.core.command.ModuleCommand;
import net.schalker.SMPS.core.module.BaseModule;
import net.schalker.SMPS.core.module.ModuleInfo;
import net.schalker.SMPS.modules.streamermode.commands.StreamCommand;
import net.schalker.SMPS.modules.streamermode.gui.StreamMenuHolder;
import net.schalker.SMPS.modules.streamermode.hooks.StreamWebhook;
import net.schalker.SMPS.modules.streamermode.listeners.StreamChatListener;
import net.schalker.SMPS.modules.streamermode.listeners.StreamMenuListener;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;

public class StreamerModeModule extends BaseModule {
   private static final String MODULE_NAME = "SM_StreamerMode";
   private static final String DEFAULT_BANNED_WORDS_FILE = "banned_words.txt";
   private static final List<String> DEFAULT_BANNED_WORDS_TEMPLATE = List.of(
      "# StreamerMode chat filter",
      "# One forbidden word or phrase per line. Empty lines and lines starting with # are ignored.",
      "# Apply changes without restarting: /stream reload banned",
      "badword1",
      "badword2",
      "spam phrase",
      "example-server.net",
      "forbidden phrase"
   );

   private FileConfiguration config;
   private FileConfiguration messages;
   private volatile List<String> bannedWords = List.of();
   private volatile List<Pattern> bannedWordPatterns = List.of();
   private final Set<UUID> streamEnabled = ConcurrentHashMap.newKeySet();
   private final Set<UUID> chatFilterEnabled = ConcurrentHashMap.newKeySet();
   private final Map<UUID, String> channelLinks = new ConcurrentHashMap<>();
   private final Set<String> registeredCommandNames = new HashSet<>();

   private StreamMenuListener menuListener;
   private StreamChatListener chatListener;
   private StreamWebhook streamWebhook;
   private LuckPerms luckPerms;
   private File dataFile;
   private File bannedWordsFile;

   public StreamerModeModule(SMPS plugin) {
      super(plugin, new ModuleInfo(MODULE_NAME, "1.3.1", "MeXaNoBoP & nukshndev", "Streamer mode with LP group, menu and chat filter"));
   }

   @Override
   public void onEnable() {
      super.onEnable();
      loadConfigs();
      reloadBannedWords();
      resolveLuckPerms();
      loadData();

      this.streamWebhook = new StreamWebhook(this.plugin, this);

      this.menuListener = new StreamMenuListener(this.plugin, this);
      this.chatListener = new StreamChatListener(this.plugin, this);
      this.plugin.getListenerManager().registerListener(this.menuListener);
      this.plugin.getListenerManager().registerListener(this.chatListener);

      if (this.isCommandEnabled("stream")) {
         registerCommandSafely(new StreamCommand(this.plugin, this));
      }

      this.plugin.getDebugSystem().log("StreamerMode", "Module enabled");
   }

   @Override
   public void onDisable() {
      super.onDisable();
      if (this.menuListener != null) {
         this.plugin.getListenerManager().unregisterListener(this.menuListener);
      }
      if (this.chatListener != null) {
         this.plugin.getListenerManager().unregisterListener(this.chatListener);
      }
      saveData();
      this.registeredCommandNames.clear();
      this.plugin.getDebugSystem().log("StreamerMode", "Module disabled");
   }

   @Override
   public void reload() {
      super.reload();
      loadConfigs();
      reloadBannedWords();
      resolveLuckPerms();
   }

   private void resolveLuckPerms() {
      this.luckPerms = null;
      try {
         RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
         if (provider != null) {
            this.luckPerms = provider.getProvider();
         }
      } catch (Exception e) {
         this.plugin.getDebugSystem().logError("StreamerMode: failed to resolve LuckPerms provider", e);
      }
   }

   private void loadConfigs() {
      this.config = this.plugin.getModuleManager().loadModuleConfig(MODULE_NAME);
      this.messages = this.plugin.getModuleManager().loadModuleConfig(MODULE_NAME, "messages.yml");
      if (this.config == null) {
         this.config = new YamlConfiguration();
      }
      if (this.messages == null) {
         this.messages = new YamlConfiguration();
      }
   }

   private void loadData() {
      File folder = this.plugin.getModuleManager().getModuleDataFolder(MODULE_NAME);
      if (folder == null) {
         return;
      }
      if (!folder.exists()) {
         folder.mkdirs();
      }

      this.dataFile = new File(folder, "data.yml");
      if (!this.dataFile.exists()) {
         saveData();
      }

      YamlConfiguration data = YamlConfiguration.loadConfiguration(this.dataFile);
      ConfigurationSection players = data.getConfigurationSection("players");
      if (players == null) {
         return;
      }

      for (String key : players.getKeys(false)) {
         UUID uuid;
         try {
            uuid = UUID.fromString(key);
         } catch (Exception ignored) {
            continue;
         }

         ConfigurationSection section = players.getConfigurationSection(key);
         if (section == null) {
            continue;
         }

         if (section.getBoolean("stream-enabled", false)) {
            this.streamEnabled.add(uuid);
         }
         if (section.getBoolean("chat-filter-enabled", true)) {
            this.chatFilterEnabled.add(uuid);
         }

         String link = section.getString("channel-link", "");
         if (link != null && !link.isBlank()) {
            this.channelLinks.put(uuid, link);
         }
      }
   }

   public void saveData() {
      if (this.dataFile == null) {
         return;
      }

      YamlConfiguration data = new YamlConfiguration();
      Map<UUID, Boolean> all = new HashMap<>();
      for (UUID uuid : this.streamEnabled) {
         all.put(uuid, true);
      }
      for (UUID uuid : this.chatFilterEnabled) {
         all.putIfAbsent(uuid, false);
      }
      for (UUID uuid : this.channelLinks.keySet()) {
         all.putIfAbsent(uuid, false);
      }

      for (UUID uuid : all.keySet()) {
         String base = "players." + uuid;
         data.set(base + ".stream-enabled", this.streamEnabled.contains(uuid));
         data.set(base + ".chat-filter-enabled", this.chatFilterEnabled.contains(uuid));
         data.set(base + ".channel-link", this.channelLinks.getOrDefault(uuid, ""));
      }

      try {
         data.save(this.dataFile);
      } catch (Exception e) {
         this.plugin.getDebugSystem().logError("StreamerMode: failed to save data.yml", e);
      }
   }

   public FileConfiguration getConfig() {
      return this.config;
   }

   public boolean isCommandEnabled(String commandKey) {
      return this.config.getBoolean("commands." + commandKey + ".enabled", true);
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

   public boolean isStreamEnabled(UUID uuid) {
      return uuid != null && this.streamEnabled.contains(uuid);
   }

   public boolean isChatFilterEnabled(UUID uuid) {
      return uuid != null && this.chatFilterEnabled.contains(uuid);
   }

   public void setChatFilterEnabled(UUID uuid, boolean enabled) {
      if (uuid == null) {
         return;
      }
      if (enabled) {
         this.chatFilterEnabled.add(uuid);
      } else {
         this.chatFilterEnabled.remove(uuid);
      }
      saveData();
   }

   public String getChannelLink(UUID uuid) {
      return this.channelLinks.get(uuid);
   }

   public void setChannelLink(UUID uuid, String link) {
      if (uuid == null) {
         return;
      }
      if (link == null || link.isBlank()) {
         this.channelLinks.remove(uuid);
      } else {
         this.channelLinks.put(uuid, link.trim());
      }
      saveData();
   }

   public void setStreamEnabled(UUID uuid, String userName, boolean enabled) {
      if (uuid == null) {
         return;
      }
      if (enabled) {
         this.streamEnabled.add(uuid);
      } else {
         this.streamEnabled.remove(uuid);
      }
      updateLuckPermsGroup(uuid, userName, enabled);
      saveData();
   }

   private void updateLuckPermsGroup(UUID uuid, String userName, boolean enabled) {
      if (this.luckPerms == null) {
         return;
      }

      String groupName = this.config.getString("luckperms.streamer-group", "streame");
      if (groupName == null || groupName.isBlank()) {
         return;
      }

      this.luckPerms.getUserManager().loadUser(uuid, userName).thenAccept(user -> {
         if (user == null) {
            return;
         }
         InheritanceNode node = InheritanceNode.builder(groupName).build();
         if (enabled) {
            user.data().add(node);
         } else {
            user.data().remove(node);
         }
         this.luckPerms.getUserManager().saveUser(user);
      }).exceptionally(ex -> {
         this.plugin.getDebugSystem().logError("StreamerMode: LuckPerms update failed", new RuntimeException(ex));
         return null;
      });
   }

   public void unlinkChannel(UUID uuid) {
      if (uuid == null) {
         return;
      }
      this.channelLinks.remove(uuid);
      saveData();
   }

   public void broadcastStreamStart(Player player) {
      if (player == null) {
         return;
      }

      String rawLink = getChannelLink(player.getUniqueId());
      String link = rawLink == null || rawLink.isBlank()
         ? this.getMessage("stream.link-missing-inline", "(ссылка не указана)")
         : rawLink;

      if (this.config.getBoolean("notifications.broadcast-on-start", true)) {
         String msg = this.getMessage(
            "stream.started-broadcast",
            "&[SECONDARY]Игрок &[MAIN]{player} &[SECONDARY]запустил стрим: &[MAIN]{link}"
         ).replace("{player}", player.getName()).replace("{link}", link);

         Bukkit.broadcastMessage(msg);
      }

      if (this.streamWebhook != null) {
         this.streamWebhook.sendStreamStart(player, rawLink);
      }
   }

   public void openMenu(Player player) {
      if (player == null) {
         return;
      }

      String title = this.getMessage("menu.title", "&[MAIN]Режим Стримера");
      Inventory inventory = Bukkit.createInventory(new StreamMenuHolder(player.getUniqueId()), 27, title);

      fillDecor(inventory);

      inventory.setItem(11, createToggleItem(
         Material.LEVER,
         this.getMessage("menu.stream-item", "&[SECONDARY]Режим Стримера"),
         this.isStreamEnabled(player.getUniqueId())
      ));

      inventory.setItem(13, createChannelInfoItem(player.getUniqueId()));

      inventory.setItem(15, createToggleItem(
         Material.WRITABLE_BOOK,
         this.getMessage("menu.filter-item", "&[SECONDARY]Фильтр Чата"),
         this.isChatFilterEnabled(player.getUniqueId())
      ));

      ItemStack close = new ItemStack(Material.BARRIER);
      ItemMeta closeMeta = close.getItemMeta();
      if (closeMeta != null) {
         closeMeta.setDisplayName(this.getMessage("menu.close-item", "&[SECONDARY]Закрыть"));
         close.setItemMeta(closeMeta);
      }
      inventory.setItem(22, close);

      player.openInventory(inventory);
   }

   private ItemStack createToggleItem(Material material, String title, boolean enabled) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(title);
         java.util.List<String> lore = new java.util.ArrayList<>();
         lore.add(this.getMessage(enabled ? "menu.status-on" : "menu.status-off", enabled ? "&aВКЛ" : "&cВЫКЛ"));
         lore.add(this.getMessage("menu.click-toggle", "&7Нажмите, чтобы переключить"));
         meta.setLore(lore);
         item.setItemMeta(meta);
      }
      return item;
   }

   private ItemStack createChannelInfoItem(UUID playerId) {
      ItemStack item = new ItemStack(Material.PAPER);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(this.getMessage("menu.channel-item", "&[SECONDARY]Привязка Канала"));

         java.util.List<String> lore = new java.util.ArrayList<>();
         String link = this.getChannelLink(playerId);
         if (link == null || link.isBlank()) {
            lore.add(this.getMessage("menu.channel-empty", "&7Ссылка: &[MAIN]не указана"));
         } else {
            lore.add(this.getMessage("menu.channel-label", "&7Ссылка: &[MAIN]{link}").replace("{link}", link));
         }
         lore.add(this.getMessage("menu.channel-hint", "&7Используйте: &[MAIN]/stream link <url>"));
         meta.setLore(lore);
         item.setItemMeta(meta);
      }
      return item;
   }

   private void fillDecor(Inventory inventory) {
      for (int i = 0; i < inventory.getSize(); i++) {
         if (i == 11 || i == 13 || i == 15 || i == 22) {
            continue;
         }
         ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
         ItemMeta meta = pane.getItemMeta();
         if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
         }
         inventory.setItem(i, pane);
      }
   }

   public BannedWordsReloadResult reloadBannedWords() {
      File folder = this.plugin.getModuleManager().getModuleDataFolder(MODULE_NAME);
      if (folder == null) {
         this.plugin.getLogger().warning("StreamerMode: module data folder is unavailable; keeping the previous banned words list");
         return new BannedWordsReloadResult(false, this.bannedWords.size(), DEFAULT_BANNED_WORDS_FILE);
      }

      String configuredName = this.config.getString("chat-filter.banned-words-file", DEFAULT_BANNED_WORDS_FILE);
      if (configuredName == null || configuredName.isBlank()) {
         configuredName = DEFAULT_BANNED_WORDS_FILE;
      }
      configuredName = configuredName.trim();

      try {
         Path folderPath = folder.toPath().toAbsolutePath().normalize();
         Path configuredPath = Path.of(configuredName);
         if (configuredPath.isAbsolute()) {
            this.plugin.getLogger().warning("StreamerMode: chat-filter.banned-words-file must be relative to the module folder");
            return new BannedWordsReloadResult(false, this.bannedWords.size(), configuredName);
         }

         Path filePath = folderPath.resolve(configuredPath).normalize();
         if (!filePath.startsWith(folderPath)) {
            this.plugin.getLogger().warning("StreamerMode: chat-filter.banned-words-file points outside the module folder");
            return new BannedWordsReloadResult(false, this.bannedWords.size(), configuredName);
         }

         if (!Files.exists(filePath)) {
            this.plugin.getLogger().warning("StreamerMode: banned words file not found, creating " + filePath);
            createBannedWordsFile(filePath, folderPath);
         }
         if (!Files.isRegularFile(filePath)) {
            this.plugin.getLogger().warning("StreamerMode: banned words path is not a regular file: " + filePath);
            return new BannedWordsReloadResult(false, this.bannedWords.size(), configuredName);
         }

         List<String> loadedWords = parseBannedWords(Files.readAllLines(filePath, StandardCharsets.UTF_8));
         List<Pattern> loadedPatterns = new ArrayList<>(loadedWords.size());
         for (String word : loadedWords) {
            loadedPatterns.add(compileBannedWord(word));
         }

         this.bannedWordsFile = filePath.toFile();
         this.bannedWords = List.copyOf(loadedWords);
         this.bannedWordPatterns = List.copyOf(loadedPatterns);

         String relativeName = folderPath.relativize(filePath).toString();
         this.plugin.getLogger().info(
            "StreamerMode: loaded " + loadedWords.size() + " banned word(s) from " + relativeName
         );
         return new BannedWordsReloadResult(true, loadedWords.size(), relativeName);
      } catch (Exception e) {
         this.plugin.getDebugSystem().logError(
            "StreamerMode: failed to load banned words from " + configuredName + "; keeping the previous list",
            e
         );
         return new BannedWordsReloadResult(false, this.bannedWords.size(), configuredName);
      }
   }

   private void createBannedWordsFile(Path filePath, Path folderPath) throws IOException {
      Path parent = filePath.getParent();
      if (parent != null) {
         Files.createDirectories(parent);
      }

      List<String> legacyWords = parseBannedWords(this.config.getStringList("chat-filter.banned-words"));
      if (!legacyWords.isEmpty()) {
         List<String> migrated = new ArrayList<>(legacyWords.size() + 2);
         migrated.add("# Migrated from chat-filter.banned-words in config.yml");
         migrated.add("# One forbidden word or phrase per line.");
         migrated.addAll(legacyWords);
         Files.write(filePath, migrated, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
         this.plugin.getLogger().info(
            "StreamerMode: migrated " + legacyWords.size() + " banned word(s) from config.yml"
         );
         return;
      }

      String relativeName = folderPath.relativize(filePath).toString().replace(File.separatorChar, '/');
      boolean extracted = this.plugin.getModuleManager().saveModuleDefaultConfig(
         MODULE_NAME,
         DEFAULT_BANNED_WORDS_FILE,
         relativeName
      );
      if (!extracted || !Files.exists(filePath)) {
         Files.write(
            filePath,
            DEFAULT_BANNED_WORDS_TEMPLATE,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE_NEW
         );
      }
   }

   private List<String> parseBannedWords(List<String> lines) {
      Set<String> uniqueWords = new LinkedHashSet<>();
      for (String rawLine : lines) {
         if (rawLine == null) {
            continue;
         }

         String line = rawLine.strip();
         if (line.startsWith("\uFEFF")) {
            line = line.substring(1).strip();
         }
         if (line.isEmpty() || line.startsWith("#")) {
            continue;
         }
         uniqueWords.add(line);
      }
      return new ArrayList<>(uniqueWords);
   }

   private Pattern compileBannedWord(String word) {
      String wordCharacter = "[\\p{L}\\p{N}_]";
      return Pattern.compile(
         "(?<!" + wordCharacter + ")" + Pattern.quote(word) + "(?!" + wordCharacter + ")",
         Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
      );
   }

   public List<String> getBannedWords() {
      return this.bannedWords;
   }

   public List<Pattern> getBannedWordPatterns() {
      return this.bannedWordPatterns;
   }

   public File getBannedWordsFile() {
      return this.bannedWordsFile;
   }

   public String getMask() {
      return this.config.getString("chat-filter.mask", "***");
   }

   public boolean isLinkMaskEnabled() {
      return this.config.getBoolean("chat-filter.mask-links", true);
   }

   public boolean isIpMaskEnabled() {
      return this.config.getBoolean("chat-filter.mask-ip", true);
   }

   private void registerCommandSafely(ModuleCommand command) {
      boolean hackEnabled = false;
      try {
         hackEnabled = this.plugin.getPluginReloader().setLifecycleContext();
         this.plugin.getCommandManager().registerModuleCommand(command);
         this.registeredCommandNames.add(command.getName().toLowerCase());
         if (command.getAliases() != null) {
            for (String alias : command.getAliases()) {
               if (alias != null) {
                  this.registeredCommandNames.add(alias.toLowerCase());
               }
            }
         }
      } catch (Exception e) {
         this.plugin.getDebugSystem().logError("StreamerMode command registration failed", e);
      } finally {
         if (hackEnabled) {
            this.plugin.getPluginReloader().clearLifecycleContext();
         }
      }
   }

   public record BannedWordsReloadResult(boolean success, int count, String fileName) {}
}
