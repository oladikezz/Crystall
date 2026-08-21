package net.schalker.SMPS.modules.announces.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.SMPS.modules.announces.AnnouncesModule;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

public class ConsoleAlertCommand extends ModuleCommand {
    private static final Pattern NAMESPACED_SOUND = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");
    private final AnnouncesModule module;

    public ConsoleAlertCommand(DoAPI plugin, AnnouncesModule module) {
        super(plugin);
        this.module = module;
    }

    @Override
    public String getName() {
        return "consolealert";
    }

    @Override
    public String getPermission() {
        return "smannounces.consolealert";
    }

    @Override
    public String getDescription() {
        return "Send formatted chat/title alert to a player from console";
    }

    @Override
    public String getUsage() {
        return "/consolealert [nickname] [type] \"[text]\" [sound|none]";
    }

    @Override
    public Collection<String> getAliases() {
        return List.of("calert");
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(this.module.getMessage(
                "alerts.console-only",
                "&cThis command can only be used from console."
            ));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(this.module.getMessage(
                "alerts.usage",
                "&eUsage: &6/consolealert [nickname] [chat|title|all] \"[text]\" [sound|none]"
            ));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(this.module.getMessage(
                "alerts.player-not-found",
                "&cPlayer not found or not online: &f{player}"
            ).replace("{player}", args[0]));
            return;
        }

        AlertType type = AlertType.fromInput(args[1]);
        if (type == null) {
            sender.sendMessage(this.module.getMessage(
                "alerts.invalid-type",
                "&cInvalid type. Available: &fchat, title, all"
            ));
            return;
        }

        String tail = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)).trim();
        ParsedInput parsed = parseInput(tail);
        if (parsed.errorKey() != null) {
            sender.sendMessage(this.module.getMessage(
                parsed.errorKey(),
                "&cInvalid message format. Use quotes around text and optional sound after it."
            ));
            return;
        }

        if (parsed.message() == null || parsed.message().isBlank()) {
            sender.sendMessage(this.module.getMessage(
                "alerts.empty-message",
                "&cMessage cannot be empty."
            ));
            return;
        }

        if (!this.module.isValidSoundKey(parsed.soundName())) {
            sender.sendMessage(this.module.getMessage(
                "alerts.invalid-sound",
                "&cInvalid sound key: &f{sound}&c. Use namespaced key or &fnone"
            ).replace("{sound}", parsed.soundName() == null ? "" : parsed.soundName()));
            return;
        }

        Component formatted = this.module.getFormatter().format(parsed.message());
        this.module.sendAlert(target, type, formatted, parsed.soundName());

        String soundDisplay = (parsed.soundName() == null || parsed.soundName().isBlank())
            ? this.module.getDefaultSound()
            : parsed.soundName();
        if (this.module.isConsoleLogEnabled()) {
            sender.sendMessage(this.module.getMessage(
                "alerts.sent",
                "&aAlert sent to &f{player}&a as &f{type}&a (sound: &f{sound}&a)."
            )
                .replace("{player}", target.getName())
                .replace("{type}", type.name().toLowerCase(Locale.ROOT))
                .replace("{sound}", soundDisplay));
        }
    }

    private ParsedInput parseInput(String tail) {
        if (tail == null || tail.isBlank()) {
            return new ParsedInput(null, null, "alerts.invalid-format");
        }

        String trimmed = tail.trim();
        if (!trimmed.startsWith("\"")) {
            return parseUnquotedInput(trimmed);
        }

        int quoteEnd = findClosingQuote(trimmed);
        if (quoteEnd < 1) {
            return new ParsedInput(null, null, "alerts.unclosed-quotes");
        }

        String message = trimmed.substring(1, quoteEnd).replace("\\\"", "\"");
        String remainder = trimmed.substring(quoteEnd + 1).trim();
        if (remainder.isEmpty()) {
            return new ParsedInput(message, null, null);
        }

        String[] tokens = remainder.split("\\s+");
        if (tokens.length != 1) {
            return new ParsedInput(null, null, "alerts.invalid-format");
        }
        return new ParsedInput(message, tokens[0], null);
    }

    private ParsedInput parseUnquotedInput(String input) {
        if (input == null || input.isBlank()) {
            return new ParsedInput(null, null, "alerts.invalid-format");
        }

        String[] tokens = input.split("\\s+");
        if (tokens.length <= 1) {
            return new ParsedInput(input, null, null);
        }

        String candidateSound = tokens[tokens.length - 1];
        if (!isSoundToken(candidateSound)) {
            return new ParsedInput(input, null, null);
        }

        String message = input.substring(0, input.length() - candidateSound.length()).trim();
        return new ParsedInput(message, candidateSound, null);
    }

    private boolean isSoundToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        if ("none".equalsIgnoreCase(token)) {
            return true;
        }
        return NAMESPACED_SOUND.matcher(token.toLowerCase(Locale.ROOT)).matches();
    }

    private int findClosingQuote(String input) {
        boolean escaped = false;
        for (int i = 1; i < input.length(); i++) {
            char current = input.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == '"') {
                return i;
            }
        }
        return -1;
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            return this.plugin.getServer().getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input))
                .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String input = args[1].toLowerCase(Locale.ROOT);
            return List.of("chat", "title", "all").stream()
                .filter(type -> type.startsWith(input))
                .collect(Collectors.toList());
        }

        return List.of();
    }

    public enum AlertType {
        CHAT,
        TITLE,
        ALL;

        public static AlertType fromInput(String input) {
            if (input == null) {
                return null;
            }
            return switch (input.toLowerCase(Locale.ROOT)) {
                case "chat" -> CHAT;
                case "title" -> TITLE;
                case "all" -> ALL;
                default -> null;
            };
        }
    }

    private record ParsedInput(String message, String soundName, String errorKey) {
    }
}

