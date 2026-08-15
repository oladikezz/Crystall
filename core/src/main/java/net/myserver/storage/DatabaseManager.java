package net.myserver.storage;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.myserver.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.UUID;

public class DatabaseManager {
    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);
    private static final Gson gson = new Gson();
    private static HikariDataSource dataSource;
    private static boolean enabled = false;

    public static boolean isEnabled() {
        return enabled;
    }

    public static void init(Config config) {
        if (!config.dbEnabled) {
            log.info("[Database] Database disabled in config, using file storage.");
            return;
        }

        try {
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl("jdbc:postgresql://" + config.dbHost + ":" + config.dbPort + "/" + config.dbName);
            hikariConfig.setUsername(config.dbUser);
            hikariConfig.setPassword(config.dbPassword);
            hikariConfig.setMaximumPoolSize(10);
            hikariConfig.setMinimumIdle(2);
            hikariConfig.setConnectionTimeout(5000);
            hikariConfig.setPoolName("CrystallDB");

            dataSource = new HikariDataSource(hikariConfig);
            createTables();
            enabled = true;
            log.info("[Database] PostgreSQL connected: {}:{}/{}", config.dbHost, config.dbPort, config.dbName);
        } catch (Exception e) {
            log.warn("[Database] Failed to connect to PostgreSQL, falling back to file storage: {}", e.getMessage());
            enabled = false;
        }
    }

    private static void createTables() throws SQLException {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS players (
                    uuid       VARCHAR(36) PRIMARY KEY,
                    name       VARCHAR(64),
                    health     REAL DEFAULT 20.0,
                    x          DOUBLE PRECISION DEFAULT 0,
                    y          DOUBLE PRECISION DEFAULT 100,
                    z          DOUBLE PRECISION DEFAULT 0,
                    yaw        REAL DEFAULT 0,
                    pitch      REAL DEFAULT 0,
                    inventory  TEXT DEFAULT '[]',
                    balance    DOUBLE PRECISION DEFAULT 100.0
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS statistics (
                    uuid           VARCHAR(36) PRIMARY KEY,
                    mob_kills      INT DEFAULT 0,
                    blocks_broken  INT DEFAULT 0,
                    play_time_sec  BIGINT DEFAULT 0,
                    FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE
                )
            """);
        }
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    // ========== Player CRUD ==========

    public static void savePlayerData(String uuid, String name, float health,
                                       double x, double y, double z, float yaw, float pitch,
                                       String inventoryJson) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO players (uuid, name, health, x, y, z, yaw, pitch, inventory)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (uuid) DO UPDATE SET
                    name = EXCLUDED.name,
                    health = EXCLUDED.health,
                    x = EXCLUDED.x, y = EXCLUDED.y, z = EXCLUDED.z,
                    yaw = EXCLUDED.yaw, pitch = EXCLUDED.pitch,
                    inventory = EXCLUDED.inventory
             """)) {
            ps.setString(1, uuid);
            ps.setString(2, name);
            ps.setFloat(3, health);
            ps.setDouble(4, x);
            ps.setDouble(5, y);
            ps.setDouble(6, z);
            ps.setFloat(7, yaw);
            ps.setFloat(8, pitch);
            ps.setString(9, inventoryJson);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("[Database] Failed to save player {}: {}", uuid, e.getMessage());
        }
    }

    public static JsonObject loadPlayerData(String uuid) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM players WHERE uuid = ?")) {
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("health", rs.getFloat("health"));
                
                JsonObject pos = new JsonObject();
                pos.addProperty("x", rs.getDouble("x"));
                pos.addProperty("y", rs.getDouble("y"));
                pos.addProperty("z", rs.getDouble("z"));
                pos.addProperty("yaw", rs.getFloat("yaw"));
                pos.addProperty("pitch", rs.getFloat("pitch"));
                obj.add("position", pos);
                
                obj.add("inventory", gson.fromJson(rs.getString("inventory"), JsonArray.class));
                return obj;
            }
        } catch (SQLException e) {
            log.error("[Database] Failed to load player {}: {}", uuid, e.getMessage());
        }
        return null;
    }

    // ========== Economy ==========

    public static double getBalance(String uuid) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT balance FROM players WHERE uuid = ?")) {
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble("balance");
        } catch (SQLException e) {
            log.error("[Database] Failed to get balance for {}: {}", uuid, e.getMessage());
        }
        return 100.0; // default
    }

    public static void setBalance(String uuid, double balance) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO players (uuid, balance) VALUES (?, ?) ON CONFLICT (uuid) DO UPDATE SET balance = EXCLUDED.balance")) {
            ps.setString(1, uuid);
            ps.setDouble(2, balance);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("[Database] Failed to set balance for {}: {}", uuid, e.getMessage());
        }
    }

    public static void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.info("[Database] Connection pool closed.");
        }
    }
}
