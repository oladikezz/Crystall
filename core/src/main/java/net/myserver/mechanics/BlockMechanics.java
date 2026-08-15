package net.myserver.mechanics;

import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.ItemComponent;

import java.time.Duration;

public class BlockMechanics {

    public static void register(GlobalEventHandler handler) {
        handler.addListener(PlayerBlockBreakEvent.class, event -> {
            Player player = event.getPlayer();
            Block block = event.getBlock();
            Instance instance = event.getInstance();

            // Простая проверка инструмента (камень без кирки не ломается с дропом)
            boolean canHarvest = true;
            if (block.compare(Block.STONE) && !player.getItemInMainHand().material().name().contains("pickaxe")) {
                canHarvest = false;
            }
            
            if (canHarvest) {
                Material dropMaterial = getDropForBlock(block);
                if (dropMaterial != Material.AIR) {
                    ItemStack dropStack = ItemStack.of(dropMaterial);
                    ItemEntity itemEntity = new ItemEntity(dropStack);
                    
                    itemEntity.setPickupDelay(Duration.ofMillis(500));
                    itemEntity.setInstance(instance, event.getBlockPosition().add(0.5, 0.5, 0.5));
                    
                    // Разлет предметов
                    double vx = (Math.random() * 0.2) - 0.1;
                    double vy = 0.2 + (Math.random() * 0.2);
                    double vz = (Math.random() * 0.2) - 0.1;
                    itemEntity.setVelocity(new Vec(vx * 20, vy * 20, vz * 20));
                }
            }

            // Обработка прочности инструмента
            ItemStack hand = player.getItemInMainHand();
            if (hand.has(ItemComponent.MAX_DAMAGE)) {
                int maxDamage = hand.get(ItemComponent.MAX_DAMAGE);
                int currentDamage = hand.get(ItemComponent.DAMAGE, 0);

                currentDamage++;

                if (currentDamage >= maxDamage) {
                    player.setItemInMainHand(ItemStack.AIR);
                } else {
                    player.setItemInMainHand(hand.with(ItemComponent.DAMAGE, currentDamage));
                }
            }
            
            // Вызов обновления блоков вокруг (для гравитации и воды)
            PhysicsSystem.triggerBlockUpdate(instance, event.getBlockPosition());
        });
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
        
        try {
            return Material.fromNamespaceId(block.namespace());
        } catch (Exception e) {
            return Material.AIR;
        }
    }
}
