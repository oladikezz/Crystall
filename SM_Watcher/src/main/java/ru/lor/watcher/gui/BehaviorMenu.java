package ru.lor.watcher.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.lor.watcher.WatcherPlugin;
import ru.lor.watcher.model.WatcherBehaviorType;
import ru.lor.watcher.model.WatcherSpawnSettings;
import ru.lor.watcher.utils.ColorUtil;
import ru.lor.watcher.utils.ItemBuilder;

public class BehaviorMenu {

    public static void open(WatcherPlugin plugin, Player admin, String targetPlayerName, WatcherSpawnSettings settings) {
        GuiHolder holder = new GuiHolder(GuiHolder.MenuType.BEHAVIOR, targetPlayerName, settings);
        Inventory inv = Bukkit.createInventory(holder, 27, ColorUtil.parse("<orange><b>Режим поведения</b></orange>"));
        holder.setInventory(inv);

        GuiHolder.fillBorder(inv);

        WatcherBehaviorType current = settings.getBehaviorType();

        int[] slots = {10, 11, 12, 13, 14};
        WatcherBehaviorType[] types = WatcherBehaviorType.values();

        for (int i = 0; i < types.length; i++) {
            WatcherBehaviorType type = types[i];
            boolean isSelected = (type == current);

            ItemStack item = new ItemBuilder(isSelected ? Material.REPEATER : Material.REDSTONE_TORCH)
                    .name((isSelected ? "<green>✔ " : "<orange>") + type.getDisplayName())
                    .loreStrings(
                            "<gray>" + type.getDescription() + "</gray>",
                            "",
                            isSelected ? "<green>Текущий выбор</green>" : "<light_purple>▶ Нажмите для выбора</light_purple>"
                    )
                    .glow(isSelected)
                    .build();

            inv.setItem(slots[i], item);
        }

        inv.setItem(22, new ItemBuilder(Material.ARROW).name("<yellow>← Готово (В главное меню)</yellow>").build());

        admin.openInventory(inv);
    }

    public static void handleClick(WatcherPlugin plugin, Player admin, int slot, GuiHolder holder) {
        String targetName = holder.getTargetPlayerName();
        WatcherSpawnSettings settings = holder.getSettings();

        if (slot == 22) {
            MainMenu.open(plugin, admin, targetName, settings);
            return;
        }

        int[] slots = {10, 11, 12, 13, 14};
        WatcherBehaviorType[] types = WatcherBehaviorType.values();

        for (int i = 0; i < slots.length; i++) {
            if (slot == slots[i] && i < types.length) {
                settings.setBehaviorType(types[i]);
                open(plugin, admin, targetName, settings);
                return;
            }
        }
    }
}
