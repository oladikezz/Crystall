package net.myserver.combat;

import net.minestom.server.entity.Entity;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.entity.EntityAttackEvent;
import net.minestom.server.event.entity.EntityDeathEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.play.EntityAnimationPacket;
import net.myserver.entities.CustomZombie;

public class CombatSystem {
    public static void register(GlobalEventHandler handler) {
        // Обработка ударов (ЛКМ)
        handler.addListener(EntityAttackEvent.class, event -> {
            Entity attacker = event.getEntity();
            Entity target = event.getTarget();

            if (target instanceof LivingEntity livingTarget) {
                float damageAmount = 1.0f;
                
                // Рассчитываем урон
                if (attacker instanceof Player player) {
                    String item = player.getItemInMainHand().material().name();
                    if (item.contains("sword")) damageAmount = 5.0f;
                    else if (item.contains("axe")) damageAmount = 6.0f;
                    else if (item.contains("pickaxe")) damageAmount = 3.0f;
                } else if (attacker instanceof CustomZombie) {
                    damageAmount = 3.0f; // Урон зомби
                }

                // Наносим урон (ручное уменьшение здоровья для совместимости с разными версиями)
                livingTarget.setHealth(livingTarget.getHealth() - damageAmount);
                
                // Визуальный эффект урона (покраснение)
                livingTarget.sendPacketToViewersAndSelf(new EntityAnimationPacket(livingTarget.getEntityId(), EntityAnimationPacket.Animation.TAKE_DAMAGE));

                // Отбрасывание (Knockback)
                float yaw = attacker.getPosition().yaw();
                livingTarget.takeKnockback(0.4f, Math.sin(yaw * (Math.PI / 180)), -Math.cos(yaw * (Math.PI / 180)));
            }
        });

        // Смерть сущности
        handler.addListener(EntityDeathEvent.class, event -> {
            Entity entity = event.getEntity();
            
            if (entity instanceof CustomZombie) {
                // Дроп гнилой плоти
                ItemEntity drop = new ItemEntity(ItemStack.of(Material.ROTTEN_FLESH, 1));
                drop.setInstance(entity.getInstance(), entity.getPosition().add(0, 0.5, 0));
            } else if (entity instanceof Player player) {
                // Респавн игрока при смерти
                player.respawn();
            }
        });
    }
}
