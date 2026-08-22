package net.myserver.modules.impl;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.item.ItemStack;
import net.myserver.modules.CrystallModule;

public class HatModule implements CrystallModule {
    private Command hatCmd;

    @Override
    public String getId() {
        return "hat";
    }

    @Override
    public String getName() {
        return "Hat";
    }

    @Override
    public String getDescription() {
        return "Надевание любого блока или предмета из руки на голову (/hat)";
    }

    @Override
    public void onEnable(GlobalEventHandler eventHandler) {
        hatCmd = new Command("hat", "head") {
            {
                setDefaultExecutor((sender, context) -> {
                    if (!(sender instanceof Player player)) return;

                    ItemStack inHand = player.getItemInMainHand();
                    if (inHand.isAir()) {
                        player.sendMessage(Component.text("Возьмите предмет в руку, чтобы надеть его на голову.", NamedTextColor.RED));
                        return;
                    }

                    ItemStack currentHelmet = player.getHelmet();
                    player.setHelmet(inHand);
                    player.setItemInMainHand(currentHelmet);

                    player.sendMessage(Component.text("🎩 Предмет успешно надет на голову!", NamedTextColor.GREEN));
                });
            }
        };

        MinecraftServer.getCommandManager().register(hatCmd);
    }

    @Override
    public void onDisable() {
        if (hatCmd != null) {
            MinecraftServer.getCommandManager().unregister(hatCmd);
        }
    }
}
