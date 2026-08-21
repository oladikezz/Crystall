package net.schalker.SMPS.modules.announces.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.SMPS.modules.announces.AnnouncesModule;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

public class ConsoleAlertLogCommand extends ModuleCommand {
    private final AnnouncesModule module;

    public ConsoleAlertLogCommand(DoAPI plugin, AnnouncesModule module) {
        super(plugin);
        this.module = module;
    }

    @Override
    public String getName() {
        return "consolealertlog";
    }

    @Override
    public String getPermission() {
        return "smannounces.consolelog";
    }

    @Override
    public String getDescription() {
        return "Toggle console feedback logging for /consolealert";
    }

    @Override
    public String getUsage() {
        return "/consolealertlog [on|off|toggle|status]";
    }

    @Override
    public Collection<String> getAliases() {
        return List.of("calog");
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(this.module.getMessage(
                "log.console-only",
                "&cThis command can only be used from console."
            ));
            return;
        }

        String mode = args.length == 0 ? "toggle" : args[0].toLowerCase(Locale.ROOT);
        switch (mode) {
            case "on" -> {
                this.module.setConsoleLogEnabled(true);
                sender.sendMessage(this.module.getMessage("log.enabled", "&aConsole alert logs enabled."));
            }
            case "off" -> {
                this.module.setConsoleLogEnabled(false);
                sender.sendMessage(this.module.getMessage("log.disabled", "&eConsole alert logs disabled."));
            }
            case "toggle" -> {
                boolean nowEnabled = this.module.toggleConsoleLogEnabled();
                sender.sendMessage(this.module.getMessage(
                    nowEnabled ? "log.enabled" : "log.disabled",
                    nowEnabled ? "&aConsole alert logs enabled." : "&eConsole alert logs disabled."
                ));
            }
            case "status" -> sender.sendMessage(this.module.getMessage(
                "log.status",
                "&7Console alert logs: &f{state}"
            ).replace("{state}", this.module.isConsoleLogEnabled() ? "enabled" : "disabled"));
            default -> sender.sendMessage(this.module.getMessage(
                "log.usage",
                "&eUsage: &6/consolealertlog [on|off|toggle|status]"
            ));
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        if (args.length != 1) {
            return List.of();
        }

        String input = args[0].toLowerCase(Locale.ROOT);
        return List.of("on", "off", "toggle", "status").stream()
            .filter(option -> option.startsWith(input))
            .toList();
    }
}

