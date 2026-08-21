package net.schalker.DoAPI.core.database;

import net.schalker.DoAPI.DoAPI;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

public abstract class ModuleDatabase {

    protected final DoAPI plugin;
    protected final String moduleName;

    public ModuleDatabase(DoAPI plugin, String moduleName) {
        this.plugin = plugin;
        this.moduleName = moduleName == null ? "unknown" : moduleName.toLowerCase(Locale.ROOT);
    }

    public void initialize() {
        if (!isConnected()) {
            plugin.getDebugSystem().logWarning(moduleName,
                    "Database is not connected, skipping table creation");
            return;
        }

        try {
            createTables();
        } catch (Throwable throwable) {
            plugin.getDebugSystem().logError(moduleName, "Failed to create tables", throwable);
        }
    }

    public abstract void createTables();

    public void shutdown() {
    }

    public boolean isConnected() {
        return plugin.getDatabaseManager() != null && plugin.getDatabaseManager().isConnected();
    }

    protected String table(String name) {
        return plugin.getDatabaseManager().getTablePrefix() + moduleName + "_" + name;
    }

    public Connection getConnection() throws SQLException {
        return plugin.getDatabaseManager().getConnection();
    }

    protected boolean isSqliteOrH2() {
        DatabaseType type = plugin.getDatabaseManager().getDatabaseType();
        return type == DatabaseType.SQLITE || type == DatabaseType.H2;
    }

    protected boolean isMysqlOrMariadb() {
        DatabaseType type = plugin.getDatabaseManager().getDatabaseType();
        return type == DatabaseType.MYSQL || type == DatabaseType.MARIADB;
    }
}
