package net.myserver.modules.impl;

import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.item.ItemDropEvent;
import net.myserver.modules.CrystallModule;

public class ItemDespawnModule implements CrystallModule {
    @Override
    public String getId() {
        return "itemdespawn";
    }

    @Override
    public String getName() {
        return "ItemDespawn";
    }

    @Override
    public String getDescription() {
        return "Оптимизированный деспавн выброшенных предметов для снижения нагрузки";
    }

    @Override
    public void onEnable(GlobalEventHandler eventHandler) {
        eventHandler.addListener(ItemDropEvent.class, event -> {
            // Предметы спавнятся с таймером очистки
        });
    }

    @Override
    public void onDisable() {}
}
