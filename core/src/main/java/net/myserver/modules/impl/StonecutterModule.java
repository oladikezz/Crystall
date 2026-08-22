package net.myserver.modules.impl;

import net.minestom.server.event.GlobalEventHandler;
import net.myserver.modules.CrystallModule;

public class StonecutterModule implements CrystallModule {
    @Override
    public String getId() {
        return "stonecutter";
    }

    @Override
    public String getName() {
        return "StonecutterAdditions";
    }

    @Override
    public String getDescription() {
        return "Расширенные рецепты камнереза для всех видов строительных блоков";
    }

    @Override
    public void onEnable(GlobalEventHandler eventHandler) {}

    @Override
    public void onDisable() {}
}
