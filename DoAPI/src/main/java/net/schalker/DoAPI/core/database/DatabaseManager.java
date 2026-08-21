package net.schalker.DoAPI.core.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.schalker.DoAPI.DoAPI;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

public class DatabaseManager {

    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final long RECONNECT_DELAY_MS = 5000L;

    public static class DatabaseEntry {

        private final String key;
        private final DatabaseConfig config;
        private volatile HikariDataSource dataSource;
        private volatile boolean connected;
        private volatile String permissions = "NONE";

        public DatabaseEntry(String key, DatabaseConfig config) {
            this.key = key;
            this.config = config;
        }

        public String getKey() {
            return key;
        }

        public DatabaseConfig getConfig() {
            return config;
        }

        public HikariDataSource getDataSource() {
            return dataSource;
        }

        public boolean isConnected() {
            return connected && dataSource != null && !dataSource.isClosed();
        }

        public String getPermissions() {
            return permissions;
        }

        public void setPermissions(String permissions) {
            this.permissions = permissions;
        }
    }

    private final DoAPI plugin;
    private final LinkedHashMap<String, DatabaseEntry> databases = new LinkedHashMap<>();

    private volatile DatabaseConfig config;
    private volatile HikariDataSource dataSource;
    private volatile boolean connected;
    private volatile int reconnectAttempts;

    public DatabaseManager(DoAPI plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        FileConfiguration fileConfig = plugin.getConfigManager().getConfig();

        synchronized (databases) {
            databases.clear();

            DatabaseConfig primary = DatabaseConfig.fromConfig(fileConfig, "database");
            if (primary == null) {
                primary = DatabaseConfig.createH2Fallback();
            }
            databases.put("database", new DatabaseEntry("database", primary));

            for (String key : fileConfig.getKeys(false)) {
                if (!key.startsWith("database") || key.equals("database")) {
                    continue;
                }
                DatabaseConfig extra = DatabaseConfig.fromConfig(fileConfig, key);
                if (extra != null) {
                    databases.put(key, new DatabaseEntry(key, extra));
                }
            }
        }

        DatabaseEntry primaryEntry = databases.get("database");
        this.config = primaryEntry.getConfig();

        if (!tryConnectEntry(primaryEntry)) {
            plugin.getLogger().warning("Primary database ("
                    + primaryEntry.getConfig().getType().getDisplayName()
                    + ") unavailable, falling back to H2");

            DatabaseEntry fallback = new DatabaseEntry("database", DatabaseConfig.createH2Fallback());
            synchronized (databases) {
                databases.put("database", fallback);
            }
            primaryEntry = fallback;
            this.config = fallback.getConfig();

            if (!tryConnectEntry(fallback)) {
                plugin.getLogger().severe("H2 fallback failed, database features are disabled");
                this.connected = false;
                this.dataSource = null;
                scheduleReconnect();
                return;
            }
        }

        this.dataSource = primaryEntry.getDataSource();
        this.connected = primaryEntry.isConnected();
        this.reconnectAttempts = 0;

        for (DatabaseEntry entry : getEntries()) {
            if (!entry.getKey().equals("database")) {
                tryConnectEntry(entry);
            }
        }

        createCoreTables();
        plugin.getLogger().info("Database connected: "
                + config.getType().getDisplayName()
                + " (" + getConnectedCount() + "/" + getTotalCount() + " pools)");
    }

    private boolean tryConnectEntry(DatabaseEntry entry) {
        DatabaseConfig entryConfig = entry.getConfig();

        try {
            File dataFolder = new File(plugin.getDataFolder(), "data");
            if (entryConfig.getType().isFile() && !dataFolder.exists() && !dataFolder.mkdirs()) {
                plugin.getLogger().warning("Could not create data folder " + dataFolder.getPath());
            }

            HikariConfig hikari = new HikariConfig();
            hikari.setJdbcUrl(entryConfig.getJdbcUrl(dataFolder.getAbsolutePath().replace('\\', '/')));

            String driver = entryConfig.getType().resolveAvailableDriver();
            if (driver != null) {
                hikari.setDriverClassName(driver);
            }

            if (entryConfig.getType().isRemote()) {
                hikari.setUsername(entryConfig.getUsername());
                hikari.setPassword(entryConfig.getPassword());
            }

            hikari.setPoolName("DoAPI-" + entry.getKey());
            hikari.setMaximumPoolSize(Math.max(1, entryConfig.getPoolSize()));
            hikari.setMinimumIdle(Math.max(1, Math.min(2, entryConfig.getPoolSize())));
            hikari.setConnectionTimeout(entryConfig.getConnectionTimeout());
            hikari.setIdleTimeout(entryConfig.getIdleTimeout());
            hikari.setMaxLifetime(entryConfig.getMaxLifetime());
            hikari.setInitializationFailTimeout(-1);
            hikari.setLeakDetectionThreshold(0);

            if (entryConfig.getType() == DatabaseType.SQLITE) {
                hikari.setMaximumPoolSize(1);
                hikari.setMinimumIdle(1);
            }
            if (entryConfig.getType().isRemote()) {
                hikari.addDataSourceProperty("cachePrepStmts", "true");
                hikari.addDataSourceProperty("prepStmtCacheSize", "250");
                hikari.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                hikari.addDataSourceProperty("useServerPrepStmts", "true");
            }

            HikariDataSource source = new HikariDataSource(hikari);
            try (Connection connection = source.getConnection()) {
                if (!connection.isValid(5)) {
                    throw new SQLException("Connection validation failed");
                }
            }

            entry.dataSource = source;
            entry.connected = true;
            detectPermissions(entry);
            return true;
        } catch (Throwable throwable) {
            entry.connected = false;
            if (entry.dataSource != null) {
                try {
                    entry.dataSource.close();
                } catch (Throwable ignored) {
                }
                entry.dataSource = null;
            }
            entry.setPermissions("NONE");
            plugin.getLogger().warning("Database '" + entry.getKey() + "' connection failed: "
                    + throwable.getMessage());
            return false;
        }
    }

    private void detectPermissions(DatabaseEntry entry) {
        String probeTable = entry.getConfig().getTablePrefix() + "doapi_probe";

        try (Connection connection = entry.getDataSource().getConnection();
             Statement statement = connection.createStatement()) {

            try {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + probeTable + " (id INT)");
                statement.executeUpdate("INSERT INTO " + probeTable + " (id) VALUES (1)");
                try (ResultSet resultSet = statement.executeQuery("SELECT id FROM " + probeTable)) {
                    resultSet.next();
                }
                statement.executeUpdate("DROP TABLE " + probeTable);
                entry.setPermissions("READ_WRITE");
                return;
            } catch (SQLException ignored) {
                try {
                    statement.executeUpdate("DROP TABLE " + probeTable);
                } catch (SQLException alsoIgnored) {
                }
            }

            try (ResultSet resultSet = statement.executeQuery("SELECT 1")) {
                resultSet.next();
                entry.setPermissions("READ_ONLY");
            }
        } catch (Throwable throwable) {
            entry.setPermissions("NONE");
        }
    }

    private void scheduleReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            plugin.getLogger().severe("Giving up on database reconnection after "
                    + MAX_RECONNECT_ATTEMPTS + " attempts");
            return;
        }
        reconnectAttempts++;

        Bukkit.getAsyncScheduler().runDelayed(plugin, task -> {
            plugin.getLogger().info("Database reconnect attempt " + reconnectAttempts
                    + "/" + MAX_RECONNECT_ATTEMPTS);
            initialize();
        }, RECONNECT_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    public void reconnect() {
        shutdown();
        reconnectAttempts = 0;
        initialize();
    }

    private void createCoreTables(DatabaseEntry entry) {
        if (!entry.isConnected() || !"READ_WRITE".equals(entry.getPermissions())) {
            return;
        }

        String table = entry.getConfig().getTablePrefix() + "migrations";
        String sql = entry.getConfig().getType().isFile()
                ? "CREATE TABLE IF NOT EXISTS " + table + " ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "module VARCHAR(64) NOT NULL,"
                    + "version INT NOT NULL,"
                    + "applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
                : "CREATE TABLE IF NOT EXISTS " + table + " ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY,"
                    + "module VARCHAR(64) NOT NULL,"
                    + "version INT NOT NULL,"
                    + "applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";

        try (Connection connection = entry.getDataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Failed to create core tables on '" + entry.getKey()
                    + "': " + throwable.getMessage());
        }
    }

    private void createCoreTables() {
        DatabaseEntry primary = getDatabase("database");
        if (primary != null) {
            createCoreTables(primary);
        }
    }

    public Connection getConnection() throws SQLException {
        return getConnection("database");
    }

    public Connection getConnection(String key) throws SQLException {
        DatabaseEntry entry = getDatabase(key);
        if (entry == null || !entry.isConnected()) {
            throw new SQLException("Database '" + key + "' is not connected");
        }
        return entry.getDataSource().getConnection();
    }

    public boolean isConnected() {
        DatabaseEntry primary = getDatabase("database");
        return primary != null && primary.isConnected();
    }

    public boolean isConnected(String key) {
        DatabaseEntry entry = getDatabase(key);
        return entry != null && entry.isConnected();
    }

    public DatabaseConfig getConfig() {
        return config;
    }

    public Map<String, DatabaseEntry> getAllDatabases() {
        synchronized (databases) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(databases));
        }
    }

    public DatabaseEntry getDatabase(String key) {
        synchronized (databases) {
            return databases.get(key);
        }
    }

    public int getConnectedCount() {
        int count = 0;
        for (DatabaseEntry entry : getEntries()) {
            if (entry.isConnected()) {
                count++;
            }
        }
        return count;
    }

    public int getTotalCount() {
        synchronized (databases) {
            return databases.size();
        }
    }

    public void shutdown() {
        for (DatabaseEntry entry : getEntries()) {
            HikariDataSource source = entry.getDataSource();
            if (source != null && !source.isClosed()) {
                try {
                    source.close();
                } catch (Throwable ignored) {
                }
            }
            entry.connected = false;
            entry.dataSource = null;
        }
        synchronized (databases) {
            databases.clear();
        }
        this.dataSource = null;
        this.connected = false;
    }

    public String getTablePrefix() {
        return config == null ? "sm_" : config.getTablePrefix();
    }

    public String table(String name) {
        return getTablePrefix() + name;
    }

    public DatabaseType getDatabaseType() {
        return config == null ? DatabaseType.H2 : config.getType();
    }

    public int executeUpdate(String sql) {
        return executeUpdate(sql, new Object[0]);
    }

    public int executeUpdate(String sql, Object... params) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            applyParams(statement, params);
            return statement.executeUpdate();
        } catch (Throwable throwable) {
            plugin.getDebugSystem().logError("Database", "Update failed: " + sql, throwable);
            return -1;
        }
    }

    public <T> T executeQuery(String sql, Function<ResultSet, T> handler, Object... params) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            applyParams(statement, params);
            try (ResultSet resultSet = statement.executeQuery()) {
                return handler.apply(resultSet);
            }
        } catch (Throwable throwable) {
            plugin.getDebugSystem().logError("Database", "Query failed: " + sql, throwable);
            return null;
        }
    }

    public CompletableFuture<Integer> executeUpdateAsync(String sql, Object... params) {
        return CompletableFuture.supplyAsync(() -> executeUpdate(sql, params));
    }

    public <T> CompletableFuture<T> executeQueryAsync(String sql, Function<ResultSet, T> handler, Object... params) {
        return CompletableFuture.supplyAsync(() -> executeQuery(sql, handler, params));
    }

    public CompletableFuture<Void> executeAsync(Consumer<Connection> action) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = getConnection()) {
                action.accept(connection);
            } catch (Throwable throwable) {
                plugin.getDebugSystem().logError("Database", "Async connection action failed", throwable);
            }
        });
    }

    public int[] executeBatch(String sql, Consumer<PreparedStatement> filler) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            filler.accept(statement);
            return statement.executeBatch();
        } catch (Throwable throwable) {
            plugin.getDebugSystem().logError("Database", "Batch failed: " + sql, throwable);
            return new int[0];
        }
    }

    public boolean isMigrationApplied(String module, int version) {
        Boolean applied = executeQuery(
                "SELECT 1 FROM " + table("migrations") + " WHERE module = ? AND version = ?",
                resultSet -> {
                    try {
                        return resultSet.next();
                    } catch (SQLException e) {
                        return false;
                    }
                },
                module, version);
        return Boolean.TRUE.equals(applied);
    }

    public void registerMigration(String module, int version) {
        if (isMigrationApplied(module, version)) {
            return;
        }
        executeUpdate("INSERT INTO " + table("migrations") + " (module, version) VALUES (?, ?)",
                module, version);
    }

    public int getCurrentMigrationVersion(String module) {
        Integer version = executeQuery(
                "SELECT MAX(version) FROM " + table("migrations") + " WHERE module = ?",
                resultSet -> {
                    try {
                        return resultSet.next() ? resultSet.getInt(1) : 0;
                    } catch (SQLException e) {
                        return 0;
                    }
                },
                module);
        return version == null ? 0 : version;
    }

    private void applyParams(PreparedStatement statement, Object[] params) throws SQLException {
        if (params == null) {
            return;
        }
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
    }

    private List<DatabaseEntry> getEntries() {
        synchronized (databases) {
            return new ArrayList<>(databases.values());
        }
    }
}
