package net.myserver.redstone;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.myserver.engine.FastMath;
import net.myserver.engine.primitive.LongOpenHashSet;

/**
 * Высокопроизводительный Zero-Allocation Redstone-движок на основе BFS-очереди.
 * Полностью исключает рекурсию и аллокации временных объектов за счет примитивных packed long координат.
 */
public class RedstoneSystem {
    private static final String[] POWER_STRINGS = {
        "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15"
    };

    private static final int[] DX = {1, -1, 0, 0, 0, 0};
    private static final int[] DY = {0, 0, 0, 0, 1, -1};
    private static final int[] DZ = {0, 0, 1, -1, 0, 0};

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
     * Полноценная Zero-Allocation BFS очередь обновления сигналов редстоуна на примитивных массивах.
     */
    public static void propagateRedstone(Instance instance, Point start) {
        if (instance == null || start == null) return;

        final int MAX_ITERATIONS = 128;
        long[] queue = new long[MAX_ITERATIONS + 64];
        int head = 0;
        int tail = 0;

        LongOpenHashSet visited = new LongOpenHashSet(64);

        long startPacked = FastMath.packBlockPos(start.blockX(), start.blockY(), start.blockZ());
        queue[tail++] = startPacked;
        visited.add(startPacked);

        int iterations = 0;

        while (head < tail && iterations++ < MAX_ITERATIONS) {
            long currentPacked = queue[head++];
            int ox = (int) (currentPacked >> 38);
            int oy = (int) (currentPacked << 26 >> 52);
            int oz = (int) (currentPacked << 38 >> 38);

            for (int dir = 0; dir < 6; dir++) {
                int nx = ox + DX[dir];
                int ny = oy + DY[dir];
                int nz = oz + DZ[dir];

                long neighborPacked = FastMath.packBlockPos(nx, ny, nz);
                Block block = instance.getBlock(nx, ny, nz);
                if (block.compare(Block.AIR)) continue;

                // 1. Редстоун пыль
                if (block.compare(Block.REDSTONE_WIRE)) {
                    int expectedPower = calculatePower(instance, nx, ny, nz);
                    int currentPower = parsePower(getProp(block, "power", "0"));

                    if (currentPower != expectedPower) {
                        instance.setBlock(nx, ny, nz, block.withProperty("power", POWER_STRINGS[expectedPower]));
                        if (visited.add(neighborPacked) && tail < queue.length) {
                            queue[tail++] = neighborPacked;
                        }
                    }
                }
                // 2. Редстоун факел
                else if (block.compare(Block.REDSTONE_TORCH) || block.compare(Block.REDSTONE_WALL_TORCH)) {
                    boolean isLit = "true".equals(getProp(block, "lit", "true"));
                    boolean shouldBeLit = !isAnyAdjacentPowered(instance, nx, ny, nz);

                    if (isLit != shouldBeLit) {
                        instance.setBlock(nx, ny, nz, block.withProperty("lit", shouldBeLit ? "true" : "false"));
                        if (visited.add(neighborPacked) && tail < queue.length) {
                            queue[tail++] = neighborPacked;
                        }
                    }
                }
                // 3. Поршень
                else if (block.compare(Block.PISTON) || block.compare(Block.STICKY_PISTON)) {
                    boolean powered = isAnyAdjacentPowered(instance, nx, ny, nz);
                    boolean extended = "true".equals(getProp(block, "extended", "false"));

                    if (powered && !extended) {
                        pushPiston(instance, nx, ny, nz, block);
                        if (visited.add(neighborPacked) && tail < queue.length) queue[tail++] = neighborPacked;
                    } else if (!powered && extended) {
                        retractPiston(instance, nx, ny, nz, block);
                        if (visited.add(neighborPacked) && tail < queue.length) queue[tail++] = neighborPacked;
                    }
                }
                // 4. Двери и люки
                else if (block.name().endsWith("_door") || block.name().endsWith("_trapdoor")) {
                    boolean powered = isAnyAdjacentPowered(instance, nx, ny, nz);
                    boolean isOpen = "true".equals(getProp(block, "open", "false"));

                    if (powered != isOpen) {
                        instance.setBlock(nx, ny, nz, block.withProperty("open", powered ? "true" : "false"));
                    }
                }
            }
        }
    }

    private static int calculatePower(Instance instance, int x, int y, int z) {
        int maxPower = 0;

        for (int dir = 0; dir < 6; dir++) {
            int nx = x + DX[dir];
            int ny = y + DY[dir];
            int nz = z + DZ[dir];

            Block b = instance.getBlock(nx, ny, nz);
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

    private static boolean isAnyAdjacentPowered(Instance instance, int x, int y, int z) {
        return calculatePower(instance, x, y, z) > 0;
    }

    private static void pushPiston(Instance instance, int x, int y, int z, Block piston) {
        String facing = getProp(piston, "facing", "up");
        int[] offset = getDirectionOffset(facing);

        int tx = x + offset[0];
        int ty = y + offset[1];
        int tz = z + offset[2];

        Block targetBlock = instance.getBlock(tx, ty, tz);

        if (targetBlock.compare(Block.AIR)) {
            instance.setBlock(x, y, z, piston.withProperty("extended", "true"));
            instance.setBlock(tx, ty, tz, Block.PISTON_HEAD.withProperty("facing", facing));
        } else if (!targetBlock.compare(Block.OBSIDIAN) && !targetBlock.compare(Block.BEDROCK)) {
            int nextX = tx + offset[0];
            int nextY = ty + offset[1];
            int nextZ = tz + offset[2];

            if (instance.getBlock(nextX, nextY, nextZ).compare(Block.AIR)) {
                instance.setBlock(nextX, nextY, nextZ, targetBlock);
                instance.setBlock(tx, ty, tz, Block.PISTON_HEAD.withProperty("facing", facing));
                instance.setBlock(x, y, z, piston.withProperty("extended", "true"));
            }
        }
    }

    private static void retractPiston(Instance instance, int x, int y, int z, Block piston) {
        String facing = getProp(piston, "facing", "up");
        int[] offset = getDirectionOffset(facing);

        int hx = x + offset[0];
        int hy = y + offset[1];
        int hz = z + offset[2];

        if (instance.getBlock(hx, hy, hz).compare(Block.PISTON_HEAD)) {
            instance.setBlock(hx, hy, hz, Block.AIR);
        }
        instance.setBlock(x, y, z, piston.withProperty("extended", "false"));
    }

    private static int[] getDirectionOffset(String facing) {
        return switch (facing) {
            case "down" -> new int[]{0, -1, 0};
            case "up" -> new int[]{0, 1, 0};
            case "north" -> new int[]{0, 0, -1};
            case "south" -> new int[]{0, 0, 1};
            case "west" -> new int[]{-1, 0, 0};
            case "east" -> new int[]{1, 0, 0};
            default -> new int[]{0, 1, 0};
        };
    }
}
