package net.myserver.modules.impl;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.myserver.modules.CrystallModule;
import net.myserver.permissions.RoleManager;

import java.time.Duration;

public class AlertModule implements CrystallModule {
    private Command alertCmd;

    @Override
    public String getId() {
        return "alert";
    }

    @Override
    public String getName() {
        return "Alert";
    }

    @Override
    public String getDescription() {
        return "Система глобальных всплывающих оповещений на экране (/alert)";
    }

    @Override
    public void onEnable(GlobalEventHandler eventHandler) {
        alertCmd = new Command("alert", "broadcast", "bc") {
            {
                var messageArg = ArgumentType.StringArray("message");

                addSyntax((sender, context) -> {
                    if (sender instanceof Player p && !RoleManager.isStaff(p)) {
                        sender.sendMessage(Component.text("Недостаточно прав.", NamedTextColor.RED));
                        return;
                    }

                    String[] parts = context.get(messageArg);
                    String message = String.join(" ", parts);

                    Component alertComp = Component.text("[ОПОВЕЩЕНИЕ] ", NamedTextColor.RED)
                            .append(Component.text(message, NamedTextColor.YELLOW));

                    Title title = Title.title(
                            Component.text("ВНИМАНИЕ!", NamedTextColor.RED),
                            Component.text(message, NamedTextColor.GOLD),
                            Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofMillis(500))
                    );

                    for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                        player.sendMessage(alertComp);
                        player.showTitle(title);
                    }
                }, messageArg);
            }
        };

        MinecraftServer.getCommandManager().register(alertCmd);
    }

    @Override
    public void onDisable() {
        if (alertCmd != null) {
            MinecraftServer.getCommandManager().unregister(alertCmd);
        }
    }
}
