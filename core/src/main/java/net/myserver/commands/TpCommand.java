package net.myserver.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;
import net.myserver.permissions.RoleManager;

public class TpCommand extends Command {
    public TpCommand() {
        super("tp", "teleport");
        setCondition((sender, commandString) -> !(sender instanceof Player p) || RoleManager.isStaff(p));

        var playerArg = ArgumentType.Word("player");

        addSyntax((sender, context) -> {
            if (sender instanceof Player player) {
                String targetName = context.get(playerArg);
                Player target = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(targetName);
                if (target != null && target.getInstance() != null) {
                    if (player.getInstance() != target.getInstance()) {
                        player.setInstance(target.getInstance(), target.getPosition());
                    } else {
                        player.teleport(target.getPosition());
                    }
                    player.sendMessage(Component.text("Вы телепортированы к " + targetName, NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("Игрок не найден или оффлайн.", NamedTextColor.RED));
                }
            }
        }, playerArg);
    }
}
