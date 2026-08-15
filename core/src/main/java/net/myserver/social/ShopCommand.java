package net.myserver.social;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

public class ShopCommand extends Command {
    public ShopCommand() {
        super("buy");

        var materialArg = ArgumentType.Word("item");
        var amountArg = ArgumentType.Integer("amount");

        addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) return;

            String materialName = context.get(materialArg).toUpperCase();
            int amount = context.get(amountArg);
            
            if (amount <= 0 || amount > 64) {
                player.sendMessage(Component.text("Количество должно быть от 1 до 64.", NamedTextColor.RED));
                return;
            }

            Material material = Material.fromNamespaceId(materialName);
            if (material == null) {
                player.sendMessage(Component.text("Предмет не найден. Используйте английские названия (например, diamond).", NamedTextColor.RED));
                return;
            }

            // Фиксированная цена: 10 монет за 1 единицу любого предмета (для базового функционала)
            double cost = amount * 10.0;
            
            if (EconomyManager.removeBalance(player.getUuid(), cost)) {
                player.getInventory().addItemStack(ItemStack.of(material, amount));
                player.sendMessage(Component.text("Вы успешно купили " + amount + "x " + materialName + " за " + cost + " монет.", NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("Недостаточно средств. Нужно " + cost + " монет.", NamedTextColor.RED));
            }
        }, materialArg, amountArg);
    }
}
