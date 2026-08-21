/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.schalker.DoAPI.DoAPI
 *  org.bukkit.Color
 *  org.bukkit.Material
 *  org.bukkit.Particle
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.configuration.file.YamlConfiguration
 *  org.bukkit.entity.EntityType
 */
package net.schalker.SMPS.modules.cosmetics.managers;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.schalker.DoAPI.DoAPI;
import net.schalker.SMPS.modules.cosmetics.models.ArrowEffectCosmetic;
import net.schalker.SMPS.modules.cosmetics.models.BalloonCosmetic;
import net.schalker.SMPS.modules.cosmetics.models.Cosmetic;
import net.schalker.SMPS.modules.cosmetics.models.CosmeticCategory;
import net.schalker.SMPS.modules.cosmetics.models.CosmeticRarity;
import net.schalker.SMPS.modules.cosmetics.models.DeathEffectCosmetic;
import net.schalker.SMPS.modules.cosmetics.models.MaceImpactEffect;
import net.schalker.SMPS.modules.cosmetics.models.ParticleCosmetic;
import net.schalker.SMPS.modules.cosmetics.models.PetCosmetic;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

public class CosmeticsManager {
    private final DoAPI plugin;
    private final Map<String, Cosmetic> cosmeticsById;
    private final Map<CosmeticCategory, Map<String, Cosmetic>> cosmeticsByCategory;
    private final Map<CosmeticRarity, List<Cosmetic>> cosmeticsByRarity;
    private final Map<String, BalloonCategoryConfig> balloonCategories;
    private final Map<String, String> balloonCosmeticCategoryById;

    public static final class BalloonCategoryConfig {
        private final String id;
        private final String name;
        private final String description;
        private final String item;

        public BalloonCategoryConfig(String id, String name, String description, String item) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.item = item;
        }

        public String getId() {
            return this.id;
        }

        public String getName() {
            return this.name;
        }

        public String getDescription() {
            return this.description;
        }

        public String getItem() {
            return this.item;
        }
    }

    public CosmeticsManager(DoAPI plugin) {
        this.plugin = plugin;
        this.cosmeticsById = new ConcurrentHashMap<String, Cosmetic>();
        this.cosmeticsByCategory = new ConcurrentHashMap<CosmeticCategory, Map<String, Cosmetic>>();
        this.cosmeticsByRarity = new ConcurrentHashMap<CosmeticRarity, List<Cosmetic>>();
        this.balloonCategories = new ConcurrentHashMap<String, BalloonCategoryConfig>();
        this.balloonCosmeticCategoryById = new ConcurrentHashMap<String, String>();
        for (CosmeticCategory cosmeticCategory : CosmeticCategory.values()) {
            this.cosmeticsByCategory.put(cosmeticCategory, Collections.synchronizedMap(new LinkedHashMap<>()));
        }
        for (Enum enum_ : CosmeticRarity.values()) {
            this.cosmeticsByRarity.put((CosmeticRarity)enum_, Collections.synchronizedList(new ArrayList()));
        }
    }

    public void loadCosmetics() {
        this.clearAll();
        int totalLoaded = 0;
        int petsLoaded = this.loadPets();
        totalLoaded += petsLoaded;
        int particlesLoaded = this.loadParticleEffects();
        totalLoaded += particlesLoaded;
        int arrowsLoaded = this.loadArrowEffects();
        totalLoaded += arrowsLoaded;
        int balloonsLoaded = this.loadBalloons();
        totalLoaded += balloonsLoaded;
        int deathLoaded = this.loadDeathEffects();
        totalLoaded += deathLoaded;
        int maceLoaded = this.loadMaceEffects();
        totalLoaded += maceLoaded;
        int tridentLoaded = this.loadTridentEffects();
        totalLoaded += tridentLoaded;
        int riptideLoaded = this.loadRiptideEffects();
        totalLoaded += riptideLoaded;
        this.debug("Loaded cosmetics: " + totalLoaded + " (pets: " + petsLoaded + ", particles: " + particlesLoaded + ", arrows: " + arrowsLoaded + ", balloons: " + balloonsLoaded + ", death: " + deathLoaded + ", mace: " + maceLoaded + ", trident: " + tridentLoaded + ", riptide: " + riptideLoaded + ")");
        this.plugin.getLogger().info("CosmeticsModule: Loaded " + totalLoaded + " cosmetics");
    }

    private int loadPets() {
        YamlConfiguration config = this.plugin.getModuleManager().loadModuleConfig("SM_cosmetics", "pets.yml");
        if (config == null) {
            this.debug("\u00d0\u0161\u00d0\u00be\u00d0\u00bd\u00d1\u201e\u00d0\u00b8\u00d0\u00b3 \u00d0\u00bf\u00d0\u00b8\u00d1\u201a\u00d0\u00be\u00d0\u00bc\u00d1\u2020\u00d0\u00b5\u00d0\u00b2 \u00d0\u00bd\u00d0\u00b5 \u00d0\u00bd\u00d0\u00b0\u00d0\u00b9\u00d0\u00b4\u00d0\u00b5\u00d0\u00bd");
            return 0;
        }
        if (!config.getBoolean("enabled", true)) {
            this.debug("\u00d0\u0178\u00d0\u00b8\u00d1\u201a\u00d0\u00be\u00d0\u00bc\u00d1\u2020\u00d1\u2039 \u00d0\u00be\u00d1\u201a\u00d0\u00ba\u00d0\u00bb\u00d1\u017d\u00d1\u2021\u00d0\u00b5\u00d0\u00bd\u00d1\u2039 \u00d0\u00b2 \u00d0\u00ba\u00d0\u00be\u00d0\u00bd\u00d1\u201e\u00d0\u00b8\u00d0\u00b3\u00d0\u00b5");
            return 0;
        }
        boolean showNames = config.getBoolean("show_names", true);
        ConfigurationSection cosmeticsSection = config.getConfigurationSection("cosmetics");
        if (cosmeticsSection == null) {
            return 0;
        }
        int loaded = 0;
        for (String id : cosmeticsSection.getKeys(false)) {
            ConfigurationSection petSection = cosmeticsSection.getConfigurationSection(id);
            if (petSection == null || !petSection.getBoolean("enabled", true)) continue;
            try {
                String entityTypeStr = petSection.getString("entity", "PIG");
                EntityType entityType = EntityType.valueOf((String)entityTypeStr.toUpperCase());
                if (entityType == EntityType.SHULKER || entityType == EntityType.WARDEN) {
                    continue;
                }
                CosmeticRarity rarity = CosmeticRarity.fromId(petSection.getString("rarity", "common"));
                boolean baby = petSection.getBoolean("baby", false);
                double scale = petSection.getDouble("scale", 1.0);
                String spawnSound = petSection.getString("spawn_sound", "");
                String catType = petSection.getString("cat_type", petSection.getString("frog_variant", ""));
                String parrotVariant = petSection.getString("parrot_variant", petSection.getString("axolotl_variant", ""));
                String item = petSection.getString("item", "minecraft:pig_spawn_egg");
                String name = petSection.getString("name", this.formatName(id));
                String permission = "smcosm.pet." + id;
                PetCosmetic pet = new PetCosmetic(id, name, rarity, permission, item, 0, true, false, entityType, baby, scale, spawnSound, catType, parrotVariant, showNames);
                this.registerCosmetic(pet);
                ++loaded;
            }
            catch (Exception e) {
                this.debug("\u00d0\u017e\u00d1\u02c6\u00d0\u00b8\u00d0\u00b1\u00d0\u00ba\u00d0\u00b0 \u00d0\u00b7\u00d0\u00b0\u00d0\u00b3\u00d1\u20ac\u00d1\u0192\u00d0\u00b7\u00d0\u00ba\u00d0\u00b8 \u00d0\u00bf\u00d0\u00b8\u00d1\u201a\u00d0\u00be\u00d0\u00bc\u00d1\u2020\u00d0\u00b0 " + id + ": " + e.getMessage());
            }
        }
        return loaded;
    }

    private int loadParticleEffects() {
        YamlConfiguration config = this.plugin.getModuleManager().loadModuleConfig("SM_cosmetics", "particles.yml");
        if (config == null) {
            this.debug("\u00d0\u0161\u00d0\u00be\u00d0\u00bd\u00d1\u201e\u00d0\u00b8\u00d0\u00b3 \u00d1\u2021\u00d0\u00b0\u00d1\u0081\u00d1\u201a\u00d0\u00b8\u00d1\u2020 \u00d0\u00bd\u00d0\u00b5 \u00d0\u00bd\u00d0\u00b0\u00d0\u00b9\u00d0\u00b4\u00d0\u00b5\u00d0\u00bd");
            return 0;
        }
        if (!config.getBoolean("enabled", true)) {
            this.debug("\u00d0\u00ad\u00d1\u201e\u00d1\u201e\u00d0\u00b5\u00d0\u00ba\u00d1\u201a\u00d1\u2039 \u00d1\u2021\u00d0\u00b0\u00d1\u0081\u00d1\u201a\u00d0\u00b8\u00d1\u2020 \u00d0\u00be\u00d1\u201a\u00d0\u00ba\u00d0\u00bb\u00d1\u017d\u00d1\u2021\u00d0\u00b5\u00d0\u00bd\u00d1\u2039 \u00d0\u00b2 \u00d0\u00ba\u00d0\u00be\u00d0\u00bd\u00d1\u201e\u00d0\u00b8\u00d0\u00b3\u00d0\u00b5");
            return 0;
        }
        ConfigurationSection cosmeticsSection = config.getConfigurationSection("cosmetics");
        if (cosmeticsSection == null) {
            return 0;
        }
        int loaded = 0;
        for (String id : cosmeticsSection.getKeys(false)) {
            ConfigurationSection particleSection = cosmeticsSection.getConfigurationSection(id);
            if (particleSection == null || !particleSection.getBoolean("enabled", true)) continue;
            try {
                ParticleCosmetic.ParticleShape shape;
                Particle particleType;
                CosmeticRarity rarity = CosmeticRarity.fromId(particleSection.getString("rarity", "common"));
                String item = particleSection.getString("item", "minecraft:blaze_powder");
                String particleStr = particleSection.getString("particle", "CRIT");
                String shapeStr = particleSection.getString("shape", "around_player");
                String colorStr = particleSection.getString("color", null);
                try {
                    particleType = Particle.valueOf((String)particleStr.toUpperCase());
                }
                catch (Exception e) {
                    particleType = this.guessParticleType(id);
                }
                try {
                    shape = ParticleCosmetic.ParticleShape.valueOf(shapeStr.toUpperCase());
                }
                catch (Exception e) {
                    shape = this.guessParticleShape(id);
                }
                Color color = null;
                if (colorStr != null && !colorStr.equalsIgnoreCase("rainbow") && colorStr.startsWith("#")) {
                    try {
                        int rgb = Integer.parseInt(colorStr.substring(1), 16);
                        color = Color.fromRGB((int)rgb);
                    }
                    catch (Exception rgb) {
                        // empty catch block
                    }
                }
                String permission = "smcosm.particle." + id;
                ParticleCosmetic particle = new ParticleCosmetic(id, this.formatName(id), rarity, permission, item, 0, true, false, particleType, 10, 0.01, shape, color);
                this.registerCosmetic(particle);
                ++loaded;
            }
            catch (Exception e) {
                this.debug("\u00d0\u017e\u00d1\u02c6\u00d0\u00b8\u00d0\u00b1\u00d0\u00ba\u00d0\u00b0 \u00d0\u00b7\u00d0\u00b0\u00d0\u00b3\u00d1\u20ac\u00d1\u0192\u00d0\u00b7\u00d0\u00ba\u00d0\u00b8 \u00d1\u008d\u00d1\u201e\u00d1\u201e\u00d0\u00b5\u00d0\u00ba\u00d1\u201a\u00d0\u00b0 " + id + ": " + e.getMessage());
            }
        }
        return loaded;
    }

    private int loadArrowEffects() {
        YamlConfiguration config = this.plugin.getModuleManager().loadModuleConfig("SM_cosmetics", "arrows.yml");
        if (config == null) {
            this.debug("\u00d0\u0161\u00d0\u00be\u00d0\u00bd\u00d1\u201e\u00d0\u00b8\u00d0\u00b3 \u00d1\u008d\u00d1\u201e\u00d1\u201e\u00d0\u00b5\u00d0\u00ba\u00d1\u201a\u00d0\u00be\u00d0\u00b2 \u00d1\u0081\u00d1\u201a\u00d1\u20ac\u00d0\u00b5\u00d0\u00bb \u00d0\u00bd\u00d0\u00b5 \u00d0\u00bd\u00d0\u00b0\u00d0\u00b9\u00d0\u00b4\u00d0\u00b5\u00d0\u00bd");
            return 0;
        }
        if (!config.getBoolean("enabled", true)) {
            this.debug("\u00d0\u00ad\u00d1\u201e\u00d1\u201e\u00d0\u00b5\u00d0\u00ba\u00d1\u201a\u00d1\u2039 \u00d1\u0081\u00d1\u201a\u00d1\u20ac\u00d0\u00b5\u00d0\u00bb \u00d0\u00be\u00d1\u201a\u00d0\u00ba\u00d0\u00bb\u00d1\u017d\u00d1\u2021\u00d0\u00b5\u00d0\u00bd\u00d1\u2039 \u00d0\u00b2 \u00d0\u00ba\u00d0\u00be\u00d0\u00bd\u00d1\u201e\u00d0\u00b8\u00d0\u00b3\u00d0\u00b5");
            return 0;
        }
        ConfigurationSection cosmeticsSection = config.getConfigurationSection("cosmetics");
        if (cosmeticsSection == null) {
            return 0;
        }
        int loaded = 0;
        for (String id : cosmeticsSection.getKeys(false)) {
            ConfigurationSection section = cosmeticsSection.getConfigurationSection(id);
            if (section == null || !section.getBoolean("enabled", true)) continue;
            try {
                CosmeticRarity rarity = CosmeticRarity.fromId(section.getString("rarity", "common"));
                String item = section.getString("item", "minecraft:arrow");
                String unifiedParticle = section.getString("particle", "CRIT");
                Particle trailParticle = Particle.valueOf((String)section.getString("trail_particle", unifiedParticle));
                Particle hitParticle = Particle.valueOf((String)section.getString("hit_particle", unifiedParticle));
                int particleCount = section.getInt("particle_count", 0);
                int trailCount = section.getInt("trail_count", particleCount > 0 ? particleCount : 1);
                int hitCount = section.getInt("hit_count", particleCount > 0 ? particleCount : 10);
                double hitSpread = section.getDouble("hit_spread", 0.5);
                if (section.contains("delta")) {
                    ConfigurationSection delta = section.getConfigurationSection("delta");
                    if (delta != null) {
                        double dx = Math.abs(delta.getDouble("x", hitSpread));
                        double dy = Math.abs(delta.getDouble("y", hitSpread));
                        double dz = Math.abs(delta.getDouble("z", hitSpread));
                        hitSpread = Math.max(hitSpread, Math.max(dx, Math.max(dy, dz)));
                    } else {
                        hitSpread = Math.max(hitSpread, section.getDouble("delta", hitSpread));
                    }
                }
                String unifiedSound = section.getString("sound", "");
                String trailSound = section.getString("trail_sound", unifiedSound);
                String hitSound = section.getString("hit_sound", unifiedSound);
                MaceImpactEffect maceImpactEffect = MaceImpactEffect.fromConfig(section.getString("mace_effect", null), id);
                String name = section.getString("name", this.formatName(id));
                String permission = "smcosm.arrow." + id;
                ArrowEffectCosmetic arrow = new ArrowEffectCosmetic(id, name, rarity, permission, item, 0, true, false, trailParticle, hitParticle, trailCount, hitCount, hitSpread, trailSound, hitSound, maceImpactEffect);
                this.registerCosmetic(arrow);
                ++loaded;
            }
            catch (Exception e) {
                this.debug("\u00d0\u017e\u00d1\u02c6\u00d0\u00b8\u00d0\u00b1\u00d0\u00ba\u00d0\u00b0 \u00d0\u00b7\u00d0\u00b0\u00d0\u00b3\u00d1\u20ac\u00d1\u0192\u00d0\u00b7\u00d0\u00ba\u00d0\u00b8 \u00d1\u008d\u00d1\u201e\u00d1\u201e\u00d0\u00b5\u00d0\u00ba\u00d1\u201a\u00d0\u00b0 \u00d1\u0081\u00d1\u201a\u00d1\u20ac\u00d0\u00b5\u00d0\u00bb\u00d1\u2039 " + id + ": " + e.getMessage());
            }
        }
        return loaded;
    }

    private int loadBalloons() {
        YamlConfiguration config = this.plugin.getModuleManager().loadModuleConfig("SM_cosmetics", "balloons.yml");
        if (config == null) {
            this.debug("\u00d0\u0161\u00d0\u00be\u00d0\u00bd\u00d1\u201e\u00d0\u00b8\u00d0\u00b3 \u00d0\u00b2\u00d0\u00be\u00d0\u00b7\u00d0\u00b4\u00d1\u0192\u00d1\u02c6\u00d0\u00bd\u00d1\u2039\u00d1\u2026 \u00d1\u02c6\u00d0\u00b0\u00d1\u20ac\u00d0\u00be\u00d0\u00b2 \u00d0\u00bd\u00d0\u00b5 \u00d0\u00bd\u00d0\u00b0\u00d0\u00b9\u00d0\u00b4\u00d0\u00b5\u00d0\u00bd");
            return 0;
        }
        if (!config.getBoolean("enabled", true)) {
            this.debug("\u00d0\u2019\u00d0\u00be\u00d0\u00b7\u00d0\u00b4\u00d1\u0192\u00d1\u02c6\u00d0\u00bd\u00d1\u2039\u00d0\u00b5 \u00d1\u02c6\u00d0\u00b0\u00d1\u20ac\u00d1\u2039 \u00d0\u00be\u00d1\u201a\u00d0\u00ba\u00d0\u00bb\u00d1\u017d\u00d1\u2021\u00d0\u00b5\u00d0\u00bd\u00d1\u2039 \u00d0\u00b2 \u00d0\u00ba\u00d0\u00be\u00d0\u00bd\u00d1\u201e\u00d0\u00b8\u00d0\u00b3\u00d0\u00b5");
            return 0;
        }
        ConfigurationSection cosmeticsSection = config.getConfigurationSection("cosmetics");
        if (cosmeticsSection == null) {
            return 0;
        }
        int loaded = 0;
        java.util.Set<String> seenBalloonVisuals = new java.util.HashSet<>();
        for (String id : cosmeticsSection.getKeys(false)) {
            ConfigurationSection section = cosmeticsSection.getConfigurationSection(id);
            if (section == null || !section.getBoolean("enabled", true)) continue;
            try {
                CosmeticRarity rarity = CosmeticRarity.fromId(section.getString("rarity", "common"));
                String item = section.getString("item", "minecraft:red_wool");
                Material material = Material.valueOf((String)section.getString("balloon_material", "RED_WOOL"));
                String color = section.getString("balloon_color", "RED");
                double leashLength = section.getDouble("leash_length", 2.5);
                double floatHeight = section.getDouble("float_height", 3.0);
                boolean hasPhysics = section.getBoolean("has_physics", true);
                String specialType = section.getString("special_type", null);
                String visualKey = ((specialType == null ? "default" : specialType.toUpperCase()) + "|" +
                    material.name() + "|" + color.toUpperCase(java.util.Locale.ROOT));
                if (!seenBalloonVisuals.add(visualKey)) {
                    this.debug("ÐŸÑ€Ð¾Ð¿ÑƒÑ‰ÐµÐ½ Ð´ÑƒÐ±Ð»Ð¸ÐºÐ°Ñ‚ ÑˆÐ°Ñ€Ð¸ÐºÐ° " + id + " (visual=" + visualKey + ")");
                    continue;
                }
                String permission = "smcosm.balloon." + id;
                BalloonCosmetic balloon = new BalloonCosmetic(id, this.formatName(id), rarity, permission, item, 0, true, false, material, color, leashLength, floatHeight, hasPhysics, specialType);
                this.registerCosmetic(balloon);
                ++loaded;
            }
            catch (Exception e) {
                this.debug("\u00d0\u017e\u00d1\u02c6\u00d0\u00b8\u00d0\u00b1\u00d0\u00ba\u00d0\u00b0 \u00d0\u00b7\u00d0\u00b0\u00d0\u00b3\u00d1\u20ac\u00d1\u0192\u00d0\u00b7\u00d0\u00ba\u00d0\u00b8 \u00d1\u02c6\u00d0\u00b0\u00d1\u20ac\u00d0\u00b8\u00d0\u00ba\u00d0\u00b0 " + id + ": " + e.getMessage());
            }
        }
        return loaded;
    }

    private int loadDeathEffects() {
        YamlConfiguration config = this.plugin.getModuleManager().loadModuleConfig("SM_cosmetics", "death_effects.yml");
        if (config == null) {
            this.debug("\u00d0\u0161\u00d0\u00be\u00d0\u00bd\u00d1\u201e\u00d0\u00b8\u00d0\u00b3 \u00d1\u008d\u00d1\u201e\u00d1\u201e\u00d0\u00b5\u00d0\u00ba\u00d1\u201a\u00d0\u00be\u00d0\u00b2 \u00d1\u0081\u00d0\u00bc\u00d0\u00b5\u00d1\u20ac\u00d1\u201a\u00d0\u00b8 \u00d0\u00bd\u00d0\u00b5 \u00d0\u00bd\u00d0\u00b0\u00d0\u00b9\u00d0\u00b4\u00d0\u00b5\u00d0\u00bd");
            return 0;
        }
        if (!config.getBoolean("enabled", true)) {
            this.debug("\u00d0\u00ad\u00d1\u201e\u00d1\u201e\u00d0\u00b5\u00d0\u00ba\u00d1\u201a\u00d1\u2039 \u00d1\u0081\u00d0\u00bc\u00d0\u00b5\u00d1\u20ac\u00d1\u201a\u00d0\u00b8 \u00d0\u00be\u00d1\u201a\u00d0\u00ba\u00d0\u00bb\u00d1\u017d\u00d1\u2021\u00d0\u00b5\u00d0\u00bd\u00d1\u2039 \u00d0\u00b2 \u00d0\u00ba\u00d0\u00be\u00d0\u00bd\u00d1\u201e\u00d0\u00b8\u00d0\u00b3\u00d0\u00b5");
            return 0;
        }
        ConfigurationSection cosmeticsSection = config.getConfigurationSection("cosmetics");
        if (cosmeticsSection == null) {
            return 0;
        }
        int loaded = 0;
        for (String id : cosmeticsSection.getKeys(false)) {
            ConfigurationSection section = cosmeticsSection.getConfigurationSection(id);
            if (section == null || !section.getBoolean("enabled", true)) continue;
            try {
                CosmeticRarity rarity = CosmeticRarity.fromId(section.getString("rarity", "common"));
                String item = section.getString("item", "minecraft:tnt");
                DeathEffectCosmetic.DeathEffectType effectType = DeathEffectCosmetic.DeathEffectType.valueOf(section.getString("effect_type", "EXPLOSION"));
                boolean showOnKill = section.getBoolean("show_on_kill", true);
                Particle particle = Particle.valueOf((String)section.getString("particle", "EXPLOSION"));
                int particleCount = section.getInt("particle_count", 10);
                double spread = section.getDouble("spread", 1.0);
                String sound = section.getString("sound", "");
                float soundVolume = (float)section.getDouble("sound_volume", 1.0);
                float soundPitch = (float)section.getDouble("sound_pitch", 1.0);
                String permission = "smcosm.death." + id;
                DeathEffectCosmetic death = new DeathEffectCosmetic(id, this.formatName(id), rarity, permission, item, 0, true, false, particle, particleCount, spread, sound, soundVolume, soundPitch, effectType, showOnKill);
                this.registerCosmetic(death);
                ++loaded;
            }
            catch (Exception e) {
                this.debug("\u00d0\u017e\u00d1\u02c6\u00d0\u00b8\u00d0\u00b1\u00d0\u00ba\u00d0\u00b0 \u00d0\u00b7\u00d0\u00b0\u00d0\u00b3\u00d1\u20ac\u00d1\u0192\u00d0\u00b7\u00d0\u00ba\u00d0\u00b8 \u00d1\u008d\u00d1\u201e\u00d1\u201e\u00d0\u00b5\u00d0\u00ba\u00d1\u201a\u00d0\u00b0 \u00d1\u0081\u00d0\u00bc\u00d0\u00b5\u00d1\u20ac\u00d1\u201a\u00d0\u00b8 " + id + ": " + e.getMessage());
            }
        }
        return loaded;
    }

    private int loadMaceEffects() {
        return this.loadWeaponEffectsFromFile("mace.yml", "mace");
    }

    private int loadTridentEffects() {
        return this.loadWeaponEffectsFromFile("trident.yml", "trident");
    }

    private int loadRiptideEffects() {
        return this.loadWeaponEffectsFromFile("riptide.yml", "riptide");
    }

    private int loadWeaponEffectsFromFile(String fileName, String label) {
        YamlConfiguration config = this.plugin.getModuleManager().loadModuleConfig("SM_cosmetics", fileName);
        if (config == null) {
            this.debug(label + " config not found: " + fileName);
            return 0;
        }
        if (!config.getBoolean("enabled", true)) {
            this.debug(label + " effects disabled in config");
            return 0;
        }
        ConfigurationSection cosmeticsSection = config.getConfigurationSection("cosmetics");
        if (cosmeticsSection == null) {
            return 0;
        }
        int loaded = 0;
        for (String id : cosmeticsSection.getKeys(false)) {
            ConfigurationSection section = cosmeticsSection.getConfigurationSection(id);
            if (section == null || !section.getBoolean("enabled", true)) continue;
            try {
                CosmeticRarity rarity = CosmeticRarity.fromId(section.getString("rarity", "common"));
                String item = section.getString("item", "minecraft:arrow");
                Particle trailParticle = Particle.valueOf((String)section.getString("trail_particle", "CRIT"));
                Particle hitParticle = Particle.valueOf((String)section.getString("hit_particle", "CRIT"));
                int trailCount = section.getInt("trail_count", 1);
                int hitCount = section.getInt("hit_count", 10);
                double hitSpread = section.getDouble("hit_spread", 0.5);
                String trailSound = section.getString("trail_sound", "");
                String hitSound = section.getString("hit_sound", "");
                MaceImpactEffect maceImpactEffect = MaceImpactEffect.fromConfig(section.getString("mace_effect", null), id);
                String name = section.getString("name", this.formatName(id));
                String permission = "smcosm.arrow." + id;
                ArrowEffectCosmetic arrow = new ArrowEffectCosmetic(id, name, rarity, permission, item, 0, true, false, trailParticle, hitParticle, trailCount, hitCount, hitSpread, trailSound, hitSound, maceImpactEffect);
                this.registerCosmetic(arrow);
                ++loaded;
            }
            catch (Exception e) {
                this.debug("Error loading " + label + " effect " + id + ": " + e.getMessage());
            }
        }
        return loaded;
    }

    private Particle guessParticleType(String id) {
        if ((id = id.toLowerCase()).contains("flame") || id.contains("fire")) {
            return Particle.FLAME;
        }
        if (id.contains("heart")) {
            return Particle.HEART;
        }
        if (id.contains("star") || id.contains("magic")) {
            return Particle.ENCHANT;
        }
        if (id.contains("smoke")) {
            return Particle.SMOKE;
        }
        if (id.contains("portal")) {
            return Particle.PORTAL;
        }
        if (id.contains("water") || id.contains("drip")) {
            return Particle.DRIPPING_WATER;
        }
        if (id.contains("lava")) {
            return Particle.DRIPPING_LAVA;
        }
        if (id.contains("snow") || id.contains("frost")) {
            return Particle.SNOWFLAKE;
        }
        if (id.contains("note") || id.contains("music")) {
            return Particle.NOTE;
        }
        if (id.contains("soul")) {
            return Particle.SOUL_FIRE_FLAME;
        }
        if (id.contains("electric") || id.contains("spark")) {
            return Particle.ELECTRIC_SPARK;
        }
        if (id.contains("cherry") || id.contains("blossom")) {
            return Particle.CHERRY_LEAVES;
        }
        if (id.contains("angel") || id.contains("wing")) {
            return Particle.END_ROD;
        }
        if (id.contains("aura")) {
            return Particle.WITCH;
        }
        return Particle.CRIT;
    }

    private ParticleCosmetic.ParticleShape guessParticleShape(String id) {
        if ((id = id.toLowerCase()).contains("rainbow_wing")) {
            return ParticleCosmetic.ParticleShape.RAINBOW_WINGS;
        }
        if (id.contains("wing")) {
            return ParticleCosmetic.ParticleShape.WINGS;
        }
        if (id.contains("helix")) {
            return ParticleCosmetic.ParticleShape.HELIX;
        }
        if (id.contains("spiral")) {
            return ParticleCosmetic.ParticleShape.SPIRAL;
        }
        if (id.contains("tornado")) {
            return ParticleCosmetic.ParticleShape.TORNADO;
        }
        if (id.contains("flame_ring")) {
            return ParticleCosmetic.ParticleShape.FLAME_RINGS;
        }
        if (id.contains("black_hole")) {
            return ParticleCosmetic.ParticleShape.BLACK_HOLE;
        }
        if (id.contains("shield")) {
            return ParticleCosmetic.ParticleShape.SHIELD;
        }
        if (id.contains("fairy")) {
            return ParticleCosmetic.ParticleShape.FLAME_FAIRY;
        }
        if (id.contains("circle") || id.contains("ring")) {
            return ParticleCosmetic.ParticleShape.CIRCLE;
        }
        if (id.contains("trail")) {
            return ParticleCosmetic.ParticleShape.TRAIL;
        }
        if (id.contains("aura")) {
            return ParticleCosmetic.ParticleShape.AURA;
        }
        if (id.contains("halo") || id.contains("above")) {
            return ParticleCosmetic.ParticleShape.ABOVE_HEAD;
        }
        return ParticleCosmetic.ParticleShape.AROUND_PLAYER;
    }

    public void registerCosmetic(Cosmetic cosmetic) {
        this.cosmeticsById.put(cosmetic.getId(), cosmetic);
        this.cosmeticsByCategory.get((Object)cosmetic.getCategory()).put(cosmetic.getId(), cosmetic);
        this.cosmeticsByRarity.get((Object)cosmetic.getRarity()).add(cosmetic);
    }

    public Cosmetic getCosmetic(String id) {
        return this.cosmeticsById.get(id);
    }

    public Collection<Cosmetic> getCosmeticsByCategory(CosmeticCategory category) {
        return Collections.unmodifiableCollection(this.cosmeticsByCategory.get((Object)category).values());
    }

    public List<Cosmetic> getCosmeticsByRarity(CosmeticRarity rarity) {
        return Collections.unmodifiableList(this.cosmeticsByRarity.get((Object)rarity));
    }

    public Collection<Cosmetic> getAllCosmetics() {
        return Collections.unmodifiableCollection(this.cosmeticsById.values());
    }

    public int getCosmeticsCount() {
        return this.cosmeticsById.size();
    }

    public int getCosmeticsCount(CosmeticCategory category) {
        return this.cosmeticsByCategory.get((Object)category).size();
    }

    public void clearAll() {
        this.cosmeticsById.clear();
        for (Map<String, Cosmetic> map : this.cosmeticsByCategory.values()) {
            map.clear();
        }
        for (List list : this.cosmeticsByRarity.values()) {
            list.clear();
        }
    }

    public void reload() {
        this.loadCosmetics();
    }

    private String formatName(String id) {
        String[] words = id.split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() <= 1) continue;
            sb.append(word.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    private void debug(String message) {
        this.plugin.getDebugSystem().log("CosmeticsManager", this.decodeMojibake(message));
    }

    private String decodeMojibake(String text) {
        if (text == null || !(text.contains("Ð") || text.contains("Ñ") || text.contains("â") || text.contains("Ã"))) {
            return text;
        }
        try {
            byte[] bytes = text.getBytes(Charset.forName("Windows-1252"));
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return text;
        }
    }
}


