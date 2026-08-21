package net.schalker.SMPS.modules.flags;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.schalker.DoAPI.DoAPI;
import org.bukkit.Location;
import org.bukkit.World;

public class FlagsDatabase {
   private static final String TABLE_NAME = "flags_settings";
   private static final String HISTORY_TABLE = "flags_history";
   private final DoAPI plugin;
   private final Path databasePath;
   private final String jdbcUrl;

   public FlagsDatabase(DoAPI plugin) {
      this.plugin = plugin;
      Path dataFolder = plugin.getDataFolder().toPath().resolve("data");
      try {
         Files.createDirectories(dataFolder);
      } catch (Exception e) {
         this.plugin.getDebugSystem().logError("Failed to create data directory", e);
      }

      this.databasePath = dataFolder.resolve("database.flags");
      String normalizedPath = this.databasePath.toAbsolutePath().toString().replace('\\', '/');
      this.jdbcUrl = "jdbc:h2:file:" + normalizedPath + ";MODE=MySQL;DATABASE_TO_UPPER=FALSE;AUTO_SERVER=TRUE;TRACE_LEVEL_FILE=0";

      try {
         Class.forName("org.h2.Driver");
      } catch (ClassNotFoundException e) {
         this.plugin.getDebugSystem().logError("H2 driver not found", e);
      }
   }

   public boolean createTables() {
      String settingsSql = """
         CREATE TABLE IF NOT EXISTS %s (
            player_uuid VARCHAR(36) NOT NULL,
            flag_type VARCHAR(50) NOT NULL,
            enabled BOOLEAN NOT NULL DEFAULT TRUE,
            PRIMARY KEY (player_uuid, flag_type)
         )
      """.formatted(TABLE_NAME);

      String historySql = """
         CREATE TABLE IF NOT EXISTS %s (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            timestamp BIGINT NOT NULL,
            player_uuid VARCHAR(36) NOT NULL,
            player_name VARCHAR(20) NOT NULL,
            flag_type VARCHAR(50) NOT NULL,
            world VARCHAR(64),
            x INT,
            y INT,
            z INT,
            flag_value INT,
            details VARCHAR(512)
         )
      """.formatted(HISTORY_TABLE);

      String playerIndexSql = "CREATE INDEX IF NOT EXISTS idx_flags_history_player ON %s(player_name)".formatted(HISTORY_TABLE);
      String flagIndexSql = "CREATE INDEX IF NOT EXISTS idx_flags_history_flag ON %s(flag_type)".formatted(HISTORY_TABLE);

      try (Connection connection = this.openConnection();
           Statement statement = connection.createStatement()) {
         statement.executeUpdate(settingsSql);
         statement.executeUpdate(historySql);
         statement.executeUpdate(playerIndexSql);
         statement.executeUpdate(flagIndexSql);
         this.repairAutoIncrement(statement);
         return true;
      } catch (SQLException e) {
         this.plugin.getDebugSystem().logError("Failed to create flags tables", e);
         return false;
      }
   }

   /**
    * Repair the AUTO_INCREMENT sequence for the history table.
    * H2's AUTO_INCREMENT can get out of sync when multiple connections insert
    * concurrently, leading to primary key violations. This resets the sequence
    * to MAX(id)+1 on every startup.
    */
   private void repairAutoIncrement(Statement statement) {
      try {
         String sql = "ALTER TABLE %s ALTER COLUMN id RESTART WITH (SELECT COALESCE(MAX(id), 0) + 1 FROM %s)"
            .formatted(HISTORY_TABLE, HISTORY_TABLE);
         statement.executeUpdate(sql);
      } catch (SQLException e) {
         // Non-fatal — log and continue
         this.plugin.getDebugSystem().logError("Failed to repair auto-increment for flags_history", e);
      }
   }

   public void saveSetting(UUID playerId, FlagType flagType, boolean enabled) {
      String sql = """
         MERGE INTO %s (player_uuid, flag_type, enabled)
         KEY (player_uuid, flag_type)
         VALUES (?, ?, ?)
      """.formatted(TABLE_NAME);

      try (Connection connection = this.openConnection();
           PreparedStatement statement = connection.prepareStatement(sql)) {
         statement.setString(1, playerId.toString());
         statement.setString(2, flagType.getKey());
         statement.setBoolean(3, enabled);
         statement.executeUpdate();
      } catch (SQLException e) {
         this.plugin.getDebugSystem().logError("Failed to save flag setting", e);
      }
   }

   public EnumMap<FlagType, Boolean> loadSettings(UUID playerId) {
      EnumMap<FlagType, Boolean> settings = new EnumMap<>(FlagType.class);

      for (FlagType type : FlagType.values()) {
         settings.put(type, true);
      }

      String sql = "SELECT flag_type, enabled FROM " + TABLE_NAME + " WHERE player_uuid = ?";

      try (Connection connection = this.openConnection();
           PreparedStatement statement = connection.prepareStatement(sql)) {
         statement.setString(1, playerId.toString());
         try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
               String typeKey = resultSet.getString("flag_type");
               boolean enabled = resultSet.getBoolean("enabled");
               FlagType type = FlagType.fromKey(typeKey);
               if (type != null) {
                  settings.put(type, enabled);
               }
            }
         }
      } catch (SQLException e) {
         this.plugin.getDebugSystem().logError("Failed to load flag settings", e);
      }

      return settings;
   }

   public boolean isEnabled(UUID playerId, FlagType flagType) {
      Map<FlagType, Boolean> settings = this.loadSettings(playerId);
      return settings.getOrDefault(flagType, true);
   }

   public void setAllFlags(UUID playerId, boolean enabled) {
      EnumMap<FlagType, Boolean> settings = new EnumMap<>(FlagType.class);
      for (FlagType type : FlagType.values()) {
         settings.put(type, enabled);
      }
      this.saveSettings(playerId, settings);
   }

   public void saveFlagEvent(FlagEvent event) {
      String sql = """
         INSERT INTO %s (timestamp, player_uuid, player_name, flag_type, world, x, y, z, flag_value, details)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      """.formatted(HISTORY_TABLE);

      try (Connection connection = this.openConnection();
           PreparedStatement statement = connection.prepareStatement(sql)) {
         this.fillFlagEventStatement(statement, event);
         statement.executeUpdate();
      } catch (SQLException e) {
         // H2 error code 23505 = unique constraint violation (AUTO_INCREMENT desync)
         if (e.getErrorCode() == 23505) {
            this.plugin.getDebugSystem().log("Flags",
               "Auto-increment collision detected, repairing sequence and retrying...");
            this.repairAndRetry(sql, event);
         } else {
            this.plugin.getDebugSystem().logError("Failed to save flag event", e);
         }
      }
   }

   private void repairAndRetry(String sql, FlagEvent event) {
      try (Connection connection = this.openConnection();
           Statement stmt = connection.createStatement()) {
         this.repairAutoIncrement(stmt);
      } catch (SQLException ex) {
         this.plugin.getDebugSystem().logError("Failed to repair auto-increment during retry", ex);
         return;
      }
      try (Connection connection = this.openConnection();
           PreparedStatement statement = connection.prepareStatement(sql)) {
         this.fillFlagEventStatement(statement, event);
         statement.executeUpdate();
      } catch (SQLException ex) {
         this.plugin.getDebugSystem().logError("Failed to save flag event after auto-increment repair", ex);
      }
   }

   private void fillFlagEventStatement(PreparedStatement statement, FlagEvent event) throws SQLException {
      statement.setLong(1, event.getTimestamp());
      statement.setString(2, event.getPlayerId().toString());
      statement.setString(3, event.getPlayerName());
      statement.setString(4, event.getFlagType().getKey());

      Location location = event.getLocation();
      String worldName = location != null && location.getWorld() != null
         ? location.getWorld().getName()
         : event.getWorld();
      if (worldName != null) {
         statement.setString(5, worldName);
      } else {
         statement.setNull(5, java.sql.Types.VARCHAR);
      }
      if (location != null) {
         statement.setInt(6, location.getBlockX());
         statement.setInt(7, location.getBlockY());
         statement.setInt(8, location.getBlockZ());
      } else {
         statement.setNull(6, java.sql.Types.INTEGER);
         statement.setNull(7, java.sql.Types.INTEGER);
         statement.setNull(8, java.sql.Types.INTEGER);
      }
      statement.setInt(9, event.getValue());
      if (event.getDetails() != null) {
         statement.setString(10, event.getDetails());
      } else {
         statement.setNull(10, java.sql.Types.VARCHAR);
      }
   }

   public List<FlagEvent> loadHistory(int limit) {
      return this.loadHistory(null, limit);
   }

   public List<FlagEvent> loadHistory(String playerName, int limit) {
      String normalizedPlayer = playerName != null ? playerName.toLowerCase() : null;

      String baseSql = """
         SELECT id, timestamp, player_uuid, player_name, flag_type, world, x, y, z, flag_value, details
         FROM %s
      """.formatted(HISTORY_TABLE);

      String where = normalizedPlayer != null ? " WHERE LOWER(player_name) = ?" : "";
      String orderLimit = " ORDER BY timestamp DESC LIMIT ?";
      String sql = baseSql + where + orderLimit;

      List<FlagEvent> events = new ArrayList<>();

      try (Connection connection = this.openConnection();
           PreparedStatement statement = connection.prepareStatement(sql)) {
         int paramIndex = 1;
         if (normalizedPlayer != null) {
            statement.setString(paramIndex++, normalizedPlayer);
         }
         statement.setInt(paramIndex, Math.max(1, limit));

         try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
               FlagEvent event = this.mapEvent(resultSet);
               if (event != null) {
                  events.add(event);
               }
            }
         }
      } catch (SQLException e) {
         this.plugin.getDebugSystem().logError("Failed to load flag history", e);
      }

      return events;
   }

   public void clearHistory() {
      String sql = "DELETE FROM " + HISTORY_TABLE;
      try (Connection connection = this.openConnection();
           PreparedStatement statement = connection.prepareStatement(sql)) {
         statement.executeUpdate();
      } catch (SQLException e) {
         this.plugin.getDebugSystem().logError("Failed to clear flag history", e);
      }
   }

   /**
    * Delete all flag history entries for a specific player (by name, case-insensitive).
    * Returns the number of deleted rows.
    */
   public int clearPlayerHistory(String playerName) {
      String sql = "DELETE FROM " + HISTORY_TABLE + " WHERE LOWER(player_name) = ?";
      try (Connection connection = this.openConnection();
           PreparedStatement statement = connection.prepareStatement(sql)) {
         statement.setString(1, playerName.toLowerCase());
         return statement.executeUpdate();
      } catch (SQLException e) {
         this.plugin.getDebugSystem().logError("Failed to clear player flag history for " + playerName, e);
         return 0;
      }
   }

   /**
    * Delete all flag notification settings for a specific player.
    */
   public void clearPlayerSettings(UUID playerId) {
      String sql = "DELETE FROM " + TABLE_NAME + " WHERE player_uuid = ?";
      try (Connection connection = this.openConnection();
           PreparedStatement statement = connection.prepareStatement(sql)) {
         statement.setString(1, playerId.toString());
         statement.executeUpdate();
      } catch (SQLException e) {
         this.plugin.getDebugSystem().logError("Failed to clear player flag settings for " + playerId, e);
      }
   }

   public void saveSettings(UUID playerId, Map<FlagType, Boolean> settings) {
      if (settings == null || settings.isEmpty()) {
         return;
      }

      String sql = """
         MERGE INTO %s (player_uuid, flag_type, enabled)
         KEY (player_uuid, flag_type)
         VALUES (?, ?, ?)
      """.formatted(TABLE_NAME);

      try (Connection connection = this.openConnection();
           PreparedStatement statement = connection.prepareStatement(sql)) {
         for (Map.Entry<FlagType, Boolean> entry : settings.entrySet()) {
            statement.setString(1, playerId.toString());
            statement.setString(2, entry.getKey().getKey());
            statement.setBoolean(3, entry.getValue());
            statement.addBatch();
         }
         statement.executeBatch();
      } catch (SQLException e) {
         this.plugin.getDebugSystem().logError("Failed to save bulk flag settings", e);
      }
   }

   private FlagEvent mapEvent(ResultSet resultSet) throws SQLException {
      String playerUuidRaw = resultSet.getString("player_uuid");
      String playerName = resultSet.getString("player_name");
      String flagKey = resultSet.getString("flag_type");
      FlagType flagType = FlagType.fromKey(flagKey);
      if (flagType == null) {
         return null;
      }

      UUID playerUuid;
      try {
         playerUuid = UUID.fromString(playerUuidRaw);
      } catch (IllegalArgumentException ex) {
         return null;
      }

      FlagEvent.Builder builder = FlagEvent.builder()
         .playerId(playerUuid)
         .playerName(playerName)
         .flagType(flagType)
         .timestamp(resultSet.getLong("timestamp"))
         .details(resultSet.getString("details"))
         .value(resultSet.getInt("flag_value"));

      String worldName = resultSet.getString("world");
      Integer x = (Integer) resultSet.getObject("x");
      Integer y = (Integer) resultSet.getObject("y");
      Integer z = (Integer) resultSet.getObject("z");

      Location location = null;
      if (worldName != null && x != null && y != null && z != null) {
         World world = this.plugin.getServer().getWorld(worldName);
         if (world != null) {
            location = new Location(world, x, y, z);
            builder.location(location);
         } else {
            builder.world(worldName);
         }
      } else if (worldName != null) {
         builder.world(worldName);
      }

      return builder.build();
   }

   private Connection openConnection() throws SQLException {
      return DriverManager.getConnection(this.jdbcUrl, "sa", "");
   }

   public Path getDatabaseFile() {
      return Paths.get(this.databasePath.toString() + ".mv.db");
   }
}
