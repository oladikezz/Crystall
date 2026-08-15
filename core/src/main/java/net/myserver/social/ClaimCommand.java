package net.myserver.social;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Player;

public class ClaimCommand extends Command {
    public ClaimCommand() {
        super("claim");

        setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) return;

            if (EconomyManager.removeBalance(player.getUuid(), 500)) {
                if (ClaimManager.claimChunk(player)) {
                    player.sendMessage(net.myserver.utils.LangManager.get(player, "claim.success", "500"));
                } else {
                    EconomyManager.addBalance(player.getUuid(), 500);
                    player.sendMessage(net.myserver.utils.LangManager.get(player, "claim.already_claimed"));
                }
            } else {
                player.sendMessage(net.myserver.utils.LangManager.get(player, "economy.not_enough", "500"));
            }
        });
    }
}
