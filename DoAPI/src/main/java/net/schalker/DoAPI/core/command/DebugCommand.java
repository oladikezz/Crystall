package net.schalker.DoAPI.core.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.schalker.DoAPI.DoAPI;

public class DebugCommand extends SubCommand {

    private final DoAPI plugin;

    public DebugCommand(DoAPI plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        boolean enabled = !plugin.getDebugSystem().isDebugEnabled();
        plugin.getDebugSystem().setDebugEnabled(enabled);

        stack.getSender().sendMessage(plugin.applyColors("&[MAIN]§l✔ &[SECONDARY]Debug-режим "
                + (enabled ? "&aвключен" : "&cвыключен")));
    }

    @Override
    public String getPermission() {
        return "smps.debug";
    }
}
