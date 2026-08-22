package net.myserver.storage;

import net.minestom.server.MinecraftServer;
import net.minestom.server.timer.TaskSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class BackupManager {
    private static final Logger log = LoggerFactory.getLogger(BackupManager.class);
    private static final File BACKUP_DIR = new File("backups");
    private static final int MAX_BACKUPS = 5;

    public static void register() {
        if (!BACKUP_DIR.exists()) BACKUP_DIR.mkdirs();

        MinecraftServer.getSchedulerManager().buildTask(() -> {
            log.info("[BackupManager] Starting scheduled world backup...");
            try {
                createBackup();
                rotateBackups();
                log.info("[BackupManager] World backup completed successfully.");
            } catch (Exception e) {
                log.error("[BackupManager] Error during world backup: {}", e.getMessage(), e);
            }
        }).repeat(TaskSchedule.minutes(60)).schedule();
    }

    private static void createBackup() throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"));
        File zipFile = new File(BACKUP_DIR, "backup_" + timestamp + ".zip");
        File sourceDir = new File("world_data");
        if (!sourceDir.exists()) return;

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            Files.walk(sourceDir.toPath())
                 .filter(path -> !Files.isDirectory(path))
                 .forEach(path -> {
                     ZipEntry zipEntry = new ZipEntry(sourceDir.toPath().relativize(path).toString().replace('\\', '/'));
                     try {
                         zos.putNextEntry(zipEntry);
                         Files.copy(path, zos);
                         zos.closeEntry();
                     } catch (IOException e) {
                         log.warn("[BackupManager] Failed to compress file {}: {}", path, e.getMessage());
                     }
                 });
        }
    }

    private static void rotateBackups() {
        File[] files = BACKUP_DIR.listFiles((dir, name) -> name.endsWith(".zip"));
        if (files != null && files.length > MAX_BACKUPS) {
            Arrays.sort(files, Comparator.comparingLong(File::lastModified));
            for (int i = 0; i < files.length - MAX_BACKUPS; i++) {
                files[i].delete();
            }
        }
    }
}
