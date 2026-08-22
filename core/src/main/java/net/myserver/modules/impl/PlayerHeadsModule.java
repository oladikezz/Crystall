package net.myserver.modules.impl;

import net.minestom.server.entity.ItemEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.entity.EntityDeathEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.myserver.modules.CrystallModule;

public class PlayerHeadsModule implements CrystallModule {
    @Override
    public String getId() {
        return "playerheads";
    }

    @Override
    public String getName() {
        return "PlayerHeads";
    }

    @Override
    public String getDescription() {
        return "Выпадение головы игрока при смерти в PvP";
    }

    @Override
    public void onEnable(GlobalEventHandler eventHandler) {
        eventHandler.addListener(EntityDeathEvent.class, event -> {
            if (event.getEntity() instanceof Player victim && victim.getInstance() != null) {
                ItemStack head = ItemStack.builder(Material.PLAYER_HEAD)
                        .customName(net.kyori.adventure.text.Component.text("Голова игрока " + victim.getUsername(), net.kyori.adventure.text.format.NamedTextColor.GOLD))
                        .build();

                ItemEntity drop = new ItemEntity(head);
                drop.setInstance(victim.getInstance(), victim.getPosition().add(0, 0.5, 0));
            }
        });
    }

    @Override
    public void onDisable() {}
}
