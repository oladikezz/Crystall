package net.myserver.mechanics;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Point;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.timer.TaskSchedule;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class FluidSystem {
    private static final Queue<FluidTick> fluidTickQueue = new ConcurrentLinkedQueue<>();

    record FluidTick(Instance instance, Point pos, Block block, int delay) {}

    public static void register(GlobalEventHandler handler) {
        handler.addListener(PlayerBlockPlaceEvent.class, event -> {
            if (event.getBlock().compare(Block.WATER) || event.getBlock().compare(Block.LAVA)) {
                fluidTickQueue.add(new FluidTick(event.getInstance(), event.getBlockPosition(), event.getBlock(), 5));
            }
        });

        MinecraftServer.getSchedulerManager().buildTask(() -> {
            int size = fluidTickQueue.size();
            for (int i = 0; i < size; i++) {
                FluidTick tick = fluidTickQueue.poll();
                if (tick == null) break;
                
                if (tick.delay > 0) {
                    fluidTickQueue.add(new FluidTick(tick.instance, tick.pos, tick.block, tick.delay - 1));
                    continue;
                }
                
                processFluidTick(tick.instance, tick.pos);
            }
        }).repeat(TaskSchedule.tick(1)).schedule();
    }
    
    public static void triggerFluidUpdate(Instance instance, Point pos) {
        Point[] neighbors = { pos.add(1, 0, 0), pos.add(-1, 0, 0), pos.add(0, 0, 1), pos.add(0, 0, -1), pos.add(0, 1, 0) };
        for (Point n : neighbors) {
            Block b = instance.getBlock(n);
            if (b.compare(Block.WATER) || b.compare(Block.LAVA)) {
                fluidTickQueue.add(new FluidTick(instance, n, b, 5));
            }
        }
    }

    private static void processFluidTick(Instance instance, Point pos) {
        Block current = instance.getBlock(pos);
        if (!current.compare(Block.WATER) && !current.compare(Block.LAVA)) return;
        
        int level = 0;
        try {
            String lProp = current.getProperty("level");
            if (lProp != null) level = Integer.parseInt(lProp);
        } catch (Exception ignored) {}
        
        if (level >= 7) return; 
        
        Block nextFlow = current.withProperty("level", String.valueOf(level + 1));
        
        Point down = pos.add(0, -1, 0);
        Block blockDown = instance.getBlock(down);
        if (blockDown.isAir()) {
            instance.setBlock(down, nextFlow);
            fluidTickQueue.add(new FluidTick(instance, down, nextFlow, 5));
            return;
        }
        
        if (blockDown.isSolid()) {
            Point[] sides = { pos.add(1, 0, 0), pos.add(-1, 0, 0), pos.add(0, 0, 1), pos.add(0, 0, -1) };
            for (Point side : sides) {
                Block b = instance.getBlock(side);
                if (b.isAir()) {
                    instance.setBlock(side, nextFlow);
                    fluidTickQueue.add(new FluidTick(instance, side, nextFlow, 5));
                }
            }
        }
    }
}
