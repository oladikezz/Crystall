package net.myserver.social;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

public class ClanCommand extends Command {
    public ClanCommand() {
        super("clan");

        var actionArg = ArgumentType.Word("action").from("create", "join");
        var nameArg = ArgumentType.Word("name");

        addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) return;

            String action = context.get(actionArg);
            String name = context.get(nameArg);

            if (action.equals("create")) {
                if (EconomyManager.removeBalance(player.getUuid(), 1000)) {
                    if (ClanManager.createClan(player.getUuid(), name)) {
                        player.sendMessage(Component.text("Клан " + name + " успешно создан!", NamedTextColor.GREEN));
                    } else {
                        EconomyManager.addBalance(player.getUuid(), 1000);
                        player.sendMessage(Component.text("Вы уже состоите в клане или такое имя занято.", NamedTextColor.RED));
                    }
                } else {
                    player.sendMessage(Component.text("Создание клана стоит 1000 монет.", NamedTextColor.RED));
                }
            } else if (action.equals("join")) {
                if (ClanManager.getClan(player.getUuid()) != null) {
                    player.sendMessage(Component.text("Вы уже состоите в клане.", NamedTextColor.RED));
                    return;
                }
                ClanManager.addMember(player.getUuid(), name);
                player.sendMessage(Component.text("Вы присоединились к клану " + name, NamedTextColor.GREEN));
            }
        }, actionArg, nameArg);
    }
}
