package ru.lor.watcher.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.lor.watcher.WatcherPlugin;
import ru.lor.watcher.model.WatcherSpawnSettings;
import ru.lor.watcher.utils.ColorUtil;
import ru.lor.watcher.utils.HeadUtil;
import ru.lor.watcher.utils.ItemBuilder;
import ru.lor.watcher.utils.PermissionUtil;

public class MainMenu {

    private static final String NO_ACCESS = "<dark_gray>Недостаточно прав</dark_gray>";

    public static void open(WatcherPlugin plugin, Player admin, String targetPlayerName, WatcherSpawnSettings settings) {
        if (settings == null) {
            settings = new WatcherSpawnSettings();
            settings.setSpawnDistance(plugin.getConfigManager().getDefaultSpawnDistance());
            settings.setDurationSeconds(plugin.getConfigManager().getDefaultDurationSeconds());
            settings.setDespawnDistance(plugin.getConfigManager().getDefaultDespawnDistance());
            settings.setPositionType(plugin.getConfigManager().getDefaultPosition());
            settings.setBehaviorType(plugin.getConfigManager().getDefaultBehavior());
        }

        GuiHolder holder = new GuiHolder(GuiHolder.MenuType.MAIN, targetPlayerName, settings);
        Inventory inv = Bukkit.createInventory(holder, 36, ColorUtil.parse("<dark_purple><b>Смотрящий</b></dark_purple> <dark_gray>| Главное меню</dark_gray>"));
        holder.setInventory(inv);

        ItemStack borderPurple = new ItemBuilder(Material.PURPLE_STAINED_GLASS_PANE).name(" ").build();
        ItemStack borderBlack = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();

        // Fill borders
        for (int i = 0; i < 36; i++) {
            if (i < 9 || i >= 27 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, (i % 2 == 0) ? borderPurple : borderBlack);
            }
        }

        // Slot 10: Target Player Select
        ItemStack playerHead;
        if (targetPlayerName != null) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(targetPlayerName);
            playerHead = new ItemBuilder(HeadUtil.getPlayerHead(op))
                    .name("<#a855f7><b>Целевой игрок</b>")
                    .loreStrings(
                            "<gray>Выбран: <yellow>" + targetPlayerName + "</yellow>",
                            "",
                            "<light_purple>Нажмите, чтобы изменить игрока</light_purple>"
                    ).build();
        } else {
            playerHead = new ItemBuilder(Material.PLAYER_HEAD)
                    .name("<#a855f7><b>Выбрать игрока</b>")
                    .loreStrings(
                            "<gray>Выбран: <red>Никто</red>",
                            "",
                            "<light_purple>Нажмите, чтобы выбрать игрока</light_purple>"
                    ).build();
        }
        inv.setItem(10, playerHead);

        // Slot 12: Spawn Watcher
        boolean hasTarget = (targetPlayerName != null && Bukkit.getPlayerExact(targetPlayerName) != null);
        boolean maySpawn = admin.hasPermission(PermissionUtil.SPAWN);
        inv.setItem(12, new ItemBuilder(Material.ENDER_EYE)
                .name("<#22c55e><b>Заспавнить Смотрящего</b>")
                .loreStrings(
                        "<gray>Создать Смотрящего возле игрока</gray>",
                        hasTarget ? "<gray>Цель: <yellow>" + targetPlayerName + "</yellow>" : "<red>Игрок не выбран или оффлайн!</red>",
                        "",
                        !maySpawn ? NO_ACCESS
                                : (hasTarget ? "<green>▶ Нажмите для запуска</green>" : "<dark_gray>Недоступено</dark_gray>")
                )
                .glow(hasTarget && maySpawn)
                .build());

        // Slot 14: Despawn Watcher
        inv.setItem(14, new ItemBuilder(Material.BARRIER)
                .name("<#ef4444><b>Убрать Смотрящего</b>")
                .loreStrings(
                        "<gray>Принудительно удалить Смотрящего</gray>",
                        hasTarget ? "<gray>Цель: <yellow>" + targetPlayerName + "</yellow>" : "<gray>Цель не выбрана</gray>",
                        "",
                        maySpawn ? "<red>▶ Нажмите для деспавна</red>" : NO_ACCESS
                ).build());

        // Slot 16: Spawn Settings (Distances & Timers)
        inv.setItem(16, new ItemBuilder(Material.COMPARATOR)
                .name("<#3b82f6><b>Параметры спавна</b>")
                .loreStrings(
                        "<gray>Дистанция: <yellow>" + settings.getSpawnDistance() + " блоков</yellow>",
                        "<gray>Время жизни: <yellow>" + settings.getDurationSeconds() + " сек</yellow>",
                        "<gray>Макс. дистанция: <yellow>" + settings.getDespawnDistance() + " блоков</yellow>",
                        "",
                        "<blue>▶ Нажмите для настройки</blue>"
                ).build());

        // Slot 19: Position & Behavior
        inv.setItem(19, new ItemBuilder(Material.COMPASS)
                .name("<#f59e0b><b>Позиция и Поведение</b>")
                .loreStrings(
                        "<gray>Позиция: <yellow>" + settings.getPositionType().getDisplayName() + "</yellow>",
                        "<gray>Поведение: <yellow>" + settings.getBehaviorType().getDisplayName() + "</yellow>",
                        "",
                        "<yellow>▶ Нажмите для изменения</yellow>"
                ).build());

        // Slot 21: Screen Effects & Audio
        inv.setItem(21, new ItemBuilder(Material.BREWING_STAND)
                .name("<#ec4899><b>Эффекты и Звуки</b>")
                .loreStrings(
                        "<gray>Звук: <yellow>" + (settings.getSoundName() != null ? settings.getSoundName() : "Нет") + "</yellow>",
                        "<gray>Сообщение: <yellow>" + (settings.getMessageText() != null ? "Настроено" : "Нет") + "</yellow>",
                        "<gray>Экранные эффекты: <yellow>Настроено</yellow>",
                        "",
                        "<light_purple>▶ Нажмите для выбора</light_purple>"
                ).build());

        // Slot 23: Automated Events
        inv.setItem(23, new ItemBuilder(Material.CLOCK)
                .name("<#8b5cf6><b>Автоматические события</b>")
                .loreStrings(
                        "<gray>Создание и управление таймерами спавна</gray>",
                        "",
                        admin.hasPermission(PermissionUtil.EVENTS)
                                ? "<dark_purple>▶ Нажмите для просмотра событий</dark_purple>"
                                : NO_ACCESS
                ).build());

        // Slot 25: Logs & History
        inv.setItem(25, new ItemBuilder(Material.WRITABLE_BOOK)
                .name("<#06b6d4><b>Логи и История</b>")
                .loreStrings(
                        "<gray>Просмотр истории появления Смотрящего</gray>",
                        "",
                        "<cyan>▶ Нажмите для просмотра логов</cyan>"
                ).build());

        // Slot 31: Reload Plugin Config
        inv.setItem(31, new ItemBuilder(Material.REDSTONE)
                .name("<#ef4444><b>Перезагрузить конфиг</b>")
                .loreStrings(
                        "<gray>Перезагрузить все файлы конфигурации</gray>",
                        "",
                        admin.hasPermission(PermissionUtil.ADMIN)
                                ? "<red>▶ Нажмите для перезагрузки</red>"
                                : NO_ACCESS
                ).build());

        admin.openInventory(inv);
    }

    public static void handleClick(WatcherPlugin plugin, Player admin, int slot, GuiHolder holder) {
        String targetName = holder.getTargetPlayerName();
        WatcherSpawnSettings settings = holder.getSettings();

        switch (slot) {
            case 10 -> PlayerSelectMenu.open(plugin, admin, settings, 0);
            case 12 -> {
                if (!requirePermission(plugin, admin, PermissionUtil.SPAWN)) {
                    return;
                }
                if (targetName == null) {
                    admin.sendMessage(ColorUtil.parse(plugin.getConfigManager().getMessage("player-not-found")));
                    return;
                }
                Player target = Bukkit.getPlayerExact(targetName);
                if (target == null) {
                    admin.sendMessage(ColorUtil.parse(plugin.getConfigManager().getMessage("player-not-found")));
                    return;
                }
                final Player spawnTarget = target;
                spawnTarget.getScheduler().run(plugin.getBukkitPlugin(), task -> {
                    if (!spawnTarget.isOnline()) {
                        return;
                    }
                    boolean success = plugin.getWatcherManager().spawnWatcher(spawnTarget, settings, admin.getName());
                    String key = success ? "spawn-success" : "spawn-already-exists";
                    admin.sendMessage(ColorUtil.parse(plugin.getConfigManager().getMessage(key)
                            .replace("{player}", spawnTarget.getName())));
                }, null);
                open(plugin, admin, targetName, settings);
            }
            case 14 -> {
                if (!requirePermission(plugin, admin, PermissionUtil.SPAWN)) {
                    return;
                }
                if (targetName == null) {
                    admin.sendMessage(ColorUtil.parse(plugin.getConfigManager().getMessage("player-not-found")));
                    return;
                }
                Player target = Bukkit.getPlayerExact(targetName);
                if (target != null && plugin.getWatcherManager().hasWatcher(target)) {
                    final Player despawnTarget = target;
                    despawnTarget.getScheduler().run(plugin.getBukkitPlugin(), task -> {
                        plugin.getWatcherManager().despawnWatcher(despawnTarget.getUniqueId(),
                                ru.lor.watcher.events.WatcherDespawnEvent.DespawnReason.MANUAL_DESPAWN);
                        admin.sendMessage(ColorUtil.parse(plugin.getConfigManager().getMessage("despawn-success")
                                .replace("{player}", despawnTarget.getName())));
                    }, null);
                } else {
                    admin.sendMessage(ColorUtil.parse(plugin.getConfigManager().getMessage("no-active-watcher").replace("{player}", targetName)));
                }
                open(plugin, admin, targetName, settings);
            }
            case 16 -> ControlMenu.open(plugin, admin, targetName, settings);
            case 19 -> PositionMenu.open(plugin, admin, targetName, settings);
            case 21 -> EffectsMenu.open(plugin, admin, targetName, settings);
            case 23 -> {
                if (!requirePermission(plugin, admin, PermissionUtil.EVENTS)) {
                    return;
                }
                EventsMenu.open(plugin, admin, targetName, settings);
            }
            case 25 -> LogsMenu.open(plugin, admin, targetName, settings, 0);
            case 31 -> {
                if (!requirePermission(plugin, admin, PermissionUtil.ADMIN)) {
                    return;
                }
                plugin.reloadConfig();
                admin.sendMessage(ColorUtil.parse(plugin.getConfigManager().getMessage("config-reloaded")));
                open(plugin, admin, targetName, settings);
            }
        }
    }

    private static boolean requirePermission(WatcherPlugin plugin, Player admin, String permission) {
        if (admin.hasPermission(permission)) {
            return true;
        }
        admin.sendMessage(ColorUtil.parse(plugin.getConfigManager().getMessage("no-permission")));
        return false;
    }
}
