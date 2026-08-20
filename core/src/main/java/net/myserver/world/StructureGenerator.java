package net.myserver.world;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.batch.ChunkBatch;
import net.minestom.server.instance.block.Block;

import java.util.Random;

public class StructureGenerator {

    public static void generateStructures(ChunkBatch batch, int chunkX, int chunkZ, int surfaceY) {
        // Детерминированный рандом на основе координат чанка
        long seed = (long) chunkX * 341873128712L + (long) chunkZ * 132897987541L;
        Random random = new Random(seed);

        // 1. Шанс генерации Заброшенной Руинной Башни (1 чанк из 35)
        if (random.nextInt(35) == 0 && surfaceY > 50 && surfaceY < 120) {
            buildRuinedTower(batch, random, 8, surfaceY, 8);
        }

        // 2. Шанс генерации Деревенского Колодца (1 чанк из 45)
        if (random.nextInt(45) == 0 && surfaceY > 60 && surfaceY < 90) {
            buildDesertWell(batch, 4, surfaceY, 4);
        }

        // 3. Шанс генерации Подземной Сокровищницы (1 чанк из 25)
        if (random.nextInt(25) == 0) {
            int dungeonY = random.nextInt(40) - 20; // y от -20 до +20
            buildUndergroundDungeon(batch, random, 8, dungeonY, 8);
        }
    }

    private static void buildRuinedTower(ChunkBatch batch, Random rand, int x, int y, int z) {
        int height = 8 + rand.nextInt(6);
        for (int dy = 0; dy < height; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    int bx = x + dx;
                    int bz = z + dz;
                    if (bx < 0 || bx >= 16 || bz < 0 || bz >= 16) continue;

                    // Стены башни
                    if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                        if (rand.nextFloat() > 0.15f) { // Частично разрушенная
                            Block mat = rand.nextBoolean() ? Block.MOSSY_STONE_BRICKS : Block.STONE_BRICKS;
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

    private static void buildUndergroundDungeon(ChunkBatch batch, Random rand, int x, int y, int z) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = 0; dy <= 4; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int bx = x + dx;
                    int bz = z + dz;
                    if (bx < 0 || bx >= 16 || bz < 0 || bz >= 16) continue;

                    if (Math.abs(dx) == 3 || Math.abs(dz) == 3 || dy == 0 || dy == 4) {
                        Block b = rand.nextFloat() > 0.3f ? Block.MOSSY_COBBLESTONE : Block.COBBLESTONE;
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
