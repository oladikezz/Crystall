package net.myserver.modules.impl;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.myserver.modules.CrystallModule;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClansModule implements CrystallModule {
    public static class Clan {
        private final String name;
        private final UUID leader;
        private final Set<UUID> members = ConcurrentHashMap.newKeySet();

        public Clan(String name, UUID leader) {
            this.name = name;
            this.leader = leader;
            this.members.add(leader);
        }

        public String getName() { return name; }
        public UUID getLeader() { return leader; }
        public Set<UUID> getMembers() { return members; }
    }

    private static final Map<String, Clan> clansByName = new ConcurrentHashMap<>();
    private static final Map<UUID, String> playerClanMap = new ConcurrentHashMap<>();
    private Command clanCmd;

    @Override
    public String getId() {
        return "clans";
    }

    @Override
    public String getName() {
        return "Clans";
    }

    @Override
    public String getDescription() {
        return "Клановая система: создание кланов, состав, приглашения и клан-чат";
    }

    @Override
    public void onEnable(GlobalEventHandler eventHandler) {
        clanCmd = new Command("clan", "clans", "c") {
            {
                var actionArg = ArgumentType.Word("action");
                var nameArg = ArgumentType.Word("target_name");

                setDefaultExecutor((sender, context) -> {
                    sender.sendMessage(Component.text("══════ Кланы Crystall ══════", NamedTextColor.GOLD));
                    sender.sendMessage(Component.text(" • /clan create <название> - создать клан", NamedTextColor.YELLOW));
                    sender.sendMessage(Component.text(" • /clan leave - покинуть клан", NamedTextColor.YELLOW));
                    sender.sendMessage(Component.text(" • /clan info - информация о вашем клане", NamedTextColor.YELLOW));
                    sender.sendMessage(Component.text(" • /clan list - список всех кланов", NamedTextColor.YELLOW));
                });

                addSyntax((sender, context) -> {
                    if (!(sender instanceof Player player)) return;
                    String action = context.get(actionArg).toLowerCase();

                    if ("info".equals(action)) {
                        String clanName = playerClanMap.get(player.getUuid());
                        if (clanName == null) {
                            player.sendMessage(Component.text("Вы не состоите в клане.", NamedTextColor.RED));
                            return;
                        }
                        Clan clan = clansByName.get(clanName.toLowerCase());
                        if (clan == null) return;

                        player.sendMessage(Component.text("════ Клан [" + clan.getName() + "] ════", NamedTextColor.GOLD));
                        player.sendMessage(Component.text(" • Участников: " + clan.getMembers().size(), NamedTextColor.YELLOW));
                    } else if ("list".equals(action)) {
                        if (clansByName.isEmpty()) {
                            player.sendMessage(Component.text("На сервере пока нет созданных кланов.", NamedTextColor.GRAY));
                            return;
                        }
                        player.sendMessage(Component.text("=== Список кланов (" + clansByName.size() + ") ===", NamedTextColor.GOLD));
                        for (Clan clan : clansByName.values()) {
                            player.sendMessage(Component.text(" • [" + clan.getName() + "] (" + clan.getMembers().size() + " игроков)", NamedTextColor.YELLOW));
                        }
                    } else if ("leave".equals(action)) {
                        String clanName = playerClanMap.remove(player.getUuid());
                        if (clanName != null) {
                            Clan clan = clansByName.get(clanName.toLowerCase());
                            if (clan != null) clan.getMembers().remove(player.getUuid());
                            player.sendMessage(Component.text("Вы вышли из клана " + clanName, NamedTextColor.YELLOW));
                        } else {
                            player.sendMessage(Component.text("Вы не состоите в клане.", NamedTextColor.RED));
                        }
                    }
                }, actionArg);

                addSyntax((sender, context) -> {
                    if (!(sender instanceof Player player)) return;
                    String action = context.get(actionArg).toLowerCase();
                    String name = context.get(nameArg);

                    if ("create".equals(action)) {
                        if (playerClanMap.containsKey(player.getUuid())) {
                            player.sendMessage(Component.text("Вы уже состоите в клане!", NamedTextColor.RED));
                            return;
                        }
                        if (clansByName.containsKey(name.toLowerCase())) {
                            player.sendMessage(Component.text("Клан с таким именем уже существует!", NamedTextColor.RED));
                            return;
                        }

                        Clan clan = new Clan(name, player.getUuid());
                        clansByName.put(name.toLowerCase(), clan);
                        playerClanMap.put(player.getUuid(), name);
                        player.sendMessage(Component.text("Вы успешно создали клан [" + name + "]!", NamedTextColor.GREEN));
                    }
                }, actionArg, nameArg);
            }
        };

        MinecraftServer.getCommandManager().register(clanCmd);
    }

    @Override
    public void onDisable() {
        if (clanCmd != null) {
            MinecraftServer.getCommandManager().unregister(clanCmd);
        }
        clansByName.clear();
        playerClanMap.clear();
    }
}
