package ru.lor.watcher.utils;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Base64;
import java.util.UUID;

public class HeadUtil {

    /**
     * Creates a PLAYER_HEAD item from Base64 skin texture string and signature.
     */
    public static ItemStack getCustomHead(String textureValue, String signature) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (textureValue == null || textureValue.trim().isEmpty()) {
            return item;
        }

        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), "Watcher");
            if (signature != null && !signature.isEmpty()) {
                profile.setProperty(new ProfileProperty("textures", textureValue, signature));
            } else {
                profile.setProperty(new ProfileProperty("textures", textureValue));
            }
            meta.setPlayerProfile(profile);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack getCustomHead(String input) {
        return getCustomHead(input, null);
    }

    /**
     * Creates a PLAYER_HEAD item for an online or offline player.
     */
    public static ItemStack getPlayerHead(OfflinePlayer player) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            item.setItemMeta(meta);
        }
        return item;
    }
}
