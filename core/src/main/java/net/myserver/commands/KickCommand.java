package net.myserver.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;
import net.myserver.permissions.RoleManager;

public class KickCommand extends Command {
    public KickCommand() {
        super("kick");
        setCondition((sender, commandString) -> !(sender instanceof Player p) || RoleManager.isStaff(p));

        var playerArg = ArgumentType.Word("player");

        addSyntax((sender, context) -> {
            String targetName = context.get(playerArg);
            Player target = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(targetName);
            
            if (target != null) {
                target.kick(Component.text("Вы были кикнуты администратором.", NamedTextColor.RED));
                sender.sendMessage(Component.text("Игрок " + targetName + " кикнут.", NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("Игрок не найден онлайн.", NamedTextColor.RED));
            }
        }, playerArg);
    }
}
