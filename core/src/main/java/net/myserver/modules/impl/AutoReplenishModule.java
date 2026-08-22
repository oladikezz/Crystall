package net.myserver.modules.impl;

import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.myserver.modules.CrystallModule;

public class AutoReplenishModule implements CrystallModule {
    @Override
    public String getId() {
        return "autoreplenish";
    }

    @Override
    public String getName() {
        return "AutoReplenish";
    }

    @Override
    public String getDescription() {
        return "Автоматическое восполнение закончившейся стопки предметов в руке из инвентаря";
    }

    @Override
    public void onEnable(GlobalEventHandler eventHandler) {
        eventHandler.addListener(PlayerUseItemEvent.class, event -> {
            Player player = event.getPlayer();
            ItemStack item = event.getItemStack();

            if (item.amount() <= 1) {
                Material mat = item.material();
                PlayerInventory inv = player.getInventory();

                // Поиск такого же предмета в других слотах инвентаря
                for (int i = 9; i < inv.getSize(); i++) {
                    ItemStack slotItem = inv.getItemStack(i);
                    if (slotItem.material() == mat && !slotItem.isAir()) {
                        inv.setItemStack(i, ItemStack.AIR);
                        player.setItemInMainHand(slotItem);
                        break;
                    }
                }
            }
        });
    }

    @Override
    public void onDisable() {}
}
