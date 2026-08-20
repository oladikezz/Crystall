package net.myserver.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.myserver.permissions.RoleManager;

/**
 * Ванильная команда управления временем (/time set <day|night|noon|midnight|ticks>, /time query).
 */
public class TimeCommand extends Command {
    public TimeCommand() {
        super("time");

        setCondition((sender, commandString) -> {
            if (sender instanceof Player player) {
                return RoleManager.isStaff(player);
            }
            return true;
        });

        var actionArg = ArgumentType.Word("action").from("set", "query", "add");
        var valueArg = ArgumentType.Word("value");

        addSyntax((sender, context) -> {
            String action = context.get(actionArg);
            String value = context.get(valueArg);

            Instance instance = null;
            if (sender instanceof Player player) {
                instance = player.getInstance();
            } else {
                instance = MinecraftServer.getInstanceManager().getInstances().stream().findFirst().orElse(null);
            }

            if (instance == null) {
                sender.sendMessage(Component.text("Мир не найден.", NamedTextColor.RED));
                return;
            }

            if ("set".equalsIgnoreCase(action)) {
                long targetTime = 0;
                switch (value.toLowerCase()) {
                    case "day" -> targetTime = 1000;
                    case "noon" -> targetTime = 6000;
                    case "night" -> targetTime = 13000;
                    case "midnight" -> targetTime = 18000;
                    default -> {
                        try {
                            targetTime = Long.parseLong(value);
                        } catch (NumberFormatException e) {
                            sender.sendMessage(Component.text("Некорректное значение времени: " + value, NamedTextColor.RED));
                            return;
                        }
                    }
                }

                instance.setTime(targetTime);
                sender.sendMessage(Component.text("Время установлено на: " + value + " (" + targetTime + " тиков)", NamedTextColor.GREEN));
            } else if ("query".equalsIgnoreCase(action)) {
                sender.sendMessage(Component.text("Текущее время: " + instance.getTime() + " тиков", NamedTextColor.YELLOW));
            }
        }, actionArg, valueArg);

        setDefaultExecutor((sender, context) -> {
            sender.sendMessage(Component.text("Использование: /time <set|query|add> <day|night|noon|midnight|число>", NamedTextColor.GRAY));
        });
    }
}
