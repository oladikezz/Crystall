package net.myserver.commands;

import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;
import net.myserver.permissions.BanManager;

public class BanCommand extends Command {
    public BanCommand() {
        super("ban");
        setCondition((sender, commandString) -> sender.hasPermission("command.ban"));

        var playerArg = ArgumentType.Word("player");

        addSyntax((sender, context) -> {
            String targetName = context.get(playerArg);
            Player target = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(targetName);
            
            if (target != null) {
                BanManager.ban(target.getUuid().toString(), "Забанен администратором.");
                target.kick(Component.text("Вы были забанены на сервере."));
                sender.sendMessage(Component.text("Игрок " + targetName + " забанен."));
            } else {
                sender.sendMessage(Component.text("Игрок не найден онлайн."));
            }
        }, playerArg);
    }
}
