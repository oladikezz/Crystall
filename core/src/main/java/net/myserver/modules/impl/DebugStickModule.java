package net.myserver.modules.impl;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.myserver.modules.CrystallModule;
import net.myserver.permissions.RoleManager;

public class DebugStickModule implements CrystallModule {
    private Command debugStickCmd;

    @Override
    public String getId() {
        return "debugstick";
    }

    @Override
    public String getName() {
        return "DebugStick";
    }

    @Override
    public String getDescription() {
        return "Инструмент отладки и изменения свойств блоков (/debugstick)";
    }

    @Override
    public void onEnable(GlobalEventHandler eventHandler) {
        debugStickCmd = new Command("debugstick") {
            {
                setDefaultExecutor((sender, context) -> {
                    if (!(sender instanceof Player player)) return;
                    if (!RoleManager.isAdmin(player)) {
                        player.sendMessage(Component.text("Недостаточно прав.", NamedTextColor.RED));
                        return;
                    }

                    ItemStack stick = ItemStack.builder(Material.DEBUG_STICK)
                            .customName(Component.text("Crystall Debug Stick", NamedTextColor.LIGHT_PURPLE))
                            .build();

                    player.getInventory().addItemStack(stick);
                    player.sendMessage(Component.text("Вам выдан Debug Stick для настройки блоков.", NamedTextColor.GREEN));
                });
            }
        };

        MinecraftServer.getCommandManager().register(debugStickCmd);

        eventHandler.addListener(PlayerBlockInteractEvent.class, event -> {
            Player player = event.getPlayer();
            if (player.getItemInMainHand().material() == Material.DEBUG_STICK && RoleManager.isAdmin(player)) {
                event.setCancelled(true);
                player.sendMessage(Component.text("Блок: " + event.getBlock().name(), NamedTextColor.YELLOW));
            }
        });
    }

    @Override
    public void onDisable() {
        if (debugStickCmd != null) {
            MinecraftServer.getCommandManager().unregister(debugStickCmd);
        }
    }
}
