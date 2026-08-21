package net.myserver.redstone;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.myserver.engine.FastMath;
import net.myserver.engine.primitive.LongOpenHashSet;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Высокопроизводительный Redstone-движок на основе BFS-очереди (Alternate Current style).
 * Исключает рекурсию, переполнение стека и повторные строковые аллокации.
 */
public class RedstoneSystem {
    private static final String[] POWER_STRINGS = {
        "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15"
    };

    private static final Vec[] NEIGHBORS = {
        new Vec(1, 0, 0), new Vec(-1, 0, 0),
        new Vec(0, 0, 1), new Vec(0, 0, -1),
        new Vec(0, 1, 0), new Vec(0, -1, 0)
    };

    public static void register(GlobalEventHandler handler) {
        handler.addListener(PlayerBlockPlaceEvent.class, event -> {
            propagateRedstone(event.getInstance(), event.getBlockPosition());
        });

        handler.addListener(PlayerBlockBreakEvent.class, event -> {
            propagateRedstone(event.getInstance(), event.getBlockPosition());
        });
    }

    private static String getProp(Block b, String key, String def) {
        String val = b.getProperty(key);
        return val != null ? val : def;
    }

    private static int parsePower(String val) {
        if (val == null || val.isEmpty()) return 0;
        int len = val.length();
        if (len == 1) {
            char c = val.charAt(0);
            if (c >= '0' && c <= '9') return c - '0';
        } else if (len == 2) {
            if (val.charAt(0) == '1') {
                char c = val.charAt(1);
                if (c >= '0' && c <= '5') return 10 + (c - '0');
            }
        }
        return 0;
    }

    /**
     * Полноценная BFS очередь обновления сигналов редстоуна.
     */
    public static void propagateRedstone(Instance instance, Point start) {
        if (instance == null || start == null) return;

        Queue<Point> queue = new ArrayDeque<>();
        LongOpenHashSet visited = new LongOpenHashSet(64);

        queue.add(start);
        visited.add(FastMath.packBlockPos(start.blockX(), start.blockY(), start.blockZ()));

        int iterations = 0;
        final int MAX_ITERATIONS = 128; // Защита от бесконечных цепей

        while (!queue.isEmpty() && iterations++ < MAX_ITERATIONS) {
            Point origin = queue.poll();

            for (Vec offset : NEIGHBORS) {
                Point pos = origin.add(offset);
                long packed = FastMath.packBlockPos(pos.blockX(), pos.blockY(), pos.blockZ());
                Block block = instance.getBlock(pos);
                if (block.compare(Block.AIR)) continue;

                // 1. Редстоун пыль
                if (block.compare(Block.REDSTONE_WIRE)) {
                    int expectedPower = calculatePower(instance, pos);
                    int currentPower = parsePower(getProp(block, "power", "0"));

                    if (currentPower != expectedPower) {
                        instance.setBlock(pos, block.withProperty("power", POWER_STRINGS[expectedPower]));
                        if (visited.add(packed)) {
                            queue.add(pos);
                        }
                    }
                }
                // 2. Редстоун факел
                else if (block.compare(Block.REDSTONE_TORCH) || block.compare(Block.REDSTONE_WALL_TORCH)) {
                    boolean isLit = "true".equals(getProp(block, "lit", "true"));
                    boolean shouldBeLit = !isAnyAdjacentPowered(instance, pos);

                    if (isLit != shouldBeLit) {
                        instance.setBlock(pos, block.withProperty("lit", shouldBeLit ? "true" : "false"));
                        if (visited.add(packed)) {
                            queue.add(pos);
                        }
                    }
                }
                // 3. Поршень
                else if (block.compare(Block.PISTON) || block.compare(Block.STICKY_PISTON)) {
                    boolean powered = isAnyAdjacentPowered(instance, pos);
                    boolean extended = "true".equals(getProp(block, "extended", "false"));

                    if (powered && !extended) {
                        pushPiston(instance, pos, block);
                        if (visited.add(packed)) queue.add(pos);
                    } else if (!powered && extended) {
                        retractPiston(instance, pos, block);
                        if (visited.add(packed)) queue.add(pos);
                    }
                }
                // 4. Двери и люки
                else if (block.name().endsWith("_door") || block.name().endsWith("_trapdoor")) {
                    boolean powered = isAnyAdjacentPowered(instance, pos);
                    boolean isOpen = "true".equals(getProp(block, "open", "false"));

                    if (powered != isOpen) {
                        instance.setBlock(pos, block.withProperty("open", powered ? "true" : "false"));
                    }
                }
            }
        }
    }

    private static int calculatePower(Instance instance, Point pos) {
        int maxPower = 0;

        for (Vec offset : NEIGHBORS) {
            Point n = pos.add(offset);
            Block b = instance.getBlock(n);
            if (b.name().endsWith("redstone_torch")) {
                if ("true".equals(getProp(b, "lit", "true"))) return 15;
            } else if (b.compare(Block.REDSTONE_BLOCK)) {
                return 15;
            } else if (b.compare(Block.REDSTONE_WIRE)) {
                int p = parsePower(getProp(b, "power", "0"));
                if (p - 1 > maxPower) maxPower = p - 1;
            }
        }
        return maxPower;
    }

    private static boolean isAnyAdjacentPowered(Instance instance, Point pos) {
        return calculatePower(instance, pos) > 0;
    }

    private static void pushPiston(Instance instance, Point pos, Block piston) {
        String facing = getProp(piston, "facing", "up");
        Vec dir = getDirection(facing);

        Point targetPos = pos.add(dir);
        Block targetBlock = instance.getBlock(targetPos);

        if (targetBlock.compare(Block.AIR)) {
            instance.setBlock(pos, piston.withProperty("extended", "true"));
            instance.setBlock(targetPos, Block.PISTON_HEAD.withProperty("facing", facing));
        } else if (!targetBlock.compare(Block.OBSIDIAN) && !targetBlock.compare(Block.BEDROCK)) {
            Point nextPos = targetPos.add(dir);
            if (instance.getBlock(nextPos).compare(Block.AIR)) {
                instance.setBlock(nextPos, targetBlock);
                instance.setBlock(targetPos, Block.PISTON_HEAD.withProperty("facing", facing));
                instance.setBlock(pos, piston.withProperty("extended", "true"));
            }
        }
    }

    private static void retractPiston(Instance instance, Point pos, Block piston) {
        String facing = getProp(piston, "facing", "up");
        Vec dir = getDirection(facing);

        Point headPos = pos.add(dir);
        if (instance.getBlock(headPos).compare(Block.PISTON_HEAD)) {
            instance.setBlock(headPos, Block.AIR);
        }
        instance.setBlock(pos, piston.withProperty("extended", "false"));
    }

    private static Vec getDirection(String facing) {
        return switch (facing) {
            case "down" -> new Vec(0, -1, 0);
            case "up" -> new Vec(0, 1, 0);
            case "north" -> new Vec(0, 0, -1);
            case "south" -> new Vec(0, 0, 1);
            case "west" -> new Vec(-1, 0, 0);
            case "east" -> new Vec(1, 0, 0);
            default -> new Vec(0, 1, 0);
        };
    }
}
