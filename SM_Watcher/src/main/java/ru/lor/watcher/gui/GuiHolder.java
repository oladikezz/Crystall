package ru.lor.watcher.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import ru.lor.watcher.model.WatcherSpawnSettings;

public class GuiHolder implements InventoryHolder {

    public enum MenuType {
        MAIN,
        PLAYER_SELECT,
        CONTROL,
        POSITION,
        BEHAVIOR,
        EFFECTS,
        EVENTS,
        LOGS
    }

    private final MenuType menuType;
    private final String targetPlayerName;
    private final WatcherSpawnSettings settings;
    private final int page;
    private Inventory inventory;

    public GuiHolder(MenuType menuType, String targetPlayerName, WatcherSpawnSettings settings, int page) {
        this.menuType = menuType;
        this.targetPlayerName = targetPlayerName;
        this.settings = settings;
        this.page = page;
    }

    public GuiHolder(MenuType menuType, String targetPlayerName, WatcherSpawnSettings settings) {
        this(menuType, targetPlayerName, settings, 0);
    }

    public GuiHolder(MenuType menuType) {
        this(menuType, null, null, 0);
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public MenuType getMenuType() {
        return menuType;
    }

    public String getTargetPlayerName() {
        return targetPlayerName;
    }

    public WatcherSpawnSettings getSettings() {
        return settings;
    }

    public int getPage() {
        return page;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public static void fillBorder(org.bukkit.inventory.Inventory inv) {
        org.bukkit.inventory.ItemStack border = new ru.lor.watcher.utils.ItemBuilder(org.bukkit.Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < inv.getSize(); i++) {
            if (i < 9 || i >= inv.getSize() - 9 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, border);
            }
        }
    }
}
