package ru.lor.watcher.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.lor.watcher.WatcherPlugin;
import ru.lor.watcher.model.WatcherLog;
import ru.lor.watcher.model.WatcherSpawnSettings;
import ru.lor.watcher.utils.ColorUtil;
import ru.lor.watcher.utils.ItemBuilder;

import java.util.List;

public class LogsMenu {

    private static final int PAGE_SIZE = 36;

    public static void open(WatcherPlugin plugin, Player admin, String targetPlayerName, WatcherSpawnSettings settings, int page) {
        List<WatcherLog> logs = plugin.getLogManager().getLogs();
        int maxPage = Math.max(0, (int) Math.ceil((double) logs.size() / PAGE_SIZE) - 1);
        if (page > maxPage) page = maxPage;

        GuiHolder holder = new GuiHolder(GuiHolder.MenuType.LOGS, targetPlayerName, settings, page);
        Inventory inv = Bukkit.createInventory(holder, 54, ColorUtil.parse("<cyan><b>История спавнов</b></cyan> <dark_gray>(Стр. " + (page + 1) + "/" + (maxPage + 1) + ")</dark_gray>"));
        holder.setInventory(inv);

        int startIndex = page * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, logs.size());

        for (int i = startIndex; i < endIndex; i++) {
            WatcherLog log = logs.get(i);
            int slot = i - startIndex;

            ItemStack item = new ItemBuilder(Material.PAPER)
                    .name("<cyan><b>Лог #" + (logs.size() - i) + "</b></cyan>")
                    .loreStrings(
                            "<gray>Цель: <yellow>" + log.getTargetPlayerName() + "</yellow>",
                            "<gray>Время: <yellow>" + log.getFormattedTime() + "</yellow>",
                            "<gray>Время жизни: <yellow>" + log.getDurationSeconds() + " сек</yellow>",
                            "<gray>Позиция: <yellow>" + log.getPosition() + "</yellow>",
                            "<gray>Кто запустил: <yellow>" + log.getExecutorName() + "</yellow>"
                    ).build();

            inv.setItem(slot, item);
        }

        // Bottom border
        ItemStack border = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 36; i < 54; i++) {
            inv.setItem(i, border);
        }

        inv.setItem(45, new ItemBuilder(Material.ARROW).name("<yellow>← В главное меню</yellow>").build());

        if (page > 0) {
            inv.setItem(48, new ItemBuilder(Material.PAPER).name("<light_purple>◄ Предыдущая страница</light_purple>").build());
        }
        if (page < maxPage) {
            inv.setItem(50, new ItemBuilder(Material.PAPER).name("<light_purple>Следующая страница ►</light_purple>").build());
        }

        admin.openInventory(inv);
    }

    public static void handleClick(WatcherPlugin plugin, Player admin, int slot, GuiHolder holder) {
        int page = holder.getPage();
        String targetName = holder.getTargetPlayerName();
        WatcherSpawnSettings settings = holder.getSettings();

        if (slot == 45) {
            MainMenu.open(plugin, admin, targetName, settings);
            return;
        }

        List<WatcherLog> logs = plugin.getLogManager().getLogs();
        int maxPage = Math.max(0, (int) Math.ceil((double) logs.size() / PAGE_SIZE) - 1);

        if (slot == 48 && page > 0) {
            open(plugin, admin, targetName, settings, page - 1);
            return;
        }

        if (slot == 50 && page < maxPage) {
            open(plugin, admin, targetName, settings, page + 1);
        }
    }
}
