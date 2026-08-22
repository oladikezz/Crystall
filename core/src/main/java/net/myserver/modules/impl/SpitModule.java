package net.myserver.modules.impl;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.sound.SoundEvent;
import net.myserver.modules.CrystallModule;

public class SpitModule implements CrystallModule {
    private Command spitCmd;

    @Override
    public String getId() {
        return "spit";
    }

    @Override
    public String getName() {
        return "Spit";
    }

    @Override
    public String getDescription() {
        return "Забавные социальные механики и анимации (/spit)";
    }

    @Override
    public void onEnable(GlobalEventHandler eventHandler) {
        spitCmd = new Command("spit", "plevok") {
            {
                setDefaultExecutor((sender, context) -> {
                    if (!(sender instanceof Player player)) return;

                    player.sendMessage(Component.text("💦 Вы плюнули!", NamedTextColor.AQUA));
                    if (player.getInstance() != null) {
                        player.getInstance().playSound(
                                net.kyori.adventure.sound.Sound.sound(SoundEvent.ENTITY_LLAMA_SPIT, net.kyori.adventure.sound.Sound.Source.PLAYER, 1.0f, 1.0f),
                                player.getPosition().x(), player.getPosition().y(), player.getPosition().z()
                        );
                    }
                });
            }
        };

        MinecraftServer.getCommandManager().register(spitCmd);
    }

    @Override
    public void onDisable() {
        if (spitCmd != null) {
            MinecraftServer.getCommandManager().unregister(spitCmd);
        }
    }
}
