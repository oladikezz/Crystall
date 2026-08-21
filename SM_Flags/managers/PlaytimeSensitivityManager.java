package net.schalker.SMPS.modules.flags.managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.schalker.DoAPI.DoAPI;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Manages playtime-based sensitivity for flags.
 * Queries the main SMPS database (sm_playtime_stats) for each player's total_minutes
 * and determines a sensitivity tier that adjusts flag thresholds.
 *
 * Tiers (configurable):
 * - NEWCOMER: < 1h       → multiplier 0.25 (super sensitive)
 * - BEGINNER: 1h – 10h   → multiplier 0.5
 * - REGULAR:  10h – 50h  → multiplier 0.75
 * - VETERAN:  50h – 200h → multiplier 1.0  (default)
 * - ELDER:    > 200h     → multiplier 1.5  (least sensitive)
 */
public class PlaytimeSensitivityManager {
   private final DoAPI plugin;
   private final Map<UUID, CachedPlaytime> cache = new ConcurrentHashMap<>();
   private static final long CACHE_TTL_MS = 5 * 60 * 1000L; // 5 minutes

   // Tier boundaries (in minutes)
   private int newcomerMaxMinutes = 60;
   private int beginnerMaxMinutes = 600;
   private int regularMaxMinutes = 3000;
   private int veteranMaxMinutes = 12000;

   // Threshold multipliers per tier
   private double newcomerMultiplier = 0.25;
   private double beginnerMultiplier = 0.5;
   private double regularMultiplier = 0.75;
   private double veteranMultiplier = 1.0;
   private double elderMultiplier = 1.5;

   private boolean enabled = true;

   public PlaytimeSensitivityManager(DoAPI plugin, FileConfiguration config) {
      this.plugin = plugin;
      this.reload(config);
   }

   public void reload(FileConfiguration config) {
      if (config == null) {
         this.enabled = false;
         return;
      }

      ConfigurationSection section = config.getConfigurationSection("playtime-sensitivity");
      if (section == null) {
         this.enabled = config.getBoolean("playtime-sensitivity.enabled", true);
         return;
      }

      this.enabled = section.getBoolean("enabled", true);

      ConfigurationSection tiers = section.getConfigurationSection("tiers");
      if (tiers == null) return;

      ConfigurationSection newcomer = tiers.getConfigurationSection("newcomer");
      if (newcomer != null) {
         this.newcomerMaxMinutes = newcomer.getInt("max-minutes", 60);
         this.newcomerMultiplier = newcomer.getDouble("threshold-multiplier", 0.25);
      }

      ConfigurationSection beginner = tiers.getConfigurationSection("beginner");
      if (beginner != null) {
         this.beginnerMaxMinutes = beginner.getInt("max-minutes", 600);
         this.beginnerMultiplier = beginner.getDouble("threshold-multiplier", 0.5);
      }

      ConfigurationSection regular = tiers.getConfigurationSection("regular");
      if (regular != null) {
         this.regularMaxMinutes = regular.getInt("max-minutes", 3000);
         this.regularMultiplier = regular.getDouble("threshold-multiplier", 0.75);
      }

      ConfigurationSection veteran = tiers.getConfigurationSection("veteran");
      if (veteran != null) {
         this.veteranMaxMinutes = veteran.getInt("max-minutes", 12000);
         this.veteranMultiplier = veteran.getDouble("threshold-multiplier", 1.0);
      }

      ConfigurationSection elder = tiers.getConfigurationSection("elder");
      if (elder != null) {
         this.elderMultiplier = elder.getDouble("threshold-multiplier", 1.5);
      }
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   /**
    * Get the threshold multiplier for a given player.
    * A lower multiplier means the flag fires more easily (more sensitive).
    * Returns 1.0 if the system is disabled or playtime cannot be determined.
    */
   public double getThresholdMultiplier(UUID playerId) {
      if (!this.enabled || playerId == null) {
         return 1.0;
      }

      long totalMinutes = this.getCachedPlaytime(playerId);
      if (totalMinutes < 0) {
         return 1.0; // Unknown playtime — use default
      }

      return this.resolveMultiplier(totalMinutes);
   }

   /**
    * Get the tier name for a player (for display purposes).
    */
   public String getTierName(UUID playerId) {
      if (!this.enabled || playerId == null) return "N/A";
      long totalMinutes = this.getCachedPlaytime(playerId);
      if (totalMinutes < 0) return "Неизвестно";
      return this.resolveTierName(totalMinutes);
   }

   /**
    * Get the tier config key for a player (e.g. "newcomer", "beginner", "regular", "veteran", "elder").
    * Returns null if disabled or playtime is unknown.
    */
   public String getTierKey(UUID playerId) {
      if (!this.enabled || playerId == null) return null;
      long totalMinutes = this.getCachedPlaytime(playerId);
      if (totalMinutes < 0) return null;
      return this.resolveTierKey(totalMinutes);
   }

   /**
    * Get player's total playtime in minutes from cache/database.
    * Returns -1 when unavailable.
    */
   public long getTotalMinutes(UUID playerId) {
      if (!this.enabled || playerId == null) {
         return -1;
      }
      return this.getCachedPlaytime(playerId);
   }

   /**
    * Upper bound for the second tier (beginner) in minutes.
    * Default is 600 (10 hours).
    */
   public int getBeginnerMaxMinutes() {
      return this.beginnerMaxMinutes;
   }

   /**
    * Pre-load playtime for a player asynchronously.
    * Call on player join to warm up the cache.
    */
   public void preloadAsync(UUID playerId) {
      if (!this.enabled || playerId == null) return;
      this.plugin.getSchedulerManager().runAsync("flags-playtime-" + playerId, () -> {
         this.fetchAndCache(playerId);
      });
   }

   /**
    * Remove a player from the cache (e.g., on quit).
    */
   public void evict(UUID playerId) {
      this.cache.remove(playerId);
   }

   /**
    * Clear entire cache.
    */
   public void clearCache() {
      this.cache.clear();
   }

   private long getCachedPlaytime(UUID playerId) {
      CachedPlaytime cached = this.cache.get(playerId);
      if (cached != null && !cached.isExpired()) {
         return cached.totalMinutes;
      }

      // Try to fetch synchronously (this should only happen rarely — cache should be warmed)
      return this.fetchAndCache(playerId);
   }

   private long fetchAndCache(UUID playerId) {
      long totalMinutes = this.queryPlaytime(playerId);
      this.cache.put(playerId, new CachedPlaytime(totalMinutes, System.currentTimeMillis()));
      return totalMinutes;
   }

   /**
    * Query the main SMPS database for the player's total_minutes from sm_playtime_stats.
    * Returns -1 if the query fails or no data is found.
    */
   private long queryPlaytime(UUID playerId) {
      if (!this.plugin.isDatabaseConnected()) {
         return -1;
      }

      String tableName = this.plugin.getDatabaseManager().table("playtime_stats");
      String sql = "SELECT total_minutes FROM " + tableName + " WHERE uuid = ?";

      try (Connection conn = this.plugin.getDatabaseManager().getConnection();
           PreparedStatement ps = conn.prepareStatement(sql)) {
         ps.setString(1, playerId.toString());
         try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
               return rs.getLong("total_minutes");
            }
         }
      } catch (Exception e) {
         this.plugin.getDebugSystem().logError("Failed to query playtime for " + playerId, e);
      }

      return -1; // No record or error
   }

   private double resolveMultiplier(long totalMinutes) {
      if (totalMinutes < this.newcomerMaxMinutes) {
         return this.newcomerMultiplier;
      } else if (totalMinutes < this.beginnerMaxMinutes) {
         return this.beginnerMultiplier;
      } else if (totalMinutes < this.regularMaxMinutes) {
         return this.regularMultiplier;
      } else if (totalMinutes < this.veteranMaxMinutes) {
         return this.veteranMultiplier;
      } else {
         return this.elderMultiplier;
      }
   }

   private String resolveTierName(long totalMinutes) {
      if (totalMinutes < this.newcomerMaxMinutes) {
         return "Новичок";
      } else if (totalMinutes < this.beginnerMaxMinutes) {
         return "Начинающий";
      } else if (totalMinutes < this.regularMaxMinutes) {
         return "Игрок";
      } else if (totalMinutes < this.veteranMaxMinutes) {
         return "Ветеран";
      } else {
         return "Старожил";
      }
   }

   private String resolveTierKey(long totalMinutes) {
      if (totalMinutes < this.newcomerMaxMinutes) {
         return "newcomer";
      } else if (totalMinutes < this.beginnerMaxMinutes) {
         return "beginner";
      } else if (totalMinutes < this.regularMaxMinutes) {
         return "regular";
      } else if (totalMinutes < this.veteranMaxMinutes) {
         return "veteran";
      } else {
         return "elder";
      }
   }

   private static class CachedPlaytime {
      final long totalMinutes;
      final long fetchedAt;

      CachedPlaytime(long totalMinutes, long fetchedAt) {
         this.totalMinutes = totalMinutes;
         this.fetchedAt = fetchedAt;
      }

      boolean isExpired() {
         return (System.currentTimeMillis() - this.fetchedAt) > CACHE_TTL_MS;
      }
   }
}

