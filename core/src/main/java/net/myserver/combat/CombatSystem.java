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
import net.myserver.entities.CustomZombie;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Высокопроизводительная боевая система (Combat System).
 * Оптимизирована прямыми сравнениями материалов без аллокаций строк на каждый удар.
 */
public class CombatSystem {
    public static final Map<UUID, Long> lastHitTimes = new ConcurrentHashMap<>();

    public static void register(GlobalEventHandler handler) {
        // Обработка ударов (ЛКМ)
        handler.addListener(EntityAttackEvent.class, event -> {
            Entity attacker = event.getEntity();
            Entity target = event.getTarget();

            if (target instanceof LivingEntity livingTarget) {
                long now = System.currentTimeMillis();
                long lastHit = lastHitTimes.getOrDefault(target.getUuid(), 0L);

                // Защита от автокликеров (задержка между получением урона 400 мс)
                if (now - lastHit < 400) {
                    return;
                }
                lastHitTimes.put(target.getUuid(), now);

                float baseDamage = 1.0f;

                // Быстрый расчет урона оружия
                if (attacker instanceof Player player) {
                    Material mat = player.getItemInMainHand().material();
                    baseDamage = getWeaponDamage(mat);
                } else if (attacker instanceof CustomZombie) {
                    baseDamage = 3.5f; // Урон зомби
                }

                // Расчет снижения урона броней
                float armorPoints = 0.0f;
                if (target instanceof Player targetPlayer) {
                    armorPoints += getArmorValue(targetPlayer.getHelmet().material());
                    armorPoints += getArmorValue(targetPlayer.getChestplate().material());
                    armorPoints += getArmorValue(targetPlayer.getLeggings().material());
                    armorPoints += getArmorValue(targetPlayer.getBoots().material());
                }

                float reduction = Math.min(20.0f, armorPoints) * 0.04f;
                float finalDamage = Math.max(0.5f, baseDamage * (1.0f - reduction));

                float currentHp = livingTarget.getHealth();
                livingTarget.setHealth(Math.max(0.0f, currentHp - finalDamage));

                // Knockback
                float yaw = attacker.getPosition().yaw();
                livingTarget.takeKnockback(0.35f, Math.sin(yaw * (Math.PI / 180)), -Math.cos(yaw * (Math.PI / 180)));
            }
        });

        // Смерть сущности
        handler.addListener(EntityDeathEvent.class, event -> {
            Entity entity = event.getEntity();
            lastHitTimes.remove(entity.getUuid());

            if (entity instanceof CustomZombie) {
                ItemEntity drop = new ItemEntity(ItemStack.of(Material.ROTTEN_FLESH, 1));
                drop.setInstance(entity.getInstance(), entity.getPosition().add(0, 0.5, 0));
            } else if (entity instanceof Player player) {
                player.respawn();
            }
        });
    }

    private static float getWeaponDamage(Material mat) {
        if (mat == Material.NETHERITE_SWORD) return 8.0f;
        if (mat == Material.DIAMOND_SWORD) return 7.0f;
        if (mat == Material.IRON_SWORD) return 6.0f;
        if (mat == Material.STONE_SWORD) return 5.0f;
        if (mat == Material.WOODEN_SWORD || mat == Material.GOLDEN_SWORD) return 4.0f;
        if (mat == Material.NETHERITE_AXE) return 10.0f;
        if (mat == Material.DIAMOND_AXE || mat == Material.IRON_AXE) return 9.0f;
        if (mat == Material.STONE_AXE || mat == Material.WOODEN_AXE) return 7.0f;
        return 1.0f;
    }

    private static float getArmorValue(Material mat) {
        if (mat == null) return 0f;
        if (mat == Material.NETHERITE_CHESTPLATE || mat == Material.DIAMOND_CHESTPLATE) return 8.0f;
        if (mat == Material.IRON_CHESTPLATE) return 6.0f;
        if (mat == Material.CHAINMAIL_CHESTPLATE || mat == Material.GOLDEN_CHESTPLATE) return 5.0f;
        if (mat == Material.LEATHER_CHESTPLATE) return 3.0f;
        if (mat == Material.NETHERITE_LEGGINGS || mat == Material.DIAMOND_LEGGINGS) return 6.0f;
        if (mat == Material.IRON_LEGGINGS) return 5.0f;
        if (mat == Material.NETHERITE_HELMET || mat == Material.DIAMOND_HELMET || mat == Material.NETHERITE_BOOTS || mat == Material.DIAMOND_BOOTS) return 3.0f;
        if (mat == Material.IRON_HELMET || mat == Material.IRON_BOOTS) return 2.0f;
        return 1.0f;
    }
}
