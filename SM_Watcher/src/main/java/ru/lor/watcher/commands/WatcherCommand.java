package ru.lor.watcher.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.lor.watcher.WatcherPlugin;
import ru.lor.watcher.events.WatcherDespawnEvent;
import ru.lor.watcher.gui.MainMenu;
import ru.lor.watcher.model.WatcherSpawnSettings;
import ru.lor.watcher.utils.ColorUtil;
import ru.lor.watcher.utils.PermissionUtil;

import java.util.Locale;

public class WatcherCommand {

    private static final int MAX_BROADCAST_LENGTH = 256;

    private final WatcherPlugin plugin;

    public WatcherCommand(WatcherPlugin plugin) {
        this.plugin = plugin;
    }

    public void handle(CommandSender sender, String[] args) {
        if (args.length == 0) {
            handleOpen(sender, args);
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "open" -> handleOpen(sender, args);
            case "spawn" -> handleSpawn(sender, args);
            case "despawn" -> handleDespawn(sender, args);
            case "message" -> handleMessage(sender, args);
            case "reload" -> handleReload(sender);
            default -> sender.sendMessage(ColorUtil.parse(
                    "<red>Неизвестная подкоманда! Используйте: /watcher [open|spawn|despawn|message|reload]</red>"));
        }
    }

    private void handleOpen(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtil.parse(plugin.getConfigManager().getMessage("only-players")));
            return;
        }
        if (!hasPermission(player, PermissionUtil.USE)) {
            return;
        }
        String targetName = args.length > 1 ? args[1] : player.getName();
        MainMenu.open(plugin, player, targetName, new WatcherSpawnSettings());
    }

    private void handleSpawn(CommandSender sender, String[] args) {
        if (!hasPermission(sender, PermissionUtil.SPAWN)) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ColorUtil.parse("<red>Использование: /watcher spawn <игрок></red>"));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ColorUtil.parse(plugin.getConfigManager().getMessage("player-not-found")));
            return;
        }

        final Player spawnTarget = target;
        spawnTarget.getScheduler().run(plugin.getBukkitPlugin(), task -> {
            if (!spawnTarget.isOnline()) {
                return;
            }
            boolean ok = plugin.getWatcherManager()
                    .spawnWatcher(spawnTarget, new WatcherSpawnSettings(), sender.getName());
            String key = ok ? "spawn-success" : "spawn-already-exists";
            sender.sendMessage(ColorUtil.parse(plugin.getConfigManager().getMessage(key)
                    .replace("{player}", spawnTarget.getName())));
        }, null);
    }

    private void handleDespawn(CommandSender sender, String[] args) {
        if (!hasPermission(sender, PermissionUtil.SPAWN)) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ColorUtil.parse("<red>Использование: /watcher despawn <игрок></red>"));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ColorUtil.parse(plugin.getConfigManager().getMessage("player-not-found")));
            return;
        }
        if (!plugin.getWatcherManager().hasWatcher(target)) {
            sender.sendMessage(ColorUtil.parse(plugin.getConfigManager().getMessage("no-active-watcher")
                    .replace("{player}", target.getName())));
            return;
        }

        final Player despawnTarget = target;
        despawnTarget.getScheduler().run(plugin.getBukkitPlugin(), task -> {
            plugin.getWatcherManager().despawnWatcher(despawnTarget.getUniqueId(),
                    WatcherDespawnEvent.DespawnReason.MANUAL_DESPAWN);
            sender.sendMessage(ColorUtil.parse(plugin.getConfigManager().getMessage("despawn-success")
                    .replace("{player}", despawnTarget.getName())));
        }, null);
    }

    private void handleMessage(CommandSender sender, String[] args) {
        if (!hasPermission(sender, PermissionUtil.MESSAGE)) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ColorUtil.parse("<red>Использование: /watcher message <текст></red>"));
            return;
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            builder.append(args[i]).append(' ');
        }

        String message = builder.toString().trim();
        if (message.length() > MAX_BROADCAST_LENGTH) {
            message = message.substring(0, MAX_BROADCAST_LENGTH);
        }

        String format = plugin.getConfigManager().getBroadcastFormat();
        Bukkit.broadcast(ColorUtil.parse(format.replace("{message}", ColorUtil.escape(message))));
    }

    private void handleReload(CommandSender sender) {
        if (!hasPermission(sender, PermissionUtil.ADMIN)) {
            return;
        }
        plugin.reloadConfig();
        sender.sendMessage(ColorUtil.parse(plugin.getConfigManager().getMessage("config-reloaded")));
    }

    private boolean hasPermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        sender.sendMessage(ColorUtil.parse(plugin.getConfigManager().getMessage("no-permission")));
        return false;
    }
}
