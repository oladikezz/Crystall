package net.schalker.SMPS.modules.cosmetics.models;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Chicken;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MainHand;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import net.schalker.SMPS.modules.cosmetics.util.FastMathUtil;
import net.schalker.SMPS.modules.cosmetics.util.MathUtil;

import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 *     "   "
 *         ProCosmetics          
 *          . .        
 *    : RAINBOW (   ), ENDER (   ), GHAST ( )
 */
public class BalloonCosmetic extends Cosmetic {
    private final Material balloonMaterial;
    private final String balloonColor;
    private final double leashLength;
    private final double floatHeight;
    private final boolean hasPhysics;
    private final double scale;
    private final String specialType; // RAINBOW, ENDER, GHAST  null
    
    //        
    private static final Material[] RAINBOW_MATERIALS = {
        Material.RED_WOOL,
        Material.ORANGE_WOOL,
        Material.YELLOW_WOOL,
        Material.LIME_WOOL,
        Material.CYAN_WOOL,
        Material.BLUE_WOOL,
        Material.PURPLE_WOOL,
        Material.MAGENTA_WOOL,
        Material.PINK_WOOL
    };
    
    //     (UUID   -> BalloonData)
    private static final Map<UUID, BalloonData> activeBalloons = new ConcurrentHashMap<>();
    private static final Random RANDOM = new Random();
    
    //     (    ProCosmetics)
    private static final double DEFAULT_LEASH_LENGTH = 2.5;
    private static final double Y_INERTIA = 0.08;
    private static final double INERTIA_DECAY = 0.92;
    private static final float ROTATION_DECAY = 0.98f;
    private static final float MIN_ROTATION_SPEED = 0.5f;
    private static final double WIND_STRENGTH = 0.008;
    private static final double TURBULENCE_FREQUENCY = 0.06;
    private static final double LEASH_REATTACH_DISTANCE_SQ = 4.0 * 4.0;
    private static final double LEASH_HARD_RESET_DISTANCE_SQ = 8.0 * 8.0;
    private static final double FAST_MOVE_SPEED_SQ = 0.9 * 0.9;
    
    //      
    private static final Vector OFFSET_VECTOR = new Vector(-0.6, 0.0, 0.15);

    /**
     *    
     */
    public static class BalloonData {
        public String cosmeticId;
        public UUID balloonEntityId;   // ItemDisplay 
        public UUID leashEntityId;     //    
        public Location location;
        public Location targetLocation;
        public Vector inertia = new Vector(0, 0.15, 0);
        public Vector temp = new Vector();
        public Vector windForce = new Vector();
        public Vector windDirection = new Vector(1.0, 0.0, 0.3);
        public float rotationSpeed;
        public double windPhase;
        public double angle = 0;
        public int ticksSinceUpdate = 0;
        
        public BalloonData() {
            this.rotationSpeed = RANDOM.nextFloat() * 2 - 1;
            this.windPhase = RANDOM.nextDouble() * Math.PI * 2;
        }
    }

    public BalloonCosmetic(String id, String name, CosmeticRarity rarity, String permission,
                           String itemMaterial, int cost, boolean enabled, boolean purchasable,
                           Material balloonMaterial, String balloonColor, double leashLength, 
                           double floatHeight, boolean hasPhysics) {
        this(id, name, rarity, permission, itemMaterial, cost, enabled, purchasable,
             balloonMaterial, balloonColor, leashLength, floatHeight, hasPhysics, null);
    }
    
    public BalloonCosmetic(String id, String name, CosmeticRarity rarity, String permission,
                           String itemMaterial, int cost, boolean enabled, boolean purchasable,
                           Material balloonMaterial, String balloonColor, double leashLength, 
                           double floatHeight, boolean hasPhysics, String specialType) {
        super(id, name, CosmeticCategory.BALLOON, rarity, permission, itemMaterial, cost, enabled, purchasable);
        this.balloonMaterial = balloonMaterial;
        this.balloonColor = balloonColor;
        this.leashLength = leashLength > 0 ? leashLength : DEFAULT_LEASH_LENGTH;
        this.floatHeight = floatHeight > 0 ? floatHeight : 2.5;
        this.hasPhysics = hasPhysics;
        this.scale = 1.0;
        this.specialType = specialType;
    }
    
    public String getSpecialType() {
        return this.specialType;
    }

    public Material getBalloonMaterial() {
        return this.balloonMaterial;
    }

    public String getBalloonColor() {
        return this.balloonColor;
    }

    public double getLeashLength() {
        return this.leashLength;
    }

    public double getFloatHeight() {
        return this.floatHeight;
    }

    public boolean hasPhysics() {
        return this.hasPhysics;
    }

    @Override
    public void equip(Player player) {
        this.unequip(player);
        
        if (!player.isOnline() || player.isDead()) {
            return;
        }
        
        try {
            //      
            Location targetLoc = getTargetLocation(player);
            
            //                  
            //                 Folia
            Chicken leashChicken = (Chicken) player.getWorld().spawnEntity(targetLoc, EntityType.CHICKEN);
            leashChicken.setBaby();
            leashChicken.setAgeLock(true);
            leashChicken.setInvisible(true);
            leashChicken.setSilent(true);
            leashChicken.setAI(false);
            leashChicken.setGravity(false);
            leashChicken.setPersistent(false);
            leashChicken.setInvulnerable(true);
            leashChicken.setCollidable(false);
            leashChicken.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1, false, false));
            
            //            
            leashChicken.setLeashHolder(player);
            
            Entity balloonEntity;
            
            //     GHAST -      
            if ("GHAST".equals(this.specialType)) {
                org.bukkit.entity.Ghast ghast = (org.bukkit.entity.Ghast) player.getWorld().spawnEntity(targetLoc, EntityType.GHAST);
                ghast.setSilent(true);           //  
                ghast.setAI(false);              //  AI
                ghast.setInvulnerable(true);     // 
                ghast.setCollidable(false);      //  
                ghast.setPersistent(false);
                ghast.setGravity(false);
                //       scale attribute (    API)
                try {
                    var scaleAttribute = resolveAttribute("SCALE");
                    if (scaleAttribute == null) { throw new IllegalStateException("SCALE attribute unavailable"); }
                    var scaleAttr = ghast.getAttribute(scaleAttribute);
                    if (scaleAttr != null) {
                        scaleAttr.setBaseValue(0.3); //  
                    }
                } catch (Exception ignored) {
                    // SCALE not available in this API build.
                }
                balloonEntity = ghast;
            } else {
                //   ItemDisplay    
                ItemDisplay display = (ItemDisplay) player.getWorld().spawnEntity(targetLoc, EntityType.ITEM_DISPLAY);
                display.setItemStack(new ItemStack(this.balloonMaterial));
                display.setPersistent(false);
                display.setTeleportDuration(2); //  
                
                //       (    45  )
                float s = (float) this.scale;
                float rotationAngle = (float) (Math.PI / 4); // 45 
                display.setTransformation(new Transformation(
                    new Vector3f(0, 0.3f, 0),              // translation
                    new AxisAngle4f(rotationAngle, 0, 0, 1), // left rotation -    Z ()
                    new Vector3f(s, s, s),                  // scale
                    new AxisAngle4f(rotationAngle, 1, 0, 0)  // right rotation -    X
                ));
                balloonEntity = display;
            }
            
            //    
            BalloonData data = new BalloonData();
            data.cosmeticId = this.id;
            data.balloonEntityId = balloonEntity.getUniqueId();
            data.leashEntityId = leashChicken.getUniqueId();
            data.location = targetLoc.clone();
            data.targetLocation = targetLoc.clone();
            
            activeBalloons.put(player.getUniqueId(), data);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void unequip(Player player) {
        BalloonData data = activeBalloons.remove(player.getUniqueId());
        if (data != null) {
            //     ( )
            if (data.leashEntityId != null) {
                Entity leashEntity = player.getServer().getEntity(data.leashEntityId);
                if (leashEntity != null) {
                    leashEntity.remove();
                }
            }
            
            //        
            if (data.balloonEntityId != null) {
                Entity entity = player.getServer().getEntity(data.balloonEntityId);
                if (entity != null) {
                    Location effectLoc = entity.getLocation();
                    effectLoc.getWorld().playSound(effectLoc, Sound.ENTITY_CHICKEN_EGG, 0.5f, 0.0f);
                    effectLoc.getWorld().spawnParticle(Particle.CLOUD, effectLoc, 10, 0.15, 0.15, 0.15, 0.05);
                    entity.remove();
                }
            }
        }
    }

    @Override
    public void update(Player player) {
        BalloonData data = activeBalloons.get(player.getUniqueId());
        if (data == null || !data.cosmeticId.equals(this.id)) return;
        
        Entity balloon = player.getServer().getEntity(data.balloonEntityId);
        Entity leash = player.getServer().getEntity(data.leashEntityId);
        
        if (balloon == null || !balloon.isValid()) {
            activeBalloons.remove(player.getUniqueId());
            if (leash != null) leash.remove();
            return;
        }
        
        //    
        if (leash == null || !leash.isValid()) {
            //        
            try {
                Chicken newChicken = (Chicken) player.getWorld().spawnEntity(data.location, EntityType.CHICKEN);
                newChicken.setBaby();
                newChicken.setAgeLock(true);
                newChicken.setInvisible(true);
                newChicken.setSilent(true);
                newChicken.setAI(false);
                newChicken.setGravity(false);
                newChicken.setPersistent(false);
                newChicken.setInvulnerable(true);
                newChicken.setCollidable(false);
                newChicken.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1, false, false));
                newChicken.setLeashHolder(player);
                data.leashEntityId = newChicken.getUniqueId();
                leash = newChicken;
            } catch (Exception ignored) {
                return;
            }
        }
        
        //    
        updatePhysics(player, data);
        stabilizeLeash(player, data, leash);
        
        //        
        balloon.teleportAsync(data.location);
        if (leash != null) {
            leash.teleportAsync(data.location);
        }
        
        //    
        if (balloon instanceof ItemDisplay display) {
            // Rainbow -       10  
            if ("RAINBOW".equals(this.specialType) && data.ticksSinceUpdate % 10 == 0) {
                int colorIndex = (int) ((System.currentTimeMillis() / 200) % RAINBOW_MATERIALS.length);
                display.setItemStack(new ItemStack(RAINBOW_MATERIALS[colorIndex]));
            }
            
            // Ender -    
            if ("ENDER".equals(this.specialType) && data.ticksSinceUpdate % 5 == 0) {
                data.location.getWorld().spawnParticle(Particle.PORTAL, data.location, 3, 0.2, 0.2, 0.2, 0.05);
            }
        }
        
        data.ticksSinceUpdate++;
    }
    
    private void stabilizeLeash(Player player, BalloonData data, Entity leash) {
        if (!(leash instanceof Chicken chicken)) {
            return;
        }

        Location playerLoc = player.getLocation();
        Location leashLoc = chicken.getLocation();
        if (playerLoc.getWorld() == null || leashLoc.getWorld() == null || !playerLoc.getWorld().equals(leashLoc.getWorld())) {
            return;
        }

        double speedSq = player.getVelocity().lengthSquared();
        double leashDistanceSq = playerLoc.distanceSquared(leashLoc);
        boolean needsReattach = speedSq > FAST_MOVE_SPEED_SQ
            || leashDistanceSq > LEASH_REATTACH_DISTANCE_SQ
            || chicken.getLeashHolder() != player;

        if (!needsReattach) {
            return;
        }

        if (leashDistanceSq > LEASH_HARD_RESET_DISTANCE_SQ) {
            Location safeLoc = getTargetLocation(player);
            data.location = safeLoc.clone();
            data.targetLocation = safeLoc.clone();
            chicken.teleportAsync(safeLoc);
        }

        try {
            chicken.setLeashHolder(player);
        } catch (Exception ignored) {
        }
    }

    private Location getTargetLocation(Player player) {
        double xOffset = player.getMainHand() == MainHand.RIGHT ? OFFSET_VECTOR.getX() : -OFFSET_VECTOR.getX();
        Vector offset = new Vector(xOffset, OFFSET_VECTOR.getY(), OFFSET_VECTOR.getZ());
        
        Location targetLoc = player.getLocation().clone();
        //       yaw  
        MathUtil.rotateAroundAxisY(offset, -FastMathUtil.toRadians(targetLoc.getYaw()));
        
        targetLoc.add(offset.getX(), this.floatHeight, offset.getZ());
        targetLoc.setPitch(0);
        return targetLoc;
    }
    
    private void updatePhysics(Player player, BalloonData data) {
        //      
        Location targetLoc = getTargetLocation(player);
        data.targetLocation = targetLoc;
        data.location.setWorld(targetLoc.getWorld());
        
        // Rotation
        data.rotationSpeed *= ROTATION_DECAY;
        if (Math.abs(data.rotationSpeed) < MIN_ROTATION_SPEED) {
            data.rotationSpeed = data.rotationSpeed >= 0 ? MIN_ROTATION_SPEED : -MIN_ROTATION_SPEED;
        }
        data.angle += data.rotationSpeed;
        
        // Store previous position
        data.temp.setX(data.location.getX());
        data.temp.setY(data.location.getY());
        data.temp.setZ(data.location.getZ());
        
        // Apply inertia
        data.inertia.multiply(INERTIA_DECAY);
        if (data.inertia.getY() < Y_INERTIA) {
            data.inertia.setY(Math.min(Y_INERTIA, data.inertia.getY() + Y_INERTIA * 0.5));
        }
        
        // Wind and turbulence
        data.windPhase += TURBULENCE_FREQUENCY;
        double windMagnitude = Math.sin(data.windPhase) * WIND_STRENGTH;
        data.windForce.setX(data.windDirection.getX() * windMagnitude + (RANDOM.nextDouble() - 0.5) * WIND_STRENGTH * 0.5);
        data.windForce.setY(data.windDirection.getY() * windMagnitude);
        data.windForce.setZ(data.windDirection.getZ() * windMagnitude + (RANDOM.nextDouble() - 0.5) * WIND_STRENGTH * 0.5);
        data.inertia.add(data.windForce);
        
        // Update position
        data.location.add(data.inertia.getX(), data.inertia.getY(), data.inertia.getZ());
        data.location.setYaw((float) data.angle);
        
        // Constrain to leash length
        double distance = targetLoc.distance(data.location);
        if (distance > this.leashLength) {
            double percentage = this.leashLength / distance;
            data.location.setX(MathUtil.lerp(targetLoc.getX(), data.location.getX(), percentage));
            data.location.setY(MathUtil.lerp(targetLoc.getY(), data.location.getY(), percentage));
            data.location.setZ(MathUtil.lerp(targetLoc.getZ(), data.location.getZ(), percentage));
        }
        
        // Calculate new inertia
        data.inertia.setX(data.location.getX() - data.temp.getX());
        data.inertia.setY(data.location.getY() - data.temp.getY());
        data.inertia.setZ(data.location.getZ() - data.temp.getZ());
        
        // Update rotation from movement
        data.rotationSpeed += (float) ((data.inertia.getX() - data.inertia.getZ()) * 0.8);
    }

    public static boolean hasActiveBalloon(UUID playerId) {
        return activeBalloons.containsKey(playerId);
    }

    public static UUID getActiveBalloonId(UUID playerId) {
        BalloonData data = activeBalloons.get(playerId);
        return data != null ? data.balloonEntityId : null;
    }

    public static String getActiveCosmeticId(UUID playerId) {
        BalloonData data = activeBalloons.get(playerId);
        return data != null ? data.cosmeticId : null;
    }
    
    public static UUID getLeashEntityId(UUID playerId) {
        BalloonData data = activeBalloons.get(playerId);
        return data != null ? data.leashEntityId : null;
    }

    public static Set<UUID> getAllActiveBalloons() {
        return activeBalloons.keySet();
    }

    public static void unequipActiveBalloon(Player player) {
        if (player == null) {
            return;
        }
        BalloonData data = activeBalloons.remove(player.getUniqueId());
        if (data == null) {
            return;
        }

        if (data.leashEntityId != null) {
            Entity leashEntity = player.getServer().getEntity(data.leashEntityId);
            if (leashEntity != null) {
                leashEntity.remove();
            }
        }

        if (data.balloonEntityId != null) {
            Entity entity = player.getServer().getEntity(data.balloonEntityId);
            if (entity != null) {
                try {
                    Location effectLoc = entity.getLocation();
                    effectLoc.getWorld().playSound(effectLoc, Sound.ENTITY_CHICKEN_EGG, 0.5f, 0.0f);
                    effectLoc.getWorld().spawnParticle(Particle.CLOUD, effectLoc, 10, 0.15, 0.15, 0.15, 0.05);
                } catch (Exception ignored) {
                }
                entity.remove();
            }
        }
    }

    public static void removeAllBalloons() {
        activeBalloons.clear();
    }
    
    /**
     *     leash   ( )  
     */
    public static UUID getOwnerOfLeashEntity(UUID leashEntityId) {
        for (Map.Entry<UUID, BalloonData> entry : activeBalloons.entrySet()) {
            if (entry.getValue().leashEntityId != null 
                && entry.getValue().leashEntityId.equals(leashEntityId)) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    /**
     *        / 
     */
    public static void respawnBalloon(Player player) {
        BalloonData data = activeBalloons.get(player.getUniqueId());
        if (data == null) return;
        
        //      
        Entity balloon = player.getServer().getEntity(data.balloonEntityId);
        Entity leash = player.getServer().getEntity(data.leashEntityId);
        
        //    
        Location newLoc = player.getLocation().add(0, 2.5, 0);
        data.location = newLoc.clone();
        data.targetLocation = newLoc.clone();
        data.inertia.zero();
        data.inertia.setY(0.15);
        
        if (balloon != null) {
            balloon.teleportAsync(newLoc);
        }
        if (leash != null) {
            leash.teleportAsync(newLoc);
            //    
            if (leash instanceof Chicken chicken) {
                chicken.setLeashHolder(player);
            }
        }
    }

    private static org.bukkit.attribute.Attribute resolveAttribute(String... names) {
        for (String name : names) {
            try {
                var field = org.bukkit.attribute.Attribute.class.getField(name);
                Object value = field.get(null);
                if (value instanceof org.bukkit.attribute.Attribute attribute) {
                    return attribute;
                }
            } catch (Exception ignored) {
            }
            try {
                if (org.bukkit.attribute.Attribute.class.isEnum()) {
                    @SuppressWarnings({"rawtypes", "unchecked"})
                    Object value = Enum.valueOf((Class) org.bukkit.attribute.Attribute.class, name);
                    if (value instanceof org.bukkit.attribute.Attribute attribute) {
                        return attribute;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
