package ru.lor.watcher.utils;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.lor.watcher.WatcherPlugin;

public final class PermissionUtil {

    public static final String USE = "watcher.use";
    public static final String SPAWN = "watcher.spawn";
    public static final String MESSAGE = "watcher.message";
    public static final String EVENTS = "watcher.events";
    public static final String ADMIN = "watcher.admin";

    private PermissionUtil() {
    }

    public static boolean has(CommandSender sender, String permission) {
        return sender != null && sender.hasPermission(permission);
    }

    public static boolean require(WatcherPlugin plugin, Player player, String permission) {
        if (has(player, permission)) {
            return true;
        }
        deny(plugin, player);
        return false;
    }

    public static boolean requireAndClose(WatcherPlugin plugin, Player player, String permission) {
        if (has(player, permission)) {
            return true;
        }
        player.closeInventory();
        deny(plugin, player);
        return false;
    }

    private static void deny(WatcherPlugin plugin, Player player) {
        player.sendMessage(ColorUtil.parse(plugin.getConfigManager().getMessage("no-permission")));
    }
}
