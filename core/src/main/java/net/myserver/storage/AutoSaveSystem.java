package net.myserver.storage;

import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.timer.TaskSchedule;

public class AutoSaveSystem {
    public static void register() {
        MinecraftServer.getSchedulerManager().buildTask(() -> {
            System.out.println("Starting background auto-save...");
            
            for (Instance instance : MinecraftServer.getInstanceManager().getInstances()) {
                instance.saveChunksToStorage();
                instance.saveInstance();
            }
            
            for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                PlayerDataManager.savePlayer(player);
            }
            
            System.out.println("Auto-save completed.");
        }).repeat(TaskSchedule.minutes(5)).schedule();
    }
}
