package net.myserver.mechanics;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.generator.GenerationUnit;
import net.minestom.server.instance.generator.Generator;
import net.minestom.server.instance.generator.UnitModifier;
import net.myserver.utils.FastNoiseLite;
import org.jetbrains.annotations.NotNull;

public class NetherGenerator implements Generator {
    private final FastNoiseLite netherNoise;

    public NetherGenerator() {
        netherNoise = new FastNoiseLite(54321);
        netherNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        netherNoise.SetFrequency(0.02f);
    }

    @Override
    public void generate(@NotNull GenerationUnit unit) {
        UnitModifier modifier = unit.modifier();
        Point start = unit.absoluteStart();
        Point end = unit.absoluteEnd();
        
        int startX = start.blockX();
        int endX = end.blockX();
        int startY = start.blockY();
        int endY = end.blockY();
        int startZ = start.blockZ();
        int endZ = end.blockZ();
        
        for (int x = startX; x < endX; x++) {
            for (int z = startZ; z < endZ; z++) {
                for (int y = startY; y < endY; y++) {
                    if (y <= 0 || y >= 127) {
                        modifier.setBlock(x, y, z, Block.BEDROCK);
                    } else if (y <= 31) {
                        // Океан лавы на нижних уровнях
                        float n = netherNoise.GetNoise(x, y, z);
                        if (n > 0.2f) {
                            modifier.setBlock(x, y, z, Block.NETHERRACK);
                        } else {
                            modifier.setBlock(x, y, z, Block.LAVA);
                        }
                    } else {
                        // Адский рельеф с пещерами и пустотами
                        if (y < 45 || y > 115) {
                            modifier.setBlock(x, y, z, Block.NETHERRACK);
                        } else {
                            float n = netherNoise.GetNoise(x, y * 1.5f, z);
                            if (n > 0.1f) {
                                modifier.setBlock(x, y, z, Block.NETHERRACK);
                            } else if (n > 0.05f && y < 60) {
                                modifier.setBlock(x, y, z, Block.SOUL_SAND);
                            } else {
                                modifier.setBlock(x, y, z, Block.AIR);
                            }
                        }
                    }
                }
            }
        }
    }
}
