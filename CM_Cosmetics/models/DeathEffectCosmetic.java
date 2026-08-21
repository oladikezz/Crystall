/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Color
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.NamespacedKey
 *  org.bukkit.Particle
 *  org.bukkit.Particle$DustOptions
 *  org.bukkit.Particle$DustTransition
 *  org.bukkit.Registry
 *  org.bukkit.Sound
 *  org.bukkit.entity.Player
 *  org.bukkit.util.Vector
 */
package net.schalker.SMPS.modules.cosmetics.models;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.schalker.SMPS.modules.cosmetics.models.Cosmetic;
import net.schalker.SMPS.modules.cosmetics.models.CosmeticCategory;
import net.schalker.SMPS.modules.cosmetics.models.CosmeticRarity;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class DeathEffectCosmetic
extends Cosmetic {
    private final Particle particle;
    private final int particleCount;
    private final double spread;
    private final String sound;
    private final float soundVolume;
    private final float soundPitch;
    private final DeathEffectType effectType;
    private final boolean showOnKill;
    private static final Random random = new Random();
    private static final Map<UUID, DeathEffectCosmetic> activeDeathEffects = new ConcurrentHashMap<UUID, DeathEffectCosmetic>();

    public DeathEffectCosmetic(String id, String name, CosmeticRarity rarity, String permission, String itemMaterial, int cost, boolean enabled, boolean purchasable, Particle particle, int particleCount, double spread, String sound, float soundVolume, float soundPitch, DeathEffectType effectType, boolean showOnKill) {
        super(id, name, CosmeticCategory.DEATH_EFFECT, rarity, permission, itemMaterial, cost, enabled, purchasable);
        this.particle = particle;
        this.particleCount = particleCount;
        this.spread = spread;
        this.sound = sound;
        this.soundVolume = soundVolume;
        this.soundPitch = soundPitch;
        this.effectType = effectType;
        this.showOnKill = showOnKill;
    }

    public Particle getParticle() {
        return this.particle;
    }

    public int getParticleCount() {
        return this.particleCount;
    }

    public double getSpread() {
        return this.spread;
    }

    public String getSound() {
        return this.sound;
    }

    public float getSoundVolume() {
        return this.soundVolume;
    }

    public float getSoundPitch() {
        return this.soundPitch;
    }

    public DeathEffectType getEffectType() {
        return this.effectType;
    }

    public boolean isShowOnKill() {
        return this.showOnKill;
    }

    @Override
    public void equip(Player player) {
        this.unequip(player);
        activeDeathEffects.put(player.getUniqueId(), this);
    }

    @Override
    public void unequip(Player player) {
        activeDeathEffects.remove(player.getUniqueId());
    }

    @Override
    public void update(Player player) {
    }

    public void playEffect(Location location) {
        if (location.getWorld() == null) {
            return;
        }
        Location effectLoc = location.clone().add(0.0, 1.0, 0.0);
        int baseMult = 2;
        int mult = this.rarity == CosmeticRarity.MYTHIC ? baseMult * 3 : (this.rarity == CosmeticRarity.LEGENDARY ? baseMult * 2 : baseMult);
        if (this.sound != null && !this.sound.isEmpty()) {
            try {
                NamespacedKey key = NamespacedKey.minecraft((String)this.sound.toLowerCase());
                Sound bukkitSound = (Sound)Registry.SOUNDS.get(key);
                if (bukkitSound != null) {
                    location.getWorld().playSound(effectLoc, bukkitSound, this.soundVolume * 1.5f, this.soundPitch);
                }
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        switch (this.effectType.ordinal()) {
            case 0: {
                this.playExplosion(effectLoc, mult);
                break;
            }
            case 1: {
                this.playSpiral(effectLoc, mult);
                break;
            }
            case 2: {
                this.playFountain(effectLoc, mult);
                break;
            }
            case 3: {
                this.playSoulRelease(effectLoc, mult);
                break;
            }
            case 4: {
                this.playBloodSplatter(effectLoc, mult);
                break;
            }
            case 5: {
                this.playFirework(effectLoc, mult);
                break;
            }
            case 6: {
                this.playLightning(effectLoc, mult);
                break;
            }
            case 7: {
                this.playFreeze(effectLoc, mult);
                break;
            }
            case 8: {
                this.playFlameBurst(effectLoc, mult);
                break;
            }
            case 9: {
                this.playHearts(effectLoc, mult);
                break;
            }
            case 10: {
                this.playSmokeCloud(effectLoc, mult);
                break;
            }
            case 11: {
                this.playConfetti(effectLoc, mult);
                break;
            }
            case 12: {
                this.playVortex(effectLoc, mult);
                break;
            }
            case 13: {
                this.playSphereBurst(effectLoc, mult);
                break;
            }
            case 14: {
                this.playPillar(effectLoc, mult);
                break;
            }
            case 15: {
                this.playHelixRise(effectLoc, mult);
                break;
            }
            case 16: {
                this.playShatter(effectLoc, mult);
                break;
            }
            case 17: {
                this.playGraveHand(effectLoc, mult);
                break;
            }
            default: {
                this.playExplosion(effectLoc, mult);
            }
        }
        if (this.rarity == CosmeticRarity.LEGENDARY || this.rarity == CosmeticRarity.MYTHIC) {
            effectLoc.getWorld().spawnParticle(Particle.END_ROD, effectLoc, 40 * mult, 2.0, 2.0, 2.0, 0.15);
        }
        if (this.rarity == CosmeticRarity.MYTHIC) {
            effectLoc.getWorld().spawnParticle(Particle.FLASH, effectLoc, 2, 0.0, 0.0, 0.0, 0.0);
            effectLoc.getWorld().spawnParticle(Particle.SONIC_BOOM, effectLoc, 1, 0.0, 0.0, 0.0, 0.0);
            effectLoc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, effectLoc, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private void playExplosion(Location loc, int mult) {
        int count = this.particleCount * mult * 2;
        double spreadMult = this.spread * (double)mult * 1.5;
        this.safeSpawn(loc, this.particle, count, spreadMult, spreadMult, spreadMult);
        loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, mult * 2, 0.8, 0.8, 0.8, 0.0);
        loc.getWorld().spawnParticle(Particle.SMOKE, loc, count / 2, spreadMult * 0.8, spreadMult * 0.8, spreadMult * 0.8, 0.05);
    }

    private void playSpiral(Location loc, int mult) {
        int count = this.particleCount * mult * 2;
        double radius = 0.4;
        for (int i = 0; i < count; ++i) {
            double angle = Math.PI * 8 * (double)i / (double)count;
            double x = Math.cos(angle) * radius;
            double y = (double)i / (double)count * 4.0;
            double z = Math.sin(angle) * radius;
            radius += 0.04;
            this.safeSpawn(loc.clone().add(x, y, z), this.particle, 2, 0.0, 0.0, 0.0);
        }
        loc.getWorld().spawnParticle(Particle.FIREWORK, loc.clone().add(0.0, 4.0, 0.0), 30 * mult, 1.0, 0.5, 1.0, 0.1);
    }

    private void playFountain(Location loc, int mult) {
        int count = this.particleCount * mult * 2;
        for (int i = 0; i < count; ++i) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double speed = 0.3 + random.nextDouble() * 0.4;
            double vx = Math.cos(angle) * speed * 0.4;
            double vy = 0.5 + random.nextDouble() * 0.6;
            double vz = Math.sin(angle) * speed * 0.4;
            loc.getWorld().spawnParticle(this.particle, loc, 0, vx, vy, vz, 0.15);
        }
        loc.getWorld().spawnParticle(Particle.DRIPPING_WATER, loc.clone().add(0.0, 2.0, 0.0), 20 * mult, 1.5, 0.5, 1.5, 0.0);
    }

    private void playSoulRelease(Location loc, int mult) {
        int soulCount = 15 * mult;
        for (int i = 0; i < soulCount; ++i) {
            double x = (random.nextDouble() - 0.5) * 1.0;
            double z = (random.nextDouble() - 0.5) * 1.0;
            loc.getWorld().spawnParticle(Particle.SOUL, loc.clone().add(x, 0.0, z), 1, 0.0, 0.8, 0.0, 0.08);
        }
        this.safeSpawn(loc, this.particle, this.particleCount * mult * 2, 0.5, 0.8, 0.5);
        loc.getWorld().spawnParticle(Particle.SCULK_SOUL, loc, 10 * mult, 0.5, 0.5, 0.5, 0.05);
    }

    private void playBloodSplatter(Location loc, int mult) {
        int i;
        Location groundLoc = loc.clone().subtract(0.0, 1.0, 0.0);
        int count = this.particleCount * mult * 2;
        for (i = 0; i < count; ++i) {
            double x = (random.nextDouble() - 0.5) * this.spread * 3.0 * (double)mult;
            double z = (random.nextDouble() - 0.5) * this.spread * 3.0 * (double)mult;
            loc.getWorld().spawnParticle(Particle.DUST, groundLoc.clone().add(x, 0.1, z), 2, 0.0, 0.0, 0.0, 0.0, (Object)new Particle.DustOptions(Color.RED, 2.0f));
        }
        for (i = 0; i < count / 3; ++i) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double vx = Math.cos(angle) * 0.2;
            double vy = 0.3 + random.nextDouble() * 0.3;
            double vz = Math.sin(angle) * 0.2;
            loc.getWorld().spawnParticle(Particle.DUST, loc, 0, vx, vy, vz, 0.1, (Object)new Particle.DustOptions(Color.fromRGB((int)139, (int)0, (int)0), 1.5f));
        }
    }

    private void playFirework(Location loc, int mult) {
        loc.getWorld().spawnParticle(Particle.FIREWORK, loc, this.particleCount * mult * 3, this.spread * (double)mult * 1.5, this.spread * (double)mult * 1.5, this.spread * (double)mult * 1.5, 0.2);
        this.safeSpawn(loc, this.particle, this.particleCount * mult, this.spread * 0.7, this.spread * 0.7, this.spread * 0.7);
        loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 30 * mult, 1.5, 1.5, 1.5, 0.3);
    }

    private void playLightning(Location loc, int mult) {
        loc.getWorld().strikeLightningEffect(loc);
        loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, this.particleCount * mult * 3, 0.3, 3.5, 0.3, 0.7);
        this.safeSpawn(loc, this.particle, this.particleCount * mult * 2, 0.5, 0.5, 0.5);
        loc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 20 * mult, 0.5, 2.0, 0.5, 0.1);
        loc.getWorld().playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.2f);
    }

    private void playGraveHand(Location loc, int mult) {
        Location ground = loc.clone().subtract(0.0, 1.0, 0.0);
        for (int step = 0; step < 18 * mult; ++step) {
            double y = (double)step * 0.08;
            double s = Math.max(0.08, 0.35 - y * 0.08);
            this.spawnHandDust(ground.clone().add(0.18, y, 0.16), s);
            this.spawnHandDust(ground.clone().add(-0.18, y, 0.16), s);
            this.spawnHandDust(ground.clone().add(0.16, y, -0.18), s);
            this.spawnHandDust(ground.clone().add(-0.16, y, -0.18), s);
            this.spawnHandDust(ground.clone().add(0.0, y + 0.18, 0.0), s * 0.8);
        }
        for (int i = 0; i < 36 * mult; ++i) {
            double angle = (double)i * 0.45;
            double radius = 0.9 - (double)i / (36.0 * (double)mult) * 0.8;
            double y = 1.6 - (double)i / (36.0 * (double)mult) * 1.5;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            loc.getWorld().spawnParticle(Particle.SCULK_SOUL, ground.clone().add(x, y, z), 1, 0.0, 0.0, 0.0, 0.02);
        }
        loc.getWorld().spawnParticle(Particle.BLOCK, ground.clone().add(0.0, 0.1, 0.0), 28 * mult, 0.5, 0.1, 0.5, 0.08, (Object)Material.SOUL_SOIL.createBlockData());
        loc.getWorld().spawnParticle(Particle.SMOKE, loc, 24 * mult, 0.5, 0.8, 0.5, 0.04);
        loc.getWorld().playSound(loc, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.9f, 0.7f);
        loc.getWorld().playSound(loc, Sound.BLOCK_SOUL_SOIL_BREAK, 0.8f, 0.9f);
    }

    private void spawnHandDust(Location loc, double spread) {
        loc.getWorld().spawnParticle(Particle.DUST, loc, 3, spread, 0.02, spread, 0.0, (Object)new Particle.DustOptions(Color.fromRGB((int)92, (int)68, (int)52), 1.3f));
    }

    private void playFreeze(Location loc, int mult) {
        loc.getWorld().spawnParticle(Particle.SNOWFLAKE, loc, this.particleCount * mult * 3, this.spread * (double)mult * 1.5, this.spread * (double)mult * 1.5, this.spread * (double)mult * 1.5, 0.08);
        loc.getWorld().spawnParticle(Particle.END_ROD, loc, 20 * mult, this.spread * 0.7, this.spread * 0.7, this.spread * 0.7, 0.04);
        loc.getWorld().spawnParticle(Particle.BLOCK, loc, 30 * mult, 1.5, 1.5, 1.5, 0.1, (Object)Material.BLUE_ICE.createBlockData());
    }

    private void playFlameBurst(Location loc, int mult) {
        int count = this.particleCount * mult * 2;
        for (int i = 0; i < count; ++i) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double speed = 0.4 + random.nextDouble() * 0.3;
            double vx = Math.cos(angle) * speed;
            double vy = random.nextDouble() * 0.7;
            double vz = Math.sin(angle) * speed;
            loc.getWorld().spawnParticle(Particle.FLAME, loc, 0, vx, vy, vz, 0.2);
        }
        loc.getWorld().spawnParticle(Particle.LAVA, loc, 10 * mult, 0.8, 0.8, 0.8, 0.0);
        loc.getWorld().spawnParticle(Particle.SMOKE, loc, 30 * mult, 1.5, 1.5, 1.5, 0.1);
    }

    private void playHearts(Location loc, int mult) {
        int count = this.particleCount * mult * 2;
        for (int i = 0; i < count; ++i) {
            double x = (random.nextDouble() - 0.5) * this.spread * 3.0;
            double y = random.nextDouble() * this.spread * 2.0;
            double z = (random.nextDouble() - 0.5) * this.spread * 3.0;
            loc.getWorld().spawnParticle(Particle.HEART, loc.clone().add(x, y, z), 1, 0.0, 0.0, 0.0, 0.0);
        }
        loc.getWorld().spawnParticle(Particle.CHERRY_LEAVES, loc, 40 * mult, 2.0, 2.0, 2.0, 0.05);
    }

    private void playSmokeCloud(Location loc, int mult) {
        loc.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc, this.particleCount * mult * 2, this.spread * (double)mult * 1.5, this.spread * 0.8, this.spread * (double)mult * 1.5, 0.03);
        this.safeSpawn(loc, this.particle, this.particleCount * mult, this.spread, this.spread, this.spread);
        loc.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc, 20 * mult, 1.5, 1.0, 1.5, 0.05);
    }

    private void playConfetti(Location loc, int mult) {
        int count = this.particleCount * mult * 2;
        for (int i = 0; i < count; ++i) {
            double x = (random.nextDouble() - 0.5) * this.spread * 3.0 * (double)mult;
            double y = random.nextDouble() * this.spread * 2.0 * (double)mult;
            double z = (random.nextDouble() - 0.5) * this.spread * 3.0 * (double)mult;
            loc.getWorld().spawnParticle(Particle.NOTE, loc.clone().add(x, y, z), 1, 0.0, 0.0, 0.0, 0.0);
        }
        loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 40 * mult, 1.5, 1.5, 1.5, 0.4);
        loc.getWorld().spawnParticle(Particle.FIREWORK, loc, 30 * mult, 2.0, 2.0, 2.0, 0.15);
    }

    private void playVortex(Location loc, int mult) {
        int rings = 15 * mult;
        for (int ring = 0; ring < rings; ++ring) {
            double radius = 3.0 - (double)ring * 0.15;
            double y = (double)ring * 0.35;
            int points = 16;
            for (int p = 0; p < points; ++p) {
                double angle = Math.PI * 2 / (double)points * (double)p + (double)ring * 0.6;
                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;
                this.safeSpawn(loc.clone().add(x, y, z), this.particle, 2, 0.0, 0.0, 0.0);
            }
        }
        loc.getWorld().spawnParticle(Particle.PORTAL, loc.clone().add(0.0, 2.0, 0.0), 80 * mult, 0.3, 0.3, 0.3, 0.7);
        loc.getWorld().spawnParticle(Particle.REVERSE_PORTAL, loc, 40 * mult, 2.0, 1.0, 2.0, 0.1);
    }

    private void playSphereBurst(Location loc, int mult) {
        int points = 80 * mult;
        double radius = 2.0 * (double)mult;
        for (int i = 0; i < points; ++i) {
            double phi = Math.acos(2.0 * random.nextDouble() - 1.0);
            double theta = random.nextDouble() * Math.PI * 2.0;
            double x = radius * Math.sin(phi) * Math.cos(theta);
            double y = radius * Math.cos(phi);
            double z = radius * Math.sin(phi) * Math.sin(theta);
            Vector dir = new Vector(x, y, z).normalize().multiply(0.3);
            loc.getWorld().spawnParticle(this.particle, loc.clone().add(x * 0.4, y * 0.4, z * 0.4), 0, dir.getX(), dir.getY(), dir.getZ(), 0.15);
        }
        loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 2, 0.0, 0.0, 0.0, 0.0);
        loc.getWorld().spawnParticle(Particle.FLASH, loc, 1, 0.0, 0.0, 0.0, 0.0);
    }

    private void playPillar(Location loc, int mult) {
        int height = 7 * mult;
        int pointsPerLevel = 12;
        double radius = 0.7;
        for (int y = 0; y < height * 10; ++y) {
            double currentRadius = radius * (1.0 - (double)y / ((double)height * 10.0) * 0.4);
            for (int p = 0; p < pointsPerLevel; ++p) {
                double angle = Math.PI * 2 / (double)pointsPerLevel * (double)p + (double)y * 0.25;
                double x = Math.cos(angle) * currentRadius;
                double z = Math.sin(angle) * currentRadius;
                this.safeSpawn(loc.clone().add(x, (double)y * 0.1, z), this.particle, 1, 0.0, 0.0, 0.0);
            }
        }
        loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0.0, (double)height, 0.0), 40 * mult, 0.5, 0.5, 0.5, 0.15);
        loc.getWorld().spawnParticle(Particle.FIREWORK, loc.clone().add(0.0, (double)height, 0.0), 30 * mult, 1.0, 0.5, 1.0, 0.2);
    }

    private void playHelixRise(Location loc, int mult) {
        int steps = 60 * mult;
        double radius = 1.3;
        double heightPerStep = 0.12;
        for (int i = 0; i < steps; ++i) {
            double angle = Math.PI * 5 * (double)i / (double)steps;
            double y = (double)i * heightPerStep;
            double x1 = Math.cos(angle) * radius;
            double z1 = Math.sin(angle) * radius;
            this.safeSpawn(loc.clone().add(x1, y, z1), this.particle, 2, 0.0, 0.0, 0.0);
            double x2 = Math.cos(angle + Math.PI) * radius;
            double z2 = Math.sin(angle + Math.PI) * radius;
            this.safeSpawn(loc.clone().add(x2, y, z2), this.particle, 2, 0.0, 0.0, 0.0);
        }
        loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0.0, (double)steps * heightPerStep / 2.0, 0.0), 30 * mult, 0.1, (double)steps * heightPerStep / 2.0, 0.1, 0.0);
    }

    private void playShatter(Location loc, int mult) {
        int fragments = 50 * mult;
        for (int i = 0; i < fragments; ++i) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double pitch = (random.nextDouble() - 0.5) * Math.PI;
            double speed = 0.4 + random.nextDouble() * 0.4;
            double vx = Math.cos(angle) * Math.cos(pitch) * speed;
            double vy = Math.sin(pitch) * speed + 0.15;
            double vz = Math.sin(angle) * Math.cos(pitch) * speed;
            loc.getWorld().spawnParticle(this.particle, loc, 0, vx, vy, vz, 0.2);
        }
        loc.getWorld().spawnParticle(Particle.CRIT, loc, 50 * mult, 0.5, 0.5, 0.5, 0.3);
        loc.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc, 40 * mult, 1.0, 1.0, 1.0, 0.2);
        loc.getWorld().playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.8f);
    }

    private void safeSpawn(Location loc, Particle particle, int count, double ox, double oy, double oz) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        try {
            if (particle == Particle.DUST || particle == Particle.DUST_PILLAR) {
                loc.getWorld().spawnParticle(Particle.DUST, loc, count, ox, oy, oz, 0.0, (Object)new Particle.DustOptions(Color.WHITE, 1.5f));
            } else if (particle == Particle.DUST_COLOR_TRANSITION) {
                loc.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, loc, count, ox, oy, oz, 0.0, (Object)new Particle.DustTransition(Color.WHITE, Color.GRAY, 1.5f));
            } else if (particle == Particle.BLOCK || particle == Particle.FALLING_DUST) {
                loc.getWorld().spawnParticle(Particle.DUST, loc, count, ox, oy, oz, 0.0, (Object)new Particle.DustOptions(Color.RED, 1.5f));
            } else if (particle == Particle.SCULK_CHARGE) {
                loc.getWorld().spawnParticle(Particle.SCULK_CHARGE, loc, count, ox, oy, oz, 0.0, (Object)Float.valueOf(0.0f));
            } else if (particle == Particle.SHRIEK) {
                loc.getWorld().spawnParticle(Particle.SHRIEK, loc, count, ox, oy, oz, 0.0, (Object)0);
            } else if (particle == Particle.VIBRATION) {
                loc.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc, count, ox, oy, oz, 0.0);
            } else if (particle == Particle.ITEM) {
                loc.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc, count, ox, oy, oz, 0.0);
            } else {
                loc.getWorld().spawnParticle(particle, loc, count, ox, oy, oz, 0.0);
            }
        }
        catch (Exception e) {
            try {
                loc.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc, count, ox, oy, oz, 0.0);
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
    }

    public static DeathEffectCosmetic getActiveEffect(UUID playerId) {
        return activeDeathEffects.get(playerId);
    }

    public static boolean hasActiveEffect(UUID playerId) {
        return activeDeathEffects.containsKey(playerId);
    }

    public static void removeAllEffects() {
        activeDeathEffects.clear();
    }

    public static enum DeathEffectType {
        EXPLOSION,
        SPIRAL,
        FOUNTAIN,
        SOUL_RELEASE,
        BLOOD_SPLATTER,
        FIREWORK,
        LIGHTNING,
        FREEZE,
        FLAME_BURST,
        HEARTS,
        SMOKE_CLOUD,
        CONFETTI,
        VORTEX,
        SPHERE_BURST,
        PILLAR,
        HELIX_RISE,
        SHATTER,
        GRAVE_HAND;

    }
}
