package net.schalker.SMPS.modules.cosmetics.models;

import org.bukkit.entity.Player;

/**
 *      
 */
public abstract class Cosmetic {
    protected final String id;
    protected final String name;
    protected final CosmeticCategory category;
    protected final CosmeticRarity rarity;
    protected final String permission;
    protected final String itemMaterial;
    protected final int cost;
    protected final boolean enabled;
    protected final boolean purchasable;

    public Cosmetic(String id, String name, CosmeticCategory category, CosmeticRarity rarity, 
                    String permission, String itemMaterial, int cost, boolean enabled, boolean purchasable) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.rarity = rarity;
        this.permission = permission;
        this.itemMaterial = itemMaterial;
        this.cost = cost;
        this.enabled = enabled;
        this.purchasable = purchasable;
    }

    /**
     * ID 
     */
    public String getId() {
        return this.id;
    }

    /**
     *  
     */
    public String getName() {
        return this.name;
    }

    /**
     *  
     */
    public CosmeticCategory getCategory() {
        return this.category;
    }

    /**
     *  
     */
    public CosmeticRarity getRarity() {
        return this.rarity;
    }

    /**
     *   
     */
    public String getPermission() {
        return this.permission;
    }

    /**
     *    
     */
    public String getItemMaterial() {
        return this.itemMaterial;
    }

    /**
     *  
     */
    public int getCost() {
        return this.cost;
    }

    /**
     *   
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     *   
     */
    public boolean isPurchasable() {
        return this.purchasable;
    }

    /**
     * ,        
     */
    public boolean hasPermission(Player player) {
        if (this.permission == null || this.permission.isEmpty()) {
            return true;
        }
        return player.hasPermission(this.permission);
    }

    public boolean isVisibleTo(Player player) {
        return true;
    }

    public boolean isOpOnly() {
        return false;
    }

    /**
     *   (  )
     */
    public abstract void equip(Player player);

    /**
     *   (  )
     */
    public abstract void unequip(Player player);

    /**
     *   (    )
     */
    public void update(Player player) {
        //     
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Cosmetic cosmetic = (Cosmetic) obj;
        return this.id.equals(cosmetic.id) && this.category == cosmetic.category;
    }

    @Override
    public int hashCode() {
        return 31 * this.id.hashCode() + this.category.hashCode();
    }
}
