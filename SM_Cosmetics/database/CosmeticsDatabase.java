package net.schalker.SMPS.modules.cosmetics.database;

import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.database.ModuleDatabase;
import net.schalker.SMPS.modules.cosmetics.models.CosmeticCategory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Database for cosmetics state.
 * Uses one table only: sm_cosmetics.
 */
public class CosmeticsDatabase extends ModuleDatabase {
    private static final String TABLE_NAME = "sm_cosmetics";

    public CosmeticsDatabase(DoAPI plugin) {
        super(plugin, "cosmetics");
    }

    @Override
    public void createTables() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            if (isSqliteOrH2()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS sm_cosmetics (
                        uuid TEXT PRIMARY KEY,
                        cosmetics TEXT NOT NULL,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                """);
            } else {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS sm_cosmetics (
                        uuid VARCHAR(36) PRIMARY KEY,
                        cosmetics LONGTEXT NOT NULL,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
            }

            plugin.getDebugSystem().log("CosmeticsDatabase", "Tables created/verified: sm_cosmetics");
        } catch (SQLException e) {
            plugin.getDebugSystem().logError("Failed to create cosmetics tables", e);
        }
    }

    /**
     * Legacy compatibility: no-op. Equipped cosmetics are stored in sm_cosmetics via savePlayerState().
     */
    public void saveEquippedCosmetic(UUID playerId, CosmeticCategory category, String cosmeticId) {
    }

    /**
     * Legacy compatibility: no-op.
     */
    public void removeEquippedCosmetic(UUID playerId, CosmeticCategory category) {
    }

    /**
     * Legacy compatibility: no-op.
     */
    public void removeAllEquippedCosmetics(UUID playerId) {
    }

    /**
     * Legacy compatibility: returns empty map.
     */
    public Map<String, String> loadEquippedCosmetics(UUID playerId) {
        return new HashMap<>();
    }

    /**
     * Legacy compatibility: no-op.
     */
    public void saveUnlockedCosmetic(UUID playerId, String cosmeticId) {
    }

    /**
     * Legacy compatibility fallback.
     */
    public boolean isUnlocked(UUID playerId, String cosmeticId) {
        return false;
    }

    /**
     * Legacy compatibility: returns empty set.
     */
    public Set<String> loadUnlockedCosmetics(UUID playerId) {
        return new HashSet<>();
    }

    /**
     * Legacy compatibility: no-op. Settings are stored in sm_cosmetics state payload.
     */
    public void savePlayerSettings(UUID playerId, boolean showOthers, boolean showMine,
                                   boolean silent, boolean reduced, String categoryVisibility) {
    }

    /**
     * Legacy compatibility: returns null.
     */
    public Object[] loadPlayerSettings(UUID playerId) {
        return null;
    }

    public void savePlayerState(UUID playerId, String cosmeticsData) {
        String sql;
        if (isSqliteOrH2()) {
            sql = """
                INSERT INTO %s (uuid, cosmetics, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT(uuid) DO UPDATE SET
                    cosmetics = excluded.cosmetics,
                    updated_at = CURRENT_TIMESTAMP
            """.formatted(TABLE_NAME);
        } else {
            sql = """
                INSERT INTO %s (uuid, cosmetics, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE
                    cosmetics = VALUES(cosmetics),
                    updated_at = CURRENT_TIMESTAMP
            """.formatted(TABLE_NAME);
        }

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerId.toString());
            ps.setString(2, cosmeticsData);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getDebugSystem().logError("Failed to save sm_cosmetics", e);
        }
    }

    public String loadPlayerState(UUID playerId) {
        String sql = "SELECT cosmetics FROM %s WHERE uuid = ?".formatted(TABLE_NAME);

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("cosmetics");
                }
            }
        } catch (SQLException e) {
            plugin.getDebugSystem().logError("Failed to load sm_cosmetics", e);
        }

        return null;
    }
}
