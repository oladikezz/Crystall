package net.schalker.SMPS.modules.phaseguard.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.SMPS.modules.phaseguard.PhaseGuardModule;
import net.schalker.SMPS.modules.phaseguard.PhaseGuardSettings;
import net.schalker.SMPS.modules.phaseguard.TrackedPlayer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class PhaseGuardCommand extends ModuleCommand {

    private static final List<String> SUBCOMMANDS = List.of("status", "reload", "stats", "clear");

    private final PhaseGuardModule module;

    public PhaseGuardCommand(DoAPI plugin, PhaseGuardModule module) {
        super(plugin);
        this.module = module;
    }

    @Override
    public String getName() {
        return "phaseguard";
    }

    @Override
    public String getPermission() {
        return PhaseGuardModule.PERMISSION_ADMIN;
    }

    @Override
    public String getDescription() {
        return "Управление защитой от прохода сквозь блоки";
    }

    @Override
    public String getUsage() {
        return "/phaseguard <status|reload|stats|clear>";
    }

    @Override
    public Collection<String> getAliases() {
        return List.of("antiphase");
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        String action = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);

        switch (action) {
            case "status" -> sendStatus(sender);
            case "reload" -> {
                module.reload();
                send(sender, module.getMessage("reloaded", "&[SECONDARY]Конфигурация PhaseGuard перезагружена."));
            }
            case "stats" -> sendStats(sender, args.length > 1 ? args[1] : null);
            case "clear" -> clear(sender, args.length > 1 ? args[1] : null);
            default -> send(sender, module.getMessage("usage", "&[SECONDARY]Использование: &[MAIN]" + getUsage()));
        }
    }

    private void sendStatus(CommandSender sender) {
        PhaseGuardSettings settings = module.getSettings();
        if (settings == null) {
            send(sender, module.getMessage("not-loaded", "&[SECONDARY]Конфигурация не загружена."));
            return;
        }

        int watched = module.getTrackedPlayers().size();
        int violations = module.getTrackedPlayers().values().stream()
                .mapToInt(TrackedPlayer::getTotalViolations)
                .sum();

        send(sender, module.getMessage("status-header", "&[MAIN]PhaseGuard"));
        send(sender, module.getMessage("status-enabled", "&[SECONDARY]Включён: &[MAIN]{value}")
                .replace("{value}", settings.isEnabled() ? "да" : "нет"));
        send(sender, module.getMessage("status-mode", "&[SECONDARY]Режим: &[MAIN]{value}")
                .replace("{value}", settings.getMode().name()));
        send(sender, module.getMessage("status-tracked", "&[SECONDARY]Под наблюдением: &[MAIN]{value}")
                .replace("{value}", String.valueOf(watched)));
        send(sender, module.getMessage("status-violations", "&[SECONDARY]Нарушений за сессию: &[MAIN]{value}")
                .replace("{value}", String.valueOf(violations)));
    }

    private void sendStats(CommandSender sender, String targetName) {
        if (targetName != null) {
            UUID targetId = resolve(targetName);
            if (targetId == null) {
                send(sender, module.getMessage("player-not-found", "&[SECONDARY]Игрок &[MAIN]{player} &[SECONDARY]не найден.")
                        .replace("{player}", targetName));
                return;
            }
            TrackedPlayer tracked = module.peekTracked(targetId);
            int total = tracked == null ? 0 : tracked.getTotalViolations();
            send(sender, module.getMessage("stats-line", "&[MAIN]{player}&[SECONDARY]: {value}")
                    .replace("{player}", targetName)
                    .replace("{value}", String.valueOf(total)));
            return;
        }

        boolean empty = true;
        send(sender, module.getMessage("stats-header", "&[MAIN]Нарушения PhaseGuard"));
        for (Map.Entry<UUID, TrackedPlayer> entry : module.getTrackedPlayers().entrySet()) {
            if (entry.getValue().getTotalViolations() <= 0) {
                continue;
            }
            empty = false;
            OfflinePlayer offline = Bukkit.getOfflinePlayer(entry.getKey());
            String name = offline.getName() == null ? entry.getKey().toString() : offline.getName();
            send(sender, module.getMessage("stats-line", "&[MAIN]{player}&[SECONDARY]: {value}")
                    .replace("{player}", name)
                    .replace("{value}", String.valueOf(entry.getValue().getTotalViolations())));
        }
        if (empty) {
            send(sender, module.getMessage("stats-empty", "&[SECONDARY]Нарушений не зафиксировано."));
        }
    }

    private void clear(CommandSender sender, String targetName) {
        if (targetName == null) {
            send(sender, module.getMessage("usage", "&[SECONDARY]Использование: &[MAIN]/phaseguard clear <ник>"));
            return;
        }
        UUID targetId = resolve(targetName);
        if (targetId == null) {
            send(sender, module.getMessage("player-not-found", "&[SECONDARY]Игрок &[MAIN]{player} &[SECONDARY]не найден.")
                    .replace("{player}", targetName));
            return;
        }
        TrackedPlayer tracked = module.peekTracked(targetId);
        if (tracked != null) {
            tracked.reset();
        }
        send(sender, module.getMessage("stats-cleared", "&[SECONDARY]Счётчик нарушений для &[MAIN]{player} &[SECONDARY]сброшен.")
                .replace("{player}", targetName));
    }

    private UUID resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore()) {
            return offline.getUniqueId();
        }
        return null;
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        if (args.length <= 1) {
            String input = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream()
                    .filter(name -> name.startsWith(input))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("stats") || args[0].equalsIgnoreCase("clear"))) {
            String input = args[1].toLowerCase(Locale.ROOT);
            return this.plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        return List.of();
    }

    private void send(CommandSender sender, String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        sender.sendMessage(message);
    }
}
