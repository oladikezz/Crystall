package net.myserver.mechanics;

import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.timer.TaskSchedule;

import java.util.Random;

public class RandomTickSystem {
    private static final Random random = new Random();

    public static void register() {
        MinecraftServer.getSchedulerManager().buildTask(() -> {
            for (Instance instance : MinecraftServer.getInstanceManager().getInstances()) {
                for (Chunk chunk : instance.getChunks()) {
                    if (chunk.getViewers().isEmpty()) continue;

                    for (int i = 0; i < 3; i++) {
                        int rx = chunk.getChunkX() * 16 + random.nextInt(16);
                        int rz = chunk.getChunkZ() * 16 + random.nextInt(16);
                        int ry = 40 + random.nextInt(20); // Ограничиваемся высотой 40-60 для поверхности

                        Block block = instance.getBlock(rx, ry, rz);
                        if (block.compare(Block.WHEAT)) {
                            String ageProp = block.getProperty("age");
                            int age = ageProp != null ? Integer.parseInt(ageProp) : 0;
                            if (age < 7) {
                                instance.setBlock(rx, ry, rz, block.withProperty("age", String.valueOf(age + 1)));
                            }
                        } else if (block.compare(Block.SUGAR_CANE)) {
                            Block blockBelow = instance.getBlock(rx, ry - 1, rz);
                            Block blockAbove = instance.getBlock(rx, ry + 1, rz);
                            if (blockBelow.compare(Block.SUGAR_CANE) || blockBelow.compare(Block.SAND) || blockBelow.compare(Block.DIRT)) {
                                if (blockAbove.isAir()) {
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
