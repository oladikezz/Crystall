package net.myserver.mechanics;

import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

import java.time.Duration;

public class BlockMechanics {

    public static void register(GlobalEventHandler handler) {
        handler.addListener(PlayerBlockBreakEvent.class, event -> {
            Player player = event.getPlayer();
            Block block = event.getBlock();
            Instance instance = event.getInstance();

            // Простая проверка инструмента
            boolean canHarvest = true;
            if (block.compare(Block.STONE) && !player.getItemInMainHand().material().name().contains("pickaxe")) {
                canHarvest = false;
            }
            
            if (canHarvest) {
                Material dropMaterial = getDropForBlock(block);
                spawnDrop(instance, event.getBlockPosition(), dropMaterial);
            }

            // Timber mechanic
            if (block.name().contains("log") && player.getItemInMainHand().material().name().contains("axe")) {
                breakTree(instance, event.getBlockPosition(), player);
            }

            // Обработка прочности инструмента
            ItemStack hand = player.getItemInMainHand();
            if (hand.has(DataComponents.MAX_DAMAGE)) {
                int maxDamage = hand.get(DataComponents.MAX_DAMAGE, 0);
                int currentDamage = hand.get(DataComponents.DAMAGE, 0);

                currentDamage++;

                if (currentDamage >= maxDamage) {
                    player.setItemInMainHand(ItemStack.AIR);
                } else {
                    player.setItemInMainHand(hand.with(DataComponents.DAMAGE, currentDamage));
                }
            }
            
            // Вызов обновления блоков вокруг (для гравитации и воды)
            PhysicsSystem.triggerBlockUpdate(instance, event.getBlockPosition());
        });
    }

    private static void breakTree(Instance instance, net.minestom.server.coordinate.Point startPos, Player player) {
        int maxBlocks = 50;
        int blocksBroken = 0;
        
        java.util.Queue<net.minestom.server.coordinate.Point> queue = new java.util.LinkedList<>();
        java.util.Set<net.minestom.server.coordinate.Point> visited = new java.util.HashSet<>();
        
        queue.add(startPos.add(0, 1, 0));
        
        while (!queue.isEmpty() && blocksBroken < maxBlocks) {
            net.minestom.server.coordinate.Point p = queue.poll();
            if (visited.contains(p)) continue;
            visited.add(p);
            
            Block b = instance.getBlock(p);
            if (b.name().contains("log")) {
                instance.setBlock(p, Block.AIR);
                spawnDrop(instance, p, getDropForBlock(b));
                blocksBroken++;
                
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = 0; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            queue.add(p.add(dx, dy, dz));
                        }
                    }
                }
            }
        }
    }

    private static void spawnDrop(Instance instance, net.minestom.server.coordinate.Point pos, Material dropMaterial) {
        if (dropMaterial == null || dropMaterial == Material.AIR) return;
        ItemStack dropStack = ItemStack.of(dropMaterial);
        ItemEntity itemEntity = new ItemEntity(dropStack);
        
        itemEntity.setPickupDelay(Duration.ofMillis(500));
        itemEntity.setInstance(instance, pos.add(0.5, 0.5, 0.5));
        
        double vx = (Math.random() * 0.2) - 0.1;
        double vy = 0.2 + (Math.random() * 0.2);
        double vz = (Math.random() * 0.2) - 0.1;
        itemEntity.setVelocity(new Vec(vx * 20, vy * 20, vz * 20));
    }

    private static Material getDropForBlock(Block block) {
        if (block.compare(Block.STONE)) return Material.COBBLESTONE;
        if (block.compare(Block.GRASS_BLOCK)) return Material.DIRT;
        if (block.compare(Block.DIRT)) return Material.DIRT;
        if (block.compare(Block.OAK_LOG)) return Material.OAK_LOG;
        if (block.compare(Block.OAK_LEAVES)) {
            if (Math.random() < 0.05) return Material.OAK_SAPLING;
            return Material.AIR;
        }
        if (block.compare(Block.SAND)) return Material.SAND;
        if (block.compare(Block.GRAVEL)) return Material.GRAVEL;
        if (block.compare(Block.WHEAT)) {
            String age = block.getProperty("age");
            if ("7".equals(age)) {
                return Material.WHEAT;
            }
            return Material.WHEAT_SEEDS;
        }
        
        Material mat = block.material();
        return mat != null ? mat : Material.AIR;
    }
}
