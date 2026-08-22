package net.myserver.modules.impl;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.myserver.modules.CrystallModule;
import net.myserver.permissions.RoleManager;

import java.util.ArrayList;
import java.util.List;

public class EssentialsModule implements CrystallModule {
    private final List<Command> registeredCommands = new ArrayList<>();

    @Override
    public String getId() {
        return "essentials";
    }

    @Override
    public String getName() {
        return "Essentials";
    }

    @Override
    public String getDescription() {
        return "Базовый набор серверных команд (/spawn, /heal, /feed, /fly, /speed)";
    }

    @Override
    public void onEnable(GlobalEventHandler eventHandler) {
        var cmdManager = MinecraftServer.getCommandManager();

        // /spawn
        Command spawnCmd = new Command("spawn") {
            {
                setDefaultExecutor((sender, context) -> {
                    if (sender instanceof Player p && p.getInstance() != null) {
                        p.teleport(new Pos(0.5, 100, 0.5));
                        p.sendMessage(Component.text("Телепортация на спавн...", NamedTextColor.GREEN));
                    }
                });
            }
        };
        cmdManager.register(spawnCmd);
        registeredCommands.add(spawnCmd);

        // /heal
        Command healCmd = new Command("heal") {
            {
                setDefaultExecutor((sender, context) -> {
                    if (sender instanceof Player p) {
                        if (!RoleManager.isStaff(p)) {
                            p.sendMessage(Component.text("Недостаточно прав.", NamedTextColor.RED));
                            return;
                        }
                        p.setHealth(20.0f);
                        p.setFood(20);
                        p.sendMessage(Component.text("Здоровье и сытость полностью восстановлены!", NamedTextColor.GREEN));
                    }
                });
            }
        };
        cmdManager.register(healCmd);
        registeredCommands.add(healCmd);

        // /feed
        Command feedCmd = new Command("feed") {
            {
                setDefaultExecutor((sender, context) -> {
                    if (sender instanceof Player p) {
                        p.setFood(20);
                        p.sendMessage(Component.text("Голод утолен!", NamedTextColor.GREEN));
                    }
                });
            }
        };
        cmdManager.register(feedCmd);
        registeredCommands.add(feedCmd);

        // /fly
        Command flyCmd = new Command("fly") {
            {
                setDefaultExecutor((sender, context) -> {
                    if (sender instanceof Player p) {
                        if (!RoleManager.isStaff(p)) {
                            p.sendMessage(Component.text("Недостаточно прав.", NamedTextColor.RED));
                            return;
                        }
                        boolean flying = !p.isFlying();
                        p.setFlying(flying);
                        p.sendMessage(Component.text("Режим полета: " + (flying ? "ВКЛЮЧЕН" : "ВЫКЛЮЧЕН"), flying ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
                    }
                });
            }
        };
        cmdManager.register(flyCmd);
        registeredCommands.add(flyCmd);
    }

    @Override
    public void onDisable() {
        var cmdManager = MinecraftServer.getCommandManager();
        for (Command cmd : registeredCommands) {
            cmdManager.unregister(cmd);
        }
        registeredCommands.clear();
    }
}
