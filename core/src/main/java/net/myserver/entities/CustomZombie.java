package net.myserver.entities;

import net.minestom.server.entity.EntityCreature;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.ai.goal.MeleeAttackGoal;
import net.minestom.server.entity.ai.target.ClosestEntityTarget;
import net.minestom.server.entity.attribute.Attribute;

import java.time.Duration;
import java.util.List;

public class CustomZombie extends EntityCreature {

    public CustomZombie() {
        super(EntityType.ZOMBIE);
        
        // Настройка атрибутов (Здоровье, Скорость)
        getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0f);
        getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.2f);
        getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(3.0f);
        
        heal(); // Восстанавливаем здоровье до максимума
        
        // AI: Поведение
        // 1. Атака ближнего боя со скоростью 1.2, радиусом атаки 2 блока и задержкой 1 секунда
        // 2. Поиск ближайшего игрока в радиусе 16 блоков
        addAIGroup(
            List.of(
                new MeleeAttackGoal(this, 1.2, 2, Duration.ofMillis(1000))
            ),
            List.of(
                new ClosestEntityTarget(this, 16, entity -> entity instanceof Player)
            )
        );
    }
}
