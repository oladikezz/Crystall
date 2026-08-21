package net.schalker.DoAPI.core.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.database.DatabaseManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DatabaseCommand extends SubCommand {

    private static final long CONFIRMATION_TIMEOUT = 30000L;

    private final DoAPI plugin;
    private final ConcurrentHashMap<UUID, Long> resetConfirmations = new ConcurrentHashMap<>();

    public DatabaseCommand(DoAPI plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            showDatabaseInfo(stack);
            return;
        }

        if (args[0].equalsIgnoreCase("reconnect")) {
            stack.getSender().sendMessage(plugin.applyColors("&[SECONDARY]Переподключение к базам данных..."));
            plugin.getSchedulerManager().runAsync("database-reconnect", () -> {
                plugin.getDatabaseManager().reconnect();
                stack.getSender().sendMessage(plugin.applyColors("&[MAIN]§l✔ &[SECONDARY]Готово: "
                        + plugin.getDatabaseManager().getConnectedCount()
                        + "/" + plugin.getDatabaseManager().getTotalCount()));
            });
            return;
        }

        if (args[0].equalsIgnoreCase("reset")) {
            handleReset(stack, args);
            return;
        }

        showDatabaseInfo(stack);
    }

    private void showDatabaseInfo(CommandSourceStack stack) {
        CommandSender sender = stack.getSender();
        String separator = plugin.applyColors("&[SECONDARY]§l" + "=".repeat(40));

        sender.sendMessage(separator);
        sender.sendMessage(plugin.applyColors("&[MAIN]§l" + plugin.applyTinyCaps("Database")));
        sender.sendMessage(separator);

        for (DatabaseManager.DatabaseEntry entry : plugin.getDatabaseManager().getAllDatabases().values()) {
            String status = switch (entry.getPermissions()) {
                case "READ_WRITE" -> "&a✔ Read/Write";
                case "READ_ONLY" -> "&e⚠ Read Only";
                default -> "&c✖ No Access";
            };

            sender.sendMessage(plugin.applyColors("&[MAIN]" + entry.getKey()
                    + " &7(" + entry.getConfig().getType().getDisplayName() + ") " + status));

            if (entry.getConfig().getType().isRemote()) {
                sender.sendMessage(plugin.applyColors("  &7" + entry.getConfig().getHost()
                        + ":" + entry.getConfig().getPort() + "/" + entry.getConfig().getDatabase()));
            } else {
                sender.sendMessage(plugin.applyColors("  &7file: " + entry.getConfig().getFilePath()));
            }

            if (entry.isConnected()) {
                sender.sendMessage(plugin.applyColors("  &7таблиц: &f" + countTables(entry)
                        + " &7префикс: &f" + entry.getConfig().getTablePrefix()));
            }
        }

        sender.sendMessage(separator);
        sender.sendMessage(plugin.applyColors("&[SECONDARY]Подключено: &f"
                + plugin.getDatabaseManager().getConnectedCount()
                + "&7/&f" + plugin.getDatabaseManager().getTotalCount()));
        sender.sendMessage(separator);
    }

    private int countTables(DatabaseManager.DatabaseEntry entry) {
        try (Connection connection = entry.getDataSource().getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            int count = 0;
            try (ResultSet resultSet = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (resultSet.next()) {
                    count++;
                }
            }
            return count;
        } catch (Throwable throwable) {
            return 0;
        }
    }

    private void handleReset(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();

        if (!(sender instanceof Player player)) {
            if (args.length > 1 && args[1].equalsIgnoreCase("confirm")) {
                performReset(sender);
            } else {
                sender.sendMessage(plugin.applyColors("&cИспользуйте: /doapi database reset confirm"));
            }
            return;
        }

        if (!player.isOp() && !player.hasPermission("*")) {
            sender.sendMessage(plugin.applyColors("&cЭта операция доступна только операторам."));
            return;
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (args.length > 1 && args[1].equalsIgnoreCase("confirm")) {
            Long requestedAt = resetConfirmations.remove(uuid);
            if (requestedAt == null || now - requestedAt > CONFIRMATION_TIMEOUT) {
                sender.sendMessage(plugin.applyColors("&cПодтверждение истекло. Начните заново."));
                return;
            }
            performReset(sender);
            return;
        }

        resetConfirmations.put(uuid, now);
        sender.sendMessage(plugin.applyColors(
                "&c⚠ Это удалит ВСЕ таблицы с префиксом "
                + plugin.getDatabaseManager().getTablePrefix()));
        sender.sendMessage(plugin.applyColors(
                "&cВведите &f/doapi database reset confirm &cв течение 30 секунд."));
    }

    private void performReset(CommandSender sender) {
        plugin.getSchedulerManager().runAsync("database-reset", () -> {
            String prefix = plugin.getDatabaseManager().getTablePrefix();
            List<String> dropped = new ArrayList<>();

            try (Connection connection = plugin.getDatabaseManager().getConnection()) {
                List<String> tables = new ArrayList<>();
                try (ResultSet resultSet = connection.getMetaData()
                        .getTables(null, null, "%", new String[]{"TABLE"})) {
                    while (resultSet.next()) {
                        String table = resultSet.getString("TABLE_NAME");
                        if (table != null && table.toLowerCase(Locale.ROOT)
                                .startsWith(prefix.toLowerCase(Locale.ROOT))) {
                            tables.add(table);
                        }
                    }
                }

                try (var statement = connection.createStatement()) {
                    for (String table : tables) {
                        statement.executeUpdate("DROP TABLE IF EXISTS " + table);
                        dropped.add(table);
                    }
                }
            } catch (Throwable throwable) {
                plugin.getDebugSystem().logError("Database", "Reset failed", throwable);
                sender.sendMessage(plugin.applyColors("&cСброс не удался: " + throwable.getMessage()));
                return;
            }

            sender.sendMessage(plugin.applyColors("&[MAIN]§l✔ &[SECONDARY]Удалено таблиц: &f" + dropped.size()));
        });
    }

    @Override
    public String getPermission() {
        return "smps.database";
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return List.of("info", "reconnect", "reset").stream()
                    .filter(option -> option.startsWith(prefix))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            return List.of("confirm").stream()
                    .filter(option -> option.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
