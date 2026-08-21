package net.schalker.SMPS.modules.cosmetics.models;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 *   " "
 *  -      
 */
public class ArrowEffectCosmetic extends Cosmetic {
    private final Particle trailParticle;
    private final Particle hitParticle;
    private final int trailCount;
    private final int hitCount;
    private final double hitSpread;
    private final String trailSound;
    private final String hitSound;
    private final MaceImpactEffect maceImpactEffect;
    
    //    (UUID  -> ArrowEffectCosmetic)
    private static final Map<UUID, ArrowEffectCosmetic> activeArrowEffects = new ConcurrentHashMap<>();
    private static final Map<UUID, ArrowEffectCosmetic> activeBowTrailEffects = new ConcurrentHashMap<>();
    private static final Map<UUID, ArrowEffectCosmetic> activeBowHitEffects = new ConcurrentHashMap<>();
    private static final Map<UUID, ArrowEffectCosmetic> activeMaceEffects = new ConcurrentHashMap<>();
    private static final Map<UUID, ArrowEffectCosmetic> activeTridentThrowEffects = new ConcurrentHashMap<>();
    private static final Map<UUID, ArrowEffectCosmetic> activeTridentThrowTrailEffects = new ConcurrentHashMap<>();
    private static final Map<UUID, ArrowEffectCosmetic> activeTridentThrowHitEffects = new ConcurrentHashMap<>();
    private static final Map<UUID, ArrowEffectCosmetic> activeTridentRiptideEffects = new ConcurrentHashMap<>();

    public ArrowEffectCosmetic(String id, String name, CosmeticRarity rarity, String permission,
                               String itemMaterial, int cost, boolean enabled, boolean purchasable,
                               Particle trailParticle, Particle hitParticle, int trailCount, int hitCount,
                               double hitSpread, String trailSound, String hitSound,
                               MaceImpactEffect maceImpactEffect) {
        super(id, name, CosmeticCategory.ARROW_EFFECT, rarity, permission, itemMaterial, cost, enabled, purchasable);
        this.trailParticle = trailParticle;
        this.hitParticle = hitParticle;
        this.trailCount = trailCount;
        this.hitCount = hitCount;
        this.hitSpread = hitSpread;
        this.trailSound = trailSound;
        this.hitSound = hitSound;
        this.maceImpactEffect = maceImpactEffect == null ? MaceImpactEffect.DUST_RING : maceImpactEffect;
    }

    /**
     *    
     */
    public Particle getTrailParticle() {
        return this.trailParticle;
    }

    /**
     *   
     */
    public Particle getHitParticle() {
        return this.hitParticle;
    }

    /**
     *    
     */
    public int getTrailCount() {
        return this.trailCount;
    }

    /**
     *    
     */
    public int getHitCount() {
        return this.hitCount;
    }

    /**
     *    
     */
    public double getHitSpread() {
        return this.hitSpread;
    }

    /**
     *  
     */
    public String getTrailSound() {
        return this.trailSound;
    }

    /**
     *  
     */
    public String getHitSound() {
        return this.hitSound;
    }

    public MaceImpactEffect getMaceImpactEffect() {
        return this.maceImpactEffect;
    }

    public void playMaceImpact(Location location, Player attacker, LivingEntity target) {
        if (location == null || attacker == null || target == null) {
            return;
        }
        this.maceImpactEffect.play(location, attacker, target);
    }

    @Override
    public void equip(Player player) {
        this.unequip(player);
        UUID playerId = player.getUniqueId();
        activeArrowEffects.put(playerId, this);
        activeBowTrailEffects.put(playerId, this);
        activeBowHitEffects.put(playerId, this);
    }

    @Override
    public void unequip(Player player) {
        UUID playerId = player.getUniqueId();
        activeArrowEffects.remove(playerId);
        activeBowTrailEffects.remove(playerId);
        activeBowHitEffects.remove(playerId);
    }

    @Override
    public void update(Player player) {
        //    ArrowTrailTask
    }

    /**
     *     
     *      MYTHIC
     */
    public void renderTrail(Location location) {
        if (location.getWorld() == null) return;
        
        //    MYTHIC
        boolean isMythic = this.rarity == CosmeticRarity.MYTHIC;
        int count = isMythic ? this.trailCount * 3 : this.trailCount;
        double spread = isMythic ? 0.15 : 0.05;
        
        try {
            //   
            safeSpawnTrail(location, count, spread);
            
            //  MYTHIC   
            if (isMythic) {
                location.getWorld().spawnParticle(
                    Particle.END_ROD,
                    location,
                    2,
                    0.1, 0.1, 0.1,
                    0.02
                );
            }
        } catch (Exception e) {
            //   
        }
    }
    
    /**
     *       
     */
    private void safeSpawnTrail(Location loc, int count, double spread) {
        try {
            if (this.trailParticle == Particle.DUST || this.trailParticle == Particle.DUST_PILLAR) {
                loc.getWorld().spawnParticle(Particle.DUST, loc, count, spread, spread, spread, 0,
                    new Particle.DustOptions(org.bukkit.Color.WHITE, 1));
            } else if (this.trailParticle == Particle.DUST_COLOR_TRANSITION) {
                loc.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, loc, count, spread, spread, spread, 0,
                    new Particle.DustTransition(org.bukkit.Color.WHITE, org.bukkit.Color.GRAY, 1));
            } else if (this.trailParticle == Particle.SCULK_CHARGE) {
                loc.getWorld().spawnParticle(Particle.SCULK_CHARGE, loc, count, spread, spread, spread, 0, 0.0f);
            } else if (this.trailParticle == Particle.SHRIEK) {
                loc.getWorld().spawnParticle(Particle.SHRIEK, loc, count, spread, spread, spread, 0, 0);
            } else if (this.trailParticle == Particle.ENTITY_EFFECT) {
                // Rainbow arrows -  
                    loc.getWorld().spawnParticle(Particle.DUST, loc, count, spread, spread, spread, 0,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(
                        (int)(Math.random() * 255), 
                        (int)(Math.random() * 255), 
                        (int)(Math.random() * 255)), 1));
            } else {
                loc.getWorld().spawnParticle(this.trailParticle, loc, count, spread, spread, spread, 0);
            }
        } catch (Exception e) {
            // 
            loc.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc, count, spread, spread, spread, 0);
        }
    }

    /**
     *   
     *     MYTHIC
     */
    public void renderHit(Location location) {
        if (location.getWorld() == null) return;
        
        //    MYTHIC
        boolean isMythic = this.rarity == CosmeticRarity.MYTHIC;
        int count = isMythic ? this.hitCount * 3 : this.hitCount;
        double spread = isMythic ? this.hitSpread * 1.5 : this.hitSpread;
        
        try {
            //    
            safeSpawnHit(location, count, spread);
            
            //    
            int ringPoints = isMythic ? 16 : 8;
            for (int i = 0; i < ringPoints; i++) {
                double angle = (2 * Math.PI / ringPoints) * i;
                double x = Math.cos(angle) * spread;
                double z = Math.sin(angle) * spread;
                safeSpawnHit(location.clone().add(x, 0.2, z), 1, 0);
            }
            
            //  MYTHIC    
            if (isMythic) {
                location.getWorld().spawnParticle(Particle.FLASH, location, 1, 0, 0, 0, 0);
                location.getWorld().spawnParticle(Particle.EXPLOSION, location, 1, 0, 0, 0, 0);
                location.getWorld().spawnParticle(Particle.END_ROD, location, 15, spread, spread, spread, 0.1);
            }
        } catch (Exception e) {
            //   
        }
    }
    
    /**
     *       
     */
    private void safeSpawnHit(Location loc, int count, double spread) {
        try {
            if (this.hitParticle == Particle.DUST || this.hitParticle == Particle.DUST_PILLAR) {
                loc.getWorld().spawnParticle(Particle.DUST, loc, count, spread, spread, spread, 0.1,
                    new Particle.DustOptions(org.bukkit.Color.WHITE, 1.5f));
            } else if (this.hitParticle == Particle.DUST_COLOR_TRANSITION) {
                loc.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, loc, count, spread, spread, spread, 0.1,
                    new Particle.DustTransition(org.bukkit.Color.WHITE, org.bukkit.Color.GRAY, 1.5f));
            } else if (this.hitParticle == Particle.SCULK_CHARGE) {
                loc.getWorld().spawnParticle(Particle.SCULK_CHARGE, loc, count, spread, spread, spread, 0.1, 0.0f);
            } else if (this.hitParticle == Particle.SHRIEK) {
                loc.getWorld().spawnParticle(Particle.SHRIEK, loc, count, spread, spread, spread, 0.1, 0);
            } else if (this.hitParticle == Particle.ENTITY_EFFECT) {
                // Rainbow hit -  
                    loc.getWorld().spawnParticle(Particle.DUST, loc, count, spread, spread, spread, 0.1,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(
                        (int)(Math.random() * 255), 
                        (int)(Math.random() * 255), 
                        (int)(Math.random() * 255)), 1.5f));
            } else {
                loc.getWorld().spawnParticle(this.hitParticle, loc, count, spread, spread, spread, 0.1);
            }
        } catch (Exception e) {
            // 
            loc.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc, count, spread, spread, spread, 0.1);
        }
    }
    
    /**
     *     
     */
    public static ArrowEffectCosmetic getActiveEffect(UUID playerId) {
        ArrowEffectCosmetic trail = activeBowTrailEffects.get(playerId);
        if (trail != null) return trail;
        ArrowEffectCosmetic hit = activeBowHitEffects.get(playerId);
        if (hit != null) return hit;
        return activeArrowEffects.get(playerId);
    }

    public static void setActiveBowTrailEffect(UUID playerId, ArrowEffectCosmetic effect) {
        if (effect == null) {
            activeBowTrailEffects.remove(playerId);
        } else {
            activeBowTrailEffects.put(playerId, effect);
        }
        syncBowLegacyEffect(playerId);
    }

    public static ArrowEffectCosmetic getActiveBowTrailEffect(UUID playerId) {
        return activeBowTrailEffects.get(playerId);
    }

    public static void setActiveBowHitEffect(UUID playerId, ArrowEffectCosmetic effect) {
        if (effect == null) {
            activeBowHitEffects.remove(playerId);
        } else {
            activeBowHitEffects.put(playerId, effect);
        }
        syncBowLegacyEffect(playerId);
    }

    public static ArrowEffectCosmetic getActiveBowHitEffect(UUID playerId) {
        return activeBowHitEffects.get(playerId);
    }

    public static void setActiveMaceEffect(UUID playerId, ArrowEffectCosmetic effect) {
        if (effect == null) {
            activeMaceEffects.remove(playerId);
        } else {
            activeMaceEffects.put(playerId, effect);
        }
    }

    public static ArrowEffectCosmetic getActiveMaceEffect(UUID playerId) {
        return activeMaceEffects.get(playerId);
    }

    public static void setActiveTridentThrowEffect(UUID playerId, ArrowEffectCosmetic effect) {
        if (effect == null) {
            activeTridentThrowEffects.remove(playerId);
            activeTridentThrowTrailEffects.remove(playerId);
            activeTridentThrowHitEffects.remove(playerId);
        } else {
            activeTridentThrowEffects.put(playerId, effect);
            activeTridentThrowTrailEffects.put(playerId, effect);
            activeTridentThrowHitEffects.put(playerId, effect);
        }
    }

    public static ArrowEffectCosmetic getActiveTridentThrowEffect(UUID playerId) {
        ArrowEffectCosmetic trail = activeTridentThrowTrailEffects.get(playerId);
        if (trail != null) return trail;
        ArrowEffectCosmetic hit = activeTridentThrowHitEffects.get(playerId);
        if (hit != null) return hit;
        return activeTridentThrowEffects.get(playerId);
    }

    public static void setActiveTridentThrowTrailEffect(UUID playerId, ArrowEffectCosmetic effect) {
        if (effect == null) {
            activeTridentThrowTrailEffects.remove(playerId);
        } else {
            activeTridentThrowTrailEffects.put(playerId, effect);
        }
        syncTridentThrowLegacyEffect(playerId);
    }

    public static ArrowEffectCosmetic getActiveTridentThrowTrailEffect(UUID playerId) {
        return activeTridentThrowTrailEffects.get(playerId);
    }

    public static void setActiveTridentThrowHitEffect(UUID playerId, ArrowEffectCosmetic effect) {
        if (effect == null) {
            activeTridentThrowHitEffects.remove(playerId);
        } else {
            activeTridentThrowHitEffects.put(playerId, effect);
        }
        syncTridentThrowLegacyEffect(playerId);
    }

    public static ArrowEffectCosmetic getActiveTridentThrowHitEffect(UUID playerId) {
        return activeTridentThrowHitEffects.get(playerId);
    }

    public static void setActiveTridentRiptideEffect(UUID playerId, ArrowEffectCosmetic effect) {
        if (effect == null) {
            activeTridentRiptideEffects.remove(playerId);
        } else {
            activeTridentRiptideEffects.put(playerId, effect);
        }
    }

    public static ArrowEffectCosmetic getActiveTridentRiptideEffect(UUID playerId) {
        return activeTridentRiptideEffects.get(playerId);
    }

    public static void clearPlayerEffects(UUID playerId) {
        activeArrowEffects.remove(playerId);
        activeBowTrailEffects.remove(playerId);
        activeBowHitEffects.remove(playerId);
        activeMaceEffects.remove(playerId);
        activeTridentThrowEffects.remove(playerId);
        activeTridentThrowTrailEffects.remove(playerId);
        activeTridentThrowHitEffects.remove(playerId);
        activeTridentRiptideEffects.remove(playerId);
    }

    private static void syncBowLegacyEffect(UUID playerId) {
        ArrowEffectCosmetic selected = activeBowTrailEffects.get(playerId);
        if (selected == null) {
            selected = activeBowHitEffects.get(playerId);
        }
        if (selected == null) {
            activeArrowEffects.remove(playerId);
        } else {
            activeArrowEffects.put(playerId, selected);
        }
    }

    private static void syncTridentThrowLegacyEffect(UUID playerId) {
        ArrowEffectCosmetic selected = activeTridentThrowTrailEffects.get(playerId);
        if (selected == null) {
            selected = activeTridentThrowHitEffects.get(playerId);
        }
        if (selected == null) {
            activeTridentThrowEffects.remove(playerId);
        } else {
            activeTridentThrowEffects.put(playerId, selected);
        }
    }

    /**
     * ,       
     */
    public static boolean hasActiveEffect(UUID playerId) {
        return activeArrowEffects.containsKey(playerId)
            || activeBowTrailEffects.containsKey(playerId)
            || activeBowHitEffects.containsKey(playerId);
    }

    /**
     *      
     */
    public static java.util.Set<UUID> getAllActiveEffects() {
        java.util.Set<UUID> all = new java.util.HashSet<>(activeArrowEffects.keySet());
        all.addAll(activeBowTrailEffects.keySet());
        all.addAll(activeBowHitEffects.keySet());
        return all;
    }

    /**
     *    
     */
    public static void removeAllEffects() {
        activeArrowEffects.clear();
        activeBowTrailEffects.clear();
        activeBowHitEffects.clear();
        activeMaceEffects.clear();
        activeTridentThrowEffects.clear();
        activeTridentThrowTrailEffects.clear();
        activeTridentThrowHitEffects.clear();
        activeTridentRiptideEffects.clear();
    }
}
