package net.myserver.modules.impl;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.myserver.modules.CrystallModule;
import net.myserver.permissions.RoleManager;

public class UserInfoModule implements CrystallModule {
    private Command userInfoCmd;

    @Override
    public String getId() {
        return "userinfo";
    }

    @Override
    public String getName() {
        return "UserInfo";
    }

    @Override
    public String getDescription() {
        return "Просмотр детальной информации о профиле игрока (/userinfo)";
    }

    @Override
    public void onEnable(GlobalEventHandler eventHandler) {
        userInfoCmd = new Command("userinfo", "whois", "playerinfo") {
            {
                var playerArg = ArgumentType.Word("target");

                addSyntax((sender, context) -> {
                    if (sender instanceof Player p && !RoleManager.isStaff(p)) {
                        sender.sendMessage(Component.text("Недостаточно прав.", NamedTextColor.RED));
                        return;
                    }

                    String targetName = context.get(playerArg);
                    Player target = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(targetName);
                    if (target == null) {
                        sender.sendMessage(Component.text("Игрок не найден в сети.", NamedTextColor.RED));
                        return;
                    }

                    sender.sendMessage(Component.text("══════ Профиль: " + target.getUsername() + " ══════", NamedTextColor.GOLD));
                    sender.sendMessage(Component.text(" • UUID: " + target.getUuid(), NamedTextColor.GRAY));
                    sender.sendMessage(Component.text(" • Роль: " + RoleManager.getRole(target.getUuid()).toUpperCase(), NamedTextColor.YELLOW));
                    sender.sendMessage(Component.text(" • Режим игры: " + target.getGameMode().name(), NamedTextColor.AQUA));
                    sender.sendMessage(Component.text(" • Здоровье: " + String.format("%.1f", target.getHealth()) + " HP", NamedTextColor.RED));
                    sender.sendMessage(Component.text(" • Голод: " + target.getFood() + " / 20", NamedTextColor.GOLD));
                    sender.sendMessage(Component.text(" • Пинг: " + target.getLatency() + " ms", NamedTextColor.GREEN));
                    sender.sendMessage(Component.text(" • Координаты: " + String.format("%.1f, %.1f, %.1f", target.getPosition().x(), target.getPosition().y(), target.getPosition().z()), NamedTextColor.WHITE));
                }, playerArg);
            }
        };

        MinecraftServer.getCommandManager().register(userInfoCmd);
    }

    @Override
    public void onDisable() {
        if (userInfoCmd != null) {
            MinecraftServer.getCommandManager().unregister(userInfoCmd);
        }
    }
}
