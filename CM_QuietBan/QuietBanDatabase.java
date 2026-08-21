package net.schalker.SMPS.modules.quietban;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.database.ModuleDatabase;

public class QuietBanDatabase extends ModuleDatabase {

   private static final String COLUMNS =
      "id, uuid, player_name, ban_level, ip_lock, ip, reason, issued_by, issued_at, expires_at, source";

   public QuietBanDatabase(DoAPI plugin) {
      super(plugin, "quietban");
   }

   public String bansTable() {
      return table("bans");
   }

   @Override
   public void createTables() {
      String sql = isSqliteOrH2()
         ? """
           CREATE TABLE IF NOT EXISTS %s (
             id VARCHAR(36) NOT NULL PRIMARY KEY,
             uuid VARCHAR(36),
             player_name VARCHAR(32) NOT NULL,
             player_name_lower VARCHAR(32) NOT NULL,
             ban_level VARCHAR(16) NOT NULL,
             ip_lock INTEGER NOT NULL DEFAULT 0,
             ip VARCHAR(64),
             reason TEXT,
             issued_by VARCHAR(32) NOT NULL,
             issued_at BIGINT NOT NULL,
             expires_at BIGINT NOT NULL DEFAULT 0,
             active INTEGER NOT NULL DEFAULT 1,
             source VARCHAR(36),
             unbanned_by VARCHAR(32),
             unban_reason TEXT,
             unbanned_at BIGINT NOT NULL DEFAULT 0
           )
           """.formatted(bansTable())
         : """
           CREATE TABLE IF NOT EXISTS %s (
             id VARCHAR(36) NOT NULL PRIMARY KEY,
             uuid VARCHAR(36),
             player_name VARCHAR(32) NOT NULL,
             player_name_lower VARCHAR(32) NOT NULL,
             ban_level VARCHAR(16) NOT NULL,
             ip_lock INT NOT NULL DEFAULT 0,
             ip VARCHAR(64),
             reason TEXT,
             issued_by VARCHAR(32) NOT NULL,
             issued_at BIGINT NOT NULL,
             expires_at BIGINT NOT NULL DEFAULT 0,
             active INT NOT NULL DEFAULT 1,
             source VARCHAR(36),
             unbanned_by VARCHAR(32),
             unban_reason TEXT,
             unbanned_at BIGINT NOT NULL DEFAULT 0
           )
           """.formatted(bansTable());

      try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
         statement.execute(sql);
      } catch (SQLException exception) {
         plugin.getDebugSystem().logError("QuietBan", "Failed to create tables", exception);
      }
   }

   public List<QuietBanEntry> loadActive() {
      List<QuietBanEntry> entries = new ArrayList<>();
      String sql = "SELECT " + COLUMNS + " FROM " + bansTable() + " WHERE active = 1";

      try (Connection connection = getConnection();
           PreparedStatement statement = connection.prepareStatement(sql);
           ResultSet result = statement.executeQuery()) {
         while (result.next()) {
            QuietBanEntry entry = read(result);
            if (entry != null) {
               entries.add(entry);
            }
         }
      } catch (SQLException exception) {
         plugin.getDebugSystem().logError("QuietBan", "Failed to load active bans", exception);
      }
      return entries;
   }

   public void insert(QuietBanEntry entry) {
      String sql = "INSERT INTO " + bansTable()
         + " (id, uuid, player_name, player_name_lower, ban_level, ip_lock, ip, reason, issued_by,"
         + " issued_at, expires_at, active, source, unbanned_by, unban_reason, unbanned_at)"
         + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, NULL, NULL, 0)";

      try (Connection connection = getConnection();
           PreparedStatement statement = connection.prepareStatement(sql)) {
         statement.setString(1, entry.id());
         statement.setString(2, entry.uuid() == null ? null : entry.uuid().toString());
         statement.setString(3, entry.playerName());
         statement.setString(4, entry.playerNameLower());
         statement.setString(5, entry.level().getKey());
         statement.setInt(6, entry.ipLock() ? 1 : 0);
         statement.setString(7, entry.ip());
         statement.setString(8, entry.reason());
         statement.setString(9, entry.issuedBy());
         statement.setLong(10, entry.issuedAt());
         statement.setLong(11, entry.expiresAt());
         statement.setString(12, entry.source());
         statement.executeUpdate();
      } catch (SQLException exception) {
         plugin.getDebugSystem().logError("QuietBan", "Failed to insert ban " + entry.id(), exception);
      }
   }

   public void updateIdentity(QuietBanEntry entry) {
      String sql = "UPDATE " + bansTable()
         + " SET uuid = ?, player_name = ?, player_name_lower = ?, ip = ? WHERE id = ?";

      try (Connection connection = getConnection();
           PreparedStatement statement = connection.prepareStatement(sql)) {
         statement.setString(1, entry.uuid() == null ? null : entry.uuid().toString());
         statement.setString(2, entry.playerName());
         statement.setString(3, entry.playerNameLower());
         statement.setString(4, entry.ip());
         statement.setString(5, entry.id());
         statement.executeUpdate();
      } catch (SQLException exception) {
         plugin.getDebugSystem().logError("QuietBan", "Failed to update ban " + entry.id(), exception);
      }
   }

   public void deactivate(String id, String unbannedBy, String unbanReason, long unbannedAt) {
      String sql = "UPDATE " + bansTable()
         + " SET active = 0, unbanned_by = ?, unban_reason = ?, unbanned_at = ? WHERE id = ?";

      try (Connection connection = getConnection();
           PreparedStatement statement = connection.prepareStatement(sql)) {
         statement.setString(1, unbannedBy);
         statement.setString(2, unbanReason);
         statement.setLong(3, unbannedAt);
         statement.setString(4, id);
         statement.executeUpdate();
      } catch (SQLException exception) {
         plugin.getDebugSystem().logError("QuietBan", "Failed to deactivate ban " + id, exception);
      }
   }

   private QuietBanEntry read(ResultSet result) throws SQLException {
      QuietBanLevel level = QuietBanLevel.fromKey(result.getString("ban_level"));
      if (level == null) {
         return null;
      }

      String rawUuid = result.getString("uuid");
      UUID uuid = null;
      if (rawUuid != null && !rawUuid.isEmpty()) {
         try {
            uuid = UUID.fromString(rawUuid);
         } catch (IllegalArgumentException exception) {
            uuid = null;
         }
      }

      String playerName = result.getString("player_name");
      return new QuietBanEntry(
         result.getString("id"),
         uuid,
         playerName == null ? "" : playerName,
         level,
         result.getInt("ip_lock") == 1,
         result.getString("ip"),
         result.getString("reason"),
         result.getString("issued_by"),
         result.getLong("issued_at"),
         result.getLong("expires_at"),
         result.getString("source"));
   }

   public static String lower(String value) {
      return value == null ? "" : value.toLowerCase(Locale.ROOT);
   }
}
