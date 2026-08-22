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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CheckerModule implements CrystallModule {
    private static final Map<UUID, Long> checkingPlayers = new ConcurrentHashMap<>();
    private Command checkCmd;

    @Override
    public String getId() {
        return "checker";
    }

    @Override
    public String getName() {
        return "Checker";
    }

    @Override
    public String getDescription() {
        return "Система вызова игроков на проверку читов (/check)";
    }

    @Override
    public void onEnable(GlobalEventHandler eventHandler) {
        checkCmd = new Command("check", "verify") {
            {
                var playerArg = ArgumentType.Word("target");

                addSyntax((sender, context) -> {
                    if (sender instanceof Player p && !RoleManager.isStaff(p)) {
                        sender.sendMessage(Component.text("У вас нет прав для этой команды.", NamedTextColor.RED));
                        return;
                    }

                    String targetName = context.get(playerArg);
                    Player target = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(targetName);

                    if (target == null) {
                        sender.sendMessage(Component.text("Игрок " + targetName + " не найден в сети.", NamedTextColor.RED));
                        return;
                    }

                    if (checkingPlayers.containsKey(target.getUuid())) {
                        checkingPlayers.remove(target.getUuid());
                        target.sendMessage(Component.text("Вы успешно прошли проверку на сторонний софт. Приятной игры!", NamedTextColor.GREEN));
                        sender.sendMessage(Component.text("Проверка игрока " + target.getUsername() + " завершена.", NamedTextColor.YELLOW));
                    } else {
                        checkingPlayers.put(target.getUuid(), System.currentTimeMillis());
                        target.sendMessage(Component.text("═══════════════════════════════════════════", NamedTextColor.RED));
                        target.sendMessage(Component.text(" ВЫ ВЫЗВАНЫ НА ПРОВЕРКУ ЧИТОВ / СОФТА!", NamedTextColor.GOLD));
                        target.sendMessage(Component.text(" Предоставьте AnyDesk / Discord модератору.", NamedTextColor.YELLOW));
                        target.sendMessage(Component.text(" Выход с сервера = АВТОМАТИЧЕСКИЙ БАН!", NamedTextColor.RED));
                        target.sendMessage(Component.text("═══════════════════════════════════════════", NamedTextColor.RED));
                        sender.sendMessage(Component.text("Игрок " + target.getUsername() + " вызван на проверку.", NamedTextColor.GREEN));
                    }
                }, playerArg);
            }
        };

        MinecraftServer.getCommandManager().register(checkCmd);
    }

    @Override
    public void onDisable() {
        if (checkCmd != null) {
            MinecraftServer.getCommandManager().unregister(checkCmd);
        }
        checkingPlayers.clear();
    }
}
