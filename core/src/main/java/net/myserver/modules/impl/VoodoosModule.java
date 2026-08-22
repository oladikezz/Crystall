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

public class VoodoosModule implements CrystallModule {
    private Command voodooCmd;

    @Override
    public String getId() {
        return "voodoos";
    }

    @Override
    public String getName() {
        return "Voodoos";
    }

    @Override
    public String getDescription() {
        return "Куклы вуду и дистанционные магические механики (/voodoo)";
    }

    @Override
    public void onEnable(GlobalEventHandler eventHandler) {
        voodooCmd = new Command("voodoo") {
            {
                setDefaultExecutor((sender, context) -> {
                    if (!(sender instanceof Player player)) return;

                    ItemStack doll = ItemStack.builder(Material.TOTEM_OF_UNDYING)
                            .customName(Component.text("🧿 Кукла Вуду", NamedTextColor.DARK_PURPLE))
                            .build();

                    player.getInventory().addItemStack(doll);
                    player.sendMessage(Component.text("Вам выдана Кукла Вуду!", NamedTextColor.LIGHT_PURPLE));
                });
            }
        };

        MinecraftServer.getCommandManager().register(voodooCmd);
    }

    @Override
    public void onDisable() {
        if (voodooCmd != null) {
            MinecraftServer.getCommandManager().unregister(voodooCmd);
        }
    }
}
