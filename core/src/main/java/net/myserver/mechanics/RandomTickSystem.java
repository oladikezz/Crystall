package net.myserver.mechanics;

import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.timer.TaskSchedule;
import net.myserver.engine.AdaptiveTickEngine;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Оптимизированная система случайных тиков (Random Tick System).
 * Интегрирована с AdaptiveTickEngine (LOD-зоны) для исключения холостых тиков.
 */
public class RandomTickSystem {

    public static void register() {
        MinecraftServer.getSchedulerManager().buildTask(() -> {
            for (Instance instance : MinecraftServer.getInstanceManager().getInstances()) {
                for (Chunk chunk : instance.getChunks()) {
                    if (chunk.getViewers().isEmpty()) continue;

                    int cx = chunk.getChunkX();
                    int cz = chunk.getChunkZ();

                    // Проверка LOD зоны тикинга через AdaptiveTickEngine
                    if (!AdaptiveTickEngine.shouldTickChunk(cx, cz)) {
                        continue;
                    }

                    ThreadLocalRandom rand = ThreadLocalRandom.current();

                    for (int i = 0; i < 3; i++) {
                        int rx = (cx << 4) + rand.nextInt(16);
                        int rz = (cz << 4) + rand.nextInt(16);
                        int ry = 30 + rand.nextInt(50); // Диапазон высот 30-80

                        Block block = instance.getBlock(rx, ry, rz);
                        if (block.compare(Block.WHEAT)) {
                            String ageProp = block.getProperty("age");
                            int age = 0;
                            if (ageProp != null && !ageProp.isEmpty()) {
                                age = ageProp.charAt(0) - '0';
                            }
                            if (age < 7) {
                                instance.setBlock(rx, ry, rz, block.withProperty("age", String.valueOf(age + 1)));
                            }
                        } else if (block.compare(Block.SUGAR_CANE)) {
                            Block blockBelow = instance.getBlock(rx, ry - 1, rz);
                            Block blockAbove = instance.getBlock(rx, ry + 1, rz);
                            if (blockBelow.compare(Block.SUGAR_CANE) || blockBelow.compare(Block.SAND) || blockBelow.compare(Block.DIRT)) {
                                if (blockAbove.compare(Block.AIR)) {
                                    instance.setBlock(rx, ry + 1, rz, Block.SUGAR_CANE);
                                }
                            }
                        }
                    }
                }
            }
        }).repeat(TaskSchedule.tick(1)).schedule();
    }
}
