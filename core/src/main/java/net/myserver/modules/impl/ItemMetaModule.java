package net.myserver.modules.impl;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.item.ItemStack;
import net.myserver.modules.CrystallModule;

public class ItemMetaModule implements CrystallModule {
    private Command renameCmd;

    @Override
    public String getId() {
        return "itemmeta";
    }

    @Override
    public String getName() {
        return "ItemMeta";
    }

    @Override
    public String getDescription() {
        return "Управление метаданными предметов: кастомные названия и описание (/rename)";
    }

    @Override
    public void onEnable(GlobalEventHandler eventHandler) {
        renameCmd = new Command("rename", "setname") {
            {
                var nameArg = ArgumentType.StringArray("new_name");

                addSyntax((sender, context) -> {
                    if (!(sender instanceof Player player)) return;

                    ItemStack inHand = player.getItemInMainHand();
                    if (inHand.isAir()) {
                        player.sendMessage(Component.text("Возьмите предмет в руку!", NamedTextColor.RED));
                        return;
                    }

                    String newName = String.join(" ", context.get(nameArg));
                    ItemStack renamed = inHand.withCustomName(Component.text(newName, NamedTextColor.GOLD));
                    player.setItemInMainHand(renamed);

                    player.sendMessage(Component.text("Название предмета изменено на: " + newName, NamedTextColor.GREEN));
                }, nameArg);
            }
        };

        MinecraftServer.getCommandManager().register(renameCmd);
    }

    @Override
    public void onDisable() {
        if (renameCmd != null) {
            MinecraftServer.getCommandManager().unregister(renameCmd);
        }
    }
}
