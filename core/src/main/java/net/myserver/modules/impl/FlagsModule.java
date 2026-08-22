package net.myserver.modules.impl;

import net.minestom.server.event.GlobalEventHandler;
import net.myserver.modules.CrystallModule;

public class FlagsModule implements CrystallModule {
    @Override
    public String getId() {
        return "flags";
    }

    @Override
    public String getName() {
        return "Flags";
    }

    @Override
    public String getDescription() {
        return "Система флагов территорий и зон безопасности";
    }

    @Override
    public void onEnable(GlobalEventHandler eventHandler) {}

    @Override
    public void onDisable() {}
}
