package net.myserver.social;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

public class PayCommand extends Command {
    public PayCommand() {
        super("pay");

        var targetArg = ArgumentType.Word("player");
        var amountArg = ArgumentType.Double("amount");

        addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) return;

            String targetName = context.get(targetArg);
            double amount = context.get(amountArg);

            if (amount <= 0) {
                player.sendMessage(Component.text("Сумма должна быть больше 0.", NamedTextColor.RED));
                return;
            }

            Player target = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(targetName);
            if (target != null) {
                if (target.getUuid().equals(player.getUuid())) {
                    player.sendMessage(Component.text("Вы не можете перевести деньги себе.", NamedTextColor.RED));
                    return;
                }

                if (EconomyManager.removeBalance(player.getUuid(), amount)) {
                    EconomyManager.addBalance(target.getUuid(), amount);
                    player.sendMessage(Component.text("Вы перевели " + amount + " монет " + targetName, NamedTextColor.GREEN));
                    target.sendMessage(Component.text("Игрок " + player.getUsername() + " перевел вам " + amount + " монет.", NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("Недостаточно средств.", NamedTextColor.RED));
                }
            } else {
                player.sendMessage(Component.text("Игрок не найден.", NamedTextColor.RED));
            }
        }, targetArg, amountArg);
    }
}
