package be.isach.ultracosmetics.permissions;

import be.isach.ultracosmetics.cosmetics.type.CosmeticType;
import org.bukkit.entity.Player;

import java.util.Set;

/**
 * No-op permission command implementation.
 * Treasure chests are disabled in the SMPS module, so this is a stub.
 */
public class PermissionCommand implements CosmeticPermissionSetter, RawPermissionSetter {

    @Override
    public void setPermissions(Player player, Set<CosmeticType<?>> types) {
        // no-op
    }

    @Override
    public boolean isUnsetSupported() {
        return false;
    }

    @Override
    public void unsetPermissions(Player player, Set<CosmeticType<?>> types) {
        // no-op
    }

    @Override
    public void setRawPermission(Player player, String permission) {
        // no-op
    }
}

