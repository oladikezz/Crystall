package net.schalker.SMPS.modules.cosmetics.models;

import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 *    
 */
public class UserCosmetics {
    private final UUID playerId;
    private final Map<CosmeticCategory, Cosmetic> equippedCosmetics;
    private final Map<CosmeticCategory, Cosmetic> savedCosmetics; //   
    private final Map<String, Boolean> unlockedCosmetics; // cosmetic_id -> unlocked

    public UserCosmetics(UUID playerId) {
        this.playerId = playerId;
        this.equippedCosmetics = new EnumMap<>(CosmeticCategory.class);
        this.savedCosmetics = new EnumMap<>(CosmeticCategory.class);
        this.unlockedCosmetics = new java.util.concurrent.ConcurrentHashMap<>();
    }

    /**
     * UUID 
     */
    public UUID getPlayerId() {
        return this.playerId;
    }

    /**
     *  
     */
    public void equipCosmetic(Cosmetic cosmetic, Player player) {
        //     
        Cosmetic current = this.equippedCosmetics.get(cosmetic.getCategory());
        if (current != null) {
            current.unequip(player);
        }
        
        //  
        cosmetic.equip(player);
        this.equippedCosmetics.put(cosmetic.getCategory(), cosmetic);
    }

    /**
     *    
     */
    public void unequipCosmetic(CosmeticCategory category, Player player) {
        Cosmetic current = this.equippedCosmetics.remove(category);
        if (current != null) {
            current.unequip(player);
        }
    }

    /**
     *   
     */
    public void unequipAll(Player player) {
        for (Map.Entry<CosmeticCategory, Cosmetic> entry : this.equippedCosmetics.entrySet()) {
            entry.getValue().unequip(player);
        }
        this.equippedCosmetics.clear();
    }

    public void stashAndUnequipCosmetic(CosmeticCategory category, Player player) {
        Cosmetic current = this.equippedCosmetics.remove(category);
        if (current != null) {
            this.savedCosmetics.put(category, current);
            current.unequip(player);
        }
    }

    public void stashAndUnequipAll(Player player) {
        for (Map.Entry<CosmeticCategory, Cosmetic> entry : this.equippedCosmetics.entrySet()) {
            this.savedCosmetics.put(entry.getKey(), entry.getValue());
            entry.getValue().unequip(player);
        }
        this.equippedCosmetics.clear();
    }

    /**
     *    
     */
    public Cosmetic getEquippedCosmetic(CosmeticCategory category) {
        return this.equippedCosmetics.get(category);
    }
    
    /**
     *    (  equip)
     */
    public void setEquippedCosmetic(CosmeticCategory category, Cosmetic cosmetic) {
        this.equippedCosmetics.put(category, cosmetic);
    }
    
    /**
     *     
     */
    public Cosmetic getSavedCosmetic(CosmeticCategory category) {
        return this.savedCosmetics.get(category);
    }
    
    /**
     *   
     */
    public void clearSavedCosmetic(CosmeticCategory category) {
        this.savedCosmetics.remove(category);
    }

    public void clearAllSavedCosmetics() {
        this.savedCosmetics.clear();
    }

    public Map<CosmeticCategory, Cosmetic> getAllSavedCosmetics() {
        return new EnumMap<>(this.savedCosmetics);
    }

    /**
     * ,    
     */
    public boolean hasEquipped(CosmeticCategory category) {
        return this.equippedCosmetics.containsKey(category);
    }

    /**
     *    
     */
    public Map<CosmeticCategory, Cosmetic> getAllEquipped() {
        return new EnumMap<>(this.equippedCosmetics);
    }

    /**
     *  
     */
    public void unlockCosmetic(String cosmeticId) {
        this.unlockedCosmetics.put(cosmeticId, true);
    }

    /**
     * ,   
     */
    public boolean isUnlocked(String cosmeticId) {
        return this.unlockedCosmetics.getOrDefault(cosmeticId, false);
    }

    /**
     *    
     */
    public Map<String, Boolean> getAllUnlocked() {
        return new java.util.HashMap<>(this.unlockedCosmetics);
    }

    /**
     *     (  )
     */
    public void updateAll(Player player) {
        if (player == null || !player.isOnline()) return;
        
        for (Cosmetic cosmetic : this.equippedCosmetics.values()) {
            cosmetic.update(player);
        }
    }
}
