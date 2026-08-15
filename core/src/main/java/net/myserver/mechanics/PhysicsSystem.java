package net.myserver.mechanics;

import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.metadata.other.FallingBlockMeta;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.entity.EntityTickEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;

public class PhysicsSystem {

    public static void register(GlobalEventHandler handler) {
        handler.addListener(EntityTickEvent.class, event -> {
            if (event.getEntity().getEntityType() == EntityType.FALLING_BLOCK) {
                Entity entity = event.getEntity();
                
                // Если коснулся земли, ставим блок
                if (entity.isOnGround() && entity.getAliveTicks() > 5) {
                    FallingBlockMeta meta = (FallingBlockMeta) entity.getEntityMeta();
                    Block block = meta.getBlock();
                    
                    Point pos = entity.getPosition();
                    entity.getInstance().setBlock(pos.blockX(), pos.blockY(), pos.blockZ(), block);
                    entity.remove();
                    
                    triggerBlockUpdate(entity.getInstance(), pos.withX(pos.blockX()).withY(pos.blockY()).withZ(pos.blockZ()));
                } else if (entity.getAliveTicks() > 200) {
                    entity.remove();
                }
            }
        });

        handler.addListener(PlayerBlockPlaceEvent.class, event -> {
            triggerBlockUpdate(event.getInstance(), event.getBlockPosition());
        });
    }

    public static void triggerBlockUpdate(Instance instance, Point origin) {
        // Проверяем гравитацию для блока СВЕРХУ от сломанного/измененного
        Point above = origin.add(0, 1, 0);
        Block blockAbove = instance.getBlock(above);
        
        if (isFallingBlock(blockAbove)) {
            makeBlockFall(instance, above, blockAbove);
        }
        
        // Передаем сигнал для жидкостей
        FluidSystem.triggerFluidUpdate(instance, origin);
    }
    
    public static void makeBlockFall(Instance instance, Point pos, Block block) {
        instance.setBlock(pos, Block.AIR);
        
        Entity fallingBlock = new Entity(EntityType.FALLING_BLOCK);
        FallingBlockMeta meta = (FallingBlockMeta) fallingBlock.getEntityMeta();
        meta.setBlock(block);
        meta.setHasNoGravity(false);
        
        fallingBlock.setInstance(instance, pos.add(0.5, 0, 0.5));
    }

    private static boolean isFallingBlock(Block block) {
        return block.compare(Block.SAND) || block.compare(Block.GRAVEL);
    }
}
