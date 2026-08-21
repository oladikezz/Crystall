package ru.lor.watcher.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.lor.watcher.WatcherPlugin;
import ru.lor.watcher.model.WatcherSpawnSettings;
import ru.lor.watcher.utils.ColorUtil;
import ru.lor.watcher.utils.HeadUtil;
import ru.lor.watcher.utils.ItemBuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerSelectMenu {

    private static final int PAGE_SIZE = 36;

    // Track mode per admin: true = nearby mode, false = all online players mode
    private static final Map<java.util.UUID, Boolean> nearbyModeMap = new ConcurrentHashMap<>();

    public static void open(WatcherPlugin plugin, Player admin, WatcherSpawnSettings settings, int page) {
        boolean nearbyMode = nearbyModeMap.getOrDefault(admin.getUniqueId(), false);

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (nearbyMode) {
            // Filter and sort by distance from admin (same world first, then distance)
            players.sort(Comparator.comparingDouble((Player p) -> {
                if (!p.getWorld().equals(admin.getWorld())) {
                    return 999999.0;
                }
                return admin.getLocation().distance(p.getLocation());
            }));
        }

        int maxPage = Math.max(0, (int) Math.ceil((double) players.size() / PAGE_SIZE) - 1);
        if (page > maxPage) page = maxPage;

        GuiHolder holder = new GuiHolder(GuiHolder.MenuType.PLAYER_SELECT, null, settings, page);
        String titleText = nearbyMode
                ? "<purple><b>Игроки рядом со мной</b></purple> <dark_gray>(Стр. " + (page + 1) + "/" + (maxPage + 1) + ")</dark_gray>"
                : "<purple><b>Выбор игрока</b></purple> <dark_gray>(Стр. " + (page + 1) + "/" + (maxPage + 1) + ")</dark_gray>";

        Inventory inv = Bukkit.createInventory(holder, 54, ColorUtil.parse(titleText));
        holder.setInventory(inv);

        int startIndex = page * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, players.size());

        for (int i = startIndex; i < endIndex; i++) {
            Player p = players.get(i);
            int slot = i - startIndex;

            boolean hasWatcher = plugin.getWatcherManager().hasWatcher(p);
            boolean sameWorld = p.getWorld().equals(admin.getWorld());
            double dist = sameWorld ? admin.getLocation().distance(p.getLocation()) : -1;

            String distText = sameWorld ? String.format("%.1f м", dist) : "Другой мир";

            ItemStack head = new ItemBuilder(HeadUtil.getPlayerHead(p))
                    .name("<#a855f7><b>" + p.getName() + "</b>")
                    .loreStrings(
                            "<gray>Статус: " + (hasWatcher ? "<purple>Смотрящий активен</purple>" : "<green>Свободен</green>"),
                            "<gray>Дистанция от вас: <yellow>" + distText + "</yellow>",
                            "<gray>Здоровье: <red>" + Math.round(p.getHealth()) + "♥</red>",
                            "<gray>Локация: <yellow>" + p.getWorld().getName() + " (" + p.getLocation().getBlockX() + ", " + p.getLocation().getBlockY() + ", " + p.getLocation().getBlockZ() + ")</yellow>",
                            "",
                            "<light_purple>▶ Нажмите для выбора цели</light_purple>"
                    )
                    .glow(hasWatcher)
                    .build();

            inv.setItem(slot, head);
        }

        // Bottom border
        ItemStack border = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 36; i < 54; i++) {
            inv.setItem(i, border);
        }

        // Controls
        inv.setItem(45, new ItemBuilder(Material.ARROW).name("<yellow>← Назад в главное меню</yellow>").build());

        if (page > 0) {
            inv.setItem(48, new ItemBuilder(Material.PAPER).name("<light_purple>◄ Предыдущая страница</light_purple>").build());
        }

        // Slot 49: Toggle Nearby Filter
        inv.setItem(49, new ItemBuilder(nearbyMode ? Material.RECOVERY_COMPASS : Material.COMPASS)
                .name(nearbyMode ? "<#38bdf8><b>Режим: Игроки РЯДОМ СО МНОЙ</b>" : "<#38bdf8><b>Режим: ВСЕ ИГРОКИ</b>")
                .loreStrings(
                        "<gray>Сортировка: " + (nearbyMode ? "<green>По ближним к вам</green>" : "<yellow>По умолчанию</yellow>"),
                        "",
                        "<light_purple>▶ Нажмите для переключения сортировки</light_purple>"
                )
                .glow(nearbyMode)
                .build());

        if (page < maxPage) {
            inv.setItem(50, new ItemBuilder(Material.PAPER).name("<light_purple>Следующая страница ►</light_purple>").build());
        }

        admin.openInventory(inv);
    }

    public static void handleClick(WatcherPlugin plugin, Player admin, int slot, GuiHolder holder) {
        int page = holder.getPage();
        WatcherSpawnSettings settings = holder.getSettings();

        if (slot == 45) {
            String savedTarget = plugin.getLastSelectedTarget(admin.getUniqueId());
            MainMenu.open(plugin, admin, savedTarget, settings);
            return;
        }

        boolean nearbyMode = nearbyModeMap.getOrDefault(admin.getUniqueId(), false);

        if (slot == 49) {
            nearbyModeMap.put(admin.getUniqueId(), !nearbyMode);
            open(plugin, admin, settings, 0);
            return;
        }

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (nearbyMode) {
            players.sort(Comparator.comparingDouble((Player p) -> {
                if (!p.getWorld().equals(admin.getWorld())) {
                    return 999999.0;
                }
                return admin.getLocation().distance(p.getLocation());
            }));
        }

        int maxPage = Math.max(0, (int) Math.ceil((double) players.size() / PAGE_SIZE) - 1);

        if (slot == 48 && page > 0) {
            open(plugin, admin, settings, page - 1);
            return;
        }

        if (slot == 50 && page < maxPage) {
            open(plugin, admin, settings, page + 1);
            return;
        }

        if (slot >= 0 && slot < 36) {
            int index = page * PAGE_SIZE + slot;
            if (index < players.size()) {
                Player selected = players.get(index);
                plugin.setLastSelectedTarget(admin.getUniqueId(), selected.getName());
                MainMenu.open(plugin, admin, selected.getName(), settings);
            }
        }
    }
}
