package net.myserver.world;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.generator.GenerationUnit;
import net.minestom.server.instance.generator.Generator;
import net.myserver.utils.FastNoiseLite;
import org.jetbrains.annotations.NotNull;

/**
 * Высокооптимизированный процедурный генератор мира.
 * Использует 2D Noise Caching, пропуск пустых секций (Section Skip) и batch-заполнение.
 */
public class CustomGenerator implements Generator {
    private final FastNoiseLite heightNoise;
    private final FastNoiseLite temperatureNoise;
    private final FastNoiseLite humidityNoise;
    private final int seed;

    public CustomGenerator(int seed) {
        this.seed = seed;

        heightNoise = new FastNoiseLite(seed);
        heightNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        heightNoise.SetFrequency(0.005f);

        temperatureNoise = new FastNoiseLite(seed + 1);
        temperatureNoise.SetNoiseType(FastNoiseLite.NoiseType.Cellular);
        temperatureNoise.SetFrequency(0.005f);

        humidityNoise = new FastNoiseLite(seed + 2);
        humidityNoise.SetNoiseType(FastNoiseLite.NoiseType.Cellular);
        humidityNoise.SetFrequency(0.005f);
    }

    @Override
    public void generate(@NotNull GenerationUnit unit) {
        Point start = unit.absoluteStart();
        Point end = unit.absoluteEnd();

        int minX = start.blockX();
        int maxX = end.blockX();
        int minZ = start.blockZ();
        int maxZ = end.blockZ();
        int minY = start.blockY();
        int maxY = end.blockY();

        // 1. Быстрый ранний выход для неба
        if (minY > 65) {
            return;
        }

        // 2. Быстрое заполнение монолитной коренной породы и камня глубоко под землей
        if (maxY <= 25) {
            for (int x = minX; x < maxX; x++) {
                for (int z = minZ; z < maxZ; z++) {
                    for (int y = minY; y < maxY; y++) {
                        unit.modifier().setBlock(x, y, z, y <= 0 ? Block.BEDROCK : Block.STONE);
                    }
                }
            }
            return;
        }

        // 3. 2D Кэш высот и биомов для секции (16x16)
        int sizeX = maxX - minX;
        int sizeZ = maxZ - minZ;
        int[][] surfaceHeights = new int[sizeX][sizeZ];
        boolean[][] isDesertMap = new boolean[sizeX][sizeZ];
        boolean[][] isForestMap = new boolean[sizeX][sizeZ];

        int minSurfaceInChunk = 1000;
        int maxSurfaceInChunk = -1000;

        for (int ix = 0; ix < sizeX; ix++) {
            int x = minX + ix;
            for (int iz = 0; iz < sizeZ; iz++) {
                int z = minZ + iz;

                float h = heightNoise.GetNoise(x, z);
                int surfaceY = 40 + (int) (h * 15);
                surfaceHeights[ix][iz] = surfaceY;

                if (surfaceY < minSurfaceInChunk) minSurfaceInChunk = surfaceY;
                if (surfaceY > maxSurfaceInChunk) maxSurfaceInChunk = surfaceY;

                float temp = temperatureNoise.GetNoise(x, z);
                float humid = humidityNoise.GetNoise(x, z);
                isDesertMap[ix][iz] = temp > 0.3f && humid < 0.0f;
                isForestMap[ix][iz] = temp > -0.2f && humid > 0.2f;
            }
        }

        // Ранний выход если вся секция выше поверхности и нет деревьев
        if (minY > maxSurfaceInChunk + 8) {
            return;
        }

        // 4. Заполнение ландшафта
        for (int ix = 0; ix < sizeX; ix++) {
            int x = minX + ix;
            for (int iz = 0; iz < sizeZ; iz++) {
                int z = minZ + iz;
                int surfaceY = surfaceHeights[ix][iz];

                if (surfaceY < minY) continue;

                boolean isDesert = isDesertMap[ix][iz];
                boolean isForest = isForestMap[ix][iz];

                Block surfaceBlock = isDesert ? Block.SAND : Block.GRASS_BLOCK;
                Block subSurfaceBlock = isDesert ? Block.SANDSTONE : Block.DIRT;

                int localMaxY = Math.min(surfaceY, maxY - 1);

                for (int y = minY; y <= localMaxY; y++) {
                    if (y == surfaceY) {
                        unit.modifier().setBlock(x, y, z, surfaceBlock);
                    } else if (y >= surfaceY - 3) {
                        unit.modifier().setBlock(x, y, z, subSurfaceBlock);
                    } else if (y > 0) {
                        unit.modifier().setBlock(x, y, z, Block.STONE);
                    } else {
                        unit.modifier().setBlock(x, y, z, Block.BEDROCK);
                    }
                }

                // 5. Деревья (детерминированный хэш)
                if (isForest && minY <= surfaceY && maxY > surfaceY) {
                    float treeHash = coordHash(x, z, seed);
                    if (treeHash < 0.015f) {
                        generateTree(unit, x, surfaceY + 1, z, start, end);
                    }
                }

                // 6. Руины
                if (x == minX + 8 && z == minZ + 8 && minY <= surfaceY && maxY > surfaceY + 8) {
                    float ruinHash = coordHash(minX, minZ, seed + 99);
                    if (ruinHash < 0.04f) {
                        generateRuinedPillar(unit, x, surfaceY + 1, z, start, end);
                    }
                }
            }
        }
    }

    private static float coordHash(int x, int z, int seed) {
        long h = ((long) x * 3129871L) ^ ((long) z * 6122421L) ^ (long) seed;
        h = (h ^ (h >> 16)) * 0x45d9f3bL;
        h = (h ^ (h >> 16)) * 0x45d9f3bL;
        h = h ^ (h >> 16);
        return (float) (Math.abs(h % 100000)) / 100000.0f;
    }

    private void generateRuinedPillar(GenerationUnit unit, int x, int y, int z, Point start, Point end) {
        for (int i = 0; i < 4; i++) {
            setBlockIfInUnit(unit, x, y + i, z, Block.MOSSY_STONE_BRICKS, start, end);
        }
        setBlockIfInUnit(unit, x, y + 4, z, Block.CHEST, start, end);
    }

    private void generateTree(GenerationUnit unit, int x, int y, int z, Point start, Point end) {
        for (int i = 0; i < 5; i++) {
            setBlockIfInUnit(unit, x, y + i, z, Block.OAK_LOG, start, end);
        }

        for (int lx = -2; lx <= 2; lx++) {
            for (int lz = -2; lz <= 2; lz++) {
                for (int ly = 3; ly <= 5; ly++) {
                    if (lx == 0 && lz == 0 && ly < 5) continue;
                    if (Math.abs(lx) == 2 && Math.abs(lz) == 2 && ly == 5) continue;

                    setBlockIfInUnit(unit, x + lx, y + ly, z + lz, Block.OAK_LEAVES, start, end);
                }
            }
        }
    }

    private void setBlockIfInUnit(GenerationUnit unit, int x, int y, int z, Block block, Point start, Point end) {
        if (x >= start.blockX() && x < end.blockX() &&
                y >= start.blockY() && y < end.blockY() &&
                z >= start.blockZ() && z < end.blockZ()) {
            unit.modifier().setBlock(x, y, z, block);
        }
    }
}
