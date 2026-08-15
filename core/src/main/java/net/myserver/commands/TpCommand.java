package net.myserver.commands;

import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

public class TpCommand extends Command {
    public TpCommand() {
        super("tp", "teleport");
        setCondition((sender, commandString) -> sender.hasPermission("command.tp"));

        var playerArg = ArgumentType.Word("player");

        addSyntax((sender, context) -> {
            if (sender instanceof Player player) {
                String targetName = context.get(playerArg);
                Player target = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(targetName);
                if (target != null) {
                    player.teleport(target.getPosition());
                    player.sendMessage(Component.text("Вы телепортированы к " + targetName));
                } else {
                    player.sendMessage(Component.text("Игрок не найден."));
                }
            }
        }, playerArg);
    }
}
