package net.schalker.SMPS.modules.cosmetics.models;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import net.schalker.SMPS.modules.cosmetics.util.FastMathUtil;
import net.schalker.SMPS.modules.cosmetics.util.MathUtil;
import net.schalker.SMPS.modules.cosmetics.util.RGBFade;

import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 *   " "
 *   ProCosmetics -   
 */
public class ParticleCosmetic extends Cosmetic {
    private final Particle particleType;
    private final int count;
    private final double speed;
    private final ParticleShape shape;
    private final Color color;
    
    //    (UUID  -> ParticleData)
    private static final Map<UUID, ParticleData> activeParticles = new ConcurrentHashMap<>();
    private static final Random RANDOM = new Random();

    public enum ParticleShape {
        AROUND_PLAYER,   //  
        ABOVE_HEAD,      //  
        SPIRAL,          //  (BloodHelix)
        CIRCLE,          // 
        WINGS,           //  
        RAINBOW_WINGS,   //  
        TRAIL,           // 
        AURA,            // 
        TORNADO,         // 
        FLAME_RINGS,     //  
        BLACK_HOLE,      //  
        SHIELD,          //  
        HELIX,           //  
        FLAME_FAIRY      //  
    }

    /**
     *    
     */
    public static class ParticleData {
        public String cosmeticId;
        public int ticks = 0;
        public float step = 0;
        public RGBFade rgb = new RGBFade();
        public double lastX;
        public double lastY;
        public double lastZ;
        public boolean hasLastLocation = false;
        public Vector fairyPos = new Vector();
        public Vector fairyVel = new Vector();
        public Vector fairyGoal = new Vector();
        public boolean fairyInitialized = false;
    }

    public ParticleCosmetic(String id, String name, CosmeticRarity rarity, String permission,
                            String itemMaterial, int cost, boolean enabled, boolean purchasable,
                            Particle particleType, int count, double speed, ParticleShape shape, Color color) {
        super(id, name, CosmeticCategory.PARTICLE_EFFECT, rarity, permission, itemMaterial, cost, enabled, purchasable);
        this.particleType = particleType;
        this.count = count;
        this.speed = speed;
        this.shape = shape;
        this.color = color != null ? color : Color.WHITE;
    }

    public Particle getParticleType() {
        return this.particleType;
    }

    public int getCount() {
        return this.count;
    }

    public ParticleShape getShape() {
        return this.shape;
    }

    @Override
    public void equip(Player player) {
        this.unequip(player);
        ParticleData data = new ParticleData();
        data.cosmeticId = this.id;
        activeParticles.put(player.getUniqueId(), data);
    }

    @Override
    public void unequip(Player player) {
        activeParticles.remove(player.getUniqueId());
    }

    @Override
    public void update(Player player) {
        ParticleData data = activeParticles.get(player.getUniqueId());
        if (data == null || !data.cosmeticId.equals(this.id)) return;
        
        Location loc = player.getLocation();
        double velX = player.getVelocity().getX();
        double velZ = player.getVelocity().getZ();
        double horizontalVelocitySquared = velX * velX + velZ * velZ;

        double deltaHorizontalSquared = 1.0;
        if (data.hasLastLocation) {
            double dx = loc.getX() - data.lastX;
            double dz = loc.getZ() - data.lastZ;
            deltaHorizontalSquared = dx * dx + dz * dz;
        }

        boolean jumping = Math.abs(player.getVelocity().getY()) > 0.02 && !player.isOnGround();
        boolean isMoving = player.isSprinting()
                || player.isFlying()
                || player.isGliding()
                || jumping
                || horizontalVelocitySquared > 0.0025
                || deltaHorizontalSquared > 0.0004;
        boolean reducedMode = !isMoving;

        data.lastX = loc.getX();
        data.lastY = loc.getY();
        data.lastZ = loc.getZ();
        data.hasLastLocation = true;

        if (reducedMode && data.ticks % 2 != 0) {
            data.ticks++;
            if (data.ticks >= 360) data.ticks = 0;
            return;
        }
        
        switch (this.shape) {
            case AROUND_PLAYER -> spawnAroundPlayer(player, loc);
            case ABOVE_HEAD -> spawnAboveHead(player, loc);
            case SPIRAL, HELIX -> spawnHelix(player, loc, data, reducedMode);
            case CIRCLE -> spawnCircle(player, loc, data);
            case WINGS -> spawnWings(player, loc, reducedMode);
            case RAINBOW_WINGS -> spawnRainbowWings(player, loc, data, reducedMode);
            case TRAIL -> spawnTrail(player, loc);
            case AURA -> spawnAura(player, loc);
            case TORNADO -> spawnTornado(player, loc, data, reducedMode);
            case FLAME_RINGS -> spawnFlameRings(player, loc, data, reducedMode);
            case BLACK_HOLE -> spawnBlackHole(player, loc, data, reducedMode);
            case SHIELD -> spawnShield(player, loc, data, reducedMode);
            case FLAME_FAIRY -> spawnFlameFairy(player, loc, data);
        }
        
        data.ticks++;
        if (data.ticks >= 360) data.ticks = 0;
    }
    
    // ======    ======
    
    /**
     *          
     */
    private void safeSpawnParticle(Player player, Location loc, int count, double offsetX, double offsetY, double offsetZ, double extra) {
        // ,   
        if (particleType == Particle.DUST || particleType == Particle.DUST_PILLAR) {
            player.getWorld().spawnParticle(Particle.DUST, loc, count, offsetX, offsetY, offsetZ, extra, 
                new Particle.DustOptions(color, 1));
        } else if (particleType == Particle.DUST_COLOR_TRANSITION) {
            player.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, loc, count, offsetX, offsetY, offsetZ, extra,
                new Particle.DustTransition(color, Color.WHITE, 1));
        } else if (particleType == Particle.SCULK_CHARGE) {
            player.getWorld().spawnParticle(Particle.SCULK_CHARGE, loc, count, offsetX, offsetY, offsetZ, extra, 0.0f);
        } else if (particleType == Particle.SHRIEK) {
            player.getWorld().spawnParticle(Particle.SHRIEK, loc, count, offsetX, offsetY, offsetZ, extra, 0);
        } else if (particleType == Particle.VIBRATION) {
            // Vibration    - 
            player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc, count, offsetX, offsetY, offsetZ, extra);
        } else {
            //    
            try {
                player.getWorld().spawnParticle(particleType, loc, count, offsetX, offsetY, offsetZ, extra);
            } catch (IllegalArgumentException e) {
                //       -   
                player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc, count, offsetX, offsetY, offsetZ, extra);
            }
        }
    }
    
    // ======   ======
    
    private void spawnAroundPlayer(Player player, Location loc) {
        safeSpawnParticle(player, loc.clone().add(0, 1, 0), count, 0.5, 0.5, 0.5, speed);
    }
    
    private void spawnAboveHead(Player player, Location loc) {
        safeSpawnParticle(player, loc.clone().add(0, 2.5, 0), count, 0.2, 0.1, 0.2, speed);
    }
    
    private void spawnTrail(Player player, Location loc) {
        safeSpawnParticle(player, loc.clone().add(0, 0.1, 0), count, 0.1, 0, 0.1, speed);
    }
    
    private void spawnAura(Player player, Location loc) {
        if ("sakura_blossom".equals(this.id)) {
            boolean moving = player.getVelocity().lengthSquared() > 0.01;
            if (moving) {
                safeSpawnParticle(player, loc.clone().add(0, 1.1, 0), count, 0.55, 0.45, 0.55, 0);
            } else {
                safeSpawnParticle(player, loc.clone().add(0, 0.1, 0), count, 0.7, 0.05, 0.7, 0);
            }
            return;
        }
        safeSpawnParticle(player, loc.clone().add(0, 1, 0), count, 0.4, 0.6, 0.4, 0);
    }
    
    // ======  ( ProCosmetics) ======
    
    private static final int[][] WING_SHAPE = new int[][]{
        {0, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 0},
        {0, 0, 1, 1, 1, 1, 1, 0, 0, 0, 1, 1, 1, 1, 1, 0, 0},
        {0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0},
        {0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0},
        {0, 0, 0, 0, 1, 1, 1, 1, 0, 1, 1, 1, 1, 0, 0, 0, 0},
        {0, 0, 0, 0, 0, 1, 1, 1, 0, 1, 1, 1, 0, 0, 0, 0, 0},
        {0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0},
        {0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0}
    };
    
    private void spawnWings(Player player, Location loc, boolean isMoving) {
        if (isMoving) {
            //   -   
            loc.add(0, 0.1, 0);
            player.getWorld().spawnParticle(Particle.DUST, loc, 5, 0, 0, 0, 0, new Particle.DustOptions(color, 1));
            return;
        }
        
        //  -   
        renderShape(player, loc, WING_SHAPE, color, 0.2, 0.1, 0.3);
    }
    
    private void spawnRainbowWings(Player player, Location loc, ParticleData data, boolean isMoving) {
        data.rgb.nextRGB();
        Color rainbowColor = data.rgb.toColor();
        
        if (isMoving) {
            loc.add(0, 0.1, 0);
            player.getWorld().spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, new Particle.DustOptions(rainbowColor, 1));
            return;
        }
        
        renderShape(player, loc, WING_SHAPE, rainbowColor, 0.2, 0.1, 0.3);
    }
    
    private void renderShape(Player player, Location loc, int[][] shape, Color color, double spacing, double heightOffset, double distanceBehind) {
        double totalWidth = shape[0].length * spacing;
        double startX = -totalWidth / 2.0 + spacing / 2.0;
        double startY = shape.length * spacing;
        double startZ = -distanceBehind;
        
        double angle = -FastMathUtil.toRadians(loc.getYaw());
        Vector vector = new Vector();
        
        for (int row = 0; row < shape.length; row++) {
            for (int col = 0; col < shape[row].length; col++) {
                if (shape[row][col] != 0) {
                    double x = startX + (col * spacing);
                    double y = startY - (row * spacing);
                    double z = startZ;
                    
                    vector.setX(x).setY(y).setZ(z);
                    MathUtil.rotateAroundAxisY(vector, angle);
                    
                    Location particleLoc = loc.clone().add(0, heightOffset, 0).add(vector);
                    player.getWorld().spawnParticle(Particle.DUST, particleLoc, 0, 0, 0, 0, 0, new Particle.DustOptions(color, 1));
                }
            }
        }
    }
    
    // ======   (BloodHelix) ======
    
    private void spawnHelix(Player player, Location loc, ParticleData data, boolean isMoving) {
        Particle.DustOptions dust = new Particle.DustOptions(color, 1);
        
        if (isMoving) {
            loc.add(0, 0.2, 0);
            player.getWorld().spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, dust);
            return;
        }
        
        float startRadius = 1.2f;
        float radiusDecay = 0.08f;
        int steps = 14;
        float anglePerStep = 270.0f / steps;
        float rotationSpeed = 2.0f;
        double heightPerStep = 0.2;
        
        float radius = startRadius;
        
        for (int step = 0; step < steps; step++) {
            float angle = FastMathUtil.toRadians((anglePerStep * step) + data.ticks * rotationSpeed);
            float oppositeAngle = angle + FastMathUtil.PI;
            
            float x1 = radius * FastMathUtil.cos(angle);
            float z1 = radius * FastMathUtil.sin(angle);
            float x2 = radius * FastMathUtil.cos(oppositeAngle);
            float z2 = radius * FastMathUtil.sin(oppositeAngle);
            
            //  
            Location loc1 = loc.clone().add(x1, 0, z1);
            player.getWorld().spawnParticle(Particle.DUST, loc1, 1, 0, 0, 0, 0, dust);
            
            //   ()
            Location loc2 = loc.clone().add(x2, 0, z2);
            player.getWorld().spawnParticle(Particle.DUST, loc2, 1, 0, 0, 0, 0, dust);
            
            loc.add(0, heightPerStep, 0);
            radius -= radiusDecay;
        }
    }
    
    // ======  ======
    
    private void spawnTornado(Player player, Location loc, ParticleData data, boolean isMoving) {
        if (isMoving) {
            float yawAngle = FastMathUtil.toRadians(loc.getYaw());
            float offsetX = 0.1f * FastMathUtil.cos(yawAngle);
            float offsetZ = 0.1f * FastMathUtil.sin(yawAngle);
            
            loc.add(offsetX, 0.1, offsetZ);
            player.getWorld().spawnParticle(Particle.FIREWORK, loc, 0, 0, 0.6, 0, 0.3);
            loc.subtract(2 * offsetX, 0, 2 * offsetZ);
            player.getWorld().spawnParticle(Particle.FIREWORK, loc, 0, 0, 0.6, 0, 0.3);
            return;
        }
        
        loc.add(0, 0.4, 0);
        int lines = 5;
        float angleBetween = 360.0f / lines;
        float rotationSpeed = 4.0f;
        float spiralRadius = 2.5f;
        
        for (int i = 0; i < lines; i++) {
            float angle = rotationSpeed * FastMathUtil.toRadians(angleBetween * i + data.ticks);
            float offsetX = spiralRadius * FastMathUtil.sin(angle);
            float offsetZ = spiralRadius * FastMathUtil.cos(angle);
            
            loc.add(offsetX, 0, offsetZ);
            player.getWorld().spawnParticle(Particle.FIREWORK, loc, 0, offsetX, 0, offsetZ, -0.1);
            loc.subtract(offsetX, 0, offsetZ);
        }
    }
    
    // ======   ======
    
    private void spawnFlameRings(Player player, Location loc, ParticleData data, boolean isMoving) {
        if (isMoving) {
            float yawAngle = FastMathUtil.toRadians(loc.getYaw());
            float offsetX = 0.1f * FastMathUtil.cos(yawAngle);
            float offsetZ = 0.1f * FastMathUtil.sin(yawAngle);
            
            loc.add(offsetX, 0, offsetZ);
            player.getWorld().spawnParticle(Particle.FLAME, loc, 1, 0.3, 0, 0.6, 0);
            loc.subtract(2 * offsetX, 0, 2 * offsetZ);
            player.getWorld().spawnParticle(Particle.FLAME, loc, 1, 0.3, 0, 0.6, 0);
            return;
        }
        
        loc.add(0, 1.0, 0);
        float rotationSpeed = 10.0f;
        double axisZRotation = 45.0;
        
        float angle = rotationSpeed * FastMathUtil.toRadians(data.ticks);
        float x = FastMathUtil.cos(angle);
        float z = FastMathUtil.sin(angle);
        
        Vector vector = new Vector(x, 0, z);
        MathUtil.rotateAroundAxisZ(vector, axisZRotation);
        MathUtil.rotateAroundAxisY(vector, -FastMathUtil.toRadians(loc.getYaw()));
        
        loc.add(vector);
        player.getWorld().spawnParticle(Particle.FLAME, loc, 0, 0, 0, 0, 0);
    }
    
    // ======   ======
    
    private void spawnBlackHole(Player player, Location loc, ParticleData data, boolean isMoving) {
        Particle.DustOptions blackDust = new Particle.DustOptions(Color.BLACK, 1);
        
        if (isMoving) {
            loc.add(0, 0.3, 0);
            player.getWorld().spawnParticle(Particle.PORTAL, loc, 4, 0.1, 0.1, 0.1, 0);
            player.getWorld().spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, blackDust);
            return;
        }
        
        loc.add(0, 0.1, 0);
        
        //  
        int lines = 6;
        int pointsPerLine = 6;
        float anglePerLine = FastMathUtil.PI * 2.0f / lines;
        float spiralRadius = 1.0f;
        float rotationAngle = 0.02f * data.ticks;
        
        for (int line = 1; line <= lines; line++) {
            for (int point = 0; point < pointsPerLine; point++) {
                float progress = point / 4.0f;
                float angle = progress + anglePerLine * line;
                float radius = progress * spiralRadius;
                
                float x = FastMathUtil.cos(angle) * radius;
                float z = FastMathUtil.sin(angle) * radius;
                
                Vector vector = new Vector(x, 0, z);
                MathUtil.rotateAroundAxisY(vector, rotationAngle);
                
                Location spiralLoc = loc.clone().add(vector);
                player.getWorld().spawnParticle(Particle.DUST, spiralLoc, 1, 0, 0, 0, 0, blackDust);
            }
        }
        
        //  
        if (data.ticks % 4 == 0) {
            double offsetX = MathUtil.randomRange(-1.7, 1.7);
            double offsetY = MathUtil.randomRange(0.5, 0.7);
            double offsetZ = MathUtil.randomRange(-1.7, 1.7);
            
            loc.add(offsetX, offsetY, offsetZ);
            player.getWorld().spawnParticle(Particle.FLAME, loc, 0, -offsetX, -offsetY, -offsetZ, 0.08);
            loc.subtract(offsetX, offsetY, offsetZ);
            
            player.getWorld().spawnParticle(Particle.PORTAL, loc, 3, 0.6, 0, 0.6, 0);
        }
    }
    
    // ======   () ======
    
    private void spawnShield(Player player, Location loc, ParticleData data, boolean isMoving) {
        if (isMoving) {
            loc.add(0, 0.4, 0);
            player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc, 3, 0, 0, 0, 0);
            data.step = 0;
            return;
        }
        
        float shieldRadius = 1.3f;
        float heightOffset = 1.4f;
        float stepIncrement = FastMathUtil.PI / 10.0f;
        float angleIncrement = FastMathUtil.PI / 15.0f;
        float maxAngle = 2.0f * FastMathUtil.PI;
        int maxTicks = 40;
        int animationTicks = 20;
        
        int currentTick = data.ticks % maxTicks;
        
        if (currentTick < animationTicks) {
            data.step += stepIncrement;
            
            float sinStep = FastMathUtil.sin(data.step);
            float cosStep = FastMathUtil.cos(data.step);
            
            for (float angle = 0; angle <= maxAngle; angle += angleIncrement) {
                float cosAngle = FastMathUtil.cos(angle);
                float sinAngle = FastMathUtil.sin(angle);
                
                float offsetX = shieldRadius * cosAngle * sinStep;
                float offsetY = shieldRadius * cosStep + heightOffset;
                float offsetZ = shieldRadius * sinAngle * sinStep;
                
                Location shieldLoc = loc.clone().add(offsetX, offsetY, offsetZ);
                player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, shieldLoc, 1, 0, 0, 0, 0);
            }
            
            if (data.step >= 8.0f * FastMathUtil.PI) {
                data.step = 0;
            }
        }
    }
    
    // ======  ======
    
    private void spawnCircle(Player player, Location loc, ParticleData data) {
        double radius = 1.0;
        int points = 16;
        float rotationSpeed = 2.0f;
        
        for (int i = 0; i < points; i++) {
            double angle = (2 * Math.PI * i / points) + FastMathUtil.toRadians(data.ticks * rotationSpeed);
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            
            player.getWorld().spawnParticle(particleType, loc.clone().add(x, 0.1, z), 1, 0, 0, 0, 0);
        }
    }
    
    // ======   ( AI) ======
    
    private void spawnFlameFairy(Player player, Location loc, ParticleData data) {
        //      
        if (!data.fairyInitialized) {
            data.fairyPos.setX(0);
            data.fairyPos.setY(1.5);
            data.fairyPos.setZ(-1.5);
            data.fairyGoal.setX(1.0);
            data.fairyGoal.setY(1.5);
            data.fairyGoal.setZ(-1.0);
            data.fairyVel.zero();
            data.fairyInitialized = true;
        }
        
        //    60    
        double distToGoalSq = data.fairyPos.distanceSquared(data.fairyGoal);
        if (distToGoalSq < 0.3 || data.ticks % 60 == 0) {
            //     
            double angle = Math.random() * Math.PI * 2;
            double radius = 1.0 + Math.random() * 1.5;
            data.fairyGoal.setX(Math.cos(angle) * radius);
            data.fairyGoal.setY(1.0 + Math.random() * 1.5);
            data.fairyGoal.setZ(Math.sin(angle) * radius);
        }
        
        //    
        Vector toGoal = data.fairyGoal.clone().subtract(data.fairyPos);
        double distance = toGoal.length();
        if (distance > 0.01) {
            toGoal.normalize().multiply(0.08);
            data.fairyVel.add(toGoal).multiply(0.9);
            
            double speed = data.fairyVel.length();
            if (speed > 0.12) {
                data.fairyVel.normalize().multiply(0.12);
            }
        }
        
        data.fairyPos.add(data.fairyVel);
        
        //    
        if (data.fairyPos.length() > 3.0) {
            data.fairyPos.normalize().multiply(3.0);
        }
        
        //     
        Location fairyLoc = loc.clone().add(data.fairyPos);
        player.getWorld().spawnParticle(Particle.FLAME, fairyLoc, 2, 0.05, 0.05, 0.05, 0.01);
        player.getWorld().spawnParticle(Particle.SMALL_FLAME, fairyLoc, 1, 0.02, 0.02, 0.02, 0);
        
        //  
        if (data.ticks % 5 == 0) {
            player.getWorld().spawnParticle(Particle.LAVA, fairyLoc, 1, 0, 0, 0, 0);
        }
    }

    public static boolean hasActiveParticle(UUID playerId) {
        return activeParticles.containsKey(playerId);
    }

    public static String getActiveParticleId(UUID playerId) {
        ParticleData data = activeParticles.get(playerId);
        return data != null ? data.cosmeticId : null;
    }

    public static Set<UUID> getAllActiveParticles() {
        return activeParticles.keySet();
    }

    public static void removeAllParticles() {
        activeParticles.clear();
    }
}
