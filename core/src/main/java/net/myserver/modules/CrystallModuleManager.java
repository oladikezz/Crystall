package net.myserver.modules;

import net.minestom.server.event.GlobalEventHandler;
import net.myserver.Config;
import net.myserver.modules.impl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CrystallModuleManager {
    private static final Logger log = LoggerFactory.getLogger(CrystallModuleManager.class);

    private static final Map<String, CrystallModule> registeredModules = new LinkedHashMap<>();
    private static final Map<String, CrystallModule> activeModules = new ConcurrentHashMap<>();

    public static void init(GlobalEventHandler eventHandler, Config config) {
        registeredModules.clear();
        activeModules.clear();

        // ─── Регистрация всех 35 встроенных модулей ядра Crystall ──────────────
        register(new AccountsModule());
        register(new AdminListModule());
        register(new AlertModule());
        register(new AnnouncesModule());
        register(new AutoReplenishModule());
        register(new CheckerModule());
        register(new ClansModule());
        register(new CosmeticsModule());
        register(new CrownsModule());
        register(new DebugStickModule());
        register(new EssentialsModule());
        register(new FastLeavesModule());
        register(new FlagsModule());
        register(new HatModule());
        register(new HelpModule());
        register(new InvseeModule());
        register(new ItemDespawnModule());
        register(new ItemMetaModule());
        register(new KeepInventoryModule());
        register(new LightcraftModule());
        register(new MarryModule());
        register(new PhaseGuardModule());
        register(new PlayerHeadsModule());
        register(new QuietBanModule());
        register(new ScaleModule());
        register(new SpitModule());
        register(new StatsModule());
        register(new StonecutterModule());
        register(new StreamerModeModule());
        register(new TrafficOptimizerModule());
        register(new TrollItemsModule());
        register(new UserInfoModule());
        register(new VanishModule());
        register(new VoodoosModule());
        register(new WatcherModule());

        // ─── Загрузка и включение модулей ─────────────────────────────────────
        int enabledCount = 0;
        for (CrystallModule module : registeredModules.values()) {
            boolean isEnabled = config.isModuleEnabled(module.getId(), module.isEnabledByDefault());
            if (isEnabled) {
                try {
                    module.onEnable(eventHandler);
                    activeModules.put(module.getId(), module);
                    enabledCount++;
                } catch (Exception e) {
                    log.error("[CrystallModules] Ошибка при инициализации модуля {}: {}", module.getName(), e.getMessage(), e);
                }
            } else {
                log.info("[CrystallModules] Модуль {} отключен в config.yml.", module.getName());
            }
        }

        log.info("[CrystallModules] Успешно загружено и активировано {} из {} модулей ядра.", enabledCount, registeredModules.size());
    }

    private static void register(CrystallModule module) {
        registeredModules.put(module.getId(), module);
    }

    public static void disableAll() {
        for (CrystallModule module : activeModules.values()) {
            try {
                module.onDisable();
            } catch (Exception e) {
                log.warn("[CrystallModules] Ошибка при отключении модуля {}: {}", module.getName(), e.getMessage());
            }
        }
        activeModules.clear();
        log.info("[CrystallModules] Все модули ядра успешно отключены.");
    }

    public static CrystallModule getModule(String id) {
        return activeModules.get(id);
    }

    public static Collection<CrystallModule> getActiveModules() {
        return Collections.unmodifiableCollection(activeModules.values());
    }

    public static Collection<CrystallModule> getRegisteredModules() {
        return Collections.unmodifiableCollection(registeredModules.values());
    }
}
