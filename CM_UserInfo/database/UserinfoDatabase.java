package net.schalker.SMPS.modules.userinfo.database;

import net.schalker.DoAPI.DoAPI;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dynamically queries all tables in s3_smps to find player info
 * by UUID, nickname, or Discord ID.
 */
public class UserinfoDatabase {
    private final DoAPI plugin;
    private static final int MAX_SEARCH_ROUNDS = 4;
    private static final int MAX_QUERY_VARIANTS = 256;
    private static final int MAX_COLUMNS_PER_QUERY = 48;
    private static final Pattern DISCORD_ID_PATTERN = Pattern.compile("\\d{5,22}");

    /** Column name patterns that may hold UUID */
    private static final String[] UUID_COLUMNS = {
        "uuid", "player_uuid", "playeruuid", "user_uuid", "useruuid", "owner_uuid",
        "member_uuid", "minecraft_uuid", "unique_id", "mc_uuid"
    };

    /** Column name patterns that may hold player name */
    private static final String[] NAME_COLUMNS = {
        "name", "player_name", "playername", "username", "user_name",
        "nickname", "nick", "player", "last_name", "lastname",
        "mc_name", "minecraft_name", "display_name"
    };

    /** Column name patterns that may hold Discord ID */
    private static final String[] DISCORD_COLUMNS = {
        "discord_id", "discordid", "discord", "discord_uid", "discorduid"
    };

    private final Map<String, List<ColumnInfo>> tableColumnsCache = new HashMap<>();

    public UserinfoDatabase(DoAPI plugin) {
        this.plugin = plugin;
    }

    // ─── public API ───────────────────────────────────────────────

    /**
     * Returns a list of all user-facing table names in the current database.
     */
    public List<String> getAllTables() {
        List<String> tables = new ArrayList<>();
        try (Connection conn = getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getTables(conn.getCatalog(), null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"));
                }
            }
        } catch (SQLException e) {
            plugin.getDebugSystem().logError("UserinfoDatabase: failed to list tables", e);
        }
        return tables;
    }

    /**
     * Returns column names for a given table.
     */
    public List<String> getColumns(String table) {
        List<ColumnInfo> infos = getColumnInfos(table);
        if (infos.isEmpty()) {
            return List.of();
        }

        List<String> columns = new ArrayList<>();
        for (ColumnInfo info : infos) {
            columns.add(info.name());
        }
        return columns;
    }

    /**
     * Searches a single table for rows matching the query.
     */
    public List<Map<String, String>> searchTable(String table, String query) {
        return searchTable(table, query, true);
    }

    /**
     * Searches a single table for rows matching the query, limited to maxRows results.
     */
    public List<Map<String, String>> searchInTable(String table, String query, int maxRows) {
        List<Map<String, String>> all = searchTable(table, query);
        if (all.size() <= maxRows) {
            return all;
        }
        return new ArrayList<>(all.subList(0, maxRows));
    }

    /**
     * Checks if a table exists in the database.
     */
    public boolean tableExists(String table) {
        if (table == null || table.isBlank()) {
            return false;
        }
        return getAllTables().stream()
            .anyMatch(t -> t.equalsIgnoreCase(table));
    }

    /**
     * Queries ALL tables for any rows matching the given query.
     * Key = table name, Value = list of row maps.
     */
    public Map<String, List<Map<String, String>>> searchAllTables(String query) {
        return searchAllTables(query, Integer.MAX_VALUE);
    }

    /**
     * Queries ALL tables for any rows matching the given query, limited to maxRows total.
     * Key = table name, Value = list of row maps.
     */
    public Map<String, List<Map<String, String>>> searchAllTables(String query, int maxRows) {
        Map<String, List<Map<String, String>>> allResults = new LinkedHashMap<>();
        if (query == null || query.isBlank()) {
            return allResults;
        }

        List<String> tables = getAllTables();
        if (tables.isEmpty()) {
            return allResults;
        }

        Map<String, Set<String>> rowSignatures = new HashMap<>();
        LinkedHashSet<String> processedQueries = new LinkedHashSet<>();
        LinkedHashSet<String> pendingQueries = new LinkedHashSet<>(buildQueryVariants(query));

        int round = 0;
        while (!pendingQueries.isEmpty() && round < MAX_SEARCH_ROUNDS && processedQueries.size() < MAX_QUERY_VARIANTS) {
            List<String> currentRoundQueries = new ArrayList<>(pendingQueries);
            pendingQueries.clear();

            for (String currentQuery : currentRoundQueries) {
                if (currentQuery == null || currentQuery.isBlank()) {
                    continue;
                }
                if (!processedQueries.add(currentQuery)) {
                    continue;
                }

                boolean allowFuzzy = !looksLikeUuid(currentQuery) && !looksLikeDiscordId(currentQuery);
                boolean allowGenericFallback = round == 0;

                for (String table : tables) {
                    List<Map<String, String>> rows = searchTable(table, currentQuery, allowFuzzy, allowGenericFallback);
                    if (rows.isEmpty()) {
                        continue;
                    }

                    List<Map<String, String>> collectedRows = allResults.computeIfAbsent(table, ignored -> new ArrayList<>());
                    Set<String> tableRowSignatures = rowSignatures.computeIfAbsent(table, ignored -> new HashSet<>());

                    for (Map<String, String> row : rows) {
                        String signature = buildRowSignature(row);
                        if (!tableRowSignatures.add(signature)) {
                            continue;
                        }

                        collectedRows.add(row);
                        collectIdentityVariantsFromRow(row, pendingQueries, processedQueries);
                        if (processedQueries.size() + pendingQueries.size() >= MAX_QUERY_VARIANTS) {
                            break;
                        }
                    }

                    if (processedQueries.size() + pendingQueries.size() >= MAX_QUERY_VARIANTS) {
                        break;
                    }
                }

                if (processedQueries.size() + pendingQueries.size() >= MAX_QUERY_VARIANTS) {
                    break;
                }
            }

            round++;
        }

        return allResults;
    }

    // ─── low-level search ─────────────────────────────────────────

    private List<Map<String, String>> searchTable(String table, String query, boolean allowFuzzyNameSearch) {
        return searchTable(table, query, allowFuzzyNameSearch, true);
    }

    private List<Map<String, String>> searchTable(String table,
                                                  String query,
                                                  boolean allowFuzzyNameSearch,
                                                  boolean allowGenericFallback) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String normalizedQuery = query.trim();
        List<String> searchableColumns = getSearchableColumns(table);
        if (searchableColumns.isEmpty()) {
            return List.of();
        }

        List<String> identityColumns = new ArrayList<>();
        for (String col : searchableColumns) {
            if (isIdentityColumn(col.toLowerCase(Locale.ROOT))) {
                identityColumns.add(col);
            }
        }

        List<Map<String, String>> results = new ArrayList<>();

        if (!identityColumns.isEmpty()) {
            List<Map<String, String>> identityRows = runSearchForColumns(
                table,
                limitColumns(identityColumns, MAX_COLUMNS_PER_QUERY),
                normalizedQuery,
                allowFuzzyNameSearch
            );
            addUniqueRows(results, identityRows);
        }

        if (allowGenericFallback && results.isEmpty()) {
            List<String> genericColumns = new ArrayList<>();
            for (String col : searchableColumns) {
                if (!identityColumns.contains(col)) {
                    genericColumns.add(col);
                }
            }

            if (!genericColumns.isEmpty()) {
                List<Map<String, String>> genericRows = runSearchForColumns(
                    table,
                    limitColumns(genericColumns, MAX_COLUMNS_PER_QUERY),
                    normalizedQuery,
                    false
                );
                addUniqueRows(results, genericRows);
            }
        }

        return results;
    }

    private List<Map<String, String>> runSearchForColumns(String table,
                                                           List<String> columns,
                                                           String query,
                                                           boolean allowFuzzyNameSearch) {
        if (columns.isEmpty()) {
            return List.of();
        }

        List<String> whereConditions = new ArrayList<>();
        List<String> params = new ArrayList<>();
        String normalizedLower = query.toLowerCase(Locale.ROOT);

        for (String column : columns) {
            whereConditions.add("LOWER(COALESCE(CAST(`" + escapeColumn(column) + "` AS CHAR), '')) = ?");
            params.add(normalizedLower);

            if (allowFuzzyNameSearch && isNameColumn(column.toLowerCase(Locale.ROOT)) && query.length() >= 3) {
                whereConditions.add("LOWER(COALESCE(CAST(`" + escapeColumn(column) + "` AS CHAR), '')) LIKE ?");
                params.add("%" + normalizedLower + "%");
            }
        }

        String sql = "SELECT * FROM `" + escapeTable(table) + "` WHERE " + String.join(" OR ", whereConditions) + " LIMIT 100";
        return executeSelect(table, sql, params);
    }

    private List<Map<String, String>> executeSelect(String table, String sql, List<String> params) {
        List<Map<String, String>> results = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setString(i + 1, params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData rsMeta = rs.getMetaData();
                int colCount = rsMeta.getColumnCount();
                while (rs.next()) {
                    Map<String, String> row = new LinkedHashMap<>();
                    for (int c = 1; c <= colCount; c++) {
                        String val = rs.getString(c);
                        row.put(rsMeta.getColumnName(c), val != null ? val : "null");
                    }
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            plugin.getDebugSystem().logError("UserinfoDatabase: search failed on " + table, e);
        }
        return results;
    }

    private void addUniqueRows(List<Map<String, String>> target, List<Map<String, String>> source) {
        if (source == null || source.isEmpty()) {
            return;
        }

        Set<String> signatures = new HashSet<>();
        for (Map<String, String> existing : target) {
            signatures.add(buildRowSignature(existing));
        }

        for (Map<String, String> row : source) {
            String signature = buildRowSignature(row);
            if (signatures.add(signature)) {
                target.add(row);
            }
        }
    }

    // ─── column metadata ─────────────────────────────────────────

    private List<ColumnInfo> getColumnInfos(String table) {
        List<ColumnInfo> cached = tableColumnsCache.get(table);
        if (cached != null) {
            return cached;
        }

        List<ColumnInfo> columns = new ArrayList<>();
        try (Connection conn = getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getColumns(conn.getCatalog(), null, table, "%")) {
                while (rs.next()) {
                    String name = rs.getString("COLUMN_NAME");
                    int dataType = rs.getInt("DATA_TYPE");
                    String typeName = rs.getString("TYPE_NAME");
                    columns.add(new ColumnInfo(name, dataType, typeName));
                }
            }
        } catch (SQLException e) {
            plugin.getDebugSystem().logError("UserinfoDatabase: failed to get columns for " + table, e);
            return List.of();
        }

        List<ColumnInfo> immutable = Collections.unmodifiableList(columns);
        tableColumnsCache.put(table, immutable);
        return immutable;
    }

    private List<String> getSearchableColumns(String table) {
        List<ColumnInfo> infos = getColumnInfos(table);
        if (infos.isEmpty()) {
            return List.of();
        }

        List<String> columns = new ArrayList<>();
        for (ColumnInfo info : infos) {
            if (isSearchableType(info.dataType(), info.typeName())) {
                columns.add(info.name());
            }
        }
        return columns;
    }

    private boolean isSearchableType(int sqlType, String typeName) {
        if (sqlType == Types.BLOB
            || sqlType == Types.BINARY
            || sqlType == Types.VARBINARY
            || sqlType == Types.LONGVARBINARY
            || sqlType == Types.ARRAY
            || sqlType == Types.STRUCT
            || sqlType == Types.DATALINK
            || sqlType == Types.DISTINCT
            || sqlType == Types.JAVA_OBJECT
            || sqlType == Types.REF
            || sqlType == Types.SQLXML
            || sqlType == Types.TIME_WITH_TIMEZONE
            || sqlType == Types.TIMESTAMP_WITH_TIMEZONE) {
            return false;
        }

        if (typeName == null) {
            return true;
        }

        String lower = typeName.toLowerCase(Locale.ROOT);
        return !lower.contains("blob") && !lower.contains("binary");
    }

    private List<String> limitColumns(List<String> columns, int max) {
        if (columns.size() <= max) {
            return columns;
        }
        return new ArrayList<>(columns.subList(0, max));
    }

    private record ColumnInfo(String name, int dataType, String typeName) {
    }

    // ─── helpers ──────────────────────────────────────────────────

    private Connection getConnection() throws SQLException {
        return plugin.getDatabaseManager().getConnection();
    }

    private boolean matchesAny(String value, String[] patterns) {
        for (String p : patterns) {
            if (value.equals(p) || value.contains(p)) {
                return true;
            }
        }
        return false;
    }

    private boolean isIdentityColumn(String lowerColumnName) {
        return matchesAny(lowerColumnName, UUID_COLUMNS)
            || matchesAny(lowerColumnName, NAME_COLUMNS)
            || matchesAny(lowerColumnName, DISCORD_COLUMNS);
    }

    private boolean isNameColumn(String lowerColumnName) {
        return matchesAny(lowerColumnName, NAME_COLUMNS);
    }

    private boolean isUuidColumn(String lowerColumnName) {
        return matchesAny(lowerColumnName, UUID_COLUMNS);
    }

    private boolean isDiscordColumn(String lowerColumnName) {
        return matchesAny(lowerColumnName, DISCORD_COLUMNS);
    }

    private boolean looksIdentityRelated(String lowerColumnName) {
        return lowerColumnName.contains("uuid")
            || lowerColumnName.contains("user")
            || lowerColumnName.contains("player")
            || lowerColumnName.contains("member")
            || lowerColumnName.contains("owner")
            || lowerColumnName.contains("name")
            || lowerColumnName.contains("nick")
            || lowerColumnName.contains("discord");
    }

    private Set<String> buildQueryVariants(String rawQuery) {
        if (rawQuery == null) {
            return Collections.emptySet();
        }

        String query = rawQuery.trim();
        if (query.isEmpty()) {
            return Collections.emptySet();
        }

        LinkedHashSet<String> variants = new LinkedHashSet<>();
        variants.add(query);
        variants.add(query.toLowerCase(Locale.ROOT));

        if (looksLikeUuid(query)) {
            String normalized = normalizeUuid(query);
            if (normalized != null) {
                variants.add(normalized);
                variants.add(normalized.replace("-", ""));
            }
        } else if (looksLikeRawUuidHex(query)) {
            String normalized = rawHexToUuid(query);
            if (normalized != null) {
                variants.add(normalized);
            }
        }

        Matcher matcher = DISCORD_ID_PATTERN.matcher(query);
        if (matcher.find()) {
            variants.add(matcher.group());
        }

        variants.removeIf(v -> v == null || v.isBlank());
        return variants;
    }

    private void collectIdentityVariantsFromRow(Map<String, String> row,
                                                Set<String> pendingQueries,
                                                Set<String> processedQueries) {
        for (Map.Entry<String, String> entry : row.entrySet()) {
            String column = entry.getKey();
            String value = entry.getValue();
            if (column == null || value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
                continue;
            }

            String lowerColumn = column.toLowerCase(Locale.ROOT);
            boolean collect = isUuidColumn(lowerColumn)
                || isNameColumn(lowerColumn)
                || isDiscordColumn(lowerColumn)
                || (looksIdentityRelated(lowerColumn) && looksLikeUuid(value))
                || (looksIdentityRelated(lowerColumn) && looksLikeRawUuidHex(value))
                || ((looksIdentityRelated(lowerColumn) || lowerColumn.contains("discord")) && looksLikeDiscordId(value));

            if (!collect) {
                continue;
            }

            for (String variant : buildQueryVariants(value)) {
                if (pendingQueries.size() + processedQueries.size() >= MAX_QUERY_VARIANTS) {
                    return;
                }
                if (!processedQueries.contains(variant)) {
                    pendingQueries.add(variant);
                }
            }
        }
    }

    private String buildRowSignature(Map<String, String> row) {
        StringBuilder signature = new StringBuilder();
        for (Map.Entry<String, String> entry : row.entrySet()) {
            signature.append(entry.getKey())
                .append('=')
                .append(entry.getValue() == null ? "null" : entry.getValue())
                .append('|');
        }
        return signature.toString();
    }

    private boolean looksLikeUuid(String value) {
        if (value == null) {
            return false;
        }
        return value.trim().toLowerCase(Locale.ROOT)
            .matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    }

    private boolean looksLikeRawUuidHex(String value) {
        if (value == null) {
            return false;
        }
        return value.trim().toLowerCase(Locale.ROOT).matches("^[0-9a-f]{32}$");
    }

    private boolean looksLikeDiscordId(String value) {
        if (value == null) {
            return false;
        }
        return value.trim().matches("^\\d{5,22}$");
    }

    private String normalizeUuid(String value) {
        if (!looksLikeUuid(value)) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String rawHexToUuid(String value) {
        if (!looksLikeRawUuidHex(value)) {
            return null;
        }

        String hex = value.trim().toLowerCase(Locale.ROOT);
        return hex.substring(0, 8) + "-"
            + hex.substring(8, 12) + "-"
            + hex.substring(12, 16) + "-"
            + hex.substring(16, 20) + "-"
            + hex.substring(20);
    }

    private String escapeTable(String table) {
        return table.replace("`", "``");
    }

    private String escapeColumn(String column) {
        return column.replace("`", "``");
    }
}
