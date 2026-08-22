package net.myserver.modules.impl;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.entity.EntityDeathEvent;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.myserver.modules.CrystallModule;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StatsModule implements CrystallModule {
    public static class PlayerStats {
        public int kills = 0;
        public int deaths = 0;
        public int blocksBroken = 0;
    }

    private static final Map<UUID, PlayerStats> statsMap = new ConcurrentHashMap<>();
    private Command statsCmd;

    @Override
    public String getId() {
        return "stats";
    }

    @Override
    public String getName() {
        return "Stats";
    }

    @Override
    public String getDescription() {
        return "Сбор и отображение игровой статистики игрока (/stats)";
    }

    @Override
    public void onEnable(GlobalEventHandler eventHandler) {
        statsCmd = new Command("stats", "statistics") {
            {
                setDefaultExecutor((sender, context) -> {
                    if (!(sender instanceof Player player)) return;

                    PlayerStats s = statsMap.computeIfAbsent(player.getUuid(), u -> new PlayerStats());
                    player.sendMessage(Component.text("══════ Статистика: " + player.getUsername() + " ══════", NamedTextColor.GOLD));
                    player.sendMessage(Component.text(" • Убийств: " + s.kills, NamedTextColor.GREEN));
                    player.sendMessage(Component.text(" • Смертей: " + s.deaths, NamedTextColor.RED));
                    player.sendMessage(Component.text(" • Сломано блоков: " + s.blocksBroken, NamedTextColor.AQUA));
                });
            }
        };

        MinecraftServer.getCommandManager().register(statsCmd);

        eventHandler.addListener(EntityDeathEvent.class, event -> {
            if (event.getEntity() instanceof Player player) {
                statsMap.computeIfAbsent(player.getUuid(), u -> new PlayerStats()).deaths++;
            }
        });

        eventHandler.addListener(PlayerBlockBreakEvent.class, event -> {
            statsMap.computeIfAbsent(event.getPlayer().getUuid(), u -> new PlayerStats()).blocksBroken++;
        });
    }

    @Override
    public void onDisable() {
        if (statsCmd != null) {
            MinecraftServer.getCommandManager().unregister(statsCmd);
        }
        statsMap.clear();
    }
}
