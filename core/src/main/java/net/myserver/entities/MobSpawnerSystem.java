package net.myserver.entities;

import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.timer.TaskSchedule;
import net.minestom.server.coordinate.Pos;

import java.util.Random;

public class MobSpawnerSystem {
    private static final Random random = new Random();

    public static void register() {
        MinecraftServer.getSchedulerManager().buildTask(() -> {
            for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                Instance instance = player.getInstance();
                if (instance == null) continue;
                
                // Условие для темного места: время суток (ночь) или пещера (Y < 40)
                long time = instance.getTime();
                boolean isNight = (time % 24000) > 13000;
                
                if (random.nextFloat() < 0.1f) { // 10% шанс заспавнить зомби каждую секунду около игрока
                    int rx = (int) player.getPosition().x() + (random.nextInt(32) - 16);
                    int rz = (int) player.getPosition().z() + (random.nextInt(32) - 16);
                    int ry = (int) player.getPosition().y();
                    
                    boolean inCave = ry < 40;
                    boolean isBadWeather = net.myserver.mechanics.WeatherTimeSystem.isRaining || net.myserver.mechanics.WeatherTimeSystem.isThundering;
                    
                    if (isNight || inCave || isBadWeather) {
                        // Проверяем, что есть свободное место (2 блока воздуха)
                        if (instance.getBlock(rx, ry, rz).isAir() && instance.getBlock(rx, ry + 1, rz).isAir()) {
                            CustomZombie zombie = new CustomZombie();
                            zombie.setInstance(instance, new Pos(rx, ry, rz));
                        }
                    }
                }
            }
        }).repeat(TaskSchedule.seconds(1)).schedule();
    }
}
