package net.schalker.SMPS.modules.flags.managers;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.schalker.DoAPI.DoAPI;
import net.schalker.SMPS.modules.flags.DiscordWebhook;
import net.schalker.SMPS.modules.flags.FlagEvent;
import net.schalker.SMPS.modules.flags.FlagSeverityResolver;
import net.schalker.SMPS.modules.flags.FlagTracker;
import net.schalker.SMPS.modules.flags.FlagType;
import net.schalker.SMPS.modules.flags.FlagsDatabase;
import net.schalker.SMPS.modules.flags.WebhookGroupManager;
import org.bukkit.entity.Player;

public class FlagsManager {
   private final DoAPI plugin;
   private final FlagsDatabase database;
   private final FlagTracker tracker;
   private WebhookGroupManager webhookGroupManager;
   private FlagSeverityResolver severityResolver;
   private AutoBanManager autoBanManager;
   private PlaytimeSensitivityManager playtimeSensitivity;
   private final int maxHistorySize;
   private final Map<UUID, EnumMap<FlagType, Boolean>> settingsCache;
   private long cooldownLowMs;
   private long cooldownMediumMs;
   private long cooldownHighMs;
   private int actionBarDuration;
   private boolean chatNotifications;
   // Per-admin display preferences (true = enabled by default)
   private final Map<UUID, Boolean> adminChatEnabled = new ConcurrentHashMap<>();
   private final Map<UUID, Boolean> adminActionBarEnabled = new ConcurrentHashMap<>();
   // Muted players: UUID → expiry timestamp (flags from muted players are silently ignored)
   private final Map<UUID, Long> mutedPlayers = new ConcurrentHashMap<>();

   public FlagsManager(DoAPI plugin, FlagsDatabase database, int maxHistorySize,
                       WebhookGroupManager webhookGroupManager, FlagSeverityResolver severityResolver,
                       AutoBanManager autoBanManager, PlaytimeSensitivityManager playtimeSensitivity) {
      this.plugin = plugin;
      this.database = database;
      this.tracker = new FlagTracker();
      this.webhookGroupManager = webhookGroupManager;
      this.severityResolver = severityResolver;
      this.autoBanManager = autoBanManager;
      this.playtimeSensitivity = playtimeSensitivity;
      this.maxHistorySize = maxHistorySize;
      this.settingsCache = new ConcurrentHashMap<>();
      this.cooldownLowMs = 60000;
      this.cooldownMediumMs = 30000;
      this.cooldownHighMs = 2000;
      this.actionBarDuration = 5;
      this.chatNotifications = false;
   }

   public void setWebhookGroupManager(WebhookGroupManager manager) {
      this.webhookGroupManager = manager;
   }

   public void setSeverityResolver(FlagSeverityResolver resolver) {
      this.severityResolver = resolver;
   }

   public void setAutoBanManager(AutoBanManager manager) {
      this.autoBanManager = manager;
   }

   public void setPlaytimeSensitivity(PlaytimeSensitivityManager manager) {
      this.playtimeSensitivity = manager;
   }

   public PlaytimeSensitivityManager getPlaytimeSensitivity() {
      return this.playtimeSensitivity;
   }

   /**
    * Get the threshold multiplier for a player based on their playtime.
    * Lower multiplier = more sensitive (lower effective threshold).
    * Returns 1.0 if playtime sensitivity is disabled.
    */
   public double getThresholdMultiplier(UUID playerId) {
      if (this.playtimeSensitivity == null) return 1.0;
      return this.playtimeSensitivity.getThresholdMultiplier(playerId);
   }

   /**
    * Get the tier config key for a player (e.g. "newcomer", "beginner").
    * Returns null if playtime sensitivity is disabled or unknown.
    */
   public String getTierKey(UUID playerId) {
      if (this.playtimeSensitivity == null) return null;
      return this.playtimeSensitivity.getTierKey(playerId);
   }

   public void setCooldowns(long lowMs, long mediumMs, long highMs) {
      this.cooldownLowMs = lowMs;
      this.cooldownMediumMs = mediumMs;
      this.cooldownHighMs = highMs;
   }

   private long getCooldownForSeverity(FlagType.FlagSeverity severity) {
      return switch (severity) {
         case LOW -> this.cooldownLowMs;
         case MEDIUM -> this.cooldownMediumMs;
         case HIGH -> this.cooldownHighMs;
      };
   }

   public void setActionBarDuration(int seconds) {
      this.actionBarDuration = seconds;
   }

   public void setChatNotifications(boolean enabled) {
      this.chatNotifications = enabled;
   }

   public FlagTracker getTracker() {
      return this.tracker;
   }

   public FlagSeverityResolver getSeverityResolver() {
      return this.severityResolver;
   }

   public boolean isAdminChatEnabled(UUID playerId) {
      return this.adminChatEnabled.getOrDefault(playerId, this.chatNotifications);
   }

   public boolean isAdminActionBarEnabled(UUID playerId) {
      return this.adminActionBarEnabled.getOrDefault(playerId, true);
   }

   public void toggleAdminChat(UUID playerId) {
      boolean current = this.isAdminChatEnabled(playerId);
      this.adminChatEnabled.put(playerId, !current);
   }

   public void toggleAdminActionBar(UUID playerId) {
      boolean current = this.isAdminActionBarEnabled(playerId);
      this.adminActionBarEnabled.put(playerId, !current);
   }

   /**
    * Mute a player's flags for a duration. All flags from this player will be
    * silently ignored (not sent to Discord, not shown to admins).
    * History still records them.
    *
    * @param playerId player to mute
    * @param durationMs duration in milliseconds (0 = unmute)
    */
   public void mutePlayer(UUID playerId, long durationMs) {
      if (durationMs <= 0) {
         this.mutedPlayers.remove(playerId);
      } else {
         this.mutedPlayers.put(playerId, System.currentTimeMillis() + durationMs);
      }
   }

   public void unmutePlayer(UUID playerId) {
      this.mutedPlayers.remove(playerId);
   }

   public boolean isMuted(UUID playerId) {
      Long expiry = this.mutedPlayers.get(playerId);
      if (expiry == null) return false;
      if (System.currentTimeMillis() >= expiry) {
         this.mutedPlayers.remove(playerId);
         return false;
      }
      return true;
   }

   /**
    * Returns remaining mute time in ms, or 0 if not muted.
    */
   public long getMuteRemaining(UUID playerId) {
      Long expiry = this.mutedPlayers.get(playerId);
      if (expiry == null) return 0;
      long remaining = expiry - System.currentTimeMillis();
      if (remaining <= 0) {
         this.mutedPlayers.remove(playerId);
         return 0;
      }
      return remaining;
   }

   public void triggerFlag(FlagEvent event) {
      // Resolve dynamic severity based on value and config,
      // scaling the level boundaries by the player's playtime sensitivity
      if (this.severityResolver != null && !event.hasManualSeverity()) {
         double multiplier = this.getThresholdMultiplier(event.getPlayerId());

         // Some flags should be consistent for everyone (no newcomer penalty)
         if (event.getFlagType() == FlagType.CHAT_SPAM || 
             event.getFlagType() == FlagType.CHAT_REPEAT || 
             event.getFlagType() == FlagType.CONTAINER_DROP) {
            multiplier = 1.0;
         }
         
         FlagType.FlagSeverity resolved = this.severityResolver.resolve(event.getFlagType(), event.getValue(), multiplier);
         event.setResolvedSeverity(resolved);
      }

      this.saveHistoryAsync(event);

      // Auto-ban check (runs on every trigger, before cooldown)
      if (this.autoBanManager != null) {
         this.autoBanManager.onFlag(event);
      }

      // Muted players — history is saved above, but notifications are suppressed
      if (this.isMuted(event.getPlayerId())) {
         return;
      }

      // noCooldown flags bypass cooldown — every action is logged
      // Cooldown is per severity level: HIGH fires almost always, LOW has longest cd
      if (!event.getFlagType().isNoCooldown()) {
         FlagType.FlagSeverity severity = event.getResolvedSeverity();
         long cd = this.getCooldownForSeverity(severity);
         if (!this.tracker.canNotifyForSeverity(event.getPlayerId(), event.getFlagType(), severity, cd)) {
            return;
         }
      }

      // Mark as notified for this specific severity level
      this.tracker.markNotifiedForSeverity(event.getPlayerId(), event.getFlagType(), event.getResolvedSeverity());

      // Send to Discord via webhook groups
      DiscordWebhook targetWebhook = this.webhookGroupManager != null
         ? this.webhookGroupManager.getWebhookForFlag(event.getFlagType())
         : null;
      if (targetWebhook != null) {
         this.plugin.getSchedulerManager().runAsync("flags-webhook", () -> {
            targetWebhook.sendFlagEmbed(event);
         });
      }

      // Notify online admins
      this.notifyAdmins(event);
   }

   private void notifyAdmins(FlagEvent event) {
      for (Player admin : this.plugin.getServer().getOnlinePlayers()) {
         if (!admin.hasPermission("smflags.menu")) {
            continue;
         }

         // Check if admin has this flag enabled
         if (!this.isFlagEnabled(admin.getUniqueId(), event.getFlagType())) {
            continue;
         }

         UUID adminId = admin.getUniqueId();

         // Send action bar if enabled for this admin
         if (this.isAdminActionBarEnabled(adminId)) {
            String message = this.formatActionBarMessage(event);
            String colored = this.plugin.applyColors(message);
            Component component = Component.text(colored);

            this.plugin.getSchedulerManager().runEntityTask(admin, "flags-notify-" + adminId, () -> {
               if (admin.isOnline()) {
                  admin.sendActionBar(component);
               }
            });

            // Schedule clearing action bar after duration
            long delayTicks = this.actionBarDuration * 20L;
            this.plugin.getSchedulerManager().runEntityTaskLater(admin, "flags-clear-" + adminId, () -> {
               if (admin.isOnline()) {
                  admin.sendActionBar(Component.empty());
               }
            }, delayTicks);
         }

         // Send chat message if enabled for this admin
         if (this.isAdminChatEnabled(adminId)) {
            Component chatComponent = this.buildChatMessage(event);
            this.plugin.getSchedulerManager().runEntityTask(admin, "flags-chat-" + adminId, () -> {
               if (admin.isOnline()) {
                  admin.sendMessage(chatComponent);
               }
            });
         }
      }
   }

   private Component buildChatMessage(FlagEvent event) {
      FlagType.FlagSeverity severity = event.getResolvedSeverity();
      TextColor severityColor = switch (severity) {
         case LOW -> TextColor.color(0x2ECC71);
         case MEDIUM -> TextColor.color(0xF39C12);
         case HIGH -> TextColor.color(0xE74C3C);
      };

      TextComponent.Builder builder = Component.text();

      // ⚠ PlayerName → FlagName (value)
      builder.append(Component.text("⚠ ", severityColor));
      builder.append(Component.text(event.getPlayerName(), NamedTextColor.WHITE));
      builder.append(Component.text(" → ", NamedTextColor.GRAY));
      builder.append(Component.text(event.getFlagType().getDisplayName(), severityColor));

      if (event.getValue() > 0) {
         builder.append(Component.text(" (" + event.getValue() + ")", NamedTextColor.GRAY));
      }

      // §8>> /tp (clickable) - details
      if (event.getLocation() != null) {
         String world = event.getWorld() != null ? event.getWorld() : "world";
         String coWorld = toCoreProtectWorld(world);
         String tpCommand = "/co teleport " + coWorld + " "
            + event.getLocation().getBlockX() + " "
            + event.getLocation().getBlockY() + " "
            + event.getLocation().getBlockZ();

         Component hoverText = Component.text("Телепортироваться к месту флага\n", NamedTextColor.GRAY)
            .append(Component.text(event.getCoordinates(), NamedTextColor.WHITE))
            .append(Component.text("\nМир: " + world, NamedTextColor.GRAY));

         builder.append(Component.text(" ", NamedTextColor.DARK_GRAY));
         builder.append(Component.text(">> ", NamedTextColor.DARK_GRAY));
         builder.append(Component.text("/co teleport", NamedTextColor.DARK_GRAY)
            .clickEvent(ClickEvent.runCommand(tpCommand))
            .hoverEvent(HoverEvent.showText(hoverText)));
      }

      if (event.getDetails() != null && !event.getDetails().isEmpty()) {
         builder.append(Component.text(" - " + event.getDetails(), NamedTextColor.DARK_GRAY));
      }

      return builder.build();
   }

   private String formatActionBarMessage(FlagEvent event) {
      StringBuilder message = new StringBuilder();
      
      // Severity color (use resolved severity)
      String color = switch (event.getResolvedSeverity()) {
         case LOW -> "&a";
         case MEDIUM -> "&e";
         case HIGH -> "&c";
      };
      
      message.append(color).append("⚠ [Флаг]&r ");
      message.append("&f").append(event.getPlayerName()).append("&r");
      message.append(" &7→&r ");
      message.append(color).append(event.getFlagType().getDisplayName()).append("&r");
      
      if (event.getValue() > 0) {
         message.append(" &7(").append(event.getValue()).append(")&r");
      }
      
      message.append(" &7[").append(event.getResolvedSeverity().getName()).append("]&r");

      return message.toString();
   }

   /**
    * Convert Bukkit world name to CoreProtect-compatible short name.
    * e.g. "world_nether" → "nether", "world_the_end" → "end", "world" → "world"
    */
   private static String toCoreProtectWorld(String worldName) {
      if (worldName == null) return "world";
      // Common Paper/Spigot nether/end world names
      if (worldName.endsWith("_nether")) return "nether";
      if (worldName.endsWith("_the_end") || worldName.endsWith("_end")) return "end";
      return worldName;
   }

   public List<FlagEvent> getHistory() {
      return this.database.loadHistory(this.maxHistorySize);
   }

   public List<FlagEvent> getHistory(String playerName) {
      return this.database.loadHistory(playerName, this.maxHistorySize);
   }

   public void clearHistory() {
      this.plugin.getSchedulerManager().runAsync("flags-history-clear", this.database::clearHistory);
   }

   /**
    * Clear all flags data for a specific player: history, tracker, mute, auto-ban counts, and settings cache.
    * Returns the number of history entries deleted (via callback — runs async).
    */
   public void clearPlayerData(UUID playerId, String playerName, java.util.function.IntConsumer callback) {
      this.tracker.clearAll(playerId);
      this.mutedPlayers.remove(playerId);
      this.settingsCache.remove(playerId);
      this.adminChatEnabled.remove(playerId);
      this.adminActionBarEnabled.remove(playerId);
      if (this.autoBanManager != null) {
         this.autoBanManager.clearPlayerCounts(playerId);
      }

      this.plugin.getSchedulerManager().runAsync("flags-clear-player-" + playerId, () -> {
         int deleted = this.database.clearPlayerHistory(playerName);
         this.database.clearPlayerSettings(playerId);
         if (callback != null) {
            callback.accept(deleted);
         }
      });
   }

   public void toggleFlag(UUID playerId, FlagType flagType) {
      EnumMap<FlagType, Boolean> settings = this.getOrLoadSettings(playerId);
      boolean newValue = !settings.getOrDefault(flagType, true);
      settings.put(flagType, newValue);
      this.saveSettingAsync(playerId, flagType, newValue);
   }

   public Map<FlagType, Boolean> getPlayerSettings(UUID playerId) {
      return new EnumMap<>(this.getOrLoadSettings(playerId));
   }

   public void setAllFlags(UUID playerId, boolean enabled) {
      EnumMap<FlagType, Boolean> settings = this.getOrLoadSettings(playerId);
      for (FlagType type : FlagType.values()) {
         settings.put(type, enabled);
      }
      this.saveSettingsAsync(playerId, new EnumMap<>(settings));
   }

   /**
    * Clear ALL flags history from the database and reset all in-memory tracking state.
    * Runs the DB operation async and calls callback with completion.
    */
   public void clearAllHistory(Runnable callback) {
      // Clear all in-memory state
      this.tracker.clearAll();
      this.mutedPlayers.clear();
      if (this.autoBanManager != null) {
         this.autoBanManager.clearCounts();
      }

      this.plugin.getSchedulerManager().runAsync("flags-clear-all-history", () -> {
         this.database.clearHistory();
         if (callback != null) {
            callback.run();
         }
      });
   }

   public void cleanup() {
      // Cleanup old tracked actions (older than 1 hour)
      this.tracker.cleanup(3600000);
      // Cleanup auto-ban counts for offline players
      if (this.autoBanManager != null) {
         this.autoBanManager.cleanup();
      }
   }

   public void clearSettingsCache() {
      this.settingsCache.clear();
   }

   private EnumMap<FlagType, Boolean> getOrLoadSettings(UUID playerId) {
      return this.settingsCache.computeIfAbsent(playerId, this.database::loadSettings);
   }

   private boolean isFlagEnabled(UUID playerId, FlagType flagType) {
      return this.getOrLoadSettings(playerId).getOrDefault(flagType, true);
   }

   private void saveSettingAsync(UUID playerId, FlagType flagType, boolean enabled) {
      this.plugin.getSchedulerManager().runAsync("flags-save-" + playerId, () -> {
         this.database.saveSetting(playerId, flagType, enabled);
      });
   }

   private void saveSettingsAsync(UUID playerId, EnumMap<FlagType, Boolean> snapshot) {
      this.plugin.getSchedulerManager().runAsync("flags-save-bulk-" + playerId, () -> {
         this.database.saveSettings(playerId, snapshot);
      });
   }

   private void saveHistoryAsync(FlagEvent event) {
      this.plugin.getSchedulerManager().runAsync("flags-history", () -> {
         this.database.saveFlagEvent(event);
      });
   }
}
