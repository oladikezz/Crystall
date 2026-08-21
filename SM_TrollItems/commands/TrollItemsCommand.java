package net.schalker.SMPS.modules.trollitems.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.SMPS.modules.trollitems.TrollItemsModule;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class TrollItemsCommand extends ModuleCommand {
    private final TrollItemsModule module;

    public TrollItemsCommand(DoAPI plugin, TrollItemsModule module) {
        super(plugin);
        this.module = module;
    }

    @Override
    public String getName() {
        return "trollitem";
    }

    @Override
    public String getPermission() {
        return TrollItemsModule.PERMISSION;
    }

    @Override
    public String getDescription() {
        return "Выдать тролль-предмет (лук-крюк / наручники / гравитационная бомба)";
    }

    @Override
    public String getUsage() {
        return "/trollitem <bow|handcuffs|bomb> [ник]";
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();

        if (args.length < 1) {
            sender.sendMessage(this.module.getMessage("usage"));
            return;
        }

        String type = args[0].toLowerCase();
        if (!type.equals("bow") && !type.equals("handcuffs") && !type.equals("bomb")) {
            sender.sendMessage(this.module.getMessage("usage"));
            return;
        }

        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(this.module.getMessage("player-not-found").replace("{player}", args[1]));
                return;
            }
        } else if (sender instanceof Player selfPlayer) {
            target = selfPlayer;
        } else {
            sender.sendMessage(this.module.getMessage("console-needs-target"));
            return;
        }

        boolean isSelf = sender instanceof Player senderPlayer && senderPlayer.equals(target);
        Player finalTarget = target;

        this.plugin.getSchedulerManager().runEntityTask(target, "trollitem-give-" + target.getUniqueId(), () -> {
            if (!finalTarget.isOnline()) {
                return;
            }

            ItemStack item = switch (type) {
                case "bow" -> this.module.createTrollBow();
                case "handcuffs" -> this.module.createWand();
                default -> this.module.createBomb();
            };

            var leftover = finalTarget.getInventory().addItem(item);
            if (!leftover.isEmpty()) {
                finalTarget.getWorld().dropItem(finalTarget.getLocation(), item);
            }

            if (isSelf) {
                sender.sendMessage(this.module.getMessage("given-self-" + type));
            } else {
                sender.sendMessage(this.module.getMessage("given-other-" + type).replace("{player}", finalTarget.getName()));
                finalTarget.sendMessage(this.module.getMessage("received-" + type));
            }
        });
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> out = new ArrayList<>();
            for (String type : List.of("bow", "handcuffs", "bomb")) {
                if (type.startsWith(input)) {
                    out.add(type);
                }
            }
            return out;
        }
        if (args.length == 2) {
            String input = args[1].toLowerCase();
            List<String> out = new ArrayList<>();
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(input)) {
                    out.add(player.getName());
                }
            }
            return out;
        }
        return List.of();
    }
}
