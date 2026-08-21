package net.schalker.SMPS.modules.cosmetics.gui;

import net.schalker.SMPS.modules.cosmetics.models.CosmeticCategory;
import net.schalker.SMPS.modules.cosmetics.models.UserCosmeticSettings;

final class CosmeticsMenuManager$1 {
    static final int[] $SwitchMap$net$schalker$SMPS$modules$cosmetics$models$CosmeticCategory;
    static final int[] $SwitchMap$net$schalker$SMPS$modules$cosmetics$models$UserCosmeticSettings$VisibilityMode;

    static {
        $SwitchMap$net$schalker$SMPS$modules$cosmetics$models$CosmeticCategory = new int[CosmeticCategory.values().length];
        try { $SwitchMap$net$schalker$SMPS$modules$cosmetics$models$CosmeticCategory[CosmeticCategory.PET.ordinal()] = 1; } catch (Throwable ignored) {}
        try { $SwitchMap$net$schalker$SMPS$modules$cosmetics$models$CosmeticCategory[CosmeticCategory.PARTICLE_EFFECT.ordinal()] = 2; } catch (Throwable ignored) {}
        try { $SwitchMap$net$schalker$SMPS$modules$cosmetics$models$CosmeticCategory[CosmeticCategory.DEATH_EFFECT.ordinal()] = 3; } catch (Throwable ignored) {}
        try { $SwitchMap$net$schalker$SMPS$modules$cosmetics$models$CosmeticCategory[CosmeticCategory.ARROW_EFFECT.ordinal()] = 4; } catch (Throwable ignored) {}
        try { $SwitchMap$net$schalker$SMPS$modules$cosmetics$models$CosmeticCategory[CosmeticCategory.BALLOON.ordinal()] = 5; } catch (Throwable ignored) {}
        try { $SwitchMap$net$schalker$SMPS$modules$cosmetics$models$CosmeticCategory[CosmeticCategory.EMOTE.ordinal()] = 6; } catch (Throwable ignored) {}
        try { $SwitchMap$net$schalker$SMPS$modules$cosmetics$models$CosmeticCategory[CosmeticCategory.MORPH.ordinal()] = 7; } catch (Throwable ignored) {}
        try { $SwitchMap$net$schalker$SMPS$modules$cosmetics$models$CosmeticCategory[CosmeticCategory.MOUNT.ordinal()] = 8; } catch (Throwable ignored) {}
        try { $SwitchMap$net$schalker$SMPS$modules$cosmetics$models$CosmeticCategory[CosmeticCategory.GADGET.ordinal()] = 9; } catch (Throwable ignored) {}
        try { $SwitchMap$net$schalker$SMPS$modules$cosmetics$models$CosmeticCategory[CosmeticCategory.STATUS.ordinal()] = 10; } catch (Throwable ignored) {}
        try { $SwitchMap$net$schalker$SMPS$modules$cosmetics$models$CosmeticCategory[CosmeticCategory.BANNER.ordinal()] = 11; } catch (Throwable ignored) {}
        try { $SwitchMap$net$schalker$SMPS$modules$cosmetics$models$CosmeticCategory[CosmeticCategory.MUSIC.ordinal()] = 12; } catch (Throwable ignored) {}

        $SwitchMap$net$schalker$SMPS$modules$cosmetics$models$UserCosmeticSettings$VisibilityMode = new int[UserCosmeticSettings.VisibilityMode.values().length];
        try { $SwitchMap$net$schalker$SMPS$modules$cosmetics$models$UserCosmeticSettings$VisibilityMode[UserCosmeticSettings.VisibilityMode.ALL.ordinal()] = 1; } catch (Throwable ignored) {}
        try { $SwitchMap$net$schalker$SMPS$modules$cosmetics$models$UserCosmeticSettings$VisibilityMode[UserCosmeticSettings.VisibilityMode.SELF_ONLY.ordinal()] = 2; } catch (Throwable ignored) {}
        try { $SwitchMap$net$schalker$SMPS$modules$cosmetics$models$UserCosmeticSettings$VisibilityMode[UserCosmeticSettings.VisibilityMode.OTHERS_ONLY.ordinal()] = 3; } catch (Throwable ignored) {}
        try { $SwitchMap$net$schalker$SMPS$modules$cosmetics$models$UserCosmeticSettings$VisibilityMode[UserCosmeticSettings.VisibilityMode.NONE.ordinal()] = 4; } catch (Throwable ignored) {}
    }

    private CosmeticsMenuManager$1() {
    }
}
