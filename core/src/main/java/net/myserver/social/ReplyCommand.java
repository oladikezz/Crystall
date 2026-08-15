package net.myserver.social;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

import java.util.UUID;

public class ReplyCommand extends Command {
    public ReplyCommand() {
        super("reply", "r");

        var messageArg = ArgumentType.StringArray("message");

        addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) return;

            String[] msgArray = context.get(messageArg);
            String msg = String.join(" ", msgArray);

            UUID targetUuid = ChatManager.lastMessageSenders.get(player.getUuid());
            if (targetUuid != null) {
                Player target = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(targetUuid);
                if (target != null) {
                    target.sendMessage(Component.text("[PM] " + player.getUsername() + " -> Вам: " + msg, NamedTextColor.LIGHT_PURPLE));
                    player.sendMessage(Component.text("[PM] Вы -> " + target.getUsername() + ": " + msg, NamedTextColor.LIGHT_PURPLE));
                    
                    ChatManager.lastMessageSenders.put(target.getUuid(), player.getUuid());
                } else {
                    player.sendMessage(Component.text("Собеседник оффлайн.", NamedTextColor.RED));
                }
            } else {
                player.sendMessage(Component.text("Вам некому отвечать.", NamedTextColor.RED));
            }
        }, messageArg);
    }
}
