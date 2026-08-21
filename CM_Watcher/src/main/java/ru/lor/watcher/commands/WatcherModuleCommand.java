package ru.lor.watcher.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.schalker.DoAPI.core.command.ModuleCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.lor.watcher.WatcherPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public class WatcherModuleCommand extends ModuleCommand {

    private final WatcherPlugin watcher;
    private final WatcherCommand handler;

    public WatcherModuleCommand(WatcherPlugin watcher) {
        super(watcher.getCore());
        this.watcher = watcher;
        this.handler = new WatcherCommand(watcher);
    }

    @Override
    public String getName() {
        return "watcher";
    }

    @Override
    public String getPermission() {
        return "watcher.use";
    }

    @Override
    public String getDescription() {
        return "Управление системой Смотрящего";
    }

    @Override
    public String getUsage() {
        return "/watcher [open|spawn|despawn|message|reload]";
    }

    @Override
    public Collection<String> getAliases() {
        return List.of("watchers", "looker");
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        handler.handle(stack.getSender(), args);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        List<String> completions = new ArrayList<>();

        if (args.length <= 1) {
            if (sender.hasPermission("watcher.use")) {
                completions.add("open");
            }
            if (sender.hasPermission("watcher.spawn")) {
                completions.add("spawn");
                completions.add("despawn");
            }
            if (sender.hasPermission("watcher.message")) {
                completions.add("message");
            }
            if (sender.hasPermission("watcher.admin")) {
                completions.add("reload");
            }
            return filter(completions, args.length == 0 ? "" : args[0]);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("open") || sub.equals("spawn") || sub.equals("despawn")) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    completions.add(player.getName());
                }
                return filter(completions, args[1]);
            }
        }

        return List.of();
    }

    private List<String> filter(List<String> source, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> filtered = new ArrayList<>();
        for (String value : source) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                filtered.add(value);
            }
        }
        return filtered;
    }

    public WatcherPlugin getWatcher() {
        return watcher;
    }
}
