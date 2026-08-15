package net.myserver.world;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.generator.GenerationUnit;
import net.minestom.server.instance.generator.Generator;
import net.myserver.utils.FastNoiseLite;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

public class CustomGenerator implements Generator {
    private final FastNoiseLite heightNoise;
    private final FastNoiseLite temperatureNoise;
    private final FastNoiseLite humidityNoise;
    private final Random random;

    public CustomGenerator(int seed) {
        random = new Random(seed);

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

        for (int x = start.blockX(); x < end.blockX(); x++) {
            for (int z = start.blockZ(); z < end.blockZ(); z++) {
                
                // Рассчитываем высоту
                float h = heightNoise.GetNoise(x, z);
                int baseHeight = 40;
                int surfaceY = baseHeight + (int) (h * 15);
                
                // Температура и влажность
                float temp = temperatureNoise.GetNoise(x, z);
                float humid = humidityNoise.GetNoise(x, z);

                // Определение биома
                boolean isDesert = temp > 0.3f && humid < 0.0f;
                boolean isForest = temp > -0.2f && humid > 0.2f;
                
                Block surfaceBlock = isDesert ? Block.SAND : Block.GRASS_BLOCK;
                Block subSurfaceBlock = isDesert ? Block.SANDSTONE : Block.DIRT;

                for (int y = start.blockY(); y < end.blockY(); y++) {
                    if (y > surfaceY) {
                        continue;
                    }
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

                // Генерация деревьев (Только в лесу и на уровне поверхности)
                if (isForest && start.blockY() <= surfaceY && end.blockY() > surfaceY) {
                    if (random.nextFloat() < 0.01f) { // 1% шанс на блок
                        generateTree(unit, x, surfaceY + 1, z, start, end);
                    }
                }
            }
        }
    }
    
    private void generateTree(GenerationUnit unit, int x, int y, int z, Point start, Point end) {
        // Ствол
        for (int i = 0; i < 5; i++) {
            setBlockIfInUnit(unit, x, y + i, z, Block.OAK_LOG, start, end);
        }
        
        // Листва
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
