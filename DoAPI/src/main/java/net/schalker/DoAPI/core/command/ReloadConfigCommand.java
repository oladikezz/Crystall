package net.schalker.DoAPI.core.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.schalker.DoAPI.DoAPI;

public class ReloadConfigCommand extends SubCommand {

    private final DoAPI plugin;

    public ReloadConfigCommand(DoAPI plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        var sender = stack.getSender();

        plugin.getConfigManager().reloadConfig();
        plugin.getDebugSystem().reloadSettings();

        sender.sendMessage(plugin.applyColors("&[MAIN]§l✔ &[SECONDARY]config.yml перезагружен"));
    }

    @Override
    public String getPermission() {
        return "smps.reload";
    }
}
