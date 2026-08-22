package net.myserver.modules.impl;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.event.GlobalEventHandler;
import net.myserver.modules.CrystallModule;

public class HelpModule implements CrystallModule {
    private Command helpCmd;

    @Override
    public String getId() {
        return "help";
    }

    @Override
    public String getName() {
        return "Help";
    }

    @Override
    public String getDescription() {
        return "Интерактивная справка и меню помощи для игроков (/help)";
    }

    @Override
    public void onEnable(GlobalEventHandler eventHandler) {
        helpCmd = new Command("help", "?", "commands") {
            {
                setDefaultExecutor((sender, context) -> {
                    sender.sendMessage(Component.text("═════════ Помощь по серверу Crystall ═════════", NamedTextColor.GOLD));
                    sender.sendMessage(Component.text(" • /spawn - телепортация на главный спавн", NamedTextColor.YELLOW));
                    sender.sendMessage(Component.text(" • /clan - меню кланов и клановых взаимодействий", NamedTextColor.YELLOW));
                    sender.sendMessage(Component.text(" • /marry - система свадеб и партнерства", NamedTextColor.YELLOW));
                    sender.sendMessage(Component.text(" • /stats - статистика убийств, смертей и блоков", NamedTextColor.YELLOW));
                    sender.sendMessage(Component.text(" • /hat - надеть блок из руки на голову", NamedTextColor.YELLOW));
                    sender.sendMessage(Component.text(" • /crown - переключить визуальную корону", NamedTextColor.YELLOW));
                    sender.sendMessage(Component.text(" • /adminlist - список администрации онлайн", NamedTextColor.YELLOW));
                    sender.sendMessage(Component.text("═══════════════════════════════════════════════", NamedTextColor.GOLD));
                });
            }
        };

        MinecraftServer.getCommandManager().register(helpCmd);
    }

    @Override
    public void onDisable() {
        if (helpCmd != null) {
            MinecraftServer.getCommandManager().unregister(helpCmd);
        }
    }
}
