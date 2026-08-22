package net.myserver.modules.impl;

import net.minestom.server.event.GlobalEventHandler;
import net.myserver.modules.CrystallModule;

public class PhaseGuardModule implements CrystallModule {
    @Override
    public String getId() {
        return "phaseguard";
    }

    @Override
    public String getName() {
        return "PhaseGuard";
    }

    @Override
    public String getDescription() {
        return "Защита от фазирования и проникновения сквозь закрытые двери и твердые блоки";
    }

    @Override
    public void onEnable(GlobalEventHandler eventHandler) {}

    @Override
    public void onDisable() {}
}
