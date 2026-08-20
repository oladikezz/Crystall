package net.myserver.storage;

import net.myserver.permissions.BanManager;
import net.myserver.permissions.RoleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Централизованный координатор сохранения и загрузки данных ядра.
 */
public class CoreDataManager {
    private static final Logger log = LoggerFactory.getLogger(CoreDataManager.class);

    public static void loadAll() {
        log.info("[CoreDataManager] Загрузка данных ядра сервера...");
        PlayerDataManager.init();
        RoleManager.init();
        BanManager.init();
        log.info("[CoreDataManager] Данные ядра успешно загружены.");
    }

    public static void saveAll() {
        log.info("[CoreDataManager] Сохранение данных ядра сервера...");
        RoleManager.save();
        BanManager.save();
        log.info("[CoreDataManager] Данные ядра успешно записаны на диск.");
    }
}
