package net.schalker.SMPS.modules.stats;

import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.database.ModuleDatabase;

public class StatsDatabase extends ModuleDatabase {
   public StatsDatabase(DoAPI plugin) {
      super(plugin, "playtime");
   }

   @Override
   public void createTables() {
      String historySql;
      String statsSql;
      if (isSqliteOrH2()) {
         historySql = """
            CREATE TABLE IF NOT EXISTS %s (
              uuid TEXT NOT NULL,
              player_name TEXT NOT NULL,
              date BIGINT NOT NULL,
              minutes BIGINT NOT NULL DEFAULT 0,
              PRIMARY KEY (uuid, date)
            )
            """.formatted(table("history"));
         statsSql = """
            CREATE TABLE IF NOT EXISTS %s (
              uuid TEXT PRIMARY KEY,
              player_name TEXT NOT NULL,
              total_minutes BIGINT NOT NULL DEFAULT 0,
              monthly_minutes BIGINT NOT NULL DEFAULT 0,
              weekly_minutes BIGINT NOT NULL DEFAULT 0,
              daily_minutes BIGINT NOT NULL DEFAULT 0,
              last_month INTEGER NOT NULL DEFAULT 0,
              last_week INTEGER NOT NULL DEFAULT 0,
              last_join BIGINT NOT NULL DEFAULT 0,
              first_join BIGINT NOT NULL DEFAULT 0,
              deaths BIGINT NOT NULL DEFAULT 0,
              player_kills BIGINT NOT NULL DEFAULT 0,
              mob_kills BIGINT NOT NULL DEFAULT 0,
              blocks_broken BIGINT NOT NULL DEFAULT 0,
              blocks_placed BIGINT NOT NULL DEFAULT 0,
              items_crafted BIGINT NOT NULL DEFAULT 0,
              dist_walk DOUBLE NOT NULL DEFAULT 0,
              dist_swim DOUBLE NOT NULL DEFAULT 0,
              dist_fly DOUBLE NOT NULL DEFAULT 0,
              chat_messages BIGINT NOT NULL DEFAULT 0,
              achievements BIGINT NOT NULL DEFAULT 0,
              is_online INTEGER NOT NULL DEFAULT 0
            )
            """.formatted(table("stats"));
      } else {
         historySql = """
            CREATE TABLE IF NOT EXISTS %s (
              uuid VARCHAR(36) NOT NULL,
              player_name VARCHAR(32) NOT NULL,
              date BIGINT NOT NULL,
              minutes BIGINT NOT NULL DEFAULT 0,
              PRIMARY KEY (uuid, date)
            )
            """.formatted(table("history"));
         statsSql = """
            CREATE TABLE IF NOT EXISTS %s (
              uuid VARCHAR(36) PRIMARY KEY,
              player_name VARCHAR(32) NOT NULL,
              total_minutes BIGINT NOT NULL DEFAULT 0,
              monthly_minutes BIGINT NOT NULL DEFAULT 0,
              weekly_minutes BIGINT NOT NULL DEFAULT 0,
              daily_minutes BIGINT NOT NULL DEFAULT 0,
              last_month INT NOT NULL DEFAULT 0,
              last_week INT NOT NULL DEFAULT 0,
              last_join BIGINT NOT NULL DEFAULT 0,
              first_join BIGINT NOT NULL DEFAULT 0,
              deaths BIGINT NOT NULL DEFAULT 0,
              player_kills BIGINT NOT NULL DEFAULT 0,
              mob_kills BIGINT NOT NULL DEFAULT 0,
              blocks_broken BIGINT NOT NULL DEFAULT 0,
              blocks_placed BIGINT NOT NULL DEFAULT 0,
              items_crafted BIGINT NOT NULL DEFAULT 0,
              dist_walk DOUBLE NOT NULL DEFAULT 0,
              dist_swim DOUBLE NOT NULL DEFAULT 0,
              dist_fly DOUBLE NOT NULL DEFAULT 0,
              chat_messages BIGINT NOT NULL DEFAULT 0,
              achievements BIGINT NOT NULL DEFAULT 0,
              is_online TINYINT NOT NULL DEFAULT 0
            )
            """.formatted(table("stats"));
      }

      try (var conn = getConnection(); var stmt = conn.createStatement()) {
         stmt.executeUpdate(historySql);
         stmt.executeUpdate(statsSql);
         this.ensureAchievementsColumn(stmt);
         this.ensureIsOnlineColumn(stmt);
      } catch (Exception e) {
         plugin.getDebugSystem().logError("Failed to create stats tables", e);
      }
   }

   private void ensureAchievementsColumn(java.sql.Statement stmt) {
      String sql = "ALTER TABLE " + table("stats") + " ADD COLUMN achievements BIGINT NOT NULL DEFAULT 0";
      try {
         stmt.executeUpdate(sql);
      } catch (Exception ignored) {
         // Column likely already exists
      }
   }

   private void ensureIsOnlineColumn(java.sql.Statement stmt) {
      String type = isSqliteOrH2() ? "INTEGER" : "TINYINT";
      String sql = "ALTER TABLE " + table("stats") + " ADD COLUMN is_online " + type + " NOT NULL DEFAULT 0";
      try {
         stmt.executeUpdate(sql);
      } catch (Exception ignored) {
         // Column likely already exists
      }
   }

   public void saveSnapshot(StatsSnapshot snapshot) {
      if (snapshot == null) {
         return;
      }
      double walk = snapshot.getDistWalkCenti() / 100.0D;
      double swim = snapshot.getDistSwimCenti() / 100.0D;
      double fly = snapshot.getDistFlyCenti() / 100.0D;

      if (isSqliteOrH2()) {
         String sql = """
            INSERT OR REPLACE INTO %s
            (uuid, player_name, total_minutes, monthly_minutes, weekly_minutes, daily_minutes,
             last_month, last_week, last_join, first_join, deaths, player_kills, mob_kills,
             blocks_broken, blocks_placed, items_crafted, dist_walk, dist_swim, dist_fly,
             chat_messages, achievements, is_online)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.formatted(table("stats"));
         try (var conn = getConnection(); var ps = conn.prepareStatement(sql)) {
            fillStatsStatement(ps, snapshot, walk, swim, fly);
            ps.executeUpdate();
         } catch (Exception e) {
            plugin.getDebugSystem().logError("Failed to save stats", e);
         }
         this.saveHistorySnapshot(snapshot);
         return;
      }

      String sql = """
         INSERT INTO %s
         (uuid, player_name, total_minutes, monthly_minutes, weekly_minutes, daily_minutes,
          last_month, last_week, last_join, first_join, deaths, player_kills, mob_kills,
          blocks_broken, blocks_placed, items_crafted, dist_walk, dist_swim, dist_fly,
          chat_messages, achievements, is_online)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
         ON DUPLICATE KEY UPDATE
           player_name = VALUES(player_name),
           total_minutes = VALUES(total_minutes),
           monthly_minutes = VALUES(monthly_minutes),
           weekly_minutes = VALUES(weekly_minutes),
           daily_minutes = VALUES(daily_minutes),
           last_month = VALUES(last_month),
           last_week = VALUES(last_week),
           last_join = VALUES(last_join),
           first_join = VALUES(first_join),
           deaths = VALUES(deaths),
           player_kills = VALUES(player_kills),
           mob_kills = VALUES(mob_kills),
           blocks_broken = VALUES(blocks_broken),
           blocks_placed = VALUES(blocks_placed),
           items_crafted = VALUES(items_crafted),
           dist_walk = VALUES(dist_walk),
           dist_swim = VALUES(dist_swim),
           dist_fly = VALUES(dist_fly),
           chat_messages = VALUES(chat_messages),
           achievements = VALUES(achievements),
           is_online = VALUES(is_online)
         """.formatted(table("stats"));

      try (var conn = getConnection(); var ps = conn.prepareStatement(sql)) {
         fillStatsStatement(ps, snapshot, walk, swim, fly);
         ps.executeUpdate();
      } catch (Exception e) {
         plugin.getDebugSystem().logError("Failed to save stats", e);
      }
      this.saveHistorySnapshot(snapshot);
   }

   private void fillStatsStatement(java.sql.PreparedStatement ps,
                                   StatsSnapshot snapshot,
                                   double walk,
                                   double swim,
                                   double fly) throws Exception {
      ps.setString(1, snapshot.getUuid().toString());
      ps.setString(2, snapshot.getName() == null ? "" : snapshot.getName());
      ps.setLong(3, snapshot.getTotalMinutes());
      ps.setLong(4, snapshot.getMonthlyMinutes());
      ps.setLong(5, snapshot.getWeeklyMinutes());
      ps.setLong(6, snapshot.getDailyMinutes());
      ps.setInt(7, snapshot.getLastMonth());
      ps.setInt(8, snapshot.getLastWeek());
      ps.setLong(9, snapshot.getLastJoin());
      ps.setLong(10, snapshot.getFirstJoin());
      ps.setLong(11, snapshot.getDeaths());
      ps.setLong(12, snapshot.getPlayerKills());
      ps.setLong(13, snapshot.getMobKills());
      ps.setLong(14, snapshot.getBlocksBroken());
      ps.setLong(15, snapshot.getBlocksPlaced());
      ps.setLong(16, snapshot.getItemsCrafted());
      ps.setDouble(17, walk);
      ps.setDouble(18, swim);
      ps.setDouble(19, fly);
      ps.setLong(20, snapshot.getChatMessages());
      ps.setLong(21, snapshot.getAchievements());
      ps.setInt(22, snapshot.isOnline() ? 1 : 0);
   }

   private void saveHistorySnapshot(StatsSnapshot snapshot) {
      long lastJoin = snapshot.getLastJoin();
      if (lastJoin <= 0L) {
         lastJoin = System.currentTimeMillis() / 1000L;
      }
      long dayStart = toDayStartSeconds(lastJoin);
      long minutes = snapshot.getDailyMinutes();

      if (isSqliteOrH2()) {
         String sql = """
            INSERT OR REPLACE INTO %s
            (uuid, player_name, date, minutes)
            VALUES (?, ?, ?, ?)
            """.formatted(table("history"));
         try (var conn = getConnection(); var ps = conn.prepareStatement(sql)) {
            ps.setString(1, snapshot.getUuid().toString());
            ps.setString(2, snapshot.getName() == null ? "" : snapshot.getName());
            ps.setLong(3, dayStart);
            ps.setLong(4, minutes);
            ps.executeUpdate();
         } catch (Exception e) {
            plugin.getDebugSystem().logError("Failed to save playtime history", e);
         }
         return;
      }

      String sql = """
         INSERT INTO %s
         (uuid, player_name, date, minutes)
         VALUES (?, ?, ?, ?)
         ON DUPLICATE KEY UPDATE
           player_name = VALUES(player_name),
           minutes = VALUES(minutes)
         """.formatted(table("history"));
      try (var conn = getConnection(); var ps = conn.prepareStatement(sql)) {
         ps.setString(1, snapshot.getUuid().toString());
         ps.setString(2, snapshot.getName() == null ? "" : snapshot.getName());
         ps.setLong(3, dayStart);
         ps.setLong(4, minutes);
         ps.executeUpdate();
      } catch (Exception e) {
         plugin.getDebugSystem().logError("Failed to save playtime history", e);
      }
   }

   public StatsSnapshot loadByUuid(UUID uuid) {
      if (uuid == null) {
         return null;
      }
      String sql = "SELECT * FROM " + table("stats") + " WHERE uuid = ?";
      try (var conn = getConnection(); var ps = conn.prepareStatement(sql)) {
         ps.setString(1, uuid.toString());
         try (var rs = ps.executeQuery()) {
            if (rs.next()) {
               return readSnapshot(rs);
            }
         }
      } catch (Exception e) {
         plugin.getDebugSystem().logError("Failed to load stats by uuid", e);
      }
      return null;
   }

   public StatsSnapshot loadByName(String name) {
      if (name == null || name.isEmpty()) {
         return null;
      }
      String sql = "SELECT * FROM " + table("stats") + " WHERE LOWER(player_name) = LOWER(?) ORDER BY last_join DESC LIMIT 1";
      try (var conn = getConnection(); var ps = conn.prepareStatement(sql)) {
         ps.setString(1, name);
         try (var rs = ps.executeQuery()) {
            if (rs.next()) {
               return readSnapshot(rs);
            }
         }
      } catch (Exception e) {
         plugin.getDebugSystem().logError("Failed to load stats by name", e);
      }
      return null;
   }

   public void resetPlayer(UUID uuid) {
      if (uuid == null) {
         return;
      }
      String statsSql = "DELETE FROM " + table("stats") + " WHERE uuid = ?";
      String historySql = "DELETE FROM " + table("history") + " WHERE uuid = ?";
      try (var conn = getConnection()) {
         try (var ps = conn.prepareStatement(statsSql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
         }
         try (var ps = conn.prepareStatement(historySql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
         }
      } catch (Exception e) {
         plugin.getDebugSystem().logError("Failed to reset stats", e);
      }
   }

   public void resetAll() {
      String statsSql = "DELETE FROM " + table("stats");
      String historySql = "DELETE FROM " + table("history");
      try (var conn = getConnection(); var stmt = conn.createStatement()) {
         stmt.executeUpdate(statsSql);
         stmt.executeUpdate(historySql);
      } catch (Exception e) {
         plugin.getDebugSystem().logError("Failed to reset all stats", e);
      }
   }

   public List<TopEntry> loadTop(StatsMetric metric, int limit, int offset) {
      List<TopEntry> result = new ArrayList<>();
      if (metric == null) {
         return result;
      }

      String valueExpr = metric.getKey();
      if (metric == StatsMetric.TOTAL_DISTANCE) {
         valueExpr = "(dist_walk + dist_swim + dist_fly)";
      }

      String sql = "SELECT uuid, player_name, " + valueExpr + " AS value FROM " + table("stats")
         + " ORDER BY value DESC LIMIT ? OFFSET ?";

      try (var conn = getConnection(); var ps = conn.prepareStatement(sql)) {
         ps.setInt(1, limit);
         ps.setInt(2, offset);
         try (var rs = ps.executeQuery()) {
            while (rs.next()) {
               UUID uuid = UUID.fromString(rs.getString("uuid"));
               String playerName = rs.getString("player_name");
               double value = rs.getDouble("value");
               result.add(new TopEntry(uuid, playerName, value));
            }
         }
      } catch (Exception e) {
         plugin.getDebugSystem().logError("Failed to load top stats", e);
      }
      return result;
   }

   private StatsSnapshot readSnapshot(ResultSet rs) throws Exception {
      UUID uuid = UUID.fromString(rs.getString("uuid"));
      String name = rs.getString("player_name");
      long totalMinutes = rs.getLong("total_minutes");
      long monthlyMinutes = rs.getLong("monthly_minutes");
      long weeklyMinutes = rs.getLong("weekly_minutes");
      long dailyMinutes = rs.getLong("daily_minutes");
      int lastMonth = rs.getInt("last_month");
      int lastWeek = rs.getInt("last_week");
      long lastJoin = rs.getLong("last_join");
      long firstJoin = rs.getLong("first_join");
      long deaths = rs.getLong("deaths");
      long playerKills = rs.getLong("player_kills");
      long mobKills = rs.getLong("mob_kills");
      long blocksBroken = rs.getLong("blocks_broken");
      long blocksPlaced = rs.getLong("blocks_placed");
      long itemsCrafted = rs.getLong("items_crafted");
      long chatMessages = rs.getLong("chat_messages");
      long distWalk = Math.round(rs.getDouble("dist_walk") * 100.0D);
      long distSwim = Math.round(rs.getDouble("dist_swim") * 100.0D);
      long distFly = Math.round(rs.getDouble("dist_fly") * 100.0D);
      long achievements = rs.getLong("achievements");
      boolean isOnline = rs.getInt("is_online") == 1;

      return new StatsSnapshot(
         uuid,
         name,
         totalMinutes,
         monthlyMinutes,
         weeklyMinutes,
         dailyMinutes,
         lastMonth,
         lastWeek,
         lastJoin,
         firstJoin,
         deaths,
         playerKills,
         mobKills,
         blocksBroken,
         blocksPlaced,
         itemsCrafted,
         distWalk,
         distSwim,
         distFly,
         chatMessages,
         achievements,
         isOnline
      );
   }

   public void setOnline(UUID uuid, boolean online) {
      if (uuid == null) {
         return;
      }
      String sql = "UPDATE " + table("stats") + " SET is_online = ? WHERE uuid = ?";
      try (var conn = getConnection(); var ps = conn.prepareStatement(sql)) {
         ps.setInt(1, online ? 1 : 0);
         ps.setString(2, uuid.toString());
         ps.executeUpdate();
      } catch (Exception e) {
         plugin.getDebugSystem().logError("Failed to update is_online", e);
      }
   }

   public void setAllOffline() {
      String sql = "UPDATE " + table("stats") + " SET is_online = 0";
      try (var conn = getConnection(); var stmt = conn.createStatement()) {
         stmt.executeUpdate(sql);
      } catch (Exception e) {
         plugin.getDebugSystem().logError("Failed to set all offline", e);
      }
   }

   private long toDayStartSeconds(long unixSeconds) {
      ZoneId zone = ZoneId.systemDefault();
      LocalDate date = Instant.ofEpochSecond(unixSeconds).atZone(zone).toLocalDate();
      return date.atStartOfDay(zone).toEpochSecond();
   }
}
