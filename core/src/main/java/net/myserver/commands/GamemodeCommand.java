package net.myserver.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.myserver.permissions.RoleManager;

public class GamemodeCommand extends Command {
    public GamemodeCommand() {
        super("gamemode", "gm");
        setCondition((sender, commandString) -> !(sender instanceof Player p) || RoleManager.isAdmin(p));

        var modeArg = ArgumentType.Word("mode").from("survival", "creative", "spectator", "adventure");

        addSyntax((sender, context) -> {
            if (sender instanceof Player player) {
                String mode = context.get(modeArg);
                player.setGameMode(GameMode.valueOf(mode.toUpperCase()));
                player.sendMessage(Component.text("Ваш режим игры изменен на " + mode, NamedTextColor.GREEN));
            }
        }, modeArg);
    }
}
