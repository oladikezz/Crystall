package net.schalker.SMPS.modules.quietban;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.module.BaseModule;
import net.schalker.DoAPI.core.module.ModuleInfo;
import net.schalker.SMPS.modules.quietban.commands.CheckQuietBanCommand;
import net.schalker.SMPS.modules.quietban.commands.QuietBanCommand;
import net.schalker.SMPS.modules.quietban.commands.UnQuietBanCommand;
import net.schalker.SMPS.modules.quietban.listeners.QuietBanListener;
import net.schalker.SMPS.modules.quietban.transport.LagProfile;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class QuietBanModule extends BaseModule {

   public static final String PERMISSION_MANAGE = "smquietban.manage";
   public static final String PERMISSION_BYPASS = "smquietban.bypass";

   private static final String EXPIRY_TASK = "quietban-expiry";
   private static final List<String> DEFAULT_DROPPABLE = List.of(
      "MovePlayerPacket", "MoveVehiclePacket", "SwingPacket", "PlayerActionPacket",
      "PlayerInputPacket", "PlayerCommandPacket", "UseItemPacket", "UseItemOnPacket",
      "InteractPacket", "SetCarriedItemPacket");

   private FileConfiguration config;
   private FileConfiguration messages;
   private QuietBanDatabase database;
   private QuietBanManager manager;
   private QuietBanListener listener;

   private volatile Map<QuietBanLevel, LagProfile> profiles = new EnumMap<>(QuietBanLevel.class);
   private volatile List<String> droppablePackets = DEFAULT_DROPPABLE;
   private volatile boolean unbanRemovesIpLinked = true;
   private volatile boolean logToConsole = true;

   public QuietBanModule(DoAPI plugin) {
      super(plugin, loadModuleInfo());
   }

   private static ModuleInfo loadModuleInfo() {
      try (InputStream stream = QuietBanModule.class.getClassLoader().getResourceAsStream("module.yml")) {
         if (stream != null) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(
               new InputStreamReader(stream, StandardCharsets.UTF_8));
            return new ModuleInfo(
               yml.getString("name", "SM_QuietBan"),
               yml.getString("version", "1.0.0"),
               yml.getString("author", "SchalkerMC"),
               yml.getString("description", "Теневой бан игроков"));
         }
      } catch (Exception ignored) {
      }
      return new ModuleInfo("SM_QuietBan", "1.0.0", "SchalkerMC", "Теневой бан игроков");
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.loadConfigs();

      this.database = new QuietBanDatabase(this.plugin);
      this.database.initialize();
      this.manager = new QuietBanManager(this.plugin, this, this.database);

      this.plugin.getSchedulerManager().runAsync("quietban-startup", () -> {
         this.manager.loadFromDatabase();
         this.plugin.getSchedulerManager().runGlobalTask("quietban-attach", this.manager::attachOnline);
      });

      this.listener = new QuietBanListener(this);
      this.plugin.getListenerManager().registerListener(this.listener);

      this.plugin.getCommandManager().registerModuleCommand(new QuietBanCommand(this.plugin, this));
      this.plugin.getCommandManager().registerModuleCommand(new UnQuietBanCommand(this.plugin, this));
      this.plugin.getCommandManager().registerModuleCommand(new CheckQuietBanCommand(this.plugin, this));

      long interval = Math.max(20L, this.config.getLong("check-interval-ticks", 200L));
      this.plugin.getSchedulerManager().runAsyncTimer(EXPIRY_TASK,
         () -> this.manager.tickExpiry(), interval, interval);

      this.plugin.getDebugSystem().log("QuietBan", "Модуль QuietBan включен");
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.plugin.getSchedulerManager().cancelTask(EXPIRY_TASK);

      if (this.listener != null) {
         this.plugin.getListenerManager().unregisterListener(this.listener);
         this.listener = null;
      }
      if (this.manager != null) {
         this.manager.detachAll();
         this.manager.clearCache();
         this.manager = null;
      }
      this.database = null;

      this.plugin.getDebugSystem().log("QuietBan", "Модуль QuietBan выключен");
   }

   @Override
   public void reload() {
      super.reload();
      this.loadConfigs();
      if (this.manager != null) {
         this.manager.refreshProfiles();
      }
   }

   private void loadConfigs() {
      this.config = this.plugin.getModuleManager().loadModuleConfig("SM_QuietBan");
      if (this.config == null) {
         this.config = new YamlConfiguration();
      }
      this.messages = this.plugin.getModuleManager().loadModuleConfig("SM_QuietBan", "messages.yml");
      if (this.messages == null) {
         this.messages = new YamlConfiguration();
      }

      long maxDelay = Math.max(50L, this.config.getLong("max-delay-ms", 5000L));
      Map<QuietBanLevel, LagProfile> loaded = new EnumMap<>(QuietBanLevel.class);
      for (QuietBanLevel level : QuietBanLevel.values()) {
         loaded.put(level, readProfile(level, maxDelay));
      }
      this.profiles = loaded;

      List<String> packets = this.config.getStringList("droppable-packets");
      List<String> cleaned = new ArrayList<>();
      for (String packet : packets) {
         if (packet != null && !packet.isBlank()) {
            cleaned.add(packet.trim());
         }
      }
      this.droppablePackets = cleaned.isEmpty() ? DEFAULT_DROPPABLE : List.copyOf(cleaned);

      this.unbanRemovesIpLinked = this.config.getBoolean("unban-removes-ip-linked", true);
      this.logToConsole = this.config.getBoolean("log-to-console", true);
   }

   private LagProfile readProfile(QuietBanLevel level, long maxDelay) {
      ConfigurationSection section = this.config.getConfigurationSection("levels." + level.getKey());
      long outbound = defaultOutbound(level);
      long inbound = defaultInbound(level);
      long jitter = defaultJitter(level);
      double drop = defaultDrop(level);

      if (section != null) {
         outbound = section.getLong("outbound-delay-ms", outbound);
         inbound = section.getLong("inbound-delay-ms", inbound);
         jitter = section.getLong("jitter-ms", jitter);
         drop = section.getDouble("packet-drop-chance", drop);
      }
      return new LagProfile(outbound, inbound, jitter, drop, maxDelay);
   }

   private long defaultOutbound(QuietBanLevel level) {
      return switch (level) {
         case QUIET -> 80L;
         case MEDIUM -> 260L;
         case AGGRESSIVE -> 1400L;
      };
   }

   private long defaultInbound(QuietBanLevel level) {
      return switch (level) {
         case QUIET -> 60L;
         case MEDIUM -> 220L;
         case AGGRESSIVE -> 1100L;
      };
   }

   private long defaultJitter(QuietBanLevel level) {
      return switch (level) {
         case QUIET -> 70L;
         case MEDIUM -> 180L;
         case AGGRESSIVE -> 700L;
      };
   }

   private double defaultDrop(QuietBanLevel level) {
      return switch (level) {
         case QUIET -> 0.04D;
         case MEDIUM -> 0.22D;
         case AGGRESSIVE -> 0.6D;
      };
   }

   public LagProfile profileFor(QuietBanLevel level) {
      LagProfile profile = this.profiles.get(level);
      if (profile != null) {
         return profile;
      }
      return new LagProfile(defaultOutbound(level), defaultInbound(level),
         defaultJitter(level), defaultDrop(level), 5000L);
   }

   public List<String> getDroppablePackets() {
      return this.droppablePackets;
   }

   public boolean isUnbanRemovingIpLinked() {
      return this.unbanRemovesIpLinked;
   }

   public QuietBanManager getManager() {
      return this.manager;
   }

   public DoAPI getApi() {
      return this.plugin;
   }

   public boolean isImmune(Player player) {
      return player != null && player.hasPermission(PERMISSION_BYPASS);
   }

   public void logAction(String message) {
      if (this.logToConsole) {
         this.plugin.getDebugSystem().log("QuietBan", message);
      }
   }

   public String getMessage(String key, String fallback) {
      String message = this.messages == null ? null : this.messages.getString(key);
      if (message == null || message.isEmpty()) {
         message = fallback;
      }
      return this.plugin.applyColors(message);
   }

   public void send(CommandSender sender, String key, String fallback) {
      sender.sendMessage(getMessage(key, fallback));
   }

   public void runAsync(String name, Runnable action) {
      this.plugin.getSchedulerManager().runAsync(name, action);
   }
}
