package net.myserver.modules.impl;

import net.minestom.server.event.GlobalEventHandler;
import net.myserver.modules.CrystallModule;

public class KeepInventoryModule implements CrystallModule {
    @Override
    public String getId() {
        return "keepinventory";
    }

    @Override
    public String getName() {
        return "KeepInventory";
    }

    @Override
    public String getDescription() {
        return "Сохранение инвентаря игрока при смерти";
    }

    @Override
    public void onEnable(GlobalEventHandler eventHandler) {}

    @Override
    public void onDisable() {}
}
