package net.myserver.modules.impl;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.myserver.modules.CrystallModule;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MarryModule implements CrystallModule {
    private static final Map<UUID, UUID> marriages = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> pendingProposals = new ConcurrentHashMap<>();
    private Command marryCmd;

    @Override
    public String getId() {
        return "marry";
    }

    @Override
    public String getName() {
        return "Marry";
    }

    @Override
    public String getDescription() {
        return "Система свадеб и бракосочетаний между игроками (/marry)";
    }

    @Override
    public void onEnable(GlobalEventHandler eventHandler) {
        marryCmd = new Command("marry", "marriage") {
            {
                var actionArg = ArgumentType.Word("action");
                var targetArg = ArgumentType.Word("target");

                setDefaultExecutor((sender, context) -> {
                    sender.sendMessage(Component.text("══════ Свадьбы Crystall ══════", NamedTextColor.LIGHT_PURPLE));
                    sender.sendMessage(Component.text(" • /marry <игрок> - сделать предложение", NamedTextColor.WHITE));
                    sender.sendMessage(Component.text(" • /marry accept - принять предложение", NamedTextColor.WHITE));
                    sender.sendMessage(Component.text(" • /marry tp - телепортироваться к партнеру", NamedTextColor.WHITE));
                    sender.sendMessage(Component.text(" • /marry divorce - развестись", NamedTextColor.WHITE));
                });

                addSyntax((sender, context) -> {
                    if (!(sender instanceof Player player)) return;
                    String action = context.get(actionArg).toLowerCase();

                    if ("accept".equals(action)) {
                        UUID proposerUuid = pendingProposals.remove(player.getUuid());
                        if (proposerUuid == null) {
                            player.sendMessage(Component.text("У вас нет активных предложений руки и сердца.", NamedTextColor.RED));
                            return;
                        }

                        Player proposer = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(proposerUuid);
                        marriages.put(player.getUuid(), proposerUuid);
                        marriages.put(proposerUuid, player.getUuid());

                        player.sendMessage(Component.text("💖 Вы приняли предложение и вступили в брак!", NamedTextColor.LIGHT_PURPLE));
                        if (proposer != null) {
                            proposer.sendMessage(Component.text("💖 " + player.getUsername() + " согласился(ась) стать вашей парой!", NamedTextColor.LIGHT_PURPLE));
                        }
                    } else if ("tp".equals(action)) {
                        UUID partnerUuid = marriages.get(player.getUuid());
                        if (partnerUuid == null) {
                            player.sendMessage(Component.text("Вы не состоите в браке.", NamedTextColor.RED));
                            return;
                        }
                        Player partner = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(partnerUuid);
                        if (partner == null || partner.getInstance() == null) {
                            player.sendMessage(Component.text("Ваш партнер сейчас не в сети.", NamedTextColor.RED));
                            return;
                        }
                        player.setInstance(partner.getInstance(), partner.getPosition());
                        player.sendMessage(Component.text("Телепортация к партнеру...", NamedTextColor.GREEN));
                    } else if ("divorce".equals(action)) {
                        UUID partnerUuid = marriages.remove(player.getUuid());
                        if (partnerUuid != null) {
                            marriages.remove(partnerUuid);
                            player.sendMessage(Component.text("Вы расторгли брак.", NamedTextColor.GRAY));
                        } else {
                            player.sendMessage(Component.text("Вы не состоите в браке.", NamedTextColor.RED));
                        }
                    } else {
                        // Попытка сделать предложение по имени
                        Player target = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(action);
                        if (target == null) {
                            player.sendMessage(Component.text("Игрок " + action + " не найден.", NamedTextColor.RED));
                            return;
                        }
                        if (target.getUuid().equals(player.getUuid())) {
                            player.sendMessage(Component.text("Вы не можете жениться на самом себе!", NamedTextColor.RED));
                            return;
                        }
                        if (marriages.containsKey(player.getUuid())) {
                            player.sendMessage(Component.text("Вы уже состоите в браке!", NamedTextColor.RED));
                            return;
                        }
                        pendingProposals.put(target.getUuid(), player.getUuid());
                        player.sendMessage(Component.text("Вы сделали предложение игроку " + target.getUsername() + "!", NamedTextColor.LIGHT_PURPLE));
                        target.sendMessage(Component.text("💍 Игрок " + player.getUsername() + " сделал(а) вам предложение руки и сердца! Напишите /marry accept чтобы согласиться.", NamedTextColor.GOLD));
                    }
                }, actionArg);
            }
        };

        MinecraftServer.getCommandManager().register(marryCmd);
    }

    @Override
    public void onDisable() {
        if (marryCmd != null) {
            MinecraftServer.getCommandManager().unregister(marryCmd);
        }
        marriages.clear();
        pendingProposals.clear();
    }
}
