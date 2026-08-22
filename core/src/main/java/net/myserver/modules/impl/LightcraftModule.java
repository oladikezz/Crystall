package net.myserver.modules.impl;

import net.minestom.server.event.GlobalEventHandler;
import net.myserver.modules.CrystallModule;

public class LightcraftModule implements CrystallModule {
    @Override
    public String getId() {
        return "lightcraft";
    }

    @Override
    public String getName() {
        return "Lightcraft";
    }

    @Override
    public String getDescription() {
        return "Динамическое освещение от факелов и светящихся предметов в руке";
    }

    @Override
    public void onEnable(GlobalEventHandler eventHandler) {}

    @Override
    public void onDisable() {}
}
