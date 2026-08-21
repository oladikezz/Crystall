package net.schalker.DoAPI.core.database;

import java.util.Locale;

public enum DatabaseType {

    MYSQL("MySQL", "com.mysql.cj.jdbc.Driver", "jdbc:mysql://"),
    MARIADB("MariaDB", "org.mariadb.jdbc.Driver", "jdbc:mariadb://"),
    SQLITE("SQLite", "org.sqlite.JDBC", "jdbc:sqlite:"),
    H2("H2", "org.h2.Driver", "jdbc:h2:");

    private final String displayName;
    private final String driverClass;
    private final String jdbcPrefix;

    DatabaseType(String displayName, String driverClass, String jdbcPrefix) {
        this.displayName = displayName;
        this.driverClass = driverClass;
        this.jdbcPrefix = jdbcPrefix;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDriverClass() {
        return driverClass;
    }

    public String getJdbcPrefix() {
        return jdbcPrefix;
    }

    public boolean isRemote() {
        return this == MYSQL || this == MARIADB;
    }

    public boolean isFile() {
        return this == SQLITE || this == H2;
    }

    public String resolveAvailableDriver() {
        if (isDriverPresent(driverClass)) {
            return driverClass;
        }
        if (this == MYSQL) {
            if (isDriverPresent("com.mysql.jdbc.Driver")) {
                return "com.mysql.jdbc.Driver";
            }
            if (isDriverPresent(MARIADB.driverClass)) {
                return MARIADB.driverClass;
            }
        }
        if (this == MARIADB && isDriverPresent(MYSQL.driverClass)) {
            return MYSQL.driverClass;
        }
        return null;
    }

    private static boolean isDriverPresent(String className) {
        try {
            Class.forName(className, false, DatabaseType.class.getClassLoader());
            return true;
        } catch (Throwable throwable) {
            return false;
        }
    }

    public static DatabaseType fromString(String value) {
        if (value == null || value.isBlank()) {
            return H2;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (DatabaseType type : values()) {
            if (type.name().equals(normalized) || type.displayName.equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        return H2;
    }
}
