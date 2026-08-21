package net.schalker.SMPS.modules.cosmetics.models;

public enum CosmeticCategory {
    PET("pet", "Pet", "cosmetics.pet"),
    PARTICLE_EFFECT("particle_effect", "Particle Effect", "cosmetics.particle"),
    DEATH_EFFECT("death_effect", "Death Effect", "cosmetics.death"),
    ARROW_EFFECT("arrow_effect", "Arrow Effect", "cosmetics.arrow"),
    BALLOON("balloon", "Balloon", "cosmetics.balloon"),
    EMOTE("emote", "Emote", "cosmetics.emote"),
    MORPH("morph", "Morph", "cosmetics.morph"),
    MOUNT("mount", "Mount", "cosmetics.mount"),
    GADGET("gadget", "Gadget", "cosmetics.gadget"),
    STATUS("status", "Status", "cosmetics.status"),
    BANNER("banner", "Banner", "cosmetics.banner"),
    MUSIC("music", "Music", "cosmetics.music");

    private final String id;
    private final String displayName;
    private final String permission;

    CosmeticCategory(String id, String displayName, String permission) {
        this.id = id;
        this.displayName = displayName;
        this.permission = permission;
    }

    public String getId() {
        return this.id;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public String getPermission() {
        return this.permission;
    }

    public static CosmeticCategory fromId(String id) {
        for (CosmeticCategory category : values()) {
            if (category.id.equalsIgnoreCase(id)) {
                return category;
            }
        }
        return null;
    }

    public static CosmeticCategory fromName(String name) {
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
