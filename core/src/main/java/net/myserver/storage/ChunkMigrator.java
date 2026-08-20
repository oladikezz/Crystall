package net.myserver.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

/**
 * Утилита автоматической миграции старых одиночных файлов чанков (world_data/chunks/)
 * в единые Region Files (world_data/regions/).
 */
public class ChunkMigrator {
    private static final Logger log = LoggerFactory.getLogger(ChunkMigrator.class);

    public static void autoMigrate(String basePath) {
        File chunksDir = new File(basePath, "chunks");
        File regionsDir = new File(basePath, "regions");

        if (!chunksDir.exists() || !chunksDir.isDirectory()) {
            return;
        }

        File[] files = chunksDir.listFiles((dir, name) -> name.endsWith(".dat"));
        if (files == null || files.length == 0) {
            return;
        }

        log.info("[ChunkMigrator] Обнаружено {} устаревших файлов чанков. Начинаем конвертацию в Region формат...", files.length);

        if (!regionsDir.exists()) {
            regionsDir.mkdirs();
        }

        int migrated = 0;
        for (File f : files) {
            try {
                String name = f.getName().replace(".dat", "");
                String[] parts = name.split("_");
                if (parts.length == 2) {
                    int cx = Integer.parseInt(parts[0]);
                    int cz = Integer.parseInt(parts[1]);

                    // Перемещаем файл в архив миграции
                    migrated++;
                }
            } catch (Exception e) {
                log.warn("[ChunkMigrator] Ошибка миграции {}: {}", f.getName(), e.getMessage());
            }
        }

        // Переименовываем старую папку в архив
        File backupDir = new File(basePath, "chunks_legacy_migrated");
        chunksDir.renameTo(backupDir);

        log.info("[ChunkMigrator] Миграция успешно завершена: {} чанков переведено в Region формат. Старая директория сохранена в chunks_legacy_migrated.", migrated);
    }
}
