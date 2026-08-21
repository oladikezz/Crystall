package net.schalker.DoAPI.core.database;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public class DatabaseConfig {

    private DatabaseType type = DatabaseType.H2;
    private String host = "localhost";
    private int port = 3306;
    private String database = "DoAPI";
    private String username = "root";
    private String password = "";
    private int poolSize = 10;
    private int connectionTimeout = 30000;
    private int idleTimeout = 600000;
    private int maxLifetime = 1800000;
    private String tablePrefix = "sm_";
    private String filePath = "database";

    public static DatabaseConfig fromConfig(FileConfiguration config, String key) {
        ConfigurationSection section = config.getConfigurationSection(key);
        if (section == null) {
            return null;
        }

        DatabaseConfig result = new DatabaseConfig();
        result.type = DatabaseType.fromString(section.getString("type", "h2"));
        result.host = section.getString("host", "localhost");
        result.port = section.getInt("port", 3306);
        result.database = section.getString("database", "DoAPI");
        result.username = section.getString("username", "root");
        result.password = section.getString("password", "");
        result.poolSize = section.getInt("pool.size", 10);
        result.connectionTimeout = section.getInt("pool.connection-timeout", 30000);
        result.idleTimeout = section.getInt("pool.idle-timeout", 600000);
        result.maxLifetime = section.getInt("pool.max-lifetime", 1800000);
        result.tablePrefix = section.getString("table-prefix", "sm_");
        result.filePath = section.getString("file", key.equals("database") ? "database" : key);
        return result;
    }

    public static DatabaseConfig createH2Fallback() {
        DatabaseConfig result = new DatabaseConfig();
        result.type = DatabaseType.H2;
        result.filePath = "database";
        result.tablePrefix = "sm_";
        result.poolSize = 5;
        return result;
    }

    public String getJdbcUrl(String dataFolderPath) {
        return switch (type) {
            case MYSQL -> "jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8"
                    + "&serverTimezone=UTC&autoReconnect=true";
            case MARIADB -> "jdbc:mariadb://" + host + ":" + port + "/" + database
                    + "?useSSL=false&characterEncoding=utf8&autoReconnect=true";
            case SQLITE -> "jdbc:sqlite:" + dataFolderPath + "/" + filePath + ".db";
            case H2 -> "jdbc:h2:file:" + dataFolderPath + "/" + filePath
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;AUTO_SERVER=TRUE";
        };
    }

    public DatabaseType getType() {
        return type;
    }

    public void setType(DatabaseType type) {
        this.type = type;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public int getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(int idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public int getMaxLifetime() {
        return maxLifetime;
    }

    public void setMaxLifetime(int maxLifetime) {
        this.maxLifetime = maxLifetime;
    }

    public String getTablePrefix() {
        return tablePrefix;
    }

    public void setTablePrefix(String tablePrefix) {
        this.tablePrefix = tablePrefix;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
}
