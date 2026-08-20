package net.myserver.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;
import net.myserver.mechanics.WeatherTimeSystem;
import net.myserver.permissions.RoleManager;

/**
 * Ванильная команда управления погодой (/weather <clear|rain|thunder>).
 */
public class WeatherCommand extends Command {
    public WeatherCommand() {
        super("weather");

        setCondition((sender, commandString) -> {
            if (sender instanceof Player player) {
                return RoleManager.isStaff(player);
            }
            return true;
        });

        var typeArg = ArgumentType.Word("type").from("clear", "rain", "thunder");

        addSyntax((sender, context) -> {
            String type = context.get(typeArg);

            switch (type.toLowerCase()) {
                case "clear" -> {
                    WeatherTimeSystem.isRaining = false;
                    WeatherTimeSystem.isThundering = false;
                    sender.sendMessage(Component.text("Погода изменена на: Ясно", NamedTextColor.GREEN));
                }
                case "rain" -> {
                    WeatherTimeSystem.isRaining = true;
                    WeatherTimeSystem.isThundering = false;
                    sender.sendMessage(Component.text("Погода изменена на: Дождь", NamedTextColor.AQUA));
                }
                case "thunder" -> {
                    WeatherTimeSystem.isRaining = true;
                    WeatherTimeSystem.isThundering = true;
                    sender.sendMessage(Component.text("Погода изменена на: Гроза", NamedTextColor.GOLD));
                }
            }
        }, typeArg);

        setDefaultExecutor((sender, context) -> {
            sender.sendMessage(Component.text("Использование: /weather <clear|rain|thunder>", NamedTextColor.GRAY));
        });
    }
}
