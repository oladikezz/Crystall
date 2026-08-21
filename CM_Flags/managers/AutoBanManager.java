package net.schalker.SMPS.modules.flags.managers;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.schalker.DoAPI.DoAPI;
import net.schalker.SMPS.modules.flags.FlagEvent;
import net.schalker.SMPS.modules.flags.FlagType;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/**
 * Auto-ban system: when no admins with smflags.menu permission are online
 * and a player accumulates enough flag triggers, auto-ban them.
 */
public class AutoBanManager {
   private final DoAPI plugin;
   private String banReason;
   private String banCommand;
   // flagType -> required trigger count
   private final Map<FlagType, Integer> enabledFlags = new EnumMap<>(FlagType.class);
   // playerUUID -> (flagType -> current trigger count)
   private final Map<UUID, Map<FlagType, Integer>> triggerCounts = new ConcurrentHashMap<>();

   public AutoBanManager(DoAPI plugin, FileConfiguration config) {
      this.plugin = plugin;
      this.reload(config);
   }

   public void reload(FileConfiguration config) {
      this.enabledFlags.clear();
      if (config == null) return;

      this.banReason = config.getString("auto-ban.ban-reason",
         "Система сочла ваше поведение подозрительным, если не согласны с баном - откройте жалобу на сайте");
      this.banCommand = config.getString("auto-ban.ban-command", "ban {player} {reason}");

      ConfigurationSection rules = config.getConfigurationSection("auto-ban.rules");
      if (rules == null) return;

      for (String flagKey : rules.getKeys(false)) {
         ConfigurationSection rule = rules.getConfigurationSection(flagKey);
         if (rule == null) continue;
         if (!rule.getBoolean("enabled", false)) continue;

         FlagType flagType = FlagType.fromKey(flagKey);
         if (flagType == null) continue;

         int triggers = rule.getInt("triggers", 3);
         this.enabledFlags.put(flagType, triggers);
      }
   }

   /**
    * Called on every flag trigger. Checks if auto-ban conditions are met.
    */
   public void onFlag(FlagEvent event) {
      if (event.getPlayerId() == null) return;
      FlagType flagType = event.getFlagType();

      // Only process flags that are configured for auto-ban
      Integer requiredTriggers = this.enabledFlags.get(flagType);
      if (requiredTriggers == null) return;

      // Check if any admin is online
      if (this.isAdminOnline()) return;

      // Increment trigger count
      Map<FlagType, Integer> playerCounts = this.triggerCounts.computeIfAbsent(
         event.getPlayerId(), k -> new ConcurrentHashMap<>());
      int newCount = playerCounts.merge(flagType, 1, Integer::sum);

      // Check if threshold reached
      if (newCount >= requiredTriggers) {
         // Reset count to avoid double-banning
         playerCounts.remove(flagType);
         this.executeBan(event.getPlayerId(), event.getPlayerName(), flagType);
      }
   }

   private boolean isAdminOnline() {
      for (Player player : this.plugin.getServer().getOnlinePlayers()) {
         if (player.hasPermission("smflags.menu")) {
            return true;
         }
      }
      return false;
   }

   private void executeBan(UUID playerId, String playerName, FlagType flagType) {
      String command = this.banCommand
         .replace("{player}", playerName)
         .replace("{reason}", this.banReason);

      this.plugin.getDebugSystem().log("Flags",
         "Auto-ban triggered for " + playerName + " (flag: " + flagType.getKey() + ")");

      // Execute ban on the main thread
      this.plugin.getSchedulerManager().runGlobalTask("flags-autoban-" + playerId, () -> {
         Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
      });
   }

   /**
    * Cleanup trigger counts for players who are no longer online.
    */
   public void cleanup() {
      Iterator<Map.Entry<UUID, Map<FlagType, Integer>>> iterator = this.triggerCounts.entrySet().iterator();
      while (iterator.hasNext()) {
         Map.Entry<UUID, Map<FlagType, Integer>> entry = iterator.next();
         Player player = this.plugin.getServer().getPlayer(entry.getKey());
         if (player == null || !player.isOnline()) {
            iterator.remove();
         }
      }
   }

   public void clearCounts() {
      this.triggerCounts.clear();
   }

   /**
    * Clear auto-ban trigger counts for a specific player.
    */
   public void clearPlayerCounts(UUID playerId) {
      this.triggerCounts.remove(playerId);
   }
}

