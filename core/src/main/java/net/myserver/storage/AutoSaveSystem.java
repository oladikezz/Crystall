package net.myserver.storage;

import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.timer.TaskSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoSaveSystem {
    private static final Logger log = LoggerFactory.getLogger(AutoSaveSystem.class);

    public static void register() {
        MinecraftServer.getSchedulerManager().buildTask(() -> {
            log.info("[AutoSave] Starting background world and player auto-save...");
            
            try {
                for (Instance instance : MinecraftServer.getInstanceManager().getInstances()) {
                    instance.saveChunksToStorage();
                    instance.saveInstance();
                }
                
                for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                    PlayerDataManager.savePlayer(player);
                }
                
                log.info("[AutoSave] Auto-save completed successfully.");
            } catch (Exception e) {
                log.error("[AutoSave] Error during auto-save: {}", e.getMessage(), e);
            }
        }).repeat(TaskSchedule.minutes(5)).schedule();
    }
}
