package net.schalker.DoAPI.core.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.schalker.DoAPI.DoAPI;

public class InfoCommand extends SubCommand {

    private final DoAPI plugin;

    public InfoCommand(DoAPI plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        var sender = stack.getSender();
        String separator = plugin.applyColors("&[SECONDARY]§l" + "=".repeat(40));

        sender.sendMessage(separator);
        sender.sendMessage(plugin.applyColors("&[MAIN]§l" + plugin.applyTinyCaps("DoAPI Info")));
        sender.sendMessage(separator);
        sender.sendMessage(plugin.applyColors("&[SECONDARY]Версия: &f" + plugin.getPluginMeta().getVersion()));
        sender.sendMessage(plugin.applyColors("&[SECONDARY]" + plugin.getDebugSystem().getSystemInfo()));
        sender.sendMessage(plugin.applyColors("&[SECONDARY]Модули: &f"
                + plugin.getModuleManager().getEnabledModuleCount()
                + "&7/&f" + plugin.getModuleManager().getModuleCount()));
        sender.sendMessage(plugin.applyColors("&[SECONDARY]Слушатели: &f"
                + plugin.getListenerManager().getListenerCount()));
        sender.sendMessage(plugin.applyColors("&[SECONDARY]Задачи: &f"
                + plugin.getSchedulerManager().getTaskCount()));
        sender.sendMessage(plugin.applyColors("&[SECONDARY]Команды модулей: &f"
                + plugin.getCommandManager().getModuleCommandCount()));
        sender.sendMessage(plugin.applyColors("&[SECONDARY]БД: &f"
                + plugin.getDatabaseManager().getDatabaseType().getDisplayName()
                + " &7(" + plugin.getDatabaseManager().getConnectedCount()
                + "/" + plugin.getDatabaseManager().getTotalCount() + ")"));
        sender.sendMessage(plugin.applyColors("&[SECONDARY]Debug: &f"
                + (plugin.getDebugSystem().isDebugEnabled() ? "&aвключен" : "&cвыключен")));
        sender.sendMessage(separator);
    }

    @Override
    public String getPermission() {
        return "smps.use";
    }
}
