package be.isach.ultracosmetics.commands;

import be.isach.ultracosmetics.UCosmeticsModule;
import be.isach.ultracosmetics.player.UltraPlayer;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class CosmeticCommand extends ModuleCommand {

    public CosmeticCommand(DoAPI plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "cosmetic";
    }

    @Override
    public String getPermission() {
        return "smucosmetics.use";
    }

    @Override
    public String getDescription() {
        return "Open the cosmetics menu";
    }

    @Override
    public String getUsage() {
        return "/cosmetic";
    }

    @Override
    public Collection<String> getAliases() {
        return Arrays.asList("cosm", "cosmetics");
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return;
        }

        plugin.getSchedulerManager().runEntityTask(player, "ucosmetics-menu-open", () -> {
            // Always resolve the LIVE module instance — the stored reference goes stale after hot-swap
            UCosmeticsModule module = UCosmeticsModule.get();
            if (module == null || module.getPlayerManager() == null || module.getMenus() == null) {
                player.sendMessage(Component.text("Cosmetics module is not ready yet.", NamedTextColor.RED));
                return;
            }
            UltraPlayer ultraPlayer = module.getPlayerManager().getUltraPlayer(player);
            if (ultraPlayer == null) {
                player.sendMessage(Component.text("Could not load your cosmetics profile.", NamedTextColor.RED));
                return;
            }
            module.getMenus().openMainMenu(ultraPlayer);
        });
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        return List.of();
    }
}

