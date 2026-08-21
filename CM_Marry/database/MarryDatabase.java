package net.schalker.SMPS.modules.marry.database;

import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.database.DatabaseType;
import net.schalker.DoAPI.core.database.ModuleDatabase;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Database handler for SM_Marry module.
 * Table: sm_marriages in the main SMPS database (s3_smps).
 */
public class MarryDatabase extends ModuleDatabase {

    /**
     * The canonical table name used in the main SMPS database.
     * Uses the global prefix (sm_) + "marriages" → sm_marriages.
     */
    private static final String TABLE_NAME = "sm_marriages";

    /**
     * Legacy table name used before migration (sm_marry_marriages).
     * Kept for migration purposes.
     */
    private static final String LEGACY_TABLE_NAME = "sm_marry_marriages";

    public MarryDatabase(DoAPI plugin) {
        super(plugin, "marry");
    }

    /**
     * Get the table name for marriages.
     */
    public String getTableName() {
        return TABLE_NAME;
    }

    @Override
    public void createTables() {
        DatabaseType type = plugin.getDatabaseManager().getDatabaseType();
        
        String sql;
        if (type == DatabaseType.SQLITE) {
            sql = """
                CREATE TABLE IF NOT EXISTS %s (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player1_uuid VARCHAR(36) NOT NULL,
                    player1_name VARCHAR(16) NOT NULL,
                    player2_uuid VARCHAR(36) NOT NULL,
                    player2_name VARCHAR(16) NOT NULL,
                    married_date TIMESTAMP NOT NULL,
                    UNIQUE(player1_uuid, player2_uuid)
                )
                """.formatted(TABLE_NAME);
        } else if (type == DatabaseType.H2) {
            sql = """
                CREATE TABLE IF NOT EXISTS %s (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    player1_uuid VARCHAR(36) NOT NULL,
                    player1_name VARCHAR(16) NOT NULL,
                    player2_uuid VARCHAR(36) NOT NULL,
                    player2_name VARCHAR(16) NOT NULL,
                    married_date TIMESTAMP NOT NULL,
                    UNIQUE(player1_uuid, player2_uuid)
                )
                """.formatted(TABLE_NAME);
        } else {
            // MySQL / MariaDB
            sql = """
                CREATE TABLE IF NOT EXISTS %s (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    player1_uuid VARCHAR(36) NOT NULL,
                    player1_name VARCHAR(16) NOT NULL,
                    player2_uuid VARCHAR(36) NOT NULL,
                    player2_name VARCHAR(16) NOT NULL,
                    married_date TIMESTAMP NOT NULL,
                    UNIQUE KEY unique_marriage (player1_uuid, player2_uuid)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """.formatted(TABLE_NAME);
        }

        try (Connection conn = getConnection();
             var stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            plugin.getDebugSystem().log("SM_Marry", "Created/verified " + TABLE_NAME + " table");
        } catch (Exception e) {
            plugin.getDebugSystem().logError("SM_Marry", "Failed to create " + TABLE_NAME + " table", e);
        }
    }

    /**
     * Create a new marriage between two players.
     */
    public boolean createMarriage(UUID player1, String name1, UUID player2, String name2) {
        String sql = "INSERT INTO " + TABLE_NAME + 
                    " (player1_uuid, player1_name, player2_uuid, player2_name, married_date) VALUES (?, ?, ?, ?, ?)";
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, player1.toString());
            ps.setString(2, name1);
            ps.setString(3, player2.toString());
            ps.setString(4, name2);
            ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            plugin.getDebugSystem().logError("SM_Marry", "Failed to create marriage", e);
            return false;
        }
    }

    /**
     * Delete a marriage between two players (regardless of order).
     */
    public boolean deleteMarriage(UUID player1, UUID player2) {
        String sql = "DELETE FROM " + TABLE_NAME + 
                    " WHERE (player1_uuid = ? AND player2_uuid = ?) OR (player1_uuid = ? AND player2_uuid = ?)";
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, player1.toString());
            ps.setString(2, player2.toString());
            ps.setString(3, player2.toString());
            ps.setString(4, player1.toString());
            int rows = ps.executeUpdate();
            return rows > 0;
        } catch (Exception e) {
            plugin.getDebugSystem().logError("SM_Marry", "Failed to delete marriage", e);
            return false;
        }
    }

    /**
     * Check if a player is married.
     */
    public boolean isMarried(UUID player) {
        String sql = "SELECT COUNT(*) FROM " + TABLE_NAME + " WHERE player1_uuid = ? OR player2_uuid = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (Exception e) {
            plugin.getDebugSystem().logError("SM_Marry", "Failed to check marriage status", e);
        }
        return false;
    }

    /**
     * Check if two players are married to each other.
     */
    public boolean areMarried(UUID player1, UUID player2) {
        String sql = "SELECT COUNT(*) FROM " + TABLE_NAME + 
                    " WHERE (player1_uuid = ? AND player2_uuid = ?) OR (player1_uuid = ? AND player2_uuid = ?)";
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, player1.toString());
            ps.setString(2, player2.toString());
            ps.setString(3, player2.toString());
            ps.setString(4, player1.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (Exception e) {
            plugin.getDebugSystem().logError("SM_Marry", "Failed to check if players are married", e);
        }
        return false;
    }

    /**
     * Get partner of a player (returns null if not married).
     */
    public MarriageInfo getPartner(UUID player) {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE player1_uuid = ? OR player2_uuid = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    UUID player1 = UUID.fromString(rs.getString("player1_uuid"));
                    String name1 = rs.getString("player1_name");
                    UUID player2 = UUID.fromString(rs.getString("player2_uuid"));
                    String name2 = rs.getString("player2_name");
                    Timestamp date = rs.getTimestamp("married_date");
                    
                    // Return the partner (not the player themselves)
                    if (player1.equals(player)) {
                        return new MarriageInfo(player2, name2, date);
                    } else {
                        return new MarriageInfo(player1, name1, date);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getDebugSystem().logError("SM_Marry", "Failed to get partner", e);
        }
        return null;
    }

    /**
     * Get count of marriages for a player.
     */
    public int getMarriageCount(UUID player) {
        String sql = "SELECT COUNT(*) FROM " + TABLE_NAME + " WHERE player1_uuid = ? OR player2_uuid = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (Exception e) {
            plugin.getDebugSystem().logError("SM_Marry", "Failed to get marriage count", e);
        }
        return 0;
    }

    /**
     * Get all marriages on the server.
     */
    public List<FullMarriageInfo> getAllMarriages() {
        List<FullMarriageInfo> marriages = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE_NAME + " ORDER BY married_date DESC";
        
        try (Connection conn = getConnection();
             var stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                UUID player1 = UUID.fromString(rs.getString("player1_uuid"));
                String name1 = rs.getString("player1_name");
                UUID player2 = UUID.fromString(rs.getString("player2_uuid"));
                String name2 = rs.getString("player2_name");
                Timestamp date = rs.getTimestamp("married_date");
                
                marriages.add(new FullMarriageInfo(player1, name1, player2, name2, date));
            }
        } catch (Exception e) {
            plugin.getDebugSystem().logError("SM_Marry", "Failed to get all marriages", e);
        }
        return marriages;
    }

    /**
     * Info about a player's partner.
     */
    public static class MarriageInfo {
        private final UUID partnerUuid;
        private final String partnerName;
        private final Timestamp marriedDate;

        public MarriageInfo(UUID partnerUuid, String partnerName, Timestamp marriedDate) {
            this.partnerUuid = partnerUuid;
            this.partnerName = partnerName;
            this.marriedDate = marriedDate;
        }

        public UUID getPartnerUuid() { return partnerUuid; }
        public String getPartnerName() { return partnerName; }
        public Timestamp getMarriedDate() { return marriedDate; }
    }

    /**
     * Full info about a marriage (both players).
     */
    public static class FullMarriageInfo {
        private final UUID player1Uuid;
        private final String player1Name;
        private final UUID player2Uuid;
        private final String player2Name;
        private final Timestamp marriedDate;

        public FullMarriageInfo(UUID player1Uuid, String player1Name, UUID player2Uuid, String player2Name, Timestamp marriedDate) {
            this.player1Uuid = player1Uuid;
            this.player1Name = player1Name;
            this.player2Uuid = player2Uuid;
            this.player2Name = player2Name;
            this.marriedDate = marriedDate;
        }

        public UUID getPlayer1Uuid() { return player1Uuid; }
        public String getPlayer1Name() { return player1Name; }
        public UUID getPlayer2Uuid() { return player2Uuid; }
        public String getPlayer2Name() { return player2Name; }
        public Timestamp getMarriedDate() { return marriedDate; }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Migration: legacy table / local file → sm_marriages
    // ══════════════════════════════════════════════════════════════════

    /**
     * Result of a migration operation.
     */
    public static class MigrationResult {
        private final boolean success;
        private final int migratedCount;
        private final int skippedCount;
        private final String message;

        public MigrationResult(boolean success, int migratedCount, int skippedCount, String message) {
            this.success = success;
            this.migratedCount = migratedCount;
            this.skippedCount = skippedCount;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public int getMigratedCount() { return migratedCount; }
        public int getSkippedCount() { return skippedCount; }
        public String getMessage() { return message; }
    }

    /**
     * Migrate marriages into {@link #TABLE_NAME} ({@code sm_marriages}).
     * <p>
     * Strategy (in order):
     * <ol>
     *   <li><b>Same database</b> — look for the legacy table {@code sm_marry_marriages}
     *       in the <em>current</em> primary SMPS database (MariaDB / MySQL / H2 / SQLite).
     *       This is the most common case because all modules share the same connection pool.</li>
     *   <li><b>Local file fallback</b> — if the legacy table is not in the primary database,
     *       try to open the local file ({@code plugins/SMPS/data/database.db} for SQLite or
     *       {@code database.mv.db} for H2) and read from there.</li>
     * </ol>
     *
     * @return MigrationResult with details about the migration outcome.
     */
    public MigrationResult migrateFromLocalFile() {
        // ── Step 1: Try the CURRENT primary database ──────────────────
        plugin.getDebugSystem().log("SM_Marry", "Migration: checking current primary DB for legacy table...");

        try {
            MigrationResult primaryResult = migrateFromSameDatabase();
            if (primaryResult != null) {
                return primaryResult; // Found legacy table (or data) in the primary DB
            }
        } catch (Exception e) {
            plugin.getDebugSystem().logError("SM_Marry", "Error checking primary DB for legacy table", e);
        }

        // ── Step 2: Try local file-based database ─────────────────────
        plugin.getDebugSystem().log("SM_Marry",
            "Migration: legacy table not in primary DB, trying local file...");

        return migrateFromFileDatabase();
    }

    /**
     * Attempt migration within the current primary database.
     * Looks for the legacy table ({@code sm_marry_marriages}) and copies
     * rows to {@code sm_marriages}.
     *
     * @return a MigrationResult if the legacy table exists (even if empty),
     *         or {@code null} if the legacy table was not found.
     */
    private MigrationResult migrateFromSameDatabase() {
        try (Connection conn = getConnection()) {
            // Check for legacy table in both cases (MariaDB is case-insensitive, H2 may not be)
            String foundLegacy = null;
            for (String candidate : List.of(LEGACY_TABLE_NAME, LEGACY_TABLE_NAME.toUpperCase())) {
                if (tableExists(conn, candidate)) {
                    foundLegacy = candidate;
                    break;
                }
            }

            if (foundLegacy == null) {
                return null; // legacy table not found → caller should try local file
            }

            plugin.getDebugSystem().log("SM_Marry",
                "Migration: found legacy table '" + foundLegacy + "' in primary DB");

            // Read all rows from the legacy table
            List<FullMarriageInfo> rows = readMarriagesFromConnection(conn, foundLegacy);

            if (rows.isEmpty()) {
                return new MigrationResult(true, 0, 0,
                    "Legacy table " + foundLegacy + " exists in primary DB but is empty.");
            }

            // Insert into the new table, skipping duplicates
            int[] counts = insertMarriages(conn, rows);
            int migrated = counts[0];
            int skipped = counts[1];

            plugin.getDebugSystem().log("SM_Marry",
                "Primary DB migration: " + migrated + " migrated, " + skipped + " skipped");

            return new MigrationResult(true, migrated, skipped,
                "Migrated from legacy table " + foundLegacy + " (same database)");

        } catch (Exception e) {
            plugin.getDebugSystem().logError("SM_Marry", "Failed during same-database migration", e);
            return new MigrationResult(false, 0, 0,
                "Failed to migrate from primary database: " + e.getMessage());
        }
    }

    /**
     * Attempt migration from a local file-based database (SQLite / H2).
     */
    private MigrationResult migrateFromFileDatabase() {
        File dataFolder = new File(plugin.getDataFolder(), "data");

        File sqliteFile = new File(dataFolder, "database.db");
        File h2File = new File(dataFolder, "database.mv.db");

        // Build a list of JDBC URLs to try
        List<String> jdbcUrls = new ArrayList<>();
        String fileUsed = null;

        if (sqliteFile.exists()) {
            String path = sqliteFile.getAbsolutePath().replace('\\', '/');
            jdbcUrls.add("jdbc:sqlite:" + path);
            fileUsed = sqliteFile.getName();
            plugin.getDebugSystem().log("SM_Marry", "Found SQLite file: " + path);

        } else if (h2File.exists()) {
            String h2Path = new File(dataFolder, "database").getAbsolutePath().replace('\\', '/');
            fileUsed = h2File.getName();
            plugin.getDebugSystem().log("SM_Marry", "Found H2 file: " + h2File.getAbsolutePath());

            // H2 is very sensitive to version / settings mismatches.
            // Try many variants; the INIT trick creates the "public" schema
            // if it's missing (common H2 2.x version-mismatch problem).
            String schemaInit = "INIT=CREATE SCHEMA IF NOT EXISTS \"public\"\\;" +
                                "SET SCHEMA \"public\"";
            jdbcUrls.addAll(List.of(
                // Variant 1: most compatible — create missing schema + read-only-ish
                "jdbc:h2:file:" + h2Path + ";IFEXISTS=TRUE;" + schemaInit,
                // Variant 2: with AUTO_SERVER so we don't conflict with SMPS lock
                "jdbc:h2:file:" + h2Path + ";IFEXISTS=TRUE;AUTO_SERVER=TRUE;" + schemaInit,
                // Variant 3: plain — works if H2 versions match
                "jdbc:h2:file:" + h2Path + ";IFEXISTS=TRUE",
                // Variant 4: plain + auto server
                "jdbc:h2:file:" + h2Path + ";IFEXISTS=TRUE;AUTO_SERVER=TRUE",
                // Variant 5: MySQL compat mode
                "jdbc:h2:file:" + h2Path + ";IFEXISTS=TRUE;MODE=MySQL;" + schemaInit,
                // Variant 6: old-style DATABASE_TO_UPPER
                "jdbc:h2:file:" + h2Path + ";IFEXISTS=TRUE;DATABASE_TO_UPPER=FALSE",
                // Variant 7: DATABASE_TO_UPPER + auto server
                "jdbc:h2:file:" + h2Path + ";IFEXISTS=TRUE;DATABASE_TO_UPPER=FALSE;AUTO_SERVER=TRUE"
            ));
        }

        if (jdbcUrls.isEmpty()) {
            return new MigrationResult(false, 0, 0,
                "No migration source found. Legacy table " + LEGACY_TABLE_NAME +
                " not in primary DB, and no local database file in plugins/SMPS/data/.");
        }

        // Try each URL until one connects and contains marriage data
        List<FullMarriageInfo> localMarriages = new ArrayList<>();
        String sourceTable = null;
        Exception lastError = null;

        for (String jdbcUrl : jdbcUrls) {
            localMarriages.clear();
            sourceTable = null;

            try (Connection localConn = DriverManager.getConnection(jdbcUrl)) {
                plugin.getDebugSystem().log("SM_Marry", "Connected: " + jdbcUrl);

                // Look for a marriage table
                for (String candidate : List.of(
                        LEGACY_TABLE_NAME, TABLE_NAME,
                        LEGACY_TABLE_NAME.toUpperCase(), TABLE_NAME.toUpperCase())) {
                    if (tableExists(localConn, candidate)) {
                        sourceTable = candidate;
                        break;
                    }
                }

                if (sourceTable == null) {
                    // Connected OK but no marriage table — stop trying
                    lastError = null;
                    break;
                }

                localMarriages = readMarriagesFromConnection(localConn, sourceTable);
                plugin.getDebugSystem().log("SM_Marry",
                    "Read " + localMarriages.size() + " rows from " + sourceTable);
                break; // success

            } catch (Exception e) {
                lastError = e;
                plugin.getDebugSystem().log("SM_Marry",
                    "Variant failed: " + e.getMessage());
            }
        }

        // Evaluate results
        if (lastError != null && sourceTable == null && localMarriages.isEmpty()) {
            plugin.getDebugSystem().logError("SM_Marry",
                "All H2/SQLite connection variants failed", lastError);
            return new MigrationResult(false, 0, 0,
                "Could not open local database file (" + fileUsed + "): " + lastError.getMessage());
        }

        if (sourceTable == null) {
            return new MigrationResult(false, 0, 0,
                "No marriage table found in " + fileUsed +
                ". Tried: " + LEGACY_TABLE_NAME + ", " + TABLE_NAME);
        }

        if (localMarriages.isEmpty()) {
            return new MigrationResult(true, 0, 0,
                "Table " + sourceTable + " in " + fileUsed + " is empty — nothing to migrate.");
        }

        // Insert into the current primary database
        try (Connection conn = getConnection()) {
            int[] counts = insertMarriages(conn, localMarriages);
            plugin.getDebugSystem().log("SM_Marry",
                "File migration: " + counts[0] + " migrated, " + counts[1] + " skipped");
            return new MigrationResult(true, counts[0], counts[1],
                "Migrated from " + fileUsed + " (table: " + sourceTable + ")");
        } catch (Exception e) {
            plugin.getDebugSystem().logError("SM_Marry",
                "Failed to write migrated data to primary database", e);
            return new MigrationResult(false, 0, 0,
                "Read OK from " + fileUsed + " but failed to write to primary DB: " + e.getMessage());
        }
    }

    // ── Shared helpers for migration ──────────────────────────────────

    /**
     * Read all marriage rows from a table in the given connection.
     * Uses column-index detection to work regardless of H2 uppercase / lowercase column names.
     */
    private List<FullMarriageInfo> readMarriagesFromConnection(Connection conn, String table) throws Exception {
        List<FullMarriageInfo> result = new ArrayList<>();

        String sql = "SELECT * FROM " + table;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            // Detect columns by uppercase name (case-insensitive)
            var meta = rs.getMetaData();
            int colCount = meta.getColumnCount();
            int cP1Uuid = -1, cP1Name = -1, cP2Uuid = -1, cP2Name = -1, cDate = -1;
            for (int i = 1; i <= colCount; i++) {
                switch (meta.getColumnName(i).toUpperCase()) {
                    case "PLAYER1_UUID" -> cP1Uuid = i;
                    case "PLAYER1_NAME" -> cP1Name = i;
                    case "PLAYER2_UUID" -> cP2Uuid = i;
                    case "PLAYER2_NAME" -> cP2Name = i;
                    case "MARRIED_DATE" -> cDate = i;
                }
            }
            if (cP1Uuid < 0 || cP1Name < 0 || cP2Uuid < 0 || cP2Name < 0 || cDate < 0) {
                plugin.getDebugSystem().logWarning("SM_Marry",
                    "Table " + table + " has unexpected columns — cannot migrate");
                return result;
            }

            while (rs.next()) {
                UUID p1 = UUID.fromString(rs.getString(cP1Uuid));
                String n1 = rs.getString(cP1Name);
                UUID p2 = UUID.fromString(rs.getString(cP2Uuid));
                String n2 = rs.getString(cP2Name);
                Timestamp date = rs.getTimestamp(cDate);
                result.add(new FullMarriageInfo(p1, n1, p2, n2, date));
            }
        }
        return result;
    }

    /**
     * Insert marriages into {@link #TABLE_NAME}, skipping duplicates.
     *
     * @return int[]{migrated, skipped}
     */
    private int[] insertMarriages(Connection conn, List<FullMarriageInfo> rows) throws Exception {
        int migrated = 0;
        int skipped = 0;

        String insertSql = "INSERT INTO " + TABLE_NAME +
            " (player1_uuid, player1_name, player2_uuid, player2_name, married_date) VALUES (?, ?, ?, ?, ?)";

        boolean wasAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);

        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            for (FullMarriageInfo m : rows) {
                if (marriageExistsInConnection(conn, m.getPlayer1Uuid(), m.getPlayer2Uuid())) {
                    skipped++;
                    continue;
                }
                ps.setString(1, m.getPlayer1Uuid().toString());
                ps.setString(2, m.getPlayer1Name());
                ps.setString(3, m.getPlayer2Uuid().toString());
                ps.setString(4, m.getPlayer2Name());
                ps.setTimestamp(5, m.getMarriedDate());
                ps.addBatch();
                migrated++;
            }
            if (migrated > 0) {
                ps.executeBatch();
            }
            conn.commit();
        } catch (Exception e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(wasAutoCommit);
        }

        return new int[]{migrated, skipped};
    }

    // ── Table / duplicate helpers ─────────────────────────────────────

    /**
     * Check whether a table exists in the given connection.
     */
    private boolean tableExists(Connection conn, String tableName) {
        // Try exact name
        try (ResultSet rs = conn.getMetaData().getTables(null, null, tableName, null)) {
            if (rs.next()) return true;
        } catch (Exception ignored) {}

        // Also try uppercase (H2 with DATABASE_TO_UPPER=TRUE)
        try (ResultSet rs = conn.getMetaData().getTables(null, null, tableName.toUpperCase(), null)) {
            if (rs.next()) return true;
        } catch (Exception ignored) {}

        // Last resort: try a dummy SELECT (works when metadata lookup is broken)
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT 1 FROM " + tableName + " WHERE 1=0");
            return true;
        } catch (Exception ignored) {}

        return false;
    }

    /**
     * Check if a marriage already exists in the target database (either direction).
     */
    private boolean marriageExistsInConnection(Connection conn, UUID player1, UUID player2) {
        String sql = "SELECT COUNT(*) FROM " + TABLE_NAME +
            " WHERE (player1_uuid = ? AND player2_uuid = ?) OR (player1_uuid = ? AND player2_uuid = ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, player1.toString());
            ps.setString(2, player2.toString());
            ps.setString(3, player2.toString());
            ps.setString(4, player1.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (Exception e) {
            plugin.getDebugSystem().logError("SM_Marry",
                "Failed to check duplicate marriage during migration", e);
            return false;
        }
    }
}
