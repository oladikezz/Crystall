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

public class DatabaseManager {
    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);
    private static final Gson gson = new Gson();
    private static HikariDataSource dataSource;
    private static volatile boolean enabled = false;

    public static boolean isEnabled() {
        return enabled && dataSource != null && !dataSource.isClosed();
    }

    public static void init(Config config) {
        if (!config.dbEnabled) {
            log.info("[Database] База данных отключена в config.yml, используется локальное хранилище.");
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
            log.info("[Database] Подключение к PostgreSQL успешно: {}:{}/{}", config.dbHost, config.dbPort, config.dbName);
        } catch (Exception e) {
            log.warn("[Database] Не удалось подключиться к PostgreSQL, переключаемся на локальные файлы: {}", e.getMessage());
            enabled = false;
        }
    }

    private static void createTables() throws SQLException {
        if (dataSource == null) return;
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
        if (dataSource == null) throw new SQLException("DataSource не инициализирован.");
        return dataSource.getConnection();
    }

    // ========== Player CRUD ==========

    public static void savePlayerData(String uuid, String name, float health,
                                       double x, double y, double z, float yaw, float pitch,
                                       String inventoryJson) {
        if (!isEnabled()) return;
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
            log.error("[Database] Ошибка сохранения игрока {}: {}", uuid, e.getMessage());
        }
    }

    public static JsonObject loadPlayerData(String uuid) {
        if (!isEnabled()) return null;
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
            log.error("[Database] Ошибка загрузки игрока {}: {}", uuid, e.getMessage());
        }
        return null;
    }

    // ========== Atomic Economy ==========

    public static double getBalance(String uuid) {
        if (!isEnabled()) return 100.0;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT balance FROM players WHERE uuid = ?")) {
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble("balance");
        } catch (SQLException e) {
            log.error("[Database] Ошибка получения баланса для {}: {}", uuid, e.getMessage());
        }
        return 100.0; // default
    }

    public static void setBalance(String uuid, double balance) {
        if (!isEnabled()) return;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO players (uuid, balance) VALUES (?, ?) ON CONFLICT (uuid) DO UPDATE SET balance = EXCLUDED.balance")) {
            ps.setString(1, uuid);
            ps.setDouble(2, balance);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("[Database] Ошибка установки баланса для {}: {}", uuid, e.getMessage());
        }
    }

    public static void addBalance(String uuid, double amount) {
        if (!isEnabled()) return;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO players (uuid, balance) VALUES (?, 100.0 + ?) ON CONFLICT (uuid) DO UPDATE SET balance = players.balance + ?")) {
            ps.setString(1, uuid);
            ps.setDouble(2, amount);
            ps.setDouble(3, amount);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("[Database] Ошибка пополнения баланса для {}: {}", uuid, e.getMessage());
        }
    }

    public static boolean removeBalance(String uuid, double amount) {
        if (!isEnabled()) return false;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "UPDATE players SET balance = balance - ? WHERE uuid = ? AND balance >= ?")) {
            ps.setDouble(1, amount);
            ps.setString(2, uuid);
            ps.setDouble(3, amount);
            int rows = ps.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            log.error("[Database] Ошибка списания баланса для {}: {}", uuid, e.getMessage());
            return false;
        }
    }

    public static void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.info("[Database] Пул подключений базы данных закрыт.");
        }
    }
}
