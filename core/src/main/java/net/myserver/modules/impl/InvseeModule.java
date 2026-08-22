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

public class InvseeModule implements CrystallModule {
    private Command invseeCmd;

    @Override
    public String getId() {
        return "invsee";
    }

    @Override
    public String getName() {
        return "Invsee";
    }

    @Override
    public String getDescription() {
        return "Просмотр и редактирование инвентаря другого игрока (/invsee)";
    }

    @Override
    public void onEnable(GlobalEventHandler eventHandler) {
        invseeCmd = new Command("invsee", "viewinv") {
            {
                var targetArg = ArgumentType.Word("target");

                addSyntax((sender, context) -> {
                    if (!(sender instanceof Player player)) return;
                    if (!RoleManager.isStaff(player)) {
                        player.sendMessage(Component.text("Недостаточно прав.", NamedTextColor.RED));
                        return;
                    }

                    String targetName = context.get(targetArg);
                    Player target = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(targetName);
                    if (target == null) {
                        player.sendMessage(Component.text("Игрок " + targetName + " не найден.", NamedTextColor.RED));
                        return;
                    }

                    player.sendMessage(Component.text("Инвентарь игрока " + target.getUsername() + ":", NamedTextColor.GOLD));
                    for (int i = 0; i < 9; i++) {
                        var item = target.getInventory().getItemStack(i);
                        if (!item.isAir()) {
                            player.sendMessage(Component.text(" [Слот " + (i + 1) + "] " + item.material().name() + " x" + item.amount(), NamedTextColor.YELLOW));
                        }
                    }
                }, targetArg);
            }
        };

        MinecraftServer.getCommandManager().register(invseeCmd);
    }

    @Override
    public void onDisable() {
        if (invseeCmd != null) {
            MinecraftServer.getCommandManager().unregister(invseeCmd);
        }
    }
}
