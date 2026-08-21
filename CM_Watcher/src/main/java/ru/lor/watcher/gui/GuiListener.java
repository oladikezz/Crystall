package ru.lor.watcher.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import ru.lor.watcher.WatcherPlugin;
import ru.lor.watcher.utils.PermissionUtil;

public class GuiListener implements Listener {

    private final WatcherPlugin plugin;

    public GuiListener(WatcherPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getInventory().getHolder() instanceof GuiHolder holder) {
            event.setCancelled(true);

            if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) {
                return;
            }

            if (!PermissionUtil.requireAndClose(plugin, player, PermissionUtil.USE)) {
                return;
            }

            int slot = event.getSlot();
            switch (holder.getMenuType()) {
                case MAIN -> MainMenu.handleClick(plugin, player, slot, holder);
                case PLAYER_SELECT -> PlayerSelectMenu.handleClick(plugin, player, slot, holder);
                case CONTROL -> ControlMenu.handleClick(plugin, player, slot, holder, event.getClick());
                case POSITION -> PositionMenu.handleClick(plugin, player, slot, holder, event.getClick());
                case BEHAVIOR -> BehaviorMenu.handleClick(plugin, player, slot, holder);
                case EFFECTS -> EffectsMenu.handleClick(plugin, player, slot, holder, event.getClick());
                case EVENTS -> EventsMenu.handleClick(plugin, player, slot, holder, event.getClick());
                case LOGS -> LogsMenu.handleClick(plugin, player, slot, holder);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof GuiHolder) {
            event.setCancelled(true);
        }
    }
}
