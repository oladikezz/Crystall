package net.myserver.modules.impl;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.myserver.modules.CrystallModule;
import net.myserver.permissions.RoleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class AdminListModule implements CrystallModule {
    private static final Logger log = LoggerFactory.getLogger(AdminListModule.class);
    private Command adminListCmd;

    @Override
    public String getId() {
        return "adminlist";
    }

    @Override
    public String getName() {
        return "AdminList";
    }

    @Override
    public String getDescription() {
        return "Список администрации онлайн и система оповещений персонала";
    }

    @Override
    public void onEnable(GlobalEventHandler eventHandler) {
        adminListCmd = new Command("adminlist", "admins", "staff") {
            {
                setDefaultExecutor((sender, context) -> {
                    List<Player> staffOnline = MinecraftServer.getConnectionManager().getOnlinePlayers().stream()
                            .filter(RoleManager::isStaff)
                            .collect(Collectors.toList());

                    if (staffOnline.isEmpty()) {
                        sender.sendMessage(Component.text("В данный момент администраторов нет в сети.", NamedTextColor.GRAY));
                        return;
                    }

                    Component header = Component.text("=== Администрация онлайн (" + staffOnline.size() + ") ===", NamedTextColor.GOLD);
                    sender.sendMessage(header);
                    for (Player p : staffOnline) {
                        String role = RoleManager.getRole(p.getUuid()).toUpperCase();
                        sender.sendMessage(Component.text(" • " + p.getUsername() + " ", NamedTextColor.YELLOW)
                                .append(Component.text("[" + role + "]", NamedTextColor.RED))
                                .append(Component.text(" (Ping: " + p.getLatency() + "ms)", NamedTextColor.GRAY)));
                    }
                });
            }
        };

        MinecraftServer.getCommandManager().register(adminListCmd);

        eventHandler.addListener(PlayerSpawnEvent.class, event -> {
            Player p = event.getPlayer();
            if (RoleManager.isStaff(p)) {
                log.info("[AdminList] Администратор {} ({}) зашел на сервер.", p.getUsername(), RoleManager.getRole(p.getUuid()));
            }
        });

        eventHandler.addListener(PlayerDisconnectEvent.class, event -> {
            Player p = event.getPlayer();
            if (RoleManager.isStaff(p)) {
                log.info("[AdminList] Администратор {} покинул сервер.", p.getUsername());
            }
        });
    }

    @Override
    public void onDisable() {
        if (adminListCmd != null) {
            MinecraftServer.getCommandManager().unregister(adminListCmd);
        }
    }
}
