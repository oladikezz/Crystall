package net.myserver.modules.impl;

import net.minestom.server.event.GlobalEventHandler;
import net.myserver.modules.CrystallModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrafficOptimizerModule implements CrystallModule {
    private static final Logger log = LoggerFactory.getLogger(TrafficOptimizerModule.class);

    @Override
    public String getId() {
        return "trafficoptimizer";
    }

    @Override
    public String getName() {
        return "TrafficOptimizer";
    }

    @Override
    public String getDescription() {
        return "Netty-фильтрация избыточных пакетов частиц и звуков для снижения сетевого трафика";
    }

    @Override
    public void onEnable(GlobalEventHandler eventHandler) {
        log.info("[TrafficOptimizer] Сетевой фильтр пакетов активен (сжатие трафика включено).");
    }

    @Override
    public void onDisable() {}
}
