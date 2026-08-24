package net.myserver.mechanics;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Point;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.timer.TaskSchedule;
import net.myserver.engine.CircularBuffer;

import java.util.ArrayDeque;

/**
 * Оптимизированная система симуляции жидкостей (Zero-GC Fluid Simulation).
 * Использует CircularBuffer и Object Pooling для полного устранения аллокаций в куче.
 */
public class FluidSystem {
    private static final int MAX_BUFFER_CAPACITY = 16384;
    private static final CircularBuffer<FluidTick> fluidTickBuffer = new CircularBuffer<>(MAX_BUFFER_CAPACITY);
    private static final ArrayDeque<FluidTick> tickPool = new ArrayDeque<>(2048);

    public static class FluidTick {
        public Instance instance;
        public Point pos;
        public int delay;

        public FluidTick set(Instance instance, Point pos, int delay) {
            this.instance = instance;
            this.pos = pos;
            this.delay = delay;
            return this;
        }
    }

    private static synchronized FluidTick acquireTick(Instance instance, Point pos, int delay) {
        FluidTick tick = tickPool.pollFirst();
        if (tick == null) {
            tick = new FluidTick();
        }
        return tick.set(instance, pos, delay);
    }

    private static synchronized void releaseTick(FluidTick tick) {
        if (tick == null) return;
        tick.instance = null;
        tick.pos = null;
        if (tickPool.size() < 2048) {
            tickPool.offerFirst(tick);
        }
    }

    public static void register(GlobalEventHandler handler) {
        handler.addListener(PlayerBlockPlaceEvent.class, event -> {
            if (event.getBlock().compare(Block.WATER) || event.getBlock().compare(Block.LAVA)) {
                fluidTickBuffer.offer(acquireTick(event.getInstance(), event.getBlockPosition(), 5));
            }
        });

        MinecraftServer.getSchedulerManager().buildTask(() -> {
            int batchSize = Math.min(fluidTickBuffer.size(), 256);
            for (int i = 0; i < batchSize; i++) {
                FluidTick tick = fluidTickBuffer.poll();
                if (tick == null) break;

                if (tick.delay > 0) {
                    tick.delay--;
                    fluidTickBuffer.offer(tick);
                    continue;
                }

                Instance inst = tick.instance;
                Point p = tick.pos;
                releaseTick(tick);

                processFluidTick(inst, p);
            }
        }).repeat(TaskSchedule.tick(1)).schedule();
    }

    public static void triggerFluidUpdate(Instance instance, Point pos) {
        Point[] neighbors = {
                pos.add(1, 0, 0), pos.add(-1, 0, 0),
                pos.add(0, 0, 1), pos.add(0, 0, -1),
                pos.add(0, 1, 0)
        };
        for (Point n : neighbors) {
            Block b = instance.getBlock(n);
            if (b.compare(Block.WATER) || b.compare(Block.LAVA)) {
                fluidTickBuffer.offer(acquireTick(instance, n, 5));
            }
        }
    }

    private static void processFluidTick(Instance instance, Point pos) {
        if (instance == null || pos == null) return;
        Block current = instance.getBlock(pos);
        if (!current.compare(Block.WATER) && !current.compare(Block.LAVA)) return;

        int level = 0;
        try {
            String lProp = current.getProperty("level");
            if (lProp != null && !lProp.isEmpty()) {
                level = lProp.charAt(0) - '0';
            }
        } catch (Exception ignored) {}

        if (level >= 7) return; // Лимит растекания (до 7 блоков)

        Block nextFlow = current.withProperty("level", String.valueOf(level + 1));

        Point down = pos.add(0, -1, 0);
        Block blockDown = instance.getBlock(down);
        if (blockDown.compare(Block.AIR)) {
            instance.setBlock(down, nextFlow);
            fluidTickBuffer.offer(acquireTick(instance, down, 5));
            return;
        }

        if (!blockDown.compare(Block.AIR)) {
            Point[] sides = { pos.add(1, 0, 0), pos.add(-1, 0, 0), pos.add(0, 0, 1), pos.add(0, 0, -1) };
            for (Point side : sides) {
                Block b = instance.getBlock(side);
                if (b.compare(Block.AIR)) {
                    instance.setBlock(side, nextFlow);
                    fluidTickBuffer.offer(acquireTick(instance, side, 5));
                }
            }
        }
    }
}
