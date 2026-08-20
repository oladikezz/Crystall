package net.myserver.entities;

import net.minestom.server.entity.EntityCreature;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.ai.goal.MeleeAttackGoal;
import net.minestom.server.entity.ai.target.ClosestEntityTarget;
import net.minestom.server.entity.attribute.Attribute;

import java.time.temporal.ChronoUnit;
import java.util.List;

public class CustomZombie extends EntityCreature {

    public CustomZombie() {
        super(EntityType.ZOMBIE);
        
        getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0f);
        getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.2f);
        getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(3.0f);
        
        heal();
        
        addAIGroup(
            List.of(
                new MeleeAttackGoal(this, 1.2, 2, ChronoUnit.SECONDS)
            ),
            List.of(
                new ClosestEntityTarget(this, 16, entity -> entity instanceof Player)
            )
        );
    }
}
