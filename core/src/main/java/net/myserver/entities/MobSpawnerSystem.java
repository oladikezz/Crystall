package net.myserver.entities;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.timer.TaskSchedule;
import net.myserver.engine.SpatialGrid;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Оптимизированная система спавна мобов (Mob Spawner System).
 * Включает строгий локальный Mob Cap через SpatialGrid и поиск твердого блока под ногами.
 */
public class MobSpawnerSystem {
    private static final int LOCAL_MOB_CAP = 15; // Максимум 15 монстров в радиусе 32 блоков вокруг игрока

    public static void register() {
        MinecraftServer.getSchedulerManager().buildTask(() -> {
            for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                Instance instance = player.getInstance();
                if (instance == null) continue;

                // 1. Проверка Mob Cap через SpatialGrid O(1)
                int nearbyMonsters = SpatialGrid.countEntitiesInRadius(instance, player.getPosition(), 32.0,
                        entity -> entity instanceof CustomZombie);

                if (nearbyMonsters >= LOCAL_MOB_CAP) {
                    continue; // Лимит мобов достигнут
                }

                long time = instance.getTime();
                boolean isNight = (time % 24000) > 13000;
                int py = (int) player.getPosition().y();
                boolean inCave = py < 40;
                boolean isBadWeather = net.myserver.mechanics.WeatherTimeSystem.isRaining || net.myserver.mechanics.WeatherTimeSystem.isThundering;

                if (!isNight && !inCave && !isBadWeather) {
                    continue;
                }

                ThreadLocalRandom rand = ThreadLocalRandom.current();
                if (rand.nextFloat() < 0.15f) { // 15% шанс попытки спавна
                    int rx = (int) player.getPosition().x() + (rand.nextInt(32) - 16);
                    int rz = (int) player.getPosition().z() + (rand.nextInt(32) - 16);

                    // 2. Ищем твердый опорный блок под ногами (до 8 блоков вниз/вверх)
                    int targetY = -1;
                    for (int dy = 5; dy >= -8; dy--) {
                        int checkY = py + dy;
                        if (checkY < -60 || checkY > 310) continue;

                        Block ground = instance.getBlock(rx, checkY, rz);
                        Block air1 = instance.getBlock(rx, checkY + 1, rz);
                        Block air2 = instance.getBlock(rx, checkY + 2, rz);

                        if (!ground.compare(Block.AIR) && air1.compare(Block.AIR) && air2.compare(Block.AIR)) {
                            targetY = checkY + 1;
                            break;
                        }
                    }

                    if (targetY != -1) {
                        CustomZombie zombie = new CustomZombie();
                        zombie.setInstance(instance, new Pos(rx + 0.5, targetY, rz + 0.5));
                    }
                }
            }
        }).repeat(TaskSchedule.seconds(2)).schedule();
    }
}
