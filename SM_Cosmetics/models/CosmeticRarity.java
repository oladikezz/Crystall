package net.schalker.SMPS.modules.cosmetics.models;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

public enum CosmeticRarity {
    COMMON("common", "Common", NamedTextColor.GRAY, 1),
    UNCOMMON("uncommon", "Uncommon", NamedTextColor.GREEN, 2),
    RARE("rare", "Rare", NamedTextColor.AQUA, 3),
    EPIC("epic", "Epic", NamedTextColor.DARK_PURPLE, 4),
    LEGENDARY("legendary", "Legendary", NamedTextColor.GOLD, 5),
    MYTHIC("mythic", "Mythic", NamedTextColor.LIGHT_PURPLE, 6),
    SPECIAL("special", "Special", NamedTextColor.RED, 7);

    private final String id;
    private final String displayName;
    private final TextColor color;
    private final int tier;

    CosmeticRarity(String id, String displayName, TextColor color, int tier) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.tier = tier;
    }

    public String getId() {
        return this.id;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public TextColor getColor() {
        return this.color;
    }

    public String getColorCode() {
        return switch (this) {
            case COMMON -> "&7";
            case UNCOMMON -> "&a";
            case RARE -> "&b";
            case EPIC -> "&5";
            case LEGENDARY -> "&6";
            case MYTHIC -> "&d";
            case SPECIAL -> "&c";
        };
    }

    public int getTier() {
        return this.tier;
    }

    public static CosmeticRarity fromId(String id) {
        if (id == null) return COMMON;
        for (CosmeticRarity rarity : values()) {
            if (rarity.id.equalsIgnoreCase(id)) {
                return rarity;
            }
        }
        return COMMON;
    }

    public static CosmeticRarity fromName(String name) {
        if (name == null) return COMMON;
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return COMMON;
        }
    }
}
