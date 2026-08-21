package net.schalker.DoAPI.core.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.reload.PluginReloader;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

public class ExperimentalCommand extends SubCommand {

    private final DoAPI plugin;
    private final PluginReloader reloader;

    public ExperimentalCommand(DoAPI plugin, PluginReloader reloader) {
        this.plugin = plugin;
        this.reloader = reloader;
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();

        if (args.length == 0) {
            sendHelp(sender);
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "enable" -> {
                reloader.enableExperimental();
                sender.sendMessage(plugin.applyColors(
                        "&e⚠ Экспериментальный режим включен до перезапуска сервера."));
            }
            case "info" -> reloader.sendExperimentalInfo(sender);
            case "softreload" -> reloader.hotReloadPlugin(sender, false);
            case "fullreload" -> {
                if (args.length > 1) {
                    reloader.hotReloadPlugin(sender, true, args[1]);
                } else {
                    reloader.hotReloadPlugin(sender, true);
                }
            }
            case "jars" -> {
                sender.sendMessage(plugin.applyColors("&[SECONDARY]Доступные JAR-файлы:"));
                for (String jar : reloader.getAvailableJars()) {
                    sender.sendMessage(plugin.applyColors("  &7- &f" + jar));
                }
            }
            default -> sendHelp(sender);
        }
    }

    private void sendHelp(CommandSender sender) {
        String separator = plugin.applyColors("&[SECONDARY]§l" + "=".repeat(40));

        sender.sendMessage(separator);
        sender.sendMessage(plugin.applyColors("&[MAIN]§l" + plugin.applyTinyCaps("Experimental")));
        sender.sendMessage(separator);
        sender.sendMessage(plugin.applyColors("&[SECONDARY]/doapi experimental enable"));
        sender.sendMessage(plugin.applyColors("&[SECONDARY]/doapi experimental info"));
        sender.sendMessage(plugin.applyColors("&[SECONDARY]/doapi experimental softreload"));
        sender.sendMessage(plugin.applyColors("&[SECONDARY]/doapi experimental fullreload [jar]"));
        sender.sendMessage(plugin.applyColors("&[SECONDARY]/doapi experimental jars"));
        sender.sendMessage(separator);
        sender.sendMessage(plugin.applyColors("&7Статус: "
                + (reloader.isExperimentalEnabled() ? "&aвключен" : "&cвыключен")));
    }

    @Override
    public String getPermission() {
        return "smps.experimental";
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return List.of("enable", "info", "softreload", "fullreload", "jars").stream()
                    .filter(option -> option.startsWith(prefix))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("fullreload")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return reloader.getAvailableJars().stream()
                    .filter(jar -> jar.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
