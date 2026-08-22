package net.myserver.modules.impl;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.myserver.modules.CrystallModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccountsModule implements CrystallModule {
    private static final Logger log = LoggerFactory.getLogger(AccountsModule.class);
    private Command accountCmd;

    @Override
    public String getId() {
        return "accounts";
    }

    @Override
    public String getName() {
        return "Accounts";
    }

    @Override
    public String getDescription() {
        return "Управление учетными записями, привязкой Discord и профилями";
    }

    @Override
    public void onEnable(GlobalEventHandler eventHandler) {
        accountCmd = new Command("account", "link") {
            {
                setDefaultExecutor((sender, context) -> {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("Команда только для игроков.", NamedTextColor.RED));
                        return;
                    }

                    player.sendMessage(Component.text("══════ Ваш аккаунт Crystall ══════", NamedTextColor.GOLD));
                    player.sendMessage(Component.text(" • Никнейм: " + player.getUsername(), NamedTextColor.YELLOW));
                    player.sendMessage(Component.text(" • UUID: " + player.getUuid(), NamedTextColor.GRAY));
                    player.sendMessage(Component.text(" • Статус аккаунта: Активен / Авторизован", NamedTextColor.GREEN));
                });
            }
        };

        MinecraftServer.getCommandManager().register(accountCmd);
    }

    @Override
    public void onDisable() {
        if (accountCmd != null) {
            MinecraftServer.getCommandManager().unregister(accountCmd);
        }
    }
}
