package net.schalker.DoAPI.core.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.reload.PluginReloader;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CommandManager {

    private record SimpleSourceStack(CommandSender sender, Location location, Entity executor)
            implements CommandSourceStack {

        @Override
        public Location getLocation() {
            return location;
        }

        @Override
        public CommandSender getSender() {
            return sender;
        }

        @Override
        public Entity getExecutor() {
            return executor;
        }

        @Override
        public CommandSourceStack withExecutor(Entity newExecutor) {
            return new SimpleSourceStack(sender, location, newExecutor);
        }

        @Override
        public CommandSourceStack withLocation(Location newLocation) {
            return new SimpleSourceStack(sender, newLocation, executor);
        }
    }

    private final DoAPI plugin;
    private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();
    private final Map<String, ModuleCommand> moduleCommands = new ConcurrentHashMap<>();
    private final Set<String> registeredCommands = ConcurrentHashMap.newKeySet();

    private final Set<String> commandMapRegistered = ConcurrentHashMap.newKeySet();

    private volatile Commands commandsRegistrar;
    private volatile boolean lifecycleFired;
    private PluginReloader reloader;

    public CommandManager(DoAPI plugin) {
        this.plugin = plugin;
    }

    public void registerSubCommand(String name, SubCommand subCommand) {
        subCommands.put(name.toLowerCase(Locale.ROOT), subCommand);
    }

    public void registerModuleCommand(ModuleCommand command) {
        if (command == null) {
            return;
        }

        String name = command.getName().toLowerCase(Locale.ROOT);
        moduleCommands.put(name, command);

        if (!lifecycleFired) {
            return;
        }

        Commands registrar = commandsRegistrar;
        if (registrar != null) {
            try {
                registerModuleCommandWithRegistrar(registrar, command);
                return;
            } catch (Throwable throwable) {
                plugin.getDebugSystem().log("Commands",
                        "Brigadier registrar rejected /" + name + ", using command map fallback");
            }
        }

        registerWithCommandMap(command);
    }

    public boolean isCommandRegistered(String name) {
        return name != null && registeredCommands.contains(name.toLowerCase(Locale.ROOT));
    }

    public void initialize(PluginReloader pluginReloader) {
        this.reloader = pluginReloader;

        registerSubCommand("reload", new ReloadCommand(plugin));
        registerSubCommand("reloadconfig", new ReloadConfigCommand(plugin));
        registerSubCommand("debug", new DebugCommand(plugin));
        registerSubCommand("info", new InfoCommand(plugin));
        registerSubCommand("database", new DatabaseCommand(plugin));
        registerSubCommand("module", new ModuleControlCommand(plugin, pluginReloader));
        registerSubCommand("experimental", new ExperimentalCommand(plugin, pluginReloader));

        registerCommandLifecycle();
    }

    private void registerCommandLifecycle() {
        try {
            plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
                Commands registrar = event.registrar();
                this.commandsRegistrar = registrar;

                registerCoreCommandWithRegistrar(registrar);
                for (ModuleCommand command : moduleCommands.values()) {
                    if (commandMapRegistered.contains(command.getName().toLowerCase(Locale.ROOT))) {
                        continue;
                    }
                    try {
                        registerModuleCommandWithRegistrar(registrar, command);
                    } catch (Throwable throwable) {
                        plugin.getDebugSystem().logError("Commands",
                                "Failed to register /" + command.getName(), throwable);
                    }
                }

                this.lifecycleFired = true;
            });
        } catch (Throwable throwable) {
            plugin.getDebugSystem().logError("Commands", "Command lifecycle registration failed", throwable);
        }
    }

    private void registerCoreCommandWithRegistrar(Commands registrar) {
        BasicCommand core = new BasicCommand() {

            @Override
            public void execute(CommandSourceStack stack, String[] args) {
                dispatch(stack, args, () -> handleCoreCommand(stack, args));
            }

            @Override
            public Collection<String> suggest(CommandSourceStack stack, String[] args) {
                return suggestCore(stack, args);
            }

            @Override
            public String permission() {
                return "smps.use";
            }
        };

        registrar.register("doapi", "DoAPI core command", List.of("sm", "smps"), core);
        registeredCommands.add("doapi");
        registeredCommands.add("sm");
        registeredCommands.add("smps");
    }

    private void registerModuleCommandWithRegistrar(Commands registrar, ModuleCommand command) {
        String name = command.getName().toLowerCase(Locale.ROOT);

        BasicCommand wrapper = new BasicCommand() {

            @Override
            public void execute(CommandSourceStack stack, String[] args) {
                dispatch(stack, args, () -> command.execute(stack, args));
            }

            @Override
            public Collection<String> suggest(CommandSourceStack stack, String[] args) {
                try {
                    return command.suggest(stack, args);
                } catch (Throwable throwable) {
                    return List.of();
                }
            }

            @Override
            public String permission() {
                return command.getPermission();
            }
        };

        registrar.register(name, command.getDescription(), command.getAliases(), wrapper);
        registeredCommands.add(name);
        for (String alias : command.getAliases()) {
            registeredCommands.add(alias.toLowerCase(Locale.ROOT));
        }

        if (plugin.shouldLogCommands()) {
            plugin.getDebugSystem().log("Commands", "Registered /" + name);
        }
    }

    private void registerWithCommandMap(ModuleCommand command) {
        String name = command.getName().toLowerCase(Locale.ROOT);

        try {
            Command bukkitCommand = new Command(name,
                    command.getDescription(),
                    command.getUsage(),
                    new ArrayList<>(command.getAliases())) {

                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    String permission = command.getPermission();
                    if (permission != null && !permission.isBlank() && !sender.hasPermission(permission)) {
                        sender.sendMessage(plugin.applyColors("&cУ вас нет прав на эту команду."));
                        return true;
                    }

                    CommandSourceStack stack = sourceStackOf(sender);
                    dispatch(stack, args, () -> command.execute(stack, args));
                    return true;
                }

                @Override
                public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                    try {
                        return new ArrayList<>(command.suggest(sourceStackOf(sender), args));
                    } catch (Throwable throwable) {
                        return List.of();
                    }
                }
            };

            String permission = command.getPermission();
            if (permission != null && !permission.isBlank()) {
                bukkitCommand.setPermission(permission);
            }

            Bukkit.getCommandMap().register("doapi", bukkitCommand);
            commandMapRegistered.add(name);
            registeredCommands.add(name);
            for (String alias : command.getAliases()) {
                registeredCommands.add(alias.toLowerCase(Locale.ROOT));
            }

            for (Player player : Bukkit.getOnlinePlayers()) {
                player.updateCommands();
            }

            if (plugin.shouldLogCommands()) {
                plugin.getDebugSystem().log("Commands", "Registered /" + name + " (command map)");
            }
        } catch (Throwable throwable) {
            plugin.getDebugSystem().logError("Commands", "Failed to register /" + name, throwable);
        }
    }

    private CommandSourceStack sourceStackOf(CommandSender sender) {
        if (sender instanceof Entity entity) {
            return new SimpleSourceStack(sender, entity.getLocation(), entity);
        }
        Location location = Bukkit.getWorlds().isEmpty()
                ? null
                : Bukkit.getWorlds().getFirst().getSpawnLocation();
        return new SimpleSourceStack(sender, location, null);
    }

    private void dispatch(CommandSourceStack stack, String[] args, Runnable body) {
        CommandSender sender = stack.getSender();

        if (sender instanceof Player player && !Bukkit.isOwnedByCurrentRegion(player)) {
            plugin.getSchedulerManager().runEntityTask(player,
                    "command-" + player.getUniqueId() + "-" + System.nanoTime(), body);
            return;
        }

        try {
            body.run();
        } catch (Throwable throwable) {
            plugin.getDebugSystem().logError("Commands", "Command execution failed", throwable);
            sender.sendMessage(plugin.applyColors("&cПри выполнении команды произошла ошибка."));
        }
    }

    public int getModuleCommandCount() {
        return moduleCommands.size();
    }

    private void handleCoreCommand(CommandSourceStack stack, String[] args) {
        if (args.length == 0) {
            sendHelp(stack);
            return;
        }

        SubCommand subCommand = subCommands.get(args[0].toLowerCase(Locale.ROOT));
        if (subCommand == null) {
            sendHelp(stack);
            return;
        }

        String permission = subCommand.getPermission();
        if (permission != null && !permission.isBlank() && !stack.getSender().hasPermission(permission)) {
            stack.getSender().sendMessage(plugin.applyColors("&cУ вас нет прав на эту команду."));
            return;
        }

        String[] rest = new String[args.length - 1];
        System.arraycopy(args, 1, rest, 0, rest.length);
        subCommand.execute(stack, rest);
    }

    public Collection<String> suggestCore(CommandSourceStack stack, String[] args) {
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            List<String> result = new ArrayList<>();
            for (Map.Entry<String, SubCommand> entry : subCommands.entrySet()) {
                String permission = entry.getValue().getPermission();
                boolean allowed = permission == null || permission.isBlank()
                        || stack.getSender().hasPermission(permission);
                if (allowed && entry.getKey().startsWith(prefix)) {
                    result.add(entry.getKey());
                }
            }
            return result;
        }

        SubCommand subCommand = subCommands.get(args[0].toLowerCase(Locale.ROOT));
        if (subCommand == null) {
            return List.of();
        }

        String[] rest = new String[args.length - 1];
        System.arraycopy(args, 1, rest, 0, rest.length);
        return subCommand.suggest(stack, rest);
    }

    private void sendHelp(CommandSourceStack stack) {
        CommandSender sender = stack.getSender();
        String separator = plugin.applyColors("&[SECONDARY]§l" + "=".repeat(40));

        sender.sendMessage(separator);
        sender.sendMessage(plugin.applyColors("&[MAIN]§l" + plugin.applyTinyCaps("DoAPI")
                + " &[SECONDARY]v" + plugin.getPluginMeta().getVersion()));
        sender.sendMessage(separator);
        sender.sendMessage(plugin.applyColors("&[SECONDARY]/doapi info &7- информация о ядре"));
        sender.sendMessage(plugin.applyColors("&[SECONDARY]/doapi reload &7- перезагрузить конфиг и модули"));
        sender.sendMessage(plugin.applyColors("&[SECONDARY]/doapi reloadconfig &7- перезагрузить только конфиг"));
        sender.sendMessage(plugin.applyColors("&[SECONDARY]/doapi debug &7- переключить debug-режим"));
        sender.sendMessage(plugin.applyColors("&[SECONDARY]/doapi database &7- состояние баз данных"));
        sender.sendMessage(plugin.applyColors("&[SECONDARY]/doapi module &7- управление модулями"));
        sender.sendMessage(plugin.applyColors("&[SECONDARY]/doapi experimental &7- экспериментальные функции"));
        sender.sendMessage(separator);
    }
}
