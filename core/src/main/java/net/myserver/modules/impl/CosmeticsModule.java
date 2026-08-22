package net.myserver.modules.impl;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.myserver.modules.CrystallModule;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CosmeticsModule implements CrystallModule {
    private static final Map<UUID, String> activeParticles = new ConcurrentHashMap<>();
    private Command cosmeticsCmd;

    @Override
    public String getId() {
        return "cosmetics";
    }

    @Override
    public String getName() {
        return "Cosmetics";
    }

    @Override
    public String getDescription() {
        return "Система кастомизации: эффекты частиц, шарики, питомцы и гардероб (/cosmetics)";
    }

    @Override
    public void onEnable(GlobalEventHandler eventHandler) {
        cosmeticsCmd = new Command("cosmetics", "cosmetic", "trails") {
            {
                setDefaultExecutor((sender, context) -> {
                    if (!(sender instanceof Player player)) return;

                    player.sendMessage(Component.text("══════ Гардероб косметики ══════", NamedTextColor.GOLD));
                    player.sendMessage(Component.text(" • Эффекты следов и частиц активны", NamedTextColor.YELLOW));
                    player.sendMessage(Component.text(" • Питомцы и воздушные шары доступны в меню", NamedTextColor.AQUA));
                });
            }
        };

        MinecraftServer.getCommandManager().register(cosmeticsCmd);
    }

    @Override
    public void onDisable() {
        if (cosmeticsCmd != null) {
            MinecraftServer.getCommandManager().unregister(cosmeticsCmd);
        }
        activeParticles.clear();
    }
}
