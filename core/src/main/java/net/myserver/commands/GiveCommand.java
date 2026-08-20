package net.myserver.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;
import net.myserver.permissions.RoleManager;

public class GiveCommand extends Command {
    public GiveCommand() {
        super("give");
        setCondition((sender, commandString) -> !(sender instanceof Player p) || RoleManager.isAdmin(p));

        var itemArg = ArgumentType.ItemStack("item");
        var amountArg = ArgumentType.Integer("amount").setDefaultValue(1);

        addSyntax((sender, context) -> {
            if (sender instanceof Player player) {
                ItemStack item = context.get(itemArg);
                int amount = context.get(amountArg);
                player.getInventory().addItemStack(item.withAmount(amount));
                player.sendMessage(Component.text("Выдано: " + item.material().name() + " x" + amount, NamedTextColor.GREEN));
            }
        }, itemArg, amountArg);
        
        addSyntax((sender, context) -> {
            if (sender instanceof Player player) {
                ItemStack item = context.get(itemArg);
                player.getInventory().addItemStack(item.withAmount(1));
                player.sendMessage(Component.text("Выдано: " + item.material().name() + " x1", NamedTextColor.GREEN));
            }
        }, itemArg);
    }
}
