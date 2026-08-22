package net.myserver.modules.impl;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.myserver.modules.CrystallModule;
import net.myserver.permissions.RoleManager;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VanishModule implements CrystallModule {
    private static final Set<UUID> vanishedPlayers = ConcurrentHashMap.newKeySet();
    private Command vanishCmd;

    @Override
    public String getId() {
        return "vanish";
    }

    @Override
    public String getName() {
        return "Vanish";
    }

    @Override
    public String getDescription() {
        return "Полная невидимость для администрации (/vanish, /v)";
    }

    @Override
    public void onEnable(GlobalEventHandler eventHandler) {
        vanishCmd = new Command("vanish", "v") {
            {
                setDefaultExecutor((sender, context) -> {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("Команда только для игроков.", NamedTextColor.RED));
                        return;
                    }

                    if (!RoleManager.isStaff(player)) {
                        player.sendMessage(Component.text("Недостаточно прав.", NamedTextColor.RED));
                        return;
                    }

                    UUID uuid = player.getUuid();
                    if (vanishedPlayers.contains(uuid)) {
                        vanishedPlayers.remove(uuid);
                        player.setInvisible(false);
                        player.setGlowing(false);
                        player.sendMessage(Component.text("Режим невидимости выключен (Vanish OFF).", NamedTextColor.YELLOW));
                    } else {
                        vanishedPlayers.add(uuid);
                        player.setInvisible(true);
                        player.setGlowing(true);
                        player.sendMessage(Component.text("Режим невидимости включен (Vanish ON). Вас никто не видит.", NamedTextColor.GREEN));
                    }
                });
            }
        };

        MinecraftServer.getCommandManager().register(vanishCmd);

        eventHandler.addListener(PlayerDisconnectEvent.class, event -> {
            vanishedPlayers.remove(event.getPlayer().getUuid());
        });
    }

    @Override
    public void onDisable() {
        if (vanishCmd != null) {
            MinecraftServer.getCommandManager().unregister(vanishCmd);
        }
        for (UUID uuid : vanishedPlayers) {
            Player p = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid);
            if (p != null) p.setInvisible(false);
        }
        vanishedPlayers.clear();
    }

    public static boolean isVanished(UUID uuid) {
        return vanishedPlayers.contains(uuid);
    }
}
