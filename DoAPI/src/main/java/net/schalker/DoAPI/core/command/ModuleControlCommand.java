package net.schalker.DoAPI.core.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.api.IModule;
import net.schalker.DoAPI.core.module.ModuleInfo;
import net.schalker.DoAPI.core.reload.PluginReloader;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public class ModuleControlCommand extends SubCommand {

    private final DoAPI plugin;
    private final PluginReloader reloader;

    public ModuleControlCommand(DoAPI plugin, PluginReloader reloader) {
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

        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "list" -> sendModuleList(sender);
            case "scan" -> {
                List<String> unloaded = plugin.getModuleManager().getUnloadedModuleFiles();
                if (unloaded.isEmpty()) {
                    sender.sendMessage(plugin.applyColors("&[SECONDARY]Новых модулей не найдено."));
                    return;
                }
                sender.sendMessage(plugin.applyColors("&[SECONDARY]Не загружены:"));
                for (String file : unloaded) {
                    sender.sendMessage(plugin.applyColors("  &7- &f" + file));
                }
            }
            case "info" -> {
                if (args.length < 2) {
                    sender.sendMessage(plugin.applyColors("&cИспользуйте: /doapi module info <name>"));
                    return;
                }
                sendModuleInfo(sender, args[1]);
            }
            case "enable" -> {
                if (args.length < 2) {
                    sender.sendMessage(plugin.applyColors("&cИспользуйте: /doapi module enable <name>"));
                    return;
                }
                reloader.enableModule(args[1], sender);
            }
            case "disable" -> {
                if (args.length < 2) {
                    sender.sendMessage(plugin.applyColors("&cИспользуйте: /doapi module disable <name>"));
                    return;
                }
                reloader.disableModule(args[1], sender);
            }
            case "reload" -> {
                if (args.length < 2) {
                    sender.sendMessage(plugin.applyColors("&cИспользуйте: /doapi module reload <name>"));
                    return;
                }
                reloader.reloadModule(args[1], sender);
            }
            case "restart" -> {
                if (args.length < 2) {
                    sender.sendMessage(plugin.applyColors("&cИспользуйте: /doapi module restart <name>"));
                    return;
                }
                reloader.restartModule(args[1], sender);
            }
            case "load" -> {
                if (args.length < 2) {
                    sender.sendMessage(plugin.applyColors("&cИспользуйте: /doapi module load <file.jar>"));
                    return;
                }
                String loaded = plugin.getModuleManager().loadModuleByFileNameAndGetName(args[1]);
                if (loaded == null) {
                    sender.sendMessage(plugin.applyColors("&c✖ Не удалось загрузить " + args[1]));
                } else {
                    sender.sendMessage(plugin.applyColors("&[MAIN]§l✔ &[SECONDARY]Загружен и включен: &f" + loaded));
                }
            }
            case "unload" -> {
                if (args.length < 2) {
                    sender.sendMessage(plugin.applyColors("&cИспользуйте: /doapi module unload <name>"));
                    return;
                }
                boolean unloaded = plugin.getModuleManager().unloadModule(args[1]);
                sender.sendMessage(plugin.applyColors(unloaded
                        ? "&[MAIN]§l✔ &[SECONDARY]Выгружен: &f" + args[1]
                        : "&c✖ Модуль не найден: " + args[1]));
            }
            case "hotswap" -> {
                if (args.length < 3) {
                    sender.sendMessage(plugin.applyColors("&cИспользуйте: /doapi module hotswap <name> <file.jar>"));
                    return;
                }
                plugin.getModuleManager().unloadModule(args[1]);
                String loaded = plugin.getModuleManager().loadModuleByFileNameAndGetName(args[2]);
                sender.sendMessage(plugin.applyColors(loaded == null
                        ? "&c✖ Hotswap не удался"
                        : "&[MAIN]§l✔ &[SECONDARY]Hotswap: &f" + loaded));
            }
            default -> sendHelp(sender);
        }
    }

    private void sendHelp(CommandSender sender) {
        String separator = plugin.applyColors("&[SECONDARY]§l" + "=".repeat(40));

        sender.sendMessage(separator);
        sender.sendMessage(plugin.applyColors("&[MAIN]§l" + plugin.applyTinyCaps("Module Control")));
        sender.sendMessage(separator);
        sender.sendMessage(plugin.applyColors("&[SECONDARY]/doapi module list"));
        sender.sendMessage(plugin.applyColors("&[SECONDARY]/doapi module info <name>"));
        sender.sendMessage(plugin.applyColors("&[SECONDARY]/doapi module enable|disable|reload|restart <name>"));
        sender.sendMessage(plugin.applyColors("&[SECONDARY]/doapi module load <file.jar>"));
        sender.sendMessage(plugin.applyColors("&[SECONDARY]/doapi module unload <name>"));
        sender.sendMessage(plugin.applyColors("&[SECONDARY]/doapi module hotswap <name> <file.jar>"));
        sender.sendMessage(plugin.applyColors("&[SECONDARY]/doapi module scan"));
        sender.sendMessage(separator);
    }

    private void sendModuleList(CommandSender sender) {
        String separator = plugin.applyColors("&[SECONDARY]§l" + "=".repeat(40));

        sender.sendMessage(separator);
        sender.sendMessage(plugin.applyColors("&[MAIN]§l" + plugin.applyTinyCaps("Module List")
                + " &[SECONDARY]" + plugin.getModuleManager().getEnabledModuleCount()
                + "/" + plugin.getModuleManager().getModuleCount()));
        sender.sendMessage(separator);

        for (IModule module : plugin.getModuleManager().getAllModules()) {
            ModuleInfo info = module.getModuleInfo();
            String status = module.isEnabled() ? "&a✔" : "&c✗";
            sender.sendMessage(plugin.applyColors("  " + status + " &[MAIN]" + info.getName()
                    + " &[SECONDARY]v" + info.getVersion()));
        }

        sender.sendMessage(separator);
    }

    private void sendModuleInfo(CommandSender sender, String name) {
        ModuleInfo info = plugin.getModuleManager().getModuleInfo(name);
        if (info == null) {
            sender.sendMessage(plugin.applyColors("&c✖ Модуль не найден: " + name));
            return;
        }

        IModule module = plugin.getModuleManager().getModule(name);
        String separator = plugin.applyColors("&[SECONDARY]§l" + "=".repeat(40));

        sender.sendMessage(separator);
        sender.sendMessage(plugin.applyColors("&[MAIN]§l" + plugin.applyTinyCaps(info.getName())));
        sender.sendMessage(separator);
        sender.sendMessage(plugin.applyColors("&[SECONDARY]Версия: &f" + info.getVersion()));
        sender.sendMessage(plugin.applyColors("&[SECONDARY]Автор: &f" + info.getAuthor()));
        sender.sendMessage(plugin.applyColors("&[SECONDARY]Описание: &f" + info.getDescription()));
        sender.sendMessage(plugin.applyColors("&[SECONDARY]Статус: "
                + (module != null && module.isEnabled() ? "&aвключен" : "&cвыключен")));
        sender.sendMessage(separator);
    }

    @Override
    public String getPermission() {
        return "sm.module.control";
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return List.of("list", "info", "enable", "disable", "reload", "restart",
                            "load", "unload", "hotswap", "scan").stream()
                    .filter(option -> option.startsWith(prefix))
                    .toList();
        }

        if (args.length == 2) {
            String action = args[0].toLowerCase(Locale.ROOT);
            String prefix = args[1].toLowerCase(Locale.ROOT);

            if (action.equals("load")) {
                return plugin.getModuleManager().getUnloadedModuleFiles().stream()
                        .filter(file -> file.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .toList();
            }
            List<String> names = new ArrayList<>(plugin.getModuleManager().getModuleNames());
            return names.stream()
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("hotswap")) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return plugin.getModuleManager().getUnloadedModuleFiles().stream()
                    .filter(file -> file.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }

        return List.of();
    }
}
