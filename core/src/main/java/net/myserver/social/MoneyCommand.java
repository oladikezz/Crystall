package net.myserver.social;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Player;

public class MoneyCommand extends Command {
    public MoneyCommand() {
        super("money", "balance", "bal");

        setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) return;
            double bal = EconomyManager.getBalance(player.getUuid());
            player.sendMessage(net.myserver.utils.LangManager.get(player, "economy.balance", String.format("%.2f", bal)));
        });
    }
}
