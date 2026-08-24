package net.myserver.world;

import net.minestom.server.instance.batch.ChunkBatch;
import net.minestom.server.instance.block.Block;

/**
 * Высокопроизводительный процедурный генератор структур.
 * Использует Zero-Allocation детерминированный генератор случайных чисел (SplitMix64 style).
 */
public class StructureGenerator {

    private static long nextSeed(long state) {
        state += 0x9E3779B97F4A7C15L;
        long z = state;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public static void generateStructures(ChunkBatch batch, int chunkX, int chunkZ, int surfaceY) {
        long seed = ((long) chunkX * 341873128712L) ^ ((long) chunkZ * 132897987541L);
        long rand1 = nextSeed(seed);
        long rand2 = nextSeed(rand1);

        // 1. Шанс генерации Заброшенной Руинной Башни (1 чанк из 35)
        if ((Math.abs(rand1) % 35) == 0 && surfaceY > 50 && surfaceY < 120) {
            buildRuinedTower(batch, rand2, 8, surfaceY, 8);
        }

        // 2. Шанс генерации Деревенского Колодца (1 чанк из 45)
        if ((Math.abs(rand2) % 45) == 0 && surfaceY > 60 && surfaceY < 90) {
            buildDesertWell(batch, 4, surfaceY, 4);
        }

        // 3. Шанс генерации Подземной Сокровищницы (1 чанк из 25)
        long rand3 = nextSeed(rand2);
        if ((Math.abs(rand3) % 25) == 0) {
            int dungeonY = (int) (Math.abs(rand3) % 40) - 20; // y от -20 до +20
            buildUndergroundDungeon(batch, rand3, 8, dungeonY, 8);
        }
    }

    private static void buildRuinedTower(ChunkBatch batch, long seed, int x, int y, int z) {
        int height = 8 + (int) (Math.abs(seed) % 6);
        long state = seed;

        for (int dy = 0; dy < height; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    int bx = x + dx;
                    int bz = z + dz;
                    if (bx < 0 || bx >= 16 || bz < 0 || bz >= 16) continue;

                    // Стены башни
                    if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                        state = nextSeed(state);
                        if ((Math.abs(state) % 100) > 15) { // Частично разрушенная
                            Block mat = (Math.abs(state) % 2 == 0) ? Block.MOSSY_STONE_BRICKS : Block.STONE_BRICKS;
                            batch.setBlock(bx, y + dy, bz, mat);
                        }
                    } else if (dy == 0) {
                        batch.setBlock(bx, y, bz, Block.COBBLESTONE);
                    }
                }
            }
        }
        // Сундук на вершине руин
        batch.setBlock(x, y + 1, z, Block.CHEST);
    }

    private static void buildDesertWell(ChunkBatch batch, int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int bx = x + dx;
                int bz = z + dz;
                if (bx < 0 || bx >= 16 || bz < 0 || bz >= 16) continue;

                batch.setBlock(bx, y, bz, Block.COBBLESTONE);
                if (dx == 0 && dz == 0) {
                    batch.setBlock(bx, y, bz, Block.WATER);
                } else if ((Math.abs(dx) == 1 && dz == 0) || (Math.abs(dz) == 1 && dx == 0)) {
                    batch.setBlock(bx, y + 1, bz, Block.OAK_FENCE);
                }
            }
        }
    }

    private static void buildUndergroundDungeon(ChunkBatch batch, long seed, int x, int y, int z) {
        long state = seed;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = 0; dy <= 4; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int bx = x + dx;
                    int bz = z + dz;
                    if (bx < 0 || bx >= 16 || bz < 0 || bz >= 16) continue;

                    if (Math.abs(dx) == 3 || Math.abs(dz) == 3 || dy == 0 || dy == 4) {
                        state = nextSeed(state);
                        Block b = ((Math.abs(state) % 100) > 30) ? Block.MOSSY_COBBLESTONE : Block.COBBLESTONE;
                        batch.setBlock(bx, y + dy, bz, b);
                    } else {
                        batch.setBlock(bx, y + dy, bz, Block.AIR);
                    }
                }
            }
        }
        // Сундук с сокровищами в центре
        batch.setBlock(x, y + 1, z, Block.CHEST);
    }
}
