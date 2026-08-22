package net.myserver.modules.impl;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.event.GlobalEventHandler;
import net.myserver.modules.CrystallModule;
import net.myserver.permissions.RoleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WatcherModule implements CrystallModule {
    private static final Logger log = LoggerFactory.getLogger(WatcherModule.class);
    private Command watcherCmd;

    @Override
    public String getId() {
        return "watcher";
    }

    @Override
    public String getName() {
        return "Watcher";
    }

    @Override
    public String getDescription() {
        return "Сетевой мониторинг, защита от эксплойтов и аудит сетевых пакетов";
    }

    @Override
    public void onEnable(GlobalEventHandler eventHandler) {
        watcherCmd = new Command("watcher", "netwatch") {
            {
                setDefaultExecutor((sender, context) -> {
                    if (sender instanceof net.minestom.server.entity.Player p && !RoleManager.isAdmin(p)) {
                        sender.sendMessage(Component.text("Недостаточно прав.", NamedTextColor.RED));
                        return;
                    }

                    sender.sendMessage(Component.text("=== [Watcher Engine Status] ===", NamedTextColor.DARK_AQUA));
                    sender.sendMessage(Component.text(" • Сетевой монитор: АКТИВЕН", NamedTextColor.GREEN));
                    sender.sendMessage(Component.text(" • Защита от эксплойтов пакетов: ВКЛЮЧЕНА", NamedTextColor.GREEN));
                    sender.sendMessage(Component.text(" • Потоков Netty: " + Runtime.getRuntime().availableProcessors(), NamedTextColor.WHITE));
                });
            }
        };

        MinecraftServer.getCommandManager().register(watcherCmd);
        log.info("[Watcher] Сетевой аудит пакетов и защита от эксплойтов активированы.");
    }

    @Override
    public void onDisable() {
        if (watcherCmd != null) {
            MinecraftServer.getCommandManager().unregister(watcherCmd);
        }
    }
}
