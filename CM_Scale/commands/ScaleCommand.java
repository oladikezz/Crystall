package net.schalker.SMPS.modules.scale.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.SMPS.modules.scale.ScaleModule;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ScaleCommand extends ModuleCommand {
    private final ScaleModule module;

    public ScaleCommand(DoAPI plugin, ScaleModule module) {
        super(plugin);
        this.module = module;
    }

    @Override
    public String getName() {
        return "scale";
    }

    @Override
    public String getPermission() {
        return "smscale.basic";
    }

    @Override
    public String getDescription() {
        return "Change player scale";
    }

    @Override
    public String getUsage() {
        return "/scale [value] or /scale [player] [value]";
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        execute(stack.getSender(), args);
    }

    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(module.getMessage("usage"));
            return;
        }

        if (args.length == 1) {
            // self scale
            if (!(sender instanceof Player player)) {
                sender.sendMessage(module.getMessage("player_only"));
                return;
            }
            if (!player.hasPermission("smscale.basic") && !player.hasPermission("smscale.advanced") && !player.hasPermission("smscale.admin")) {
                 player.sendMessage(module.getMessage("no_permission"));
                 return;
            }

            double scale;
            try {
                scale = Double.parseDouble(args[0]);
            } catch (NumberFormatException e) {
                player.sendMessage(module.getMessage("invalid_number"));
                return;
            }
            
            // Limit checks for non-admins
            if (!player.hasPermission("smscale.admin")) {
                double min = module.getConfig().getDouble("scaling.basic.min", 0.7);
                double max = module.getConfig().getDouble("scaling.basic.max", 1.3);

                if (player.hasPermission("smscale.advanced")) {
                    min = module.getConfig().getDouble("scaling.advanced.min", 0.5);
                    max = module.getConfig().getDouble("scaling.advanced.max", 2.0);
                }
                
                if (scale < min || scale > max) {
                    player.sendMessage(module.getMessage("invalid_range")
                        .replace("%min%", String.valueOf(min))
                        .replace("%max%", String.valueOf(max)));
                    return;
                }
            }

            module.animateScale(player, scale); // Use module method which I updated
            player.sendMessage(module.getMessage("success").replace("%scale%", String.valueOf(scale)));
            
        } else if (args.length == 2) {
            // target scale
            if (!sender.hasPermission("smscale.admin")) {
                sender.sendMessage(module.getMessage("no_permission"));
                return;
            }
            
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(module.getMessage("player_not_found"));
                return;
            }

            double scale;
            try {
                scale = Double.parseDouble(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(module.getMessage("invalid_number"));
                return;
            }
            
            module.animateScale(target, scale);
            sender.sendMessage(module.getMessage("success_other")
                .replace("%target%", target.getName())
                .replace("%scale%", String.valueOf(scale)));
        } else {
            sender.sendMessage(module.getMessage("usage"));
        }
    }
}
