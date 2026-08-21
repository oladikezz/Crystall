package net.schalker.DoAPI.core.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.Collection;
import java.util.List;

public abstract class SubCommand {

    public abstract void execute(CommandSourceStack stack, String[] args);

    public abstract String getPermission();

    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        return List.of();
    }
}
