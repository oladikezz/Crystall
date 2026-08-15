package net.myserver.mechanics;

import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.generator.GenerationUnit;
import net.minestom.server.instance.generator.Generator;
import net.minestom.server.instance.generator.UnitModifier;

import java.util.Random;

public class NetherGenerator implements Generator {
    private final Random random = new Random(54321);

    @Override
    public void generate(GenerationUnit unit) {
        UnitModifier modifier = unit.modifier();
        
        int startY = unit.absoluteStart().blockY();
        int endY = unit.absoluteEnd().blockY();
        
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = startY; y < endY; y++) {
                    if (y <= -64 || y >= 127) {
                        modifier.setBlock(x, y, z, Block.BEDROCK);
                    } else if (y <= 31 && y > -64) {
                        // Океан лавы
                        if (random.nextInt(100) < 10 && y > 10) {
                            modifier.setBlock(x, y, z, Block.NETHERRACK);
                        } else {
                            modifier.setBlock(x, y, z, Block.LAVA);
                        }
                    } else if (y < 127) {
                        // Массив адского камня с "полостью" посередине
                        if (y < 50 || y > 100) {
                            modifier.setBlock(x, y, z, Block.NETHERRACK);
                        } else {
                            // Воздушная прослойка с колоннами
                            if (random.nextInt(100) < 5) { // 5% шанс на блок (колонны/острова)
                                modifier.setBlock(x, y, z, Block.NETHERRACK);
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
