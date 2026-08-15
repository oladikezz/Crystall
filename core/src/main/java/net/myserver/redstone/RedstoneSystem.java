package net.myserver.redstone;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;

public class RedstoneSystem {

    public static void register(GlobalEventHandler handler) {
        handler.addListener(PlayerBlockPlaceEvent.class, event -> {
            updateRedstoneRecursive(event.getInstance(), event.getBlockPosition(), 0);
        });

        handler.addListener(PlayerBlockBreakEvent.class, event -> {
            updateRedstoneRecursive(event.getInstance(), event.getBlockPosition(), 0);
        });
    }

    public static void updateRedstoneRecursive(Instance instance, Point origin, int depth) {
        if (depth > 20) return; // Защита от бесконечного цикла

        Point[] neighbors = {
            origin,
            origin.add(1, 0, 0), origin.add(-1, 0, 0),
            origin.add(0, 0, 1), origin.add(0, 0, -1),
            origin.add(0, 1, 0), origin.add(0, -1, 0)
        };

        for (Point pos : neighbors) {
            Block block = instance.getBlock(pos);
            if (block.isAir()) continue;

            // 1. Редстоун пыль
            if (block.compare(Block.REDSTONE_WIRE)) {
                int expectedPower = calculatePower(instance, pos);
                int currentPower = Integer.parseInt(block.getProperty("power", "0"));

                if (currentPower != expectedPower) {
                    instance.setBlock(pos, block.withProperty("power", String.valueOf(expectedPower)));
                    updateRedstoneRecursive(instance, pos, depth + 1);
                }
            }
            
            // 2. Редстоун факел
            else if (block.compare(Block.REDSTONE_TORCH) || block.compare(Block.REDSTONE_WALL_TORCH)) {
                boolean isLit = Boolean.parseBoolean(block.getProperty("lit", "true"));
                
                // Простая проверка инверсии: запитан ли блок под факелом (или за ним)
                // Для простоты: если рядом есть пыль с power > 0, факел гаснет
                boolean shouldBeLit = !isAnyAdjacentPowered(instance, pos);
                
                if (isLit != shouldBeLit) {
                    instance.setBlock(pos, block.withProperty("lit", String.valueOf(shouldBeLit)));
                    updateRedstoneRecursive(instance, pos, depth + 1);
                }
            }
            
            // 3. Поршень
            else if (block.compare(Block.PISTON) || block.compare(Block.STICKY_PISTON)) {
                boolean powered = isAnyAdjacentPowered(instance, pos);
                boolean extended = Boolean.parseBoolean(block.getProperty("extended", "false"));
                
                if (powered && !extended) {
                    // Выдвигаем
                    pushPiston(instance, pos, block);
                    updateRedstoneRecursive(instance, pos, depth + 1);
                } else if (!powered && extended) {
                    // Задвигаем (мгновенно убираем голову)
                    retractPiston(instance, pos, block);
                    updateRedstoneRecursive(instance, pos, depth + 1);
                }
            }
            
            // 4. Двери и люки
            else if (block.name().contains("_door") || block.name().contains("_trapdoor")) {
                boolean powered = isAnyAdjacentPowered(instance, pos);
                boolean isOpen = Boolean.parseBoolean(block.getProperty("open", "false"));
                
                if (powered != isOpen) {
                    instance.setBlock(pos, block.withProperty("open", String.valueOf(powered)));
                }
            }
        }
    }

    private static int calculatePower(Instance instance, Point pos) {
        int maxPower = 0;
        Point[] neighbors = {
            pos.add(1, 0, 0), pos.add(-1, 0, 0),
            pos.add(0, 0, 1), pos.add(0, 0, -1),
            pos.add(0, 1, 0), pos.add(0, -1, 0)
        };

        for (Point n : neighbors) {
            Block b = instance.getBlock(n);
            if (b.name().contains("redstone_torch")) {
                if (b.getProperty("lit", "true").equals("true")) return 15;
            } else if (b.compare(Block.REDSTONE_BLOCK)) {
                return 15;
            } else if (b.compare(Block.REDSTONE_WIRE)) {
                int p = Integer.parseInt(b.getProperty("power", "0"));
                if (p - 1 > maxPower) maxPower = p - 1;
            }
        }
        return maxPower;
    }

    private static boolean isAnyAdjacentPowered(Instance instance, Point pos) {
        return calculatePower(instance, pos) > 0;
    }

    private static void pushPiston(Instance instance, Point pos, Block piston) {
        String facing = piston.getProperty("facing", "up");
        Vec dir = getDirection(facing);
        
        Point targetPos = pos.add(dir);
        Block targetBlock = instance.getBlock(targetPos);
        
        if (targetBlock.isAir()) {
            instance.setBlock(pos, piston.withProperty("extended", "true"));
            instance.setBlock(targetPos, Block.PISTON_HEAD.withProperty("facing", facing));
        } else if (!targetBlock.compare(Block.OBSIDIAN) && !targetBlock.compare(Block.BEDROCK)) {
            // Толкаем 1 блок
            Point nextPos = targetPos.add(dir);
            if (instance.getBlock(nextPos).isAir()) {
                instance.setBlock(nextPos, targetBlock);
                instance.setBlock(targetPos, Block.PISTON_HEAD.withProperty("facing", facing));
                instance.setBlock(pos, piston.withProperty("extended", "true"));
            }
        }
    }

    private static void retractPiston(Instance instance, Point pos, Block piston) {
        String facing = piston.getProperty("facing", "up");
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
