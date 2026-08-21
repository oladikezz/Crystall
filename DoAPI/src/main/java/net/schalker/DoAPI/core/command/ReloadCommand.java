package net.schalker.DoAPI.core.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.schalker.DoAPI.DoAPI;

public class ReloadCommand extends SubCommand {

    private final DoAPI plugin;

    public ReloadCommand(DoAPI plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        var sender = stack.getSender();
        long start = System.currentTimeMillis();

        sender.sendMessage(plugin.applyColors("&[SECONDARY]Перезагрузка DoAPI..."));

        plugin.getConfigManager().reloadConfig();
        plugin.getDebugSystem().reloadSettings();
        plugin.getModuleManager().reloadAllModules();

        long elapsed = System.currentTimeMillis() - start;
        sender.sendMessage(plugin.applyColors("&[MAIN]§l✔ &[SECONDARY]Перезагружено за " + elapsed + " мс "
                + "&7(" + plugin.getModuleManager().getEnabledModuleCount()
                + "/" + plugin.getModuleManager().getModuleCount() + " модулей)"));
    }

    @Override
    public String getPermission() {
        return "smps.reload";
    }
}
