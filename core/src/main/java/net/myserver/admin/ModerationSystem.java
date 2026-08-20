package net.myserver.admin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;
import net.myserver.permissions.RoleManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ModerationSystem {
    // Храним время окончания мута в миллисекундах потокобезопасно
    private static final Map<UUID, Long> mutes = new ConcurrentHashMap<>();

    public static boolean isMuted(UUID uuid) {
        Long muteEnd = mutes.get(uuid);
        if (muteEnd != null) {
            if (System.currentTimeMillis() > muteEnd) {
                mutes.remove(uuid); // Мут истек
                return false;
            }
            return true;
        }
        return false;
    }
    
    public static long getMuteTimeLeft(UUID uuid) {
        Long muteEnd = mutes.get(uuid);
        if (muteEnd != null) {
            return Math.max(0, (muteEnd - System.currentTimeMillis()) / 1000 / 60); // В минутах
        }
        return 0;
    }

    public static class MuteCommand extends Command {
        public MuteCommand() {
            super("mute");
            var targetArg = ArgumentType.Word("target");
            var timeArg = ArgumentType.Integer("minutes");

            addSyntax((sender, context) -> {
                if (sender instanceof Player p && !RoleManager.isStaff(p)) {
                    sender.sendMessage(Component.text("⛔ У вас нет прав для выдачи мута!", NamedTextColor.RED));
                    return;
                }

                String targetName = context.get(targetArg);
                int minutes = context.get(timeArg);

                if (minutes <= 0) {
                    sender.sendMessage(Component.text("Длительность мута должна быть больше 0 минут.", NamedTextColor.RED));
                    return;
                }
                
                Player target = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(targetName);
                if (target == null) {
                    sender.sendMessage(Component.text("Игрок не найден онлайн.", NamedTextColor.RED));
                    return;
                }

                long muteEnd = System.currentTimeMillis() + (minutes * 60L * 1000L);
                mutes.put(target.getUuid(), muteEnd);
                
                target.sendMessage(Component.text("Вам был выдан мут на " + minutes + " минут.", NamedTextColor.RED));
                sender.sendMessage(Component.text("Игрок " + targetName + " замучен на " + minutes + " минут.", NamedTextColor.GREEN));
            }, targetArg, timeArg);
        }
    }

    public static class UnmuteCommand extends Command {
        public UnmuteCommand() {
            super("unmute");
            var targetArg = ArgumentType.Word("target");

            addSyntax((sender, context) -> {
                if (sender instanceof Player p && !RoleManager.isStaff(p)) {
                    sender.sendMessage(Component.text("⛔ У вас нет прав для снятия мута!", NamedTextColor.RED));
                    return;
                }

                String targetName = context.get(targetArg);
                Player target = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(targetName);
                
                if (target != null && mutes.containsKey(target.getUuid())) {
                    mutes.remove(target.getUuid());
                    target.sendMessage(Component.text("Ваш мут был снят. Вы снова можете писать в чат.", NamedTextColor.GREEN));
                    sender.sendMessage(Component.text("Игрок " + targetName + " размучен.", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("Игрок не найден или не находится в муте.", NamedTextColor.RED));
                }
            }, targetArg);
        }
    }
}
