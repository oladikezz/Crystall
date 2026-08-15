package net.myserver.social;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

public class MsgCommand extends Command {
    public MsgCommand() {
        super("msg", "tell", "w");

        var targetArg = ArgumentType.Word("player");
        var messageArg = ArgumentType.StringArray("message");

        addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) return;
            
            String targetName = context.get(targetArg);
            String[] msgArray = context.get(messageArg);
            String msg = String.join(" ", msgArray);

            Player target = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(targetName);
            if (target != null) {
                target.sendMessage(Component.text("[PM] " + player.getUsername() + " -> Вам: " + msg, NamedTextColor.LIGHT_PURPLE));
                player.sendMessage(Component.text("[PM] Вы -> " + targetName + ": " + msg, NamedTextColor.LIGHT_PURPLE));
                
                ChatManager.lastMessageSenders.put(target.getUuid(), player.getUuid());
                ChatManager.lastMessageSenders.put(player.getUuid(), target.getUuid());
            } else {
                player.sendMessage(Component.text("Игрок не найден.", NamedTextColor.RED));
            }
        }, targetArg, messageArg);
    }
}
