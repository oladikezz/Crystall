package net.schalker.SMPS.modules.playerheads.managers;

import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.database.ModuleDatabase;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

public class PlayerHeadsDatabaseManager extends ModuleDatabase {

    private String srDatabase = "s3_SkinsRestorer";
    private String srPlayerSkinsTable = "sr_player_skins";
    private String srUrlSkinsTable = "sr_url_skins";
    private String srPlayersTable = "sr_players";
    private boolean srEnabled = true;

    public PlayerHeadsDatabaseManager(DoAPI plugin) {
        super(plugin, "playerheads");
    }

    public void configureSkinsRestorer(boolean enabled, String database,
                                        String playerSkinsTable, String urlSkinsTable,
                                        String playersTable) {
        this.srEnabled = enabled;
        this.srDatabase = database;
        this.srPlayerSkinsTable = playerSkinsTable;
        this.srUrlSkinsTable = urlSkinsTable;
        this.srPlayersTable = playersTable;
    }

    /**
     * Skin texture data from SkinsRestorer database.
     */
    public record SkinData(String value, String signature) {}

    /**
     * Resolve a player's skin texture from the SkinsRestorer database.
     *
     * Resolution order:
     * 1. Check sr_players to see what skin the player is using
     *    - If skin_type = PLAYER → look up that UUID in sr_player_skins
     *    - If skin_type = URL    → look up that URL in sr_url_skins
     * 2. Fallback: look up the player's own UUID directly in sr_player_skins
     */
    public SkinData lookupSkin(UUID uuid) {
        if (!srEnabled || uuid == null) return null;

        String prefix = srDatabase != null && !srDatabase.isEmpty()
            ? srDatabase + "." : "";

        // Step 1: Check what skin the player is assigned in sr_players
        String playersSql = "SELECT skin_identifier, skin_type FROM " + prefix + srPlayersTable + " WHERE uuid = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(playersSql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String skinIdentifier = rs.getString("skin_identifier");
                String skinType = rs.getString("skin_type");

                if (skinIdentifier != null && skinType != null) {
                    SkinData resolved = resolveByType(prefix, skinIdentifier, skinType);
                    if (resolved != null) return resolved;
                }
            }
        } catch (SQLException e) {
            plugin.getDebugSystem().log("PlayerHeads", "SR sr_players lookup failed: " + e.getMessage());
        }

        // Step 2: Fallback — look up player's own skin directly
        return lookupPlayerSkin(prefix, uuid.toString());
    }

    /**
     * Resolve skin data based on the skin_type from sr_players.
     */
    private SkinData resolveByType(String prefix, String identifier, String type) {
        return switch (type.toUpperCase()) {
            case "PLAYER" -> lookupPlayerSkin(prefix, identifier);
            case "URL" -> lookupUrlSkin(prefix, identifier);
            default -> {
                // Unknown type — try as player UUID first, then URL
                SkinData data = lookupPlayerSkin(prefix, identifier);
                yield data != null ? data : lookupUrlSkin(prefix, identifier);
            }
        };
    }

    /**
     * Look up skin by UUID in sr_player_skins.
     */
    private SkinData lookupPlayerSkin(String prefix, String uuid) {
        String sql = "SELECT value, signature FROM " + prefix + srPlayerSkinsTable + " WHERE uuid = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String value = rs.getString("value");
                String signature = rs.getString("signature");
                if (value != null && !value.isEmpty()) {
                    plugin.getDebugSystem().log("PlayerHeads", "Resolved skin from sr_player_skins for " + uuid);
                    return new SkinData(value, signature);
                }
            }
        } catch (SQLException e) {
            plugin.getDebugSystem().log("PlayerHeads", "sr_player_skins lookup failed for " + uuid + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Look up skin by URL in sr_url_skins.
     */
    private SkinData lookupUrlSkin(String prefix, String url) {
        String sql = "SELECT value, signature FROM " + prefix + srUrlSkinsTable + " WHERE url = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, url);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String value = rs.getString("value");
                String signature = rs.getString("signature");
                if (value != null && !value.isEmpty()) {
                    plugin.getDebugSystem().log("PlayerHeads", "Resolved skin from sr_url_skins for " + url);
                    return new SkinData(value, signature);
                }
            }
        } catch (SQLException e) {
            plugin.getDebugSystem().log("PlayerHeads", "sr_url_skins lookup failed for " + url + ": " + e.getMessage());
        }

        return null;
    }

    @Override
    public void createTables() {
        String sql;

        if (isSqliteOrH2()) {
            sql = """
                CREATE TABLE IF NOT EXISTS %s (
                    uuid TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    death_count INTEGER NOT NULL DEFAULT 0
                )
                """.formatted(table("deaths"));
        } else {
            sql = """
                CREATE TABLE IF NOT EXISTS %s (
                    uuid VARCHAR(36) PRIMARY KEY,
                    name VARCHAR(32) NOT NULL,
                    death_count INT NOT NULL DEFAULT 0,
                    INDEX idx_name (name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """.formatted(table("deaths"));
        }

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            ensureRequiredColumns(conn);
            plugin.getDebugSystem().log("PlayerHeads", "Database table ready: uuid, name, death_count");
        } catch (SQLException exception) {
            plugin.getDebugSystem().logError("Failed to create PlayerHeads tables", exception);
        }
    }

    public int incrementDeath(UUID uuid, String name) {
        String sql;

        if (isSqliteOrH2()) {
            sql = """
                INSERT INTO %s (uuid, name, death_count) VALUES (?, ?, 1)
                ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, death_count = death_count + 1
                """.formatted(table("deaths"));
        } else {
            sql = """
                INSERT INTO %s (uuid, name, death_count) VALUES (?, ?, 1)
                ON DUPLICATE KEY UPDATE name = VALUES(name), death_count = death_count + 1
                """.formatted(table("deaths"));
        }

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, name);
            stmt.executeUpdate();
        } catch (SQLException exception) {
            plugin.getDebugSystem().logError("Failed to increment death count", exception);
        }

        return getDeathCount(uuid);
    }

    public int getDeathCount(UUID uuid) {
        String sql = "SELECT death_count FROM %s WHERE uuid = ?".formatted(table("deaths"));

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("death_count");
            }
            return 0;
        } catch (SQLException exception) {
            plugin.getDebugSystem().logError("Failed to get death count", exception);
            return 0;
        }
    }

    private void ensureRequiredColumns(Connection conn) throws SQLException {
        String tableName = table("deaths");

        if (!hasColumn(conn, tableName, "uuid")) {
            executeAlter(conn, "ALTER TABLE %s ADD COLUMN uuid VARCHAR(36)".formatted(tableName));
        }
        if (!hasColumn(conn, tableName, "name")) {
            executeAlter(conn, "ALTER TABLE %s ADD COLUMN name VARCHAR(32)".formatted(tableName));
        }
        if (!hasColumn(conn, tableName, "death_count")) {
            executeAlter(conn, "ALTER TABLE %s ADD COLUMN death_count INT NOT NULL DEFAULT 0".formatted(tableName));
        }
    }

    private boolean hasColumn(Connection conn, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        String normalizedTable = tableName;
        int dot = tableName.indexOf('.');
        if (dot >= 0 && dot + 1 < tableName.length()) {
            normalizedTable = tableName.substring(dot + 1);
        }

        try (ResultSet rs = metaData.getColumns(conn.getCatalog(), null, normalizedTable, columnName)) {
            if (rs.next()) {
                return true;
            }
        }
        try (ResultSet rs = metaData.getColumns(conn.getCatalog(), null, normalizedTable.toUpperCase(), columnName.toUpperCase())) {
            return rs.next();
        }
    }

    private void executeAlter(Connection conn, String sql) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException exception) {
            plugin.getDebugSystem().logError("Failed to apply schema update: " + sql, exception);
        }
    }
}