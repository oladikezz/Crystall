package net.myserver.modules.impl;

import net.minestom.server.coordinate.Point;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.myserver.modules.CrystallModule;

public class FastLeavesModule implements CrystallModule {
    @Override
    public String getId() {
        return "fastleaves";
    }

    @Override
    public String getName() {
        return "FastLeaves";
    }

    @Override
    public String getDescription() {
        return "Быстрое и реалистичное опадание листвы при срубе дерева";
    }

    @Override
    public void onEnable(GlobalEventHandler eventHandler) {
        eventHandler.addListener(PlayerBlockBreakEvent.class, event -> {
            Block block = event.getBlock();
            String name = block.name().toLowerCase();

            // Если сломано бревно
            if (name.contains("log") || name.contains("wood")) {
                Instance instance = event.getInstance();
                Point pos = event.getBlockPosition();

                // Проверяем соседние блоки на наличие листвы
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dy = -1; dy <= 4; dy++) {
                        for (int dz = -3; dz <= 3; dz++) {
                            Point leafPos = pos.add(dx, dy, dz);
                            Block target = instance.getBlock(leafPos);
                            if (target.name().toLowerCase().contains("leaves")) {
                                instance.setBlock(leafPos, Block.AIR);
                            }
                        }
                    }
                }
            }
        });
    }

    @Override
    public void onDisable() {}
}
