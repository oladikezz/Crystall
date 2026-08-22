package net.myserver.modules.impl;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.myserver.modules.CrystallModule;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StreamerModeModule implements CrystallModule {
    private static final Set<UUID> streamerPlayers = ConcurrentHashMap.newKeySet();
    private Command streamerCmd;

    @Override
    public String getId() {
        return "streamermode";
    }

    @Override
    public String getName() {
        return "StreamerMode";
    }

    @Override
    public String getDescription() {
        return "Режим стримера: защита от снайпинга, скрытие чата и координат (/streamermode)";
    }

    @Override
    public void onEnable(GlobalEventHandler eventHandler) {
        streamerCmd = new Command("streamermode", "streamer") {
            {
                setDefaultExecutor((sender, context) -> {
                    if (!(sender instanceof Player player)) return;

                    if (streamerPlayers.contains(player.getUuid())) {
                        streamerPlayers.remove(player.getUuid());
                        player.sendMessage(Component.text("🎬 Режим стримера выключен.", NamedTextColor.YELLOW));
                    } else {
                        streamerPlayers.add(player.getUuid());
                        player.sendMessage(Component.text("🎬 Режим стримера ВКЛЮЧЕН! Личные данные защищены.", NamedTextColor.GREEN));
                    }
                });
            }
        };

        MinecraftServer.getCommandManager().register(streamerCmd);
    }

    @Override
    public void onDisable() {
        if (streamerCmd != null) {
            MinecraftServer.getCommandManager().unregister(streamerCmd);
        }
        streamerPlayers.clear();
    }
}
