package ru.lor.watcher.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.lor.watcher.WatcherPlugin;
import ru.lor.watcher.model.WatcherSpawnSettings;
import ru.lor.watcher.utils.ColorUtil;
import ru.lor.watcher.utils.ItemBuilder;

import java.util.List;

public class ControlMenu {

    public static void open(WatcherPlugin plugin, Player admin, String targetPlayerName, WatcherSpawnSettings settings) {
        GuiHolder holder = new GuiHolder(GuiHolder.MenuType.CONTROL, targetPlayerName, settings);
        Inventory inv = Bukkit.createInventory(holder, 45, ColorUtil.parse("<blue><b>Параметры спавна</b></blue>"));
        holder.setInventory(inv);

        GuiHolder.fillBorder(inv);

        // Row 1: Spawn Distance (Slots 10, 11, 12, 13 | 15: Custom)
        inv.setItem(9, new ItemBuilder(Material.ENDER_PEARL).name("<#3b82f6><b>Дистанция спавна</b>").loreStrings("<gray>Выбрано: <yellow>" + settings.getSpawnDistance() + " бл.</yellow>").build());
        List<Double> distPresets = plugin.getConfigManager().getSpawnDistancePresets();
        int[] distSlots = {10, 11, 12, 13};
        for (int i = 0; i < distSlots.length && i < distPresets.size(); i++) {
            double val = distPresets.get(i);
            boolean selected = (settings.getSpawnDistance() == val);
            inv.setItem(distSlots[i], new ItemBuilder(selected ? Material.LIME_DYE : Material.GRAY_DYE)
                    .name((selected ? "<green>✔ " : "<gray>") + val + " бл.")
                    .glow(selected)
                    .build());
        }
        inv.setItem(15, new ItemBuilder(Material.ANVIL)
                .name("<yellow>Собственное значение</yellow>")
                .loreStrings("<gray>Ввести число через чат</gray>")
                .build());

        // Row 2: Duration (Slots 19, 20, 21, 22 | 23: Infinite | 24: Custom)
        boolean isInf = settings.isInfiniteDuration();
        String durText = isInf ? "<purple>Бесконечно (пока не подойдут близко/ударят)</purple>" : settings.getDurationSeconds() + " сек.";
        inv.setItem(18, new ItemBuilder(Material.CLOCK).name("<#eab308><b>Время жизни</b>").loreStrings("<gray>Выбрано: <yellow>" + durText + "</yellow>").build());

        List<Integer> durPresets = plugin.getConfigManager().getDurationPresets();
        int[] durSlots = {19, 20, 21, 22};
        for (int i = 0; i < durSlots.length && i < durPresets.size(); i++) {
            int val = durPresets.get(i);
            boolean selected = (!isInf && settings.getDurationSeconds() == val);
            inv.setItem(durSlots[i], new ItemBuilder(selected ? Material.LIME_DYE : Material.GRAY_DYE)
                    .name((selected ? "<green>✔ " : "<gray>") + val + " сек.")
                    .glow(selected)
                    .build());
        }

        // Slot 23: Infinite Duration Toggle
        inv.setItem(23, new ItemBuilder(isInf ? Material.NETHER_STAR : Material.DRAGON_EGG)
                .name("<purple><b>∞ Бесконечный спавн</b></purple>")
                .loreStrings(
                        "<gray>Смотрящий стоит бесконечно, пока игрок не подойдёт близко (1-2 бл) или не ударит его!</gray>",
                        "",
                        isInf ? "<green>✔ ВКЛЮЧЕНО</green>" : "<light_purple>▶ Нажмите для включения</light_purple>"
                )
                .glow(isInf)
                .build());

        inv.setItem(24, new ItemBuilder(Material.ANVIL)
                .name("<yellow>Собственное значение</yellow>")
                .loreStrings("<gray>Ввести секунды через чат</gray>")
                .build());

        // Row 3: Despawn Distance (Slots 28, 29, 30, 31 | 33: Custom)
        inv.setItem(27, new ItemBuilder(Material.MAP).name("<#ef4444><b>Дистанция исчезновения</b>").loreStrings("<gray>Выбрано: <yellow>" + settings.getDespawnDistance() + " бл.</yellow>").build());
        List<Double> despPresets = plugin.getConfigManager().getDespawnDistancePresets();
        int[] despSlots = {28, 29, 30, 31};
        for (int i = 0; i < despSlots.length && i < despPresets.size(); i++) {
            double val = despPresets.get(i);
            boolean selected = (settings.getDespawnDistance() == val);
            inv.setItem(despSlots[i], new ItemBuilder(selected ? Material.LIME_DYE : Material.GRAY_DYE)
                    .name((selected ? "<green>✔ " : "<gray>") + val + " бл.")
                    .glow(selected)
                    .build());
        }
        inv.setItem(33, new ItemBuilder(Material.ANVIL)
                .name("<yellow>Собственное значение</yellow>")
                .loreStrings("<gray>Ввести дистанцию через чат</gray>")
                .build());

        // Back button
        inv.setItem(40, new ItemBuilder(Material.ARROW).name("<yellow>← Готово (В главное меню)</yellow>").build());

        admin.openInventory(inv);
    }

    public static void handleClick(WatcherPlugin plugin, Player admin, int slot, GuiHolder holder, ClickType click) {
        String targetName = holder.getTargetPlayerName();
        WatcherSpawnSettings settings = holder.getSettings();

        if (slot == 40) {
            MainMenu.open(plugin, admin, targetName, settings);
            return;
        }

        // Spawn Distance presets
        List<Double> distPresets = plugin.getConfigManager().getSpawnDistancePresets();
        int[] distSlots = {10, 11, 12, 13};
        for (int i = 0; i < distSlots.length; i++) {
            if (slot == distSlots[i] && i < distPresets.size()) {
                settings.setSpawnDistance(distPresets.get(i));
                open(plugin, admin, targetName, settings);
                return;
            }
        }
        if (slot == 15) {
            plugin.getInputSessionManager().startSession(admin, "<blue>Введите дистанцию появления (в блоках):</blue>", input -> {
                try {
                    double val = Double.parseDouble(input);
                    settings.setSpawnDistance(val);
                    admin.sendMessage(ColorUtil.parse(plugin.getConfigManager().getMessage("chat-input-success")));
                } catch (NumberFormatException e) {
                    admin.sendMessage(ColorUtil.parse(plugin.getConfigManager().getMessage("chat-input-invalid-number")));
                }
                open(plugin, admin, targetName, settings);
            });
            return;
        }

        // Duration presets
        List<Integer> durPresets = plugin.getConfigManager().getDurationPresets();
        int[] durSlots = {19, 20, 21, 22};
        for (int i = 0; i < durSlots.length; i++) {
            if (slot == durSlots[i] && i < durPresets.size()) {
                settings.setInfiniteDuration(false);
                settings.setDurationSeconds(durPresets.get(i));
                open(plugin, admin, targetName, settings);
                return;
            }
        }
        if (slot == 23) {
            settings.setInfiniteDuration(!settings.isInfiniteDuration());
            open(plugin, admin, targetName, settings);
            return;
        }
        if (slot == 24) {
            plugin.getInputSessionManager().startSession(admin, "<yellow>Введите время жизни Смотрящего (в секундах):</yellow>", input -> {
                try {
                    int val = Integer.parseInt(input);
                    settings.setInfiniteDuration(false);
                    settings.setDurationSeconds(val);
                    admin.sendMessage(ColorUtil.parse(plugin.getConfigManager().getMessage("chat-input-success")));
                } catch (NumberFormatException e) {
                    admin.sendMessage(ColorUtil.parse(plugin.getConfigManager().getMessage("chat-input-invalid-number")));
                }
                open(plugin, admin, targetName, settings);
            });
            return;
        }

        // Despawn Distance presets
        List<Double> despPresets = plugin.getConfigManager().getDespawnDistancePresets();
        int[] despSlots = {28, 29, 30, 31};
        for (int i = 0; i < despSlots.length; i++) {
            if (slot == despSlots[i] && i < despPresets.size()) {
                settings.setDespawnDistance(despPresets.get(i));
                open(plugin, admin, targetName, settings);
                return;
            }
        }
        if (slot == 33) {
            plugin.getInputSessionManager().startSession(admin, "<red>Введите макс. дистанцию исчезновения (в блоках):</red>", input -> {
                try {
                    double val = Double.parseDouble(input);
                    settings.setDespawnDistance(val);
                    admin.sendMessage(ColorUtil.parse(plugin.getConfigManager().getMessage("chat-input-success")));
                } catch (NumberFormatException e) {
                    admin.sendMessage(ColorUtil.parse(plugin.getConfigManager().getMessage("chat-input-invalid-number")));
                }
                open(plugin, admin, targetName, settings);
            });
        }
    }
}
