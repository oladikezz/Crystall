package ru.lor.watcher.gui;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.lor.watcher.WatcherPlugin;
import ru.lor.watcher.model.WatcherPositionType;
import ru.lor.watcher.model.WatcherSpawnSettings;
import ru.lor.watcher.utils.ColorUtil;
import ru.lor.watcher.utils.ItemBuilder;

public class PositionMenu {

    public static void open(WatcherPlugin plugin, Player admin, String targetPlayerName, WatcherSpawnSettings settings) {
        GuiHolder holder = new GuiHolder(GuiHolder.MenuType.POSITION, targetPlayerName, settings);
        Inventory inv = Bukkit.createInventory(holder, 27, ColorUtil.parse("<yellow><b>Позиция появления</b></yellow>"));
        holder.setInventory(inv);

        GuiHolder.fillBorder(inv);

        WatcherPositionType current = settings.getPositionType();
        Location customLoc = settings.getCustomLocation();

        int[] slots = {10, 11, 12, 13, 14, 15};
        WatcherPositionType[] types = WatcherPositionType.values();

        for (int i = 0; i < types.length; i++) {
            WatcherPositionType type = types[i];
            boolean isSelected = (customLoc == null && type == current);

            ItemStack item = new ItemBuilder(isSelected ? Material.COMPASS : Material.PAPER)
                    .name((isSelected ? "<green>✔ " : "<yellow>") + type.getDisplayName())
                    .loreStrings(
                            "<gray>" + type.getDescription() + "</gray>",
                            "",
                            isSelected ? "<green>Текущий выбор</green>" : "<light_purple>▶ Нажмите для выбора</light_purple>"
                    )
                    .glow(isSelected)
                    .build();

            inv.setItem(slots[i], item);
        }

        // Slot 16: Custom Exact Coordinates
        boolean hasCustom = (customLoc != null);
        String coordsText = hasCustom
                ? "<green>" + customLoc.getBlockX() + ", " + customLoc.getBlockY() + ", " + customLoc.getBlockZ() + " (" + customLoc.getWorld().getName() + ")</green>"
                : "<red>Не заданы (используются относительные)</red>";

        inv.setItem(16, new ItemBuilder(hasCustom ? Material.BEACON : Material.TARGET)
                .name("<#38bdf8><b>Точные координаты (X, Y, Z)</b>")
                .loreStrings(
                        "<gray>Координаты: " + coordsText,
                        "",
                        "<light_purple>▶ ЛКМ: Взять мои текущие X, Y, Z</light_purple>",
                        "<yellow>▶ Shift + ЛКМ: Ввести X Y Z через чат</yellow>",
                        "<red>▶ ПКМ: Сбросить точные координаты</red>"
                )
                .glow(hasCustom)
                .build());

        inv.setItem(22, new ItemBuilder(Material.ARROW).name("<yellow>← Готово (В главное меню)</yellow>").build());

        admin.openInventory(inv);
    }

    public static void handleClick(WatcherPlugin plugin, Player admin, int slot, GuiHolder holder, ClickType click) {
        String targetName = holder.getTargetPlayerName();
        WatcherSpawnSettings settings = holder.getSettings();

        if (slot == 22) {
            MainMenu.open(plugin, admin, targetName, settings);
            return;
        }

        int[] slots = {10, 11, 12, 13, 14, 15};
        WatcherPositionType[] types = WatcherPositionType.values();

        for (int i = 0; i < slots.length; i++) {
            if (slot == slots[i] && i < types.length) {
                settings.setCustomLocation(null);
                settings.setPositionType(types[i]);
                open(plugin, admin, targetName, settings);
                return;
            }
        }

        if (slot == 16) {
            if (click.isRightClick()) {
                settings.setCustomLocation(null);
                open(plugin, admin, targetName, settings);
            } else if (click.isShiftClick()) {
                plugin.getInputSessionManager().startSession(admin, "<#38bdf8>Введите координаты через пробел 'X Y Z' (напр. 100 64 -200):</#38bdf8>", input -> {
                    try {
                        String[] parts = input.trim().split("\\s+");
                        if (parts.length >= 3) {
                            double x = Double.parseDouble(parts[0]);
                            double y = Double.parseDouble(parts[1]);
                            double z = Double.parseDouble(parts[2]);
                            settings.setCustomLocation(new Location(admin.getWorld(), x, y, z));
                            admin.sendMessage(ColorUtil.parse("<green>Точные координаты установлены на: <yellow>" + x + " " + y + " " + z + "</yellow>!</green>"));
                        } else {
                            admin.sendMessage(ColorUtil.parse("<red>Неверный формат! Формат: X Y Z</red>"));
                        }
                    } catch (NumberFormatException e) {
                        admin.sendMessage(ColorUtil.parse("<red>Неверный формат чисел!</red>"));
                    }
                    open(plugin, admin, targetName, settings);
                });
            } else {
                settings.setCustomLocation(admin.getLocation().clone());
                admin.sendMessage(ColorUtil.parse("<green>Точные координаты установлены на вашу текущую позицию!</green>"));
                open(plugin, admin, targetName, settings);
            }
        }
    }

    public static void handleClick(WatcherPlugin plugin, Player admin, int slot, GuiHolder holder) {
        handleClick(plugin, admin, slot, holder, ClickType.LEFT);
    }
}
