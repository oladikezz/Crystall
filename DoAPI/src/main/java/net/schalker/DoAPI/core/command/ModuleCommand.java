package net.schalker.DoAPI.core.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.schalker.DoAPI.DoAPI;

import java.util.Collection;
import java.util.List;

public abstract class ModuleCommand implements BasicCommand {

    protected final DoAPI plugin;

    public ModuleCommand(DoAPI plugin) {
        this.plugin = plugin;
    }

    public abstract String getName();

    public abstract String getPermission();

    public abstract String getDescription();

    public abstract String getUsage();

    public Collection<String> getAliases() {
        return List.of();
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        return List.of();
    }

    @Override
    public String permission() {
        return getPermission();
    }

    protected DoAPI getPlugin() {
        return plugin;
    }
}
