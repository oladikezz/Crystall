package net.schalker.SMPS.modules.userinfo.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.SMPS.modules.userinfo.UserinfoModule;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class UserinfoCommand extends ModuleCommand {
    private final UserinfoModule module;
    private static final int MAX_ROWS_TO_SHOW = 10;

    private static final Set<String> HIDDEN_COLUMNS = Set.of("flags", "settings");
    private static final Set<String> HIDDEN_TABLES = Set.of("sm_flags_settings");

    public UserinfoCommand(DoAPI plugin, UserinfoModule module) {
        super(plugin);
        this.module = module;
    }

    @Override
    public String getName() {
        return "userinfo";
    }

    @Override
    public String getPermission() {
        return "smuserinfo.use";
    }

    @Override
    public String getDescription() {
        return "Поиск данных игрока по БД";
    }

    @Override
    public String getUsage() {
        return "/userinfo <nick|UUID|discordId> [table]";
    }

    @Override
    public Collection<String> getAliases() {
        return Arrays.asList("ui");
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();

        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /userinfo <nick|UUID|discordId> [table]", NamedTextColor.YELLOW));
            return;
        }

        String query = args[0];
        String table = args.length >= 2 ? args[1] : null;

        if (table != null && !module.getDatabase().tableExists(table)) {
            sender.sendMessage(Component.text("Таблица не найдена: " + table, NamedTextColor.RED));
            return;
        }

        sender.sendMessage(Component.text(
            "Ищу в БД: " + query + (table == null ? " (все таблицы)" : " (таблица: " + table + ")") + " ...",
            NamedTextColor.YELLOW));

        String taskId = "userinfo-search-" + sender.getName();
        plugin.getSchedulerManager().runAsync(taskId, () -> {
            Map<String, List<Map<String, String>>> results;

            if (table == null) {
                results = module.getDatabase().searchAllTables(query, MAX_ROWS_TO_SHOW);
            } else {
                List<Map<String, String>> rows = module.getDatabase().searchInTable(table, query, MAX_ROWS_TO_SHOW);
                results = new LinkedHashMap<>();
                if (!rows.isEmpty()) {
                    results.put(table, rows);
                }
            }

            if (sender instanceof Player player) {
                plugin.getSchedulerManager().runEntityTask(player, "userinfo-chat-" + player.getName(), () -> {
                    if (player.isOnline()) {
                        sendResultsToSender(player, query, results);
                    }
                });
                return;
            }

            sendResultsToSender(sender, query, results);
        });
    }

    private List<Map.Entry<String, List<Map<String, String>>>> visibleEntries(Map<String, List<Map<String, String>>> results) {
        return results.entrySet().stream()
            .filter(entry -> !HIDDEN_TABLES.contains(entry.getKey().toLowerCase(Locale.ROOT)))
            .sorted(Comparator.comparing(Map.Entry::getKey))
            .collect(Collectors.toList());
    }

    private void sendResultsToSender(CommandSender sender,
                                     String query,
                                     Map<String, List<Map<String, String>>> results) {
        if (results == null || results.isEmpty()) {
            sender.sendMessage(Component.text("Ничего не найдено по запросу: " + query, NamedTextColor.RED));
            return;
        }

        List<Map.Entry<String, List<Map<String, String>>>> entries = visibleEntries(results);
        int shownRows = entries.stream().mapToInt(e -> e.getValue().size()).sum();
        sender.sendMessage(Component.text(
            "Найдено " + shownRows + " строк в " + entries.size() + " таблицах (лимит: " + MAX_ROWS_TO_SHOW + ").",
            NamedTextColor.GREEN));

        int rowCounter = 0;
        for (Map.Entry<String, List<Map<String, String>>> entry : entries) {
            if (rowCounter >= MAX_ROWS_TO_SHOW) {
                break;
            }

            sender.sendMessage(colorize("&[MAIN]=== &[SECONDARY]" + entry.getKey() + " &[MAIN]==="));
            for (Map<String, String> row : entry.getValue()) {
                if (rowCounter >= MAX_ROWS_TO_SHOW) {
                    break;
                }

                rowCounter++;
                sender.sendMessage(Component.text("  #" + rowCounter, NamedTextColor.GRAY));

                List<Map.Entry<String, String>> columns = new ArrayList<>(row.entrySet());
                columns.sort(Comparator.comparing(Map.Entry::getKey));

                for (Map.Entry<String, String> col : columns) {
                    String colName = col.getKey();
                    if (HIDDEN_COLUMNS.contains(colName.toLowerCase(Locale.ROOT))) {
                        continue;
                    }

                    String displayValue = col.getValue() == null ? "null" : col.getValue();
                    sender.sendMessage(Component.text("    " + colName + ": ", NamedTextColor.WHITE)
                        .append(colorize("&[SECONDARY]" + displayValue)
                            .clickEvent(ClickEvent.copyToClipboard(displayValue))
                            .hoverEvent(HoverEvent.showText(Component.text("Нажмите чтобы скопировать", NamedTextColor.YELLOW)))));
                }
            }
        }

        if (rowCounter >= MAX_ROWS_TO_SHOW) {
            sender.sendMessage(Component.text("Показаны только первые " + MAX_ROWS_TO_SHOW + " записей.", NamedTextColor.YELLOW));
        }
    }

    private Component colorize(String text) {
        if (text == null) {
            return Component.empty();
        }
        try {
            String main = resolveThemeColor("main-color", "&6");
            String secondary = resolveThemeColor("secondary-color", "&e");
            text = text
                .replace("&[MAIN]", main)
                .replace("&[main]", main)
                .replace("&[SECONDARY]", secondary)
                .replace("&[secondary]", secondary);
        } catch (Exception ignored) {
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    private String resolveThemeColor(String globalKey, String fallback) {
        String value = null;
        try {
            if (this.plugin.getConfigManager() != null && this.plugin.getConfigManager().getConfig() != null) {
                value = this.plugin.getConfigManager().getConfig().getString(globalKey);
            }
        } catch (Exception ignored) {
        }

        if (value == null || value.isBlank()) {
            value = fallback;
        }

        return normalizeColorCode(value);
    }

    private String normalizeColorCode(String value) {
        if (value == null) {
            return "";
        }

        String color = value.trim();
        if (color.startsWith("#") && color.length() == 7) {
            String hex = color.substring(1);
            StringBuilder builder = new StringBuilder("&x");
            for (char ch : hex.toCharArray()) {
                builder.append('&').append(ch);
            }
            return builder.toString();
        }

        return color;
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        if (args.length == 1) {
            String current = args[0].toLowerCase();
            return plugin.getServer().getOnlinePlayers().stream()
                .map(p -> p.getName())
                .filter(n -> n.toLowerCase().startsWith(current))
                .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String current = args[1].toLowerCase();
            return module.getDatabase().getAllTables().stream()
                .filter(t -> t.toLowerCase().startsWith(current))
                .sorted()
                .limit(30)
                .collect(Collectors.toList());
        }

        return List.of();
    }
}
