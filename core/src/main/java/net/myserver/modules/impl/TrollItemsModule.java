package net.myserver.modules.impl;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.myserver.modules.CrystallModule;
import net.myserver.permissions.RoleManager;

public class TrollItemsModule implements CrystallModule {
    private Command trollCmd;

    @Override
    public String getId() {
        return "trollitems";
    }

    @Override
    public String getName() {
        return "TrollItems";
    }

    @Override
    public String getDescription() {
        return "Предметы для ивентов, развлечений и троллинга (/trollitem)";
    }

    @Override
    public void onEnable(GlobalEventHandler eventHandler) {
        trollCmd = new Command("trollitem", "troll") {
            {
                setDefaultExecutor((sender, context) -> {
                    if (!(sender instanceof Player player)) return;
                    if (!RoleManager.isAdmin(player)) {
                        player.sendMessage(Component.text("Недостаточно прав.", NamedTextColor.RED));
                        return;
                    }

                    ItemStack stick = ItemStack.builder(Material.STICK)
                            .customName(Component.text("💥 Палочка Взрыва", NamedTextColor.RED))
                            .build();

                    player.getInventory().addItemStack(stick);
                    player.sendMessage(Component.text("Вам выдан тролль-предмет!", NamedTextColor.GOLD));
                });
            }
        };

        MinecraftServer.getCommandManager().register(trollCmd);
    }

    @Override
    public void onDisable() {
        if (trollCmd != null) {
            MinecraftServer.getCommandManager().unregister(trollCmd);
        }
    }
}
