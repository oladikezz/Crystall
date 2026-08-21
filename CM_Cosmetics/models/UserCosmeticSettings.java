package net.schalker.SMPS.modules.cosmetics.models;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class UserCosmeticSettings {
    private final UUID playerId;

    private boolean showOthersEffects = true;
    private boolean showMyEffectsToOthers = true;
    private final Set<CosmeticCategory> enabledCategories;
    private final Map<CosmeticCategory, VisibilityMode> categoryVisibility;
    private boolean silentMode = false;
    private boolean reducedEffects = false;

    public enum VisibilityMode {
        ALL,
        SELF_ONLY,
        OTHERS_ONLY,
        NONE
    }

    public UserCosmeticSettings(UUID playerId) {
        this.playerId = playerId;
        this.enabledCategories = EnumSet.allOf(CosmeticCategory.class);
        this.categoryVisibility = new EnumMap<>(CosmeticCategory.class);
        for (CosmeticCategory category : CosmeticCategory.values()) {
            this.categoryVisibility.put(category, VisibilityMode.ALL);
        }
    }

    public UUID getPlayerId() {
        return this.playerId;
    }

    public boolean isShowOthersEffects() {
        return this.showOthersEffects;
    }

    public void setShowOthersEffects(boolean show) {
        this.showOthersEffects = show;
    }

    public boolean isShowMyEffectsToOthers() {
        return this.showMyEffectsToOthers;
    }

    public void setShowMyEffectsToOthers(boolean show) {
        this.showMyEffectsToOthers = show;
    }

    public boolean isCategoryEnabled(CosmeticCategory category) {
        return this.enabledCategories.contains(category);
    }

    public void enableCategory(CosmeticCategory category) {
        this.enabledCategories.add(category);
    }

    public void disableCategory(CosmeticCategory category) {
        this.enabledCategories.remove(category);
    }

    public boolean toggleCategory(CosmeticCategory category) {
        if (this.enabledCategories.contains(category)) {
            this.enabledCategories.remove(category);
            return false;
        }
        this.enabledCategories.add(category);
        return true;
    }

    public VisibilityMode getCategoryVisibility(CosmeticCategory category) {
        return this.categoryVisibility.getOrDefault(category, VisibilityMode.ALL);
    }

    public void setCategoryVisibility(CosmeticCategory category, VisibilityMode mode) {
        this.categoryVisibility.put(category, mode);
    }

    public VisibilityMode toggleCategoryVisibility(CosmeticCategory category) {
        VisibilityMode current = this.getCategoryVisibility(category);
        VisibilityMode next = switch (current) {
            case ALL -> VisibilityMode.SELF_ONLY;
            case SELF_ONLY -> VisibilityMode.OTHERS_ONLY;
            case OTHERS_ONLY -> VisibilityMode.NONE;
            case NONE -> VisibilityMode.ALL;
        };
        this.categoryVisibility.put(category, next);
        return next;
    }

    public boolean isSilentMode() {
        return this.silentMode;
    }

    public void setSilentMode(boolean silent) {
        this.silentMode = silent;
    }

    public boolean toggleSilentMode() {
        this.silentMode = !this.silentMode;
        return this.silentMode;
    }

    public boolean isReducedEffects() {
        return this.reducedEffects;
    }

    public void setReducedEffects(boolean reduced) {
        this.reducedEffects = reduced;
    }

    public boolean toggleReducedEffects() {
        this.reducedEffects = !this.reducedEffects;
        return this.reducedEffects;
    }

    public void enableAllCategories() {
        this.enabledCategories.addAll(EnumSet.allOf(CosmeticCategory.class));
    }

    public void disableAllCategories() {
        this.enabledCategories.clear();
    }

    public void setAllCategoriesVisibility(VisibilityMode mode) {
        for (CosmeticCategory category : CosmeticCategory.values()) {
            this.categoryVisibility.put(category, mode);
        }
    }

    public boolean shouldSeeEffect(UUID ownerId, CosmeticCategory category) {
        VisibilityMode mode = this.getCategoryVisibility(category);
        if (ownerId.equals(this.playerId)) {
            return mode == VisibilityMode.ALL || mode == VisibilityMode.SELF_ONLY;
        }
        if (!this.showOthersEffects) {
            return false;
        }
        return this.isCategoryEnabled(category)
                && (mode == VisibilityMode.ALL || mode == VisibilityMode.OTHERS_ONLY);
    }

    public boolean shouldViewerSeeMyEffect(UUID viewerId, CosmeticCategory category) {
        if (!this.showMyEffectsToOthers && this.isEffectsCategory(category)) {
            return false;
        }
        VisibilityMode mode = this.getCategoryVisibility(category);
        if (viewerId.equals(this.playerId)) {
            return mode == VisibilityMode.ALL || mode == VisibilityMode.SELF_ONLY;
        }
        return mode == VisibilityMode.ALL || mode == VisibilityMode.OTHERS_ONLY;
    }

    private boolean isEffectsCategory(CosmeticCategory category) {
        return category == CosmeticCategory.PARTICLE_EFFECT
                || category == CosmeticCategory.DEATH_EFFECT
                || category == CosmeticCategory.ARROW_EFFECT;
    }
}
