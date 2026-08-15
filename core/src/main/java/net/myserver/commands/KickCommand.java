package net.myserver.commands;

import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

public class KickCommand extends Command {
    public KickCommand() {
        super("kick");
        setCondition((sender, commandString) -> sender.hasPermission("command.kick"));

        var playerArg = ArgumentType.Word("player");

        addSyntax((sender, context) -> {
            String targetName = context.get(playerArg);
            Player target = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(targetName);
            
            if (target != null) {
                target.kick(Component.text("Вы были кикнуты администратором."));
                sender.sendMessage(Component.text("Игрок " + targetName + " кикнут."));
            } else {
                sender.sendMessage(Component.text("Игрок не найден онлайн."));
            }
        }, playerArg);
    }
}
