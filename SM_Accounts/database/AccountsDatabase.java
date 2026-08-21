package site.deforce.SM_Accounts.database;

import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.database.DatabaseType;
import net.schalker.DoAPI.core.database.ModuleDatabase;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.util.UUID;

/**
 * Database handler for SM_Accounts module.
 * Manages the sm_accounts table for Discord linking.
 */
public class AccountsDatabase extends ModuleDatabase {

    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public AccountsDatabase(DoAPI plugin) {
        super(plugin, "accounts");
    }

    @Override
    public void createTables() {
        String sql;
        // Use global table resolver to get sm_accounts instead of sm_accounts_accounts
        String tableName = plugin.getDatabaseManager().table("accounts");
        DatabaseType type = plugin.getDatabaseManager().getDatabaseType();

        if (type == DatabaseType.SQLITE) {
            sql = """
                CREATE TABLE IF NOT EXISTS %s (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid VARCHAR(36) NOT NULL UNIQUE,
                    username VARCHAR(16),
                    discordid VARCHAR(20),
                    code VARCHAR(10),
                    code_creation TIMESTAMP
                )
                """.formatted(tableName);
        } else if (type == DatabaseType.H2) {
             sql = """
                CREATE TABLE IF NOT EXISTS %s (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    uuid VARCHAR(36) NOT NULL UNIQUE,
                    username VARCHAR(16),
                    discordid VARCHAR(20),
                    code VARCHAR(10),
                    code_creation TIMESTAMP
                )
                """.formatted(tableName);
        } else {
            // MySQL / MariaDB
            sql = """
                CREATE TABLE IF NOT EXISTS %s (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    uuid VARCHAR(36) NOT NULL UNIQUE,
                    username VARCHAR(16),
                    discordid VARCHAR(20),
                    code VARCHAR(10),
                    code_creation TIMESTAMP
                )
                """.formatted(tableName);
        }

        try (var conn = getConnection();
             var stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            plugin.getDebugSystem().log("SM_Accounts", "Created/verified " + tableName + " table (Type: " + type + ")");
        } catch (Exception e) {
            plugin.getDebugSystem().logError("Failed to create " + tableName + " table", e);
        }
    }

    /**
     * Check if player has linked their Discord account.
     * @param uuid Player UUID
     * @return true if discordid is not null and not empty
     */
    public boolean isLinked(UUID uuid) {
        String tableName = plugin.getDatabaseManager().table("accounts");
        String sql = "SELECT discordid FROM " + tableName + " WHERE uuid = ?";

        try (var conn = getConnection();
             var ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    String discordId = rs.getString("discordid");
                    return discordId != null && !discordId.isBlank();
                }
            }
        } catch (Exception e) {
            plugin.getDebugSystem().logError("Failed to check if player is linked", e);
        }
        return false;
    }

    /**
     * Get existing verification code for player, or null if none exists.
     * @param uuid Player UUID
     * @return existing code or null
     */
    public String getExistingCode(UUID uuid) {
        String tableName = plugin.getDatabaseManager().table("accounts");
        String sql = "SELECT code FROM " + tableName + " WHERE uuid = ?";

        try (var conn = getConnection();
             var ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    String code = rs.getString("code");
                    if (code != null && !code.isBlank()) {
                        return code;
                    }
                }
            }
        } catch (Exception e) {
            plugin.getDebugSystem().logError("Failed to get existing code", e);
        }
        return null;
    }

    /**
     * Generate a new 10-character verification code.
     */
    public String generateCode() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * Create or update player record with a new verification code.
     * @param uuid Player UUID
     * @param username Player username
     * @return The generated code
     */
    public String createOrUpdateCode(UUID uuid, String username) {
        String code = generateCode();
        Timestamp now = new Timestamp(System.currentTimeMillis());
        String tableName = plugin.getDatabaseManager().table("accounts");

        // Check if player exists in database
        String checkSql = "SELECT id FROM " + tableName + " WHERE uuid = ?";
        boolean exists = false;

        try (var conn = getConnection();
             var ps = conn.prepareStatement(checkSql)) {
            ps.setString(1, uuid.toString());
            try (var rs = ps.executeQuery()) {
                exists = rs.next();
            }
        } catch (Exception e) {
            plugin.getDebugSystem().logError("Failed to check player existence", e);
        }

        if (exists) {
            // Update existing record
            String updateSql = "UPDATE " + tableName +
                    " SET username = ?, code = ?, code_creation = ? WHERE uuid = ?";
            try (var conn = getConnection();
                 var ps = conn.prepareStatement(updateSql)) {
                ps.setString(1, username);
                ps.setString(2, code);
                ps.setTimestamp(3, now);
                ps.setString(4, uuid.toString());
                ps.executeUpdate();
                plugin.getDebugSystem().log("SM_Accounts", "Updated code for " + username + ": " + code);
            } catch (Exception e) {
                plugin.getDebugSystem().logError("Failed to update code", e);
            }
        } else {
            // Insert new record
            String insertSql = "INSERT INTO " + tableName +
                    " (uuid, username, code, code_creation) VALUES (?, ?, ?, ?)";
            try (var conn = getConnection();
                 var ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, username);
                ps.setString(3, code);
                ps.setTimestamp(4, now);
                ps.executeUpdate();
                plugin.getDebugSystem().log("SM_Accounts", "Created new entry for " + username + " with code: " + code);
            } catch (Exception e) {
                plugin.getDebugSystem().logError("Failed to insert new player record", e);
            }
        }

        return code;
    }

    /**
     * Get Discord ID for player UUID.
     * @param uuid Player UUID
     * @return discord ID or null
     */
    public String getDiscordId(UUID uuid) {
        String tableName = plugin.getDatabaseManager().table("accounts");
        String sql = "SELECT discordid FROM " + tableName + " WHERE uuid = ?";

        try (var conn = getConnection();
             var ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("discordid");
                }
            }
        } catch (Exception e) {
            plugin.getDebugSystem().logError("Failed to get discord ID", e);
        }
        return null;
    }

    /**
     * Get UUID for username (case-insensitive search).
     * @param username Player username
     * @return UUID or null
     */
    public UUID getUUID(String username) {
        String tableName = plugin.getDatabaseManager().table("accounts");
        String sql = "SELECT uuid FROM " + tableName + " WHERE LOWER(username) = LOWER(?)";

        try (var conn = getConnection();
             var ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return UUID.fromString(rs.getString("uuid"));
                }
            }
        } catch (Exception e) {
            plugin.getDebugSystem().logError("Failed to get UUID for username: " + username, e);
        }
        return null;
    }

    /**
     * Unlink a user by removing their discord ID.
     * @param uuid Player UUID
     */
    public void unlinkUser(UUID uuid) {
        String tableName = plugin.getDatabaseManager().table("accounts");
        String sql = "UPDATE " + tableName + " SET discordid = NULL WHERE uuid = ?";

        try (var conn = getConnection();
             var ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
            plugin.getDebugSystem().log("SM_Accounts", "Unlinked user with UUID: " + uuid);
        } catch (Exception e) {
            plugin.getDebugSystem().logError("Failed to unlink user", e);
        }
    }

    /**
     * Get Discord ID for a player by username (case-insensitive).
     * @param username Player username
     * @return discord ID or null
     */
    public String getDiscordIdByUsername(String username) {
        String tableName = plugin.getDatabaseManager().table("accounts");
        String sql = "SELECT discordid FROM " + tableName + " WHERE LOWER(username) = LOWER(?)";

        try (var conn = getConnection();
             var ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("discordid");
                }
            }
        } catch (Exception e) {
            plugin.getDebugSystem().logError("Failed to get discord ID by username: " + username, e);
        }
        return null;
    }

    /**
     * Completely delete a player record by username (case-insensitive).
     * @param username Player username
     * @return true if a row was deleted
     */
    public boolean deleteByUsername(String username) {
        String tableName = plugin.getDatabaseManager().table("accounts");
        String sql = "DELETE FROM " + tableName + " WHERE LOWER(username) = LOWER(?)";

        try (var conn = getConnection();
             var ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            int affected = ps.executeUpdate();
            if (affected > 0) {
                plugin.getDebugSystem().log("SM_Accounts", "Deleted account record for: " + username);
                return true;
            }
        } catch (Exception e) {
            plugin.getDebugSystem().logError("Failed to delete account for: " + username, e);
        }
        return false;
    }

    /**
     * Check if a username exists in the accounts table.
     * @param username Player username
     * @return true if exists
     */
    public boolean existsByUsername(String username) {
        String tableName = plugin.getDatabaseManager().table("accounts");
        String sql = "SELECT id FROM " + tableName + " WHERE LOWER(username) = LOWER(?)";

        try (var conn = getConnection();
             var ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (var rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            plugin.getDebugSystem().logError("Failed to check existence for: " + username, e);
        }
        return false;
    }
}
