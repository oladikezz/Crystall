package site.deforce.SM_Accounts.database;

import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.database.DatabaseType;
import net.schalker.DoAPI.core.database.ModuleDatabase;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Database handler for twink management.
 * Manages the sm_twinks table for main-twink relationships.
 */
public class TwinksDatabase extends ModuleDatabase {

    public TwinksDatabase(DoAPI plugin) {
        super(plugin, "twinks");
    }

    @Override
    public void createTables() {
        String sql;
        String tableName = plugin.getDatabaseManager().table("twinks");
        DatabaseType type = plugin.getDatabaseManager().getDatabaseType();

        if (type == DatabaseType.SQLITE) {
            sql = """
                CREATE TABLE IF NOT EXISTS %s (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    main VARCHAR(16) NOT NULL,
                    twink VARCHAR(16) NOT NULL UNIQUE,
                    linked_at TIMESTAMP NOT NULL
                )
                """.formatted(tableName);
        } else if (type == DatabaseType.H2) {
            sql = """
                CREATE TABLE IF NOT EXISTS %s (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    main VARCHAR(16) NOT NULL,
                    twink VARCHAR(16) NOT NULL UNIQUE,
                    linked_at TIMESTAMP NOT NULL
                )
                """.formatted(tableName);
        } else {
            // MySQL / MariaDB
            sql = """
                CREATE TABLE IF NOT EXISTS %s (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    main VARCHAR(16) NOT NULL,
                    twink VARCHAR(16) NOT NULL UNIQUE,
                    linked_at TIMESTAMP NOT NULL,
                    INDEX idx_main (main),
                    INDEX idx_twink (twink)
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
     * Check if a player is a twink account by username.
     * @param username Player username
     * @return true if this username is registered as a twink
     */
    public boolean isTwink(String username) {
        String tableName = plugin.getDatabaseManager().table("twinks");
        String sql = "SELECT id FROM " + tableName + " WHERE LOWER(twink) = LOWER(?)";

        try (var conn = getConnection();
             var ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (var rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            plugin.getDebugSystem().logError("Failed to check if player is twink", e);
        }
        return false;
    }

    /**
     * Check if a player is a main account (has twinks registered).
     * @param username Player username
     * @return true if this username has twinks registered
     */
    public boolean isMain(String username) {
        String tableName = plugin.getDatabaseManager().table("twinks");
        String sql = "SELECT id FROM " + tableName + " WHERE LOWER(main) = LOWER(?)";

        try (var conn = getConnection();
             var ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (var rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            plugin.getDebugSystem().logError("Failed to check if player is main", e);
        }
        return false;
    }

    /**
     * Get the main account username for a twink.
     * @param twinkUsername Twink username
     * @return Main account username or null if not a twink
     */
    public String getMainUsername(String twinkUsername) {
        String tableName = plugin.getDatabaseManager().table("twinks");
        String sql = "SELECT main FROM " + tableName + " WHERE LOWER(twink) = LOWER(?)";

        try (var conn = getConnection();
             var ps = conn.prepareStatement(sql)) {
            ps.setString(1, twinkUsername);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("main");
                }
            }
        } catch (Exception e) {
            plugin.getDebugSystem().logError("Failed to get main username for twink", e);
        }
        return null;
    }

    /**
     * Get all twink usernames for a main account.
     * @param mainUsername Main account username
     * @return List of twink usernames
     */
    public List<String> getTwinkUsernames(String mainUsername) {
        List<String> twinks = new ArrayList<>();
        String tableName = plugin.getDatabaseManager().table("twinks");
        String sql = "SELECT twink FROM " + tableName + " WHERE LOWER(main) = LOWER(?)";

        try (var conn = getConnection();
             var ps = conn.prepareStatement(sql)) {
            ps.setString(1, mainUsername);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    twinks.add(rs.getString("twink"));
                }
            }
        } catch (Exception e) {
            plugin.getDebugSystem().logError("Failed to get twink usernames for main", e);
        }
        return twinks;
    }

    /**
     * Add a twink relationship.
     * @param mainUsername Main account username
     * @param twinkUsername Twink account username
     * @return true if successful
     */
    public boolean addTwink(String mainUsername, String twinkUsername) {
        String tableName = plugin.getDatabaseManager().table("twinks");
        String sql = "INSERT INTO " + tableName + " (main, twink, linked_at) VALUES (?, ?, ?)";
        Timestamp now = new Timestamp(System.currentTimeMillis());

        try (var conn = getConnection();
             var ps = conn.prepareStatement(sql)) {
            ps.setString(1, mainUsername);
            ps.setString(2, twinkUsername);
            ps.setTimestamp(3, now);
            ps.executeUpdate();
            plugin.getDebugSystem().log("SM_Accounts", "Added twink " + twinkUsername + " for main " + mainUsername);
            return true;
        } catch (Exception e) {
            plugin.getDebugSystem().logError("Failed to add twink", e);
        }
        return false;
    }

    /**
     * Remove a twink relationship by twink username.
     * @param twinkUsername Twink account username
     * @return true if successful
     */
    public boolean removeTwink(String twinkUsername) {
        String tableName = plugin.getDatabaseManager().table("twinks");
        String sql = "DELETE FROM " + tableName + " WHERE LOWER(twink) = LOWER(?)";

        try (var conn = getConnection();
             var ps = conn.prepareStatement(sql)) {
            ps.setString(1, twinkUsername);
            int affected = ps.executeUpdate();
            if (affected > 0) {
                plugin.getDebugSystem().log("SM_Accounts", "Removed twink with username: " + twinkUsername);
                return true;
            }
        } catch (Exception e) {
            plugin.getDebugSystem().logError("Failed to remove twink", e);
        }
        return false;
    }

    /**
     * Get twink info by username.
     * @param twinkUsername Twink username
     * @return TwinkInfo object or null
     */
    public TwinkInfo getTwinkInfo(String twinkUsername) {
        String tableName = plugin.getDatabaseManager().table("twinks");
        String sql = "SELECT * FROM " + tableName + " WHERE LOWER(twink) = LOWER(?)";

        try (var conn = getConnection();
             var ps = conn.prepareStatement(sql)) {
            ps.setString(1, twinkUsername);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new TwinkInfo(
                            rs.getString("main"),
                            rs.getString("twink"),
                            rs.getTimestamp("linked_at")
                    );
                }
            }
        } catch (Exception e) {
            plugin.getDebugSystem().logError("Failed to get twink info", e);
        }
        return null;
    }

    /**
     * Get all related usernames (main + all twinks) for a given username.
     * Works whether the username is a main or a twink.
     * @param username Any account username
     * @return List of all related usernames (including the input username)
     */
    public List<String> getAllRelatedUsernames(String username) {
        List<String> related = new ArrayList<>();

        // First check if this is a twink
        String mainUsername = getMainUsername(username);
        if (mainUsername != null) {
            // This is a twink, get main and all twinks
            related.add(mainUsername);
            related.addAll(getTwinkUsernames(mainUsername));
        } else {
            // This might be a main, check if it has twinks
            List<String> twinks = getTwinkUsernames(username);
            if (!twinks.isEmpty()) {
                related.add(username);
                related.addAll(twinks);
            } else {
                // This account has no twink relationships
                related.add(username);
            }
        }

        return related;
    }

    /**
     * Data class for twink information.
     */
    public record TwinkInfo(String mainUsername, String twinkUsername, Timestamp linkedAt) {}
}
