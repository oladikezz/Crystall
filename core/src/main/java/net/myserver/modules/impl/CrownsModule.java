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

public class CrownsModule implements CrystallModule {
    private static final Set<UUID> activeCrowns = ConcurrentHashMap.newKeySet();
    private Command crownCmd;

    @Override
    public String getId() {
        return "crowns";
    }

    @Override
    public String getName() {
        return "Crowns";
    }

    @Override
    public String getDescription() {
        return "Визуальные короны и титулы игроков (/crown)";
    }

    @Override
    public void onEnable(GlobalEventHandler eventHandler) {
        crownCmd = new Command("crown", "crowns") {
            {
                setDefaultExecutor((sender, context) -> {
                    if (!(sender instanceof Player player)) return;

                    if (activeCrowns.contains(player.getUuid())) {
                        activeCrowns.remove(player.getUuid());
                        player.sendMessage(Component.text("👑 Корона снята.", NamedTextColor.YELLOW));
                    } else {
                        activeCrowns.add(player.getUuid());
                        player.sendMessage(Component.text("👑 Корона надета! Над вашей головой сияет корона.", NamedTextColor.GOLD));
                    }
                });
            }
        };

        MinecraftServer.getCommandManager().register(crownCmd);
    }

    @Override
    public void onDisable() {
        if (crownCmd != null) {
            MinecraftServer.getCommandManager().unregister(crownCmd);
        }
        activeCrowns.clear();
    }
}
