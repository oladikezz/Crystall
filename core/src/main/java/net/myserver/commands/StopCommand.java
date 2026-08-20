package net.myserver.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Player;
import net.myserver.permissions.RoleManager;

/**
 * Ванильная команда корректной остановки сервера (/stop).
 */
public class StopCommand extends Command {
    public StopCommand() {
        super("stop");

        setCondition((sender, commandString) -> {
            if (sender instanceof Player player) {
                return RoleManager.isAdmin(player);
            }
            return true;
        });

        setDefaultExecutor((sender, context) -> {
            sender.sendMessage(Component.text("Остановка сервера...", NamedTextColor.RED));
            MinecraftServer.stopCleanly();
        });
    }
}
