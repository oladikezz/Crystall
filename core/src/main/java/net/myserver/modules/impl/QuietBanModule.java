package net.myserver.modules.impl;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerChatEvent;
import net.myserver.modules.CrystallModule;
import net.myserver.permissions.RoleManager;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class QuietBanModule implements CrystallModule {
    private static final Set<UUID> shadowMuted = ConcurrentHashMap.newKeySet();
    private Command quietBanCmd;

    @Override
    public String getId() {
        return "quietban";
    }

    @Override
    public String getName() {
        return "QuietBan";
    }

    @Override
    public String getDescription() {
        return "Теневой мут и тихие блокировки нарушителей правил (/quietban)";
    }

    @Override
    public void onEnable(GlobalEventHandler eventHandler) {
        quietBanCmd = new Command("quietban", "shadowmute", "smute") {
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
                        sender.sendMessage(Component.text("Игрок " + targetName + " не найден в сети.", NamedTextColor.RED));
                        return;
                    }

                    if (shadowMuted.contains(target.getUuid())) {
                        shadowMuted.remove(target.getUuid());
                        sender.sendMessage(Component.text("Теневой мут с игрока " + target.getUsername() + " снят.", NamedTextColor.GREEN));
                    } else {
                        shadowMuted.add(target.getUuid());
                        sender.sendMessage(Component.text("Игрок " + target.getUsername() + " получил теневой мут (он видит свои сообщения, остальные — нет).", NamedTextColor.YELLOW));
                    }
                }, playerArg);
            }
        };

        MinecraftServer.getCommandManager().register(quietBanCmd);

        eventHandler.addListener(PlayerChatEvent.class, event -> {
            Player p = event.getPlayer();
            if (shadowMuted.contains(p.getUuid())) {
                // Отменяем глобальную рассылку, отправляем сообщение только самому игроку
                event.setCancelled(true);
                p.sendMessage(Component.text("<" + p.getUsername() + "> " + event.getRawMessage()));
            }
        });
    }

    @Override
    public void onDisable() {
        if (quietBanCmd != null) {
            MinecraftServer.getCommandManager().unregister(quietBanCmd);
        }
        shadowMuted.clear();
    }
}
