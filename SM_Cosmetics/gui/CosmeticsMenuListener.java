package net.schalker.SMPS.modules.cosmetics.gui;

import net.schalker.SMPS.modules.cosmetics.models.Cosmetic;
import net.schalker.SMPS.modules.cosmetics.models.CosmeticCategory;
import net.schalker.SMPS.modules.cosmetics.models.UserCosmeticSettings;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import net.schalker.DoAPI.DoAPI;
import net.schalker.SMPS.modules.cosmetics.CosmeticsModule;
import net.schalker.SMPS.modules.cosmetics.managers.MessageManager;
import net.schalker.SMPS.modules.cosmetics.models.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * ÃƒÂÃ¢â‚¬ÂºÃƒÂÃ‚Â¸Ãƒâ€˜Ã‚ÂÃƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚ÂµÃƒÂÃ‚Â½ÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â€šÂ¬ ÃƒÂÃ‚Â´ÃƒÂÃ‚Â»Ãƒâ€˜Ã‚Â ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â±Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â°ÃƒÂÃ‚Â±ÃƒÂÃ‚Â¾Ãƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚ÂºÃƒÂÃ‚Â¸ ÃƒÂÃ‚ÂºÃƒÂÃ‚Â»ÃƒÂÃ‚Â¸ÃƒÂÃ‚ÂºÃƒÂÃ‚Â¾ÃƒÂÃ‚Â² ÃƒÂÃ‚Â² ÃƒÂÃ‚Â¼ÃƒÂÃ‚ÂµÃƒÂÃ‚Â½Ãƒâ€˜Ã…Â½ ÃƒÂÃ‚ÂºÃƒÂÃ‚Â¾Ãƒâ€˜Ã‚ÂÃƒÂÃ‚Â¼ÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚Â¸ÃƒÂÃ‚ÂºÃƒÂÃ‚Â¸
 */
public class CosmeticsMenuListener implements Listener {
    @SuppressWarnings("unused")
    private final DoAPI plugin;
    private final CosmeticsModule module;
    private final CosmeticsMenuManager menuManager;
    private final MessageManager messages;

    public CosmeticsMenuListener(DoAPI plugin, CosmeticsModule module, 
                                  CosmeticsMenuManager menuManager, MessageManager messages) {
        this.plugin = plugin;
        this.module = module;
        this.menuManager = menuManager;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        UUID playerId = player.getUniqueId();
        if (this.menuManager.getOpenInventory(playerId) != event.getView().getTopInventory()) {
            return;
        }

        String menuId = this.menuManager.getOpenMenuId(playerId);

        event.setCancelled(true);
        event.setResult(org.bukkit.event.Event.Result.DENY);

        if (menuId == null) {
            this.menuManager.closeMenu(playerId);
            player.closeInventory();
            return;
        }

        InventoryAction action = event.getAction();
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY ||
            action == InventoryAction.COLLECT_TO_CURSOR ||
            action == InventoryAction.HOTBAR_SWAP ||
            action == InventoryAction.CLONE_STACK ||
            action == InventoryAction.SWAP_WITH_CURSOR) {
            return;
        }

        if (event.getClickedInventory() == null) return;
        if (event.getClickedInventory().equals(player.getInventory())) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int slot = event.getRawSlot();

        if (menuId.equals(CosmeticsMenuManager.MAIN_MENU_ID)) {
            this.handleMainMenuClick(player, slot, clicked, event.isRightClick());
        } else if (menuId.startsWith(CosmeticsMenuManager.CATEGORY_MENU_ID)) {
            this.handleCategoryMenuClick(player, slot, clicked);
        } else if (menuId.equals(CosmeticsMenuManager.CAT_VARIANTS_MENU_ID)) {
            this.handleCatVariantsMenuClick(player, slot);
        } else if (menuId.equals(CosmeticsMenuManager.PARROT_VARIANTS_MENU_ID)) {
            this.handleParrotVariantsMenuClick(player, slot);
        } else if (menuId.equals(CosmeticsMenuManager.FROG_VARIANTS_MENU_ID)) {
            this.handleFrogVariantsMenuClick(player, slot);
        } else if (menuId.equals(CosmeticsMenuManager.BALLOON_VARIANTS_MENU_ID)) {
            this.handleBalloonVariantsMenuClick(player, slot);
        } else if (menuId.equals(CosmeticsMenuManager.WEAPON_EFFECTS_MENU_ID)) {
            this.handleWeaponEffectsMenuClick(player, slot);
        } else if (menuId.equals(CosmeticsMenuManager.BOW_EFFECTS_MENU_ID)) {
            this.handleEffectSelectionMenuClick(player, slot, "bow_");
        } else if (menuId.equals(CosmeticsMenuManager.MACE_EFFECTS_MENU_ID)) {
            this.handleEffectSelectionMenuClick(player, slot, "mace_hit_");
        } else if (menuId.equals(CosmeticsMenuManager.TRIDENT_EFFECTS_MENU_ID)) {
            this.handleTridentEffectsMenuClick(player, slot);
        } else if (menuId.equals(CosmeticsMenuManager.TRIDENT_THROW_EFFECTS_MENU_ID)) {
            this.handleEffectSelectionMenuClick(player, slot, "trident_throw_");
        } else if (menuId.equals(CosmeticsMenuManager.TRIDENT_RIPTIDE_EFFECTS_MENU_ID)) {
            this.handleEffectSelectionMenuClick(player, slot, "trident_riptide_");
        } else if (menuId.equals(CosmeticsMenuManager.SETTINGS_MENU_ID)) {
            this.handleSettingsMenuClick(player, slot, clicked);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (this.menuManager.getOpenInventory(player.getUniqueId()) == event.getView().getTopInventory()) {
            event.setCancelled(true);
            event.setResult(org.bukkit.event.Event.Result.DENY);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            if (!this.menuManager.hasOpenMenu(player.getUniqueId())) return;
            if (event.getInventory() != this.menuManager.getOpenInventory(player.getUniqueId())) return;
            this.menuManager.closeMenu(player.getUniqueId());
        }
    }

    /**
     * ÃƒÂÃ…Â¾ÃƒÂÃ‚Â±Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â°ÃƒÂÃ‚Â±ÃƒÂÃ‚Â¾Ãƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚ÂºÃƒÂÃ‚Â° ÃƒÂÃ‚ÂºÃƒÂÃ‚Â»ÃƒÂÃ‚Â¸ÃƒÂÃ‚ÂºÃƒÂÃ‚Â° ÃƒÂÃ‚Â² ÃƒÂÃ‚Â³ÃƒÂÃ‚Â»ÃƒÂÃ‚Â°ÃƒÂÃ‚Â²ÃƒÂÃ‚Â½ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â¼ ÃƒÂÃ‚Â¼ÃƒÂÃ‚ÂµÃƒÂÃ‚Â½Ãƒâ€˜Ã…Â½
     */
    private void handleMainMenuClick(Player player, int slot, ItemStack clicked, boolean rightClick) {
        if (slot == 49) {
            player.closeInventory();
            return;
        }

        if (slot == 48) {
            this.menuManager.openSettingsMenu(player);
            return;
        }

        if (slot == 50) {
            this.module.getUserCosmeticsManager().unequipAll(player);
            this.messages.send(player, "unequip.all-cleared");
            this.menuManager.openMainMenu(player);
            return;
        }

        if (slot == 16) {
            if (rightClick) {
                UUID playerId = player.getUniqueId();
                boolean hadAny = ArrowEffectCosmetic.getActiveBowTrailEffect(playerId) != null
                        || ArrowEffectCosmetic.getActiveBowHitEffect(playerId) != null;
                ArrowEffectCosmetic.setActiveBowTrailEffect(playerId, null);
                ArrowEffectCosmetic.setActiveBowHitEffect(playerId, null);
                if (hadAny) {
                    this.messages.send(player, "unequip.success", "category", this.weaponCategoryLabel("bow_"));
                } else {
                    this.messages.send(player, "unequip.not-equipped");
                }
                this.module.getUserCosmeticsManager().savePlayerState(player);
                this.menuManager.openMainMenu(player);
            } else {
                this.menuManager.openBowEffectsMenu(player);
            }
            return;
        }

        if (slot == 29) {
            if (rightClick) {
                boolean success = this.module.getUserCosmeticsManager().unequip(player, CosmeticCategory.PARTICLE_EFFECT);
                if (success) {
                    this.messages.send(player, "unequip.success", "category", this.translateCategory(CosmeticCategory.PARTICLE_EFFECT));
                } else {
                    this.messages.send(player, "unequip.not-equipped");
                }
                this.menuManager.openMainMenu(player);
            } else {
                this.menuManager.openCategoryMenu(player, CosmeticCategory.PARTICLE_EFFECT, 0);
            }
            return;
        }

        if (slot == 31) {
            if (rightClick) {
                boolean success = this.module.getUserCosmeticsManager().unequip(player, CosmeticCategory.BALLOON);
                if (success) {
                    this.messages.send(player, "unequip.success", "category", this.translateCategory(CosmeticCategory.BALLOON));
                } else {
                    this.messages.send(player, "unequip.not-equipped");
                }
                this.menuManager.openMainMenu(player);
            } else {
                this.menuManager.openCategoryMenu(player, CosmeticCategory.BALLOON, 0);
            }
            return;
        }

        if (slot == 12) {
            if (rightClick) {
                boolean success = this.module.getUserCosmeticsManager().unequip(player, CosmeticCategory.DEATH_EFFECT);
                if (success) {
                    this.messages.send(player, "unequip.success", "category", this.translateCategory(CosmeticCategory.DEATH_EFFECT));
                } else {
                    this.messages.send(player, "unequip.not-equipped");
                }
                this.menuManager.openMainMenu(player);
            } else {
                this.menuManager.openCategoryMenu(player, CosmeticCategory.DEATH_EFFECT, 0);
            }
            return;
        }

        if (slot == 14) {
            if (rightClick) {
                ArrowEffectCosmetic.setActiveMaceEffect(player.getUniqueId(), null);
                this.messages.send(player, "unequip.success", "category", this.weaponCategoryLabel("mace_hit_"));
                this.menuManager.openMainMenu(player);
            } else {
                this.menuManager.openMaceEffectsMenu(player);
            }
            return;
        }

        if (slot == 33) {
            if (rightClick) {
                ArrowEffectCosmetic.setActiveTridentThrowEffect(player.getUniqueId(), null);
                ArrowEffectCosmetic.setActiveTridentRiptideEffect(player.getUniqueId(), null);
                this.messages.send(player, "unequip.success", "category", this.weaponCategoryLabel("trident_throw_"));
                this.menuManager.openMainMenu(player);
            } else {
                this.menuManager.openTridentEffectsMenu(player);
            }
            return;
        }

        int[] categorySlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        int slotIndex = 0;
        for (CosmeticCategory cat : CosmeticCategory.values()) {
            if (cat == CosmeticCategory.ARROW_EFFECT || cat == CosmeticCategory.PARTICLE_EFFECT || cat == CosmeticCategory.BALLOON || cat == CosmeticCategory.DEATH_EFFECT) {
                continue;
            }
            if (this.module.getCosmeticsManager().getCosmeticsCount(cat) == 0) {
                continue;
            }
            if (slotIndex >= categorySlots.length) {
                break;
            }

            if (slot == categorySlots[slotIndex]) {
                if (rightClick) {
                    boolean success = this.module.getUserCosmeticsManager().unequip(player, cat);
                    if (success) {
                        this.messages.send(player, "unequip.success", "category", this.translateCategory(cat));
                    } else {
                        this.messages.send(player, "unequip.not-equipped");
                    }
                    this.menuManager.openMainMenu(player);
                } else {
                    this.menuManager.openCategoryMenu(player, cat, 0);
                }
                return;
            }
            slotIndex++;
        }
    }
    private void handleWeaponEffectsMenuClick(Player player, int slot) {
        if (slot == 49) {
            this.menuManager.openMainMenu(player);
            return;
        }
        if (slot == 29) {
            this.menuManager.openBowEffectsMenu(player);
            return;
        }
        if (slot == 31) {
            this.menuManager.openMaceEffectsMenu(player);
            return;
        }
        if (slot == 33) {
            this.menuManager.openTridentEffectsMenu(player);
        }
    }

    private void handleTridentEffectsMenuClick(Player player, int slot) {
        if (slot == 49) {
            this.menuManager.openMainMenu(player);
            return;
        }
        if (slot == 20) {
            this.menuManager.openTridentThrowEffectsMenu(player);
            return;
        }
        if (slot == 24) {
            this.menuManager.openTridentRiptideEffectsMenu(player);
        }
    }

    private void handleEffectSelectionMenuClick(Player player, int slot, String prefix) {
        if (slot == 49) {
            this.menuManager.openMainMenu(player);
            return;
        }

        int[] cosmeticSlots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };

        if (slot == cosmeticSlots[0]) {
            this.clearWeaponEffectsForMenu(player, prefix);
            this.module.getUserCosmeticsManager().savePlayerState(player);
            this.messages.send(player, "unequip.success", "category", this.weaponCategoryLabel(prefix));
            this.reopenEffectMenu(player, prefix);
            return;
        }

        List<Cosmetic> effects = this.getArrowEffectsByPrefix(player, prefix);
        for (int i = 0; i < cosmeticSlots.length; i++) {
            if (slot != cosmeticSlots[i]) {
                continue;
            }
            if (i == 0) {
                return;
            }
            int effectIndex = i - 1;
            if (effectIndex < 0 || effectIndex >= effects.size()) {
                return;
            }
            Cosmetic cosmetic = effects.get(effectIndex);
            if (!(cosmetic instanceof ArrowEffectCosmetic arrowEffect)) {
                return;
            }

            if (!cosmetic.hasPermission(player)) {
                if (cosmetic.isPurchasable()) {
                    this.messages.send(player, "purchase.not-enough-money",
                        "cost", String.valueOf(cosmetic.getCost()));
                } else {
                    this.messages.send(player, "equip.no-permission");
                }
                this.reopenEffectMenu(player, prefix);
                return;
            }

            this.setWeaponEffect(player, prefix, arrowEffect);
            this.messages.send(player, "equip.success",
                "cosmetic", this.menuManager.getCosmeticDisplayName(cosmetic),
                "category", this.weaponCategoryLabel(prefix));

            this.module.getUserCosmeticsManager().savePlayerState(player);
            this.reopenEffectMenu(player, prefix);
            return;
        }
    }

    private List<Cosmetic> getArrowEffectsByPrefix(Player player, String prefix) {
        List<Cosmetic> effects = new ArrayList<>();
        Collection<Cosmetic> all = this.module.getCosmeticsManager().getCosmeticsByCategory(CosmeticCategory.ARROW_EFFECT);
        for (Cosmetic cosmetic : all) {
            if (!cosmetic.isVisibleTo(player)) {
                continue;
            }
            if (cosmetic.getId().startsWith(prefix)) {
                effects.add(cosmetic);
            }
        }
        return effects;
    }

    private boolean isWeaponEffectEquipped(Player player, String prefix, ArrowEffectCosmetic effect) {
        ArrowEffectCosmetic equipped = switch (prefix) {
            case "bow_" -> ArrowEffectCosmetic.getActiveBowTrailEffect(player.getUniqueId());
            case "mace_hit_" -> ArrowEffectCosmetic.getActiveMaceEffect(player.getUniqueId());
            case "trident_throw_" -> ArrowEffectCosmetic.getActiveTridentThrowEffect(player.getUniqueId());
            case "trident_riptide_" -> ArrowEffectCosmetic.getActiveTridentRiptideEffect(player.getUniqueId());
            default -> null;
        };
        return equipped != null && equipped.getId().equals(effect.getId());
    }

    private void setWeaponEffect(Player player, String prefix, ArrowEffectCosmetic effect) {
        UUID playerId = player.getUniqueId();
        switch (prefix) {
            case "bow_" -> {
                if (effect == null) {
                    ArrowEffectCosmetic.setActiveBowTrailEffect(playerId, null);
                    ArrowEffectCosmetic.setActiveBowHitEffect(playerId, null);
                } else {
                    ArrowEffectCosmetic.setActiveBowTrailEffect(playerId, effect);
                    ArrowEffectCosmetic.setActiveBowHitEffect(playerId, effect);
                }
            }
            case "mace_hit_" -> ArrowEffectCosmetic.setActiveMaceEffect(playerId, effect);
            case "trident_throw_" -> {
                if (effect == null) {
                    ArrowEffectCosmetic.setActiveTridentThrowTrailEffect(playerId, null);
                    ArrowEffectCosmetic.setActiveTridentThrowHitEffect(playerId, null);
                } else {
                    ArrowEffectCosmetic.setActiveTridentThrowTrailEffect(playerId, effect);
                    ArrowEffectCosmetic.setActiveTridentThrowHitEffect(playerId, effect);
                }
            }
            case "trident_riptide_" -> ArrowEffectCosmetic.setActiveTridentRiptideEffect(playerId, effect);
            default -> {
            }
        }
    }

    private void setWeaponTrailEffect(Player player, String prefix, ArrowEffectCosmetic effect) {
        UUID playerId = player.getUniqueId();
        if ("bow_".equals(prefix)) {
            ArrowEffectCosmetic.setActiveBowHitEffect(playerId, null);
            ArrowEffectCosmetic.setActiveBowTrailEffect(playerId, effect);
            return;
        }
        if ("trident_throw_".equals(prefix)) {
            ArrowEffectCosmetic.setActiveTridentThrowHitEffect(playerId, null);
            ArrowEffectCosmetic.setActiveTridentThrowTrailEffect(playerId, effect);
        }
    }

    private void setWeaponHitEffect(Player player, String prefix, ArrowEffectCosmetic effect) {
        UUID playerId = player.getUniqueId();
        if ("bow_".equals(prefix)) {
            ArrowEffectCosmetic.setActiveBowTrailEffect(playerId, null);
            ArrowEffectCosmetic.setActiveBowHitEffect(playerId, effect);
            return;
        }
        if ("trident_throw_".equals(prefix)) {
            ArrowEffectCosmetic.setActiveTridentThrowTrailEffect(playerId, null);
            ArrowEffectCosmetic.setActiveTridentThrowHitEffect(playerId, effect);
        }
    }

    private void clearWeaponEffectsForMenu(Player player, String prefix) {
        UUID playerId = player.getUniqueId();
        switch (prefix) {
            case "bow_" -> {
                ArrowEffectCosmetic.setActiveBowTrailEffect(playerId, null);
                ArrowEffectCosmetic.setActiveBowHitEffect(playerId, null);
            }
            case "mace_hit_" -> ArrowEffectCosmetic.setActiveMaceEffect(playerId, null);
            case "trident_throw_" -> {
                ArrowEffectCosmetic.setActiveTridentThrowTrailEffect(playerId, null);
                ArrowEffectCosmetic.setActiveTridentThrowHitEffect(playerId, null);
            }
            case "trident_riptide_" -> ArrowEffectCosmetic.setActiveTridentRiptideEffect(playerId, null);
            default -> {
            }
        }
    }

    private void reopenEffectMenu(Player player, String prefix) {
        switch (prefix) {
            case "bow_" -> this.menuManager.openBowEffectsMenu(player);
            case "mace_hit_" -> this.menuManager.openMaceEffectsMenu(player);
            case "trident_throw_" -> this.menuManager.openTridentThrowEffectsMenu(player);
            case "trident_riptide_" -> this.menuManager.openTridentRiptideEffectsMenu(player);
            default -> this.menuManager.openWeaponEffectsMenu(player);
        }
    }

    /**
     * ÃƒÂÃ…Â¾ÃƒÂÃ‚Â±Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â°ÃƒÂÃ‚Â±ÃƒÂÃ‚Â¾Ãƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚ÂºÃƒÂÃ‚Â° ÃƒÂÃ‚ÂºÃƒÂÃ‚Â»ÃƒÂÃ‚Â¸ÃƒÂÃ‚ÂºÃƒÂÃ‚Â° ÃƒÂÃ‚Â² ÃƒÂÃ‚Â¼ÃƒÂÃ‚ÂµÃƒÂÃ‚Â½Ãƒâ€˜Ã…Â½ ÃƒÂÃ‚ÂºÃƒÂÃ‚Â°Ãƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚ÂµÃƒÂÃ‚Â³ÃƒÂÃ‚Â¾Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â¸ÃƒÂÃ‚Â¸
     */
    private void handleCategoryMenuClick(Player player, int slot, ItemStack clicked) {
        CosmeticCategory category = this.menuManager.getSelectedCategory(player.getUniqueId());
        if (category == null) return;
        
        int page = this.menuManager.getCurrentPage(player.getUniqueId());
        
        // ÃƒÂÃ‚ÂÃƒÂÃ‚Â°ÃƒÂÃ‚Â·ÃƒÂÃ‚Â°ÃƒÂÃ‚Â´
        if (slot == 49) {
            this.menuManager.openMainMenu(player);
            return;
        }
        
        // ÃƒÂÃ…Â¸Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚ÂµÃƒÂÃ‚Â´Ãƒâ€˜Ã¢â‚¬Â¹ÃƒÂÃ‚Â´Ãƒâ€˜Ã†â€™Ãƒâ€˜Ã¢â‚¬Â°ÃƒÂÃ‚Â°Ãƒâ€˜Ã‚Â Ãƒâ€˜Ã‚ÂÃƒâ€˜Ã¢â‚¬Å¡Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â°ÃƒÂÃ‚Â½ÃƒÂÃ‚Â¸Ãƒâ€˜Ã¢â‚¬Â ÃƒÂÃ‚Â°
        if (slot == 48 && clicked.getType() == Material.ARROW) {
            this.menuManager.openCategoryMenu(player, category, page - 1);
            return;
        }
        
        // ÃƒÂÃ‚Â¡ÃƒÂÃ‚Â»ÃƒÂÃ‚ÂµÃƒÂÃ‚Â´Ãƒâ€˜Ã†â€™Ãƒâ€˜Ã…Â½Ãƒâ€˜Ã¢â‚¬Â°ÃƒÂÃ‚Â°Ãƒâ€˜Ã‚Â Ãƒâ€˜Ã‚ÂÃƒâ€˜Ã¢â‚¬Å¡Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â°ÃƒÂÃ‚Â½ÃƒÂÃ‚Â¸Ãƒâ€˜Ã¢â‚¬Â ÃƒÂÃ‚Â°
        if (slot == 50 && clicked.getType() == Material.ARROW) {
            this.menuManager.openCategoryMenu(player, category, page + 1);
            return;
        }
        
        // Legacy redstone unequip for non-empty categories
        if (slot == 53 && clicked.getType() == Material.REDSTONE) {
            this.module.getUserCosmeticsManager().unequip(player, category);
            this.messages.send(player, "unequip.success", "category", this.translateCategory(category));
            this.menuManager.openCategoryMenu(player, category, page);
            return;
        }
        
        // ÃƒÂÃ…Â¡ÃƒÂÃ‚Â»ÃƒÂÃ‚Â¸ÃƒÂÃ‚Âº ÃƒÂÃ‚Â¿ÃƒÂÃ‚Â¾ ÃƒÂÃ‚ÂºÃƒÂÃ‚Â¾Ãƒâ€˜Ã‚ÂÃƒÂÃ‚Â¼ÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚Â¸ÃƒÂÃ‚ÂºÃƒÂÃ‚Âµ
        int[] cosmeticSlots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };
        
        boolean includeEmptyOption = true;

        for (int i = 0; i < cosmeticSlots.length; i++) {
            if (slot == cosmeticSlots[i]) {
                Collection<Cosmetic> cosmetics = this.module.getCosmeticsManager().getCosmeticsByCategory(category);
                List<Cosmetic> cosmeticList = new ArrayList<>(cosmetics);
                cosmeticList.removeIf(cosmetic -> !cosmetic.isVisibleTo(player));
                if (category == CosmeticCategory.PET) {
                    cosmeticList.removeIf(cosmetic ->
                        (cosmetic.getId().startsWith("cat_") && !cosmetic.getId().equals("cat_pet")) ||
                        (cosmetic.getId().startsWith("parrot_") && !cosmetic.getId().equals("parrot_pet")) ||
                        (cosmetic.getId().startsWith("axolotl_") && !cosmetic.getId().equals("axolotl_pet")) ||
                        (cosmetic.getId().startsWith("frog_") && !cosmetic.getId().equals("frog_pet"))
                    );
                }
                
                int displayIndex = page * 28 + i;

                if (includeEmptyOption && displayIndex == 0) {
                    this.module.getUserCosmeticsManager().unequip(player, category);
                    this.messages.send(player, "unequip.success", "category", this.translateCategory(category));
                    this.menuManager.openCategoryMenu(player, category, page);
                    return;
                }

                int index = displayIndex - (includeEmptyOption ? 1 : 0);
                
                if (index >= 0 && index < cosmeticList.size()) {
                    Cosmetic cosmetic = cosmeticList.get(index);
                    if (category == CosmeticCategory.PET && "cat_pet".equals(cosmetic.getId())) {
                        this.menuManager.openCatVariantsMenu(player, page);
                        return;
                    }
                    if (category == CosmeticCategory.PET && "parrot_pet".equals(cosmetic.getId())) {
                        this.menuManager.openParrotVariantsMenu(player, page);
                        return;
                    }
                    this.handleCosmeticClick(player, cosmetic, category, page);
                }
                return;
            }
        }
    }

    private void handleCatVariantsMenuClick(Player player, int slot) {
        int sourcePage = this.menuManager.getCurrentPage(player.getUniqueId());

        if (slot == 48) {
            this.menuManager.openCategoryMenu(player, CosmeticCategory.PET, sourcePage);
            return;
        }
        if (slot == 50) {
            this.module.getUserCosmeticsManager().unequip(player, CosmeticCategory.PET);
            this.messages.send(player, "unequip.success", "category", this.translateCategory(CosmeticCategory.PET));
            this.menuManager.openCatVariantsMenu(player, sourcePage);
            return;
        }

        int[] cosmeticSlots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };

        List<Cosmetic> catVariants = new ArrayList<>();
        Collection<Cosmetic> pets = this.module.getCosmeticsManager().getCosmeticsByCategory(CosmeticCategory.PET);
        for (Cosmetic cosmetic : pets) {
            if (!cosmetic.isVisibleTo(player)) {
                continue;
            }
            if (cosmetic.getId().startsWith("cat_") && !cosmetic.getId().equals("cat_pet")) {
                catVariants.add(cosmetic);
            }
        }

        for (int i = 0; i < cosmeticSlots.length; i++) {
            if (slot != cosmeticSlots[i]) {
                continue;
            }
            if (i >= 0 && i < catVariants.size()) {
                Cosmetic cosmetic = catVariants.get(i);
                Cosmetic equipped = this.module.getUserCosmeticsManager().getEquipped(player.getUniqueId(), CosmeticCategory.PET);
                if (equipped != null && equipped.getId().equals(cosmetic.getId())) {
                    this.module.getUserCosmeticsManager().unequip(player, CosmeticCategory.PET);
                    this.messages.send(player, "unequip.success", "category", this.translateCategory(CosmeticCategory.PET));
                    this.menuManager.openCatVariantsMenu(player, sourcePage);
                    return;
                }
                if (cosmetic.hasPermission(player)) {
                    boolean success = this.module.getUserCosmeticsManager().equip(player, cosmetic);
                    if (success) {
                        this.messages.send(player, "equip.success",
                            "cosmetic", this.menuManager.getCosmeticDisplayName(cosmetic),
                            "category", this.translateCategory(CosmeticCategory.PET));
                    }
                    this.menuManager.openCatVariantsMenu(player, sourcePage);
                    return;
                }
                if (cosmetic.isPurchasable()) {
                    this.messages.send(player, "purchase.not-enough-money",
                        "cost", String.valueOf(cosmetic.getCost()));
                    this.menuManager.openCatVariantsMenu(player, sourcePage);
                    return;
                }
                this.messages.send(player, "equip.no-permission");
                this.menuManager.openCatVariantsMenu(player, sourcePage);
            }
            return;
        }
    }

    private void handleParrotVariantsMenuClick(Player player, int slot) {
        int sourcePage = this.menuManager.getCurrentPage(player.getUniqueId());

        if (slot == 48) {
            this.menuManager.openCategoryMenu(player, CosmeticCategory.PET, sourcePage);
            return;
        }
        if (slot == 50) {
            this.module.getUserCosmeticsManager().unequip(player, CosmeticCategory.PET);
            this.messages.send(player, "unequip.success", "category", this.translateCategory(CosmeticCategory.PET));
            this.menuManager.openParrotVariantsMenu(player, sourcePage);
            return;
        }

        int[] cosmeticSlots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };

        List<Cosmetic> parrotVariants = new ArrayList<>();
        Collection<Cosmetic> pets = this.module.getCosmeticsManager().getCosmeticsByCategory(CosmeticCategory.PET);
        for (Cosmetic cosmetic : pets) {
            if (!cosmetic.isVisibleTo(player)) {
                continue;
            }
            if (cosmetic.getId().startsWith("parrot_") && !cosmetic.getId().equals("parrot_pet")) {
                parrotVariants.add(cosmetic);
            }
        }

        for (int i = 0; i < cosmeticSlots.length; i++) {
            if (slot != cosmeticSlots[i]) {
                continue;
            }
            if (i >= 0 && i < parrotVariants.size()) {
                Cosmetic cosmetic = parrotVariants.get(i);
                Cosmetic equipped = this.module.getUserCosmeticsManager().getEquipped(player.getUniqueId(), CosmeticCategory.PET);
                if (equipped != null && equipped.getId().equals(cosmetic.getId())) {
                    this.module.getUserCosmeticsManager().unequip(player, CosmeticCategory.PET);
                    this.messages.send(player, "unequip.success", "category", this.translateCategory(CosmeticCategory.PET));
                    this.menuManager.openParrotVariantsMenu(player, sourcePage);
                    return;
                }
                if (cosmetic.hasPermission(player)) {
                    boolean success = this.module.getUserCosmeticsManager().equip(player, cosmetic);
                    if (success) {
                        this.messages.send(player, "equip.success",
                            "cosmetic", this.menuManager.getCosmeticDisplayName(cosmetic),
                            "category", this.translateCategory(CosmeticCategory.PET));
                    }
                    this.menuManager.openParrotVariantsMenu(player, sourcePage);
                    return;
                }
                if (cosmetic.isPurchasable()) {
                    this.messages.send(player, "purchase.not-enough-money",
                        "cost", String.valueOf(cosmetic.getCost()));
                    this.menuManager.openParrotVariantsMenu(player, sourcePage);
                    return;
                }
                this.messages.send(player, "equip.no-permission");
                this.menuManager.openParrotVariantsMenu(player, sourcePage);
            }
            return;
        }
    }

    private void handleFrogVariantsMenuClick(Player player, int slot) {
        int sourcePage = this.menuManager.getCurrentPage(player.getUniqueId());

        if (slot == 48) {
            this.menuManager.openCategoryMenu(player, CosmeticCategory.PET, sourcePage);
            return;
        }
        if (slot == 50) {
            this.module.getUserCosmeticsManager().unequip(player, CosmeticCategory.PET);
            this.messages.send(player, "unequip.success", "category", this.translateCategory(CosmeticCategory.PET));
            this.menuManager.openFrogVariantsMenu(player, sourcePage);
            return;
        }

        int[] cosmeticSlots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };

        List<Cosmetic> variants = new ArrayList<>();
        Collection<Cosmetic> pets = this.module.getCosmeticsManager().getCosmeticsByCategory(CosmeticCategory.PET);
        for (Cosmetic cosmetic : pets) {
            if (!cosmetic.isVisibleTo(player)) {
                continue;
            }
            if (cosmetic.getId().startsWith("frog_") && !cosmetic.getId().equals("frog_pet")) {
                variants.add(cosmetic);
            }
        }

        for (int i = 0; i < cosmeticSlots.length; i++) {
            if (slot != cosmeticSlots[i]) {
                continue;
            }
            if (i >= 0 && i < variants.size()) {
                Cosmetic cosmetic = variants.get(i);
                Cosmetic equipped = this.module.getUserCosmeticsManager().getEquipped(player.getUniqueId(), CosmeticCategory.PET);
                if (equipped != null && equipped.getId().equals(cosmetic.getId())) {
                    this.module.getUserCosmeticsManager().unequip(player, CosmeticCategory.PET);
                    this.messages.send(player, "unequip.success", "category", this.translateCategory(CosmeticCategory.PET));
                    this.menuManager.openFrogVariantsMenu(player, sourcePage);
                    return;
                }
                if (cosmetic.hasPermission(player)) {
                    boolean success = this.module.getUserCosmeticsManager().equip(player, cosmetic);
                    if (success) {
                        this.messages.send(player, "equip.success",
                            "cosmetic", this.menuManager.getCosmeticDisplayName(cosmetic),
                            "category", this.translateCategory(CosmeticCategory.PET));
                    }
                    this.menuManager.openFrogVariantsMenu(player, sourcePage);
                    return;
                }
                if (cosmetic.isPurchasable()) {
                    this.messages.send(player, "purchase.not-enough-money",
                        "cost", String.valueOf(cosmetic.getCost()));
                    this.menuManager.openFrogVariantsMenu(player, sourcePage);
                    return;
                }
                this.messages.send(player, "equip.no-permission");
                this.menuManager.openFrogVariantsMenu(player, sourcePage);
            }
            return;
        }
    }

    private void handleBalloonVariantsMenuClick(Player player, int slot) {
        int sourcePage = this.menuManager.getCurrentPage(player.getUniqueId());
        String groupId = this.menuManager.getSelectedBalloonGroup(player.getUniqueId());
        if (groupId == null || groupId.isBlank()) {
            this.menuManager.openCategoryMenu(player, CosmeticCategory.BALLOON, sourcePage);
            return;
        }

        if (slot == 48) {
            this.menuManager.openCategoryMenu(player, CosmeticCategory.BALLOON, sourcePage);
            return;
        }

        if (slot == 50) {
            this.module.getUserCosmeticsManager().unequip(player, CosmeticCategory.BALLOON);
            this.messages.send(player, "unequip.success", "category", this.translateCategory(CosmeticCategory.BALLOON));
            this.menuManager.openBalloonVariantsMenu(player, sourcePage, groupId);
            return;
        }

        int[] cosmeticSlots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };

        List<Cosmetic> variants = this.menuManager.getBalloonVariants(player, groupId);
        for (int i = 0; i < cosmeticSlots.length; i++) {
            if (slot != cosmeticSlots[i]) {
                continue;
            }
            if (i >= 0 && i < variants.size()) {
                Cosmetic cosmetic = variants.get(i);
                Cosmetic equipped = this.module.getUserCosmeticsManager().getEquipped(player.getUniqueId(), CosmeticCategory.BALLOON);
                if (equipped != null && equipped.getId().equals(cosmetic.getId())) {
                    this.module.getUserCosmeticsManager().unequip(player, CosmeticCategory.BALLOON);
                    this.messages.send(player, "unequip.success", "category", this.translateCategory(CosmeticCategory.BALLOON));
                    this.menuManager.openBalloonVariantsMenu(player, sourcePage, groupId);
                    return;
                }
                if (cosmetic.hasPermission(player)) {
                    boolean success = this.module.getUserCosmeticsManager().equip(player, cosmetic);
                    if (success) {
                        this.messages.send(player, "equip.success",
                            "cosmetic", this.menuManager.getCosmeticDisplayName(cosmetic),
                            "category", this.translateCategory(CosmeticCategory.BALLOON));
                    }
                    this.menuManager.openBalloonVariantsMenu(player, sourcePage, groupId);
                    return;
                }
                if (cosmetic.isPurchasable()) {
                    this.messages.send(player, "purchase.not-enough-money",
                        "cost", String.valueOf(cosmetic.getCost()));
                    this.menuManager.openBalloonVariantsMenu(player, sourcePage, groupId);
                    return;
                }
                this.messages.send(player, "equip.no-permission");
                this.menuManager.openBalloonVariantsMenu(player, sourcePage, groupId);
            }
            return;
        }
    }

    /**
     * ÃƒÂÃ…Â¾ÃƒÂÃ‚Â±Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â°ÃƒÂÃ‚Â±ÃƒÂÃ‚Â¾Ãƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚ÂºÃƒÂÃ‚Â° ÃƒÂÃ‚ÂºÃƒÂÃ‚Â»ÃƒÂÃ‚Â¸ÃƒÂÃ‚ÂºÃƒÂÃ‚Â° ÃƒÂÃ‚Â¿ÃƒÂÃ‚Â¾ ÃƒÂÃ‚ÂºÃƒÂÃ‚Â¾Ãƒâ€˜Ã‚ÂÃƒÂÃ‚Â¼ÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚Â¸ÃƒÂÃ‚ÂºÃƒÂÃ‚Âµ
     */
    private void handleCosmeticClick(Player player, Cosmetic cosmetic, CosmeticCategory category, int page) {
        Cosmetic equipped = this.module.getUserCosmeticsManager().getEquipped(player.getUniqueId(), category);
        
        // ÃƒÂÃ¢â‚¬Â¢Ãƒâ€˜Ã‚ÂÃƒÂÃ‚Â»ÃƒÂÃ‚Â¸ Ãƒâ€˜Ã†â€™ÃƒÂÃ‚Â¶ÃƒÂÃ‚Âµ Ãƒâ€˜Ã‚ÂÃƒÂÃ‚ÂºÃƒÂÃ‚Â¸ÃƒÂÃ‚Â¿ÃƒÂÃ‚Â¸Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â²ÃƒÂÃ‚Â°ÃƒÂÃ‚Â½ÃƒÂÃ‚Â¾ - Ãƒâ€˜Ã‚ÂÃƒÂÃ‚Â½ÃƒÂÃ‚Â¸ÃƒÂÃ‚Â¼ÃƒÂÃ‚Â°ÃƒÂÃ‚ÂµÃƒÂÃ‚Â¼
        if (equipped != null && equipped.getId().equals(cosmetic.getId())) {
            this.module.getUserCosmeticsManager().unequip(player, category);
            this.messages.send(player, "unequip.success", "category", this.translateCategory(category));
            this.menuManager.openCategoryMenu(player, category, page);
            return;
        }
        
        // ÃƒÂÃ¢â‚¬Â¢Ãƒâ€˜Ã‚ÂÃƒÂÃ‚Â»ÃƒÂÃ‚Â¸ Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â°ÃƒÂÃ‚Â·ÃƒÂÃ‚Â±ÃƒÂÃ‚Â»ÃƒÂÃ‚Â¾ÃƒÂÃ‚ÂºÃƒÂÃ‚Â¸Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â²ÃƒÂÃ‚Â°ÃƒÂÃ‚Â½ÃƒÂÃ‚Â¾ - Ãƒâ€˜Ã‚ÂÃƒÂÃ‚ÂºÃƒÂÃ‚Â¸ÃƒÂÃ‚Â¿ÃƒÂÃ‚Â¸Ãƒâ€˜Ã¢â€šÂ¬Ãƒâ€˜Ã†â€™ÃƒÂÃ‚ÂµÃƒÂÃ‚Â¼
        if (cosmetic.hasPermission(player)) {
            boolean success = this.module.getUserCosmeticsManager().equip(player, cosmetic);
            if (success) {
                this.messages.send(player, "equip.success", 
                    "cosmetic", this.menuManager.getCosmeticDisplayName(cosmetic),
                    "category", this.translateCategory(category));
            }
            this.menuManager.openCategoryMenu(player, category, page);
            return;
        }
        
        // ÃƒÂÃ¢â‚¬Â¢Ãƒâ€˜Ã‚ÂÃƒÂÃ‚Â»ÃƒÂÃ‚Â¸ ÃƒÂÃ‚Â¼ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â¶ÃƒÂÃ‚Â½ÃƒÂÃ‚Â¾ ÃƒÂÃ‚ÂºÃƒâ€˜Ã†â€™ÃƒÂÃ‚Â¿ÃƒÂÃ‚Â¸Ãƒâ€˜Ã¢â‚¬Å¡Ãƒâ€˜Ã…â€™
        if (cosmetic.isPurchasable()) {
            // TODO: ÃƒÂÃ‚Â ÃƒÂÃ‚ÂµÃƒÂÃ‚Â°ÃƒÂÃ‚Â»ÃƒÂÃ‚Â¸ÃƒÂÃ‚Â·ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â²ÃƒÂÃ‚Â°Ãƒâ€˜Ã¢â‚¬Å¡Ãƒâ€˜Ã…â€™ ÃƒÂÃ‚Â¿ÃƒÂÃ‚Â¾ÃƒÂÃ‚ÂºÃƒâ€˜Ã†â€™ÃƒÂÃ‚Â¿ÃƒÂÃ‚ÂºÃƒâ€˜Ã†â€™
            this.messages.send(player, "purchase.not-enough-money", 
                "cost", String.valueOf(cosmetic.getCost()));
            return;
        }
        
        // ÃƒÂÃ¢â‚¬â€ÃƒÂÃ‚Â°ÃƒÂÃ‚Â±ÃƒÂÃ‚Â»ÃƒÂÃ‚Â¾ÃƒÂÃ‚ÂºÃƒÂÃ‚Â¸Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â²ÃƒÂÃ‚Â°ÃƒÂÃ‚Â½ÃƒÂÃ‚Â¾
        this.messages.send(player, "equip.no-permission");
    }

    /**
     * ÃƒÂÃ…Â¾ÃƒÂÃ‚Â±Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â°ÃƒÂÃ‚Â±ÃƒÂÃ‚Â¾Ãƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚ÂºÃƒÂÃ‚Â° ÃƒÂÃ‚ÂºÃƒÂÃ‚Â»ÃƒÂÃ‚Â¸ÃƒÂÃ‚ÂºÃƒÂÃ‚Â° ÃƒÂÃ‚Â² ÃƒÂÃ‚Â¼ÃƒÂÃ‚ÂµÃƒÂÃ‚Â½Ãƒâ€˜Ã…Â½ ÃƒÂÃ‚Â½ÃƒÂÃ‚Â°Ãƒâ€˜Ã‚ÂÃƒâ€˜Ã¢â‚¬Å¡Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â¾ÃƒÂÃ‚ÂµÃƒÂÃ‚Âº
     */
    private void handleSettingsMenuClick(Player player, int slot, ItemStack clicked) {
        UserCosmeticSettings settings = this.module.getUserCosmeticsManager()
            .getOrCreateSettings(player.getUniqueId());
        
        // ÃƒÂÃ‚ÂÃƒÂÃ‚Â°ÃƒÂÃ‚Â·ÃƒÂÃ‚Â°ÃƒÂÃ‚Â´
        if (slot == 49) {
            this.menuManager.openMainMenu(player);
            return;
        }
        
        // ÃƒÂÃ…Â¸ÃƒÂÃ‚Â¾ÃƒÂÃ‚ÂºÃƒÂÃ‚Â°ÃƒÂÃ‚Â·Ãƒâ€˜Ã¢â‚¬Â¹ÃƒÂÃ‚Â²ÃƒÂÃ‚Â°Ãƒâ€˜Ã¢â‚¬Å¡Ãƒâ€˜Ã…â€™ Ãƒâ€˜Ã‚ÂÃƒâ€˜Ã¢â‚¬Å¾Ãƒâ€˜Ã¢â‚¬Å¾ÃƒÂÃ‚ÂµÃƒÂÃ‚ÂºÃƒâ€˜Ã¢â‚¬Å¡Ãƒâ€˜Ã¢â‚¬Â¹ ÃƒÂÃ‚Â´Ãƒâ€˜Ã¢â€šÂ¬Ãƒâ€˜Ã†â€™ÃƒÂÃ‚Â³ÃƒÂÃ‚Â¸Ãƒâ€˜Ã¢â‚¬Â¦ (Ãƒâ€˜Ã‚ÂÃƒÂÃ‚Â»ÃƒÂÃ‚Â¾Ãƒâ€˜Ã¢â‚¬Å¡ 11)
        if (slot == 11) {
            boolean newValue = !settings.isShowOthersEffects();
            settings.setShowOthersEffects(newValue);
            this.messages.send(player, newValue ? "settings.others-enabled" : "settings.others-disabled");
            this.persistAndReopenSettings(player);
            return;
        }
        
        // ÃƒÂÃ…Â¸ÃƒÂÃ‚Â¾ÃƒÂÃ‚ÂºÃƒÂÃ‚Â°ÃƒÂÃ‚Â·Ãƒâ€˜Ã¢â‚¬Â¹ÃƒÂÃ‚Â²ÃƒÂÃ‚Â°Ãƒâ€˜Ã¢â‚¬Å¡Ãƒâ€˜Ã…â€™ ÃƒÂÃ‚Â¼ÃƒÂÃ‚Â¾ÃƒÂÃ‚Â¸ Ãƒâ€˜Ã‚ÂÃƒâ€˜Ã¢â‚¬Å¾Ãƒâ€˜Ã¢â‚¬Å¾ÃƒÂÃ‚ÂµÃƒÂÃ‚ÂºÃƒâ€˜Ã¢â‚¬Å¡Ãƒâ€˜Ã¢â‚¬Â¹ ÃƒÂÃ‚Â´Ãƒâ€˜Ã¢â€šÂ¬Ãƒâ€˜Ã†â€™ÃƒÂÃ‚Â³ÃƒÂÃ‚Â¸ÃƒÂÃ‚Â¼ (Ãƒâ€˜Ã‚ÂÃƒÂÃ‚Â»ÃƒÂÃ‚Â¾Ãƒâ€˜Ã¢â‚¬Å¡ 15)
        if (slot == 15) {
            boolean newValue = !settings.isShowMyEffectsToOthers();
            settings.setShowMyEffectsToOthers(newValue);
            if (!newValue) {
                this.module.getUserCosmeticsManager().hideEffectCategoriesTemporary(player);
            } else {
                this.module.getUserCosmeticsManager().restoreEffectCategoriesTemporary(player);
            }
            this.messages.send(player, newValue ? "settings.mine-enabled" : "settings.mine-disabled");
            this.persistAndReopenSettings(player);
            return;
        }
        
        // ÃƒÂÃ¢â‚¬â„¢ÃƒÂÃ‚ÂºÃƒÂÃ‚Â»Ãƒâ€˜Ã…Â½Ãƒâ€˜Ã¢â‚¬Â¡ÃƒÂÃ‚Â¸Ãƒâ€˜Ã¢â‚¬Å¡Ãƒâ€˜Ã…â€™ ÃƒÂÃ‚Â²Ãƒâ€˜Ã‚ÂÃƒâ€˜Ã¢â‚¬Ëœ
        if (slot == 46) {
            settings.enableAllCategories();
            settings.setAllCategoriesVisibility(UserCosmeticSettings.VisibilityMode.ALL);
            this.messages.send(player, "settings.all-enabled");
            this.persistAndReopenSettings(player);
            return;
        }
        
        // ÃƒÂÃ¢â‚¬â„¢Ãƒâ€˜Ã¢â‚¬Â¹ÃƒÂÃ‚ÂºÃƒÂÃ‚Â»Ãƒâ€˜Ã…Â½Ãƒâ€˜Ã¢â‚¬Â¡ÃƒÂÃ‚Â¸Ãƒâ€˜Ã¢â‚¬Å¡Ãƒâ€˜Ã…â€™ ÃƒÂÃ‚Â²Ãƒâ€˜Ã‚ÂÃƒâ€˜Ã¢â‚¬Ëœ
        if (slot == 52) {
            settings.setAllCategoriesVisibility(UserCosmeticSettings.VisibilityMode.NONE);
            this.messages.send(player, "settings.all-disabled");
            this.persistAndReopenSettings(player);
            return;
        }
        
        // ÃƒÂÃ…Â¸ÃƒÂÃ‚ÂµÃƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚ÂµÃƒÂÃ‚ÂºÃƒÂÃ‚Â»Ãƒâ€˜Ã…Â½Ãƒâ€˜Ã¢â‚¬Â¡ÃƒÂÃ‚ÂµÃƒÂÃ‚Â½ÃƒÂÃ‚Â¸ÃƒÂÃ‚Âµ ÃƒÂÃ‚ÂºÃƒÂÃ‚Â°Ãƒâ€˜Ã¢â‚¬Å¡ÃƒÂÃ‚ÂµÃƒÂÃ‚Â³ÃƒÂÃ‚Â¾Ãƒâ€˜Ã¢â€šÂ¬ÃƒÂÃ‚Â¸ÃƒÂÃ‚Â¹ (Ãƒâ€˜Ã‚ÂÃƒÂÃ‚Â»ÃƒÂÃ‚Â¾Ãƒâ€˜Ã¢â‚¬Å¡Ãƒâ€˜Ã¢â‚¬Â¹ 29-33)
        CosmeticCategory[] settingsCategories = this.menuManager.getSettingsVisibilityCategories();
        int[] categorySlots = this.menuManager.getSettingsCategorySlots();
        
        for (int i = 0; i < categorySlots.length; i++) {
            if (slot == categorySlots[i] && i < settingsCategories.length) {
                CosmeticCategory category = settingsCategories[i];
                UserCosmeticSettings.VisibilityMode newMode = settings.toggleCategoryVisibility(category);
                String categoryName = this.menuManager.getSettingsCategoryLabel(i);
                String modeStr = switch (newMode) {
                    case ALL -> this.chatText("settings.mode.all", "all");
                    case SELF_ONLY -> this.chatText("settings.mode.self_only", "self only");
                    case OTHERS_ONLY -> this.chatText("settings.mode.others_only", "others only");
                    case NONE -> this.chatText("settings.mode.none", "none");
                };
                this.messages.send(player, "settings.category-changed",
                    "category", categoryName,
                    "mode", modeStr);
                this.persistAndReopenSettings(player);
                return;
            }
        }
    }

    private void persistAndReopenSettings(Player player) {
        this.module.getUserCosmeticsManager().savePlayerState(player);
        this.menuManager.openSettingsMenu(player);
    }

    private String translateCategory(CosmeticCategory category) {
        return this.menuManager.translateCategory(category);
    }

    private String weaponCategoryLabel(String prefix) {
        return switch (prefix) {
            case "bow_" -> this.chatText("categories.weapon_bow", "Arrow Effects");
            case "mace_hit_" -> this.chatText("categories.weapon_mace", "Mace Effects");
            case "trident_throw_", "trident_riptide_" -> this.chatText("categories.weapon_trident", "Trident Effects");
            default -> this.menuManager.translateCategory(CosmeticCategory.ARROW_EFFECT);
        };
    }

    private String text(String key, String fallback) {
        String value = this.menuManager.getGuiMessage(key);
        if (value == null || value.isBlank() || value.equals(key)) {
            return fallback;
        }
        return value;
    }

    private String chatText(String key, String fallback) {
        String value = this.messages.getRaw(key);
        if (value == null || value.isBlank() || value.equals(key)) {
            return fallback;
        }
        return value;
    }

}
