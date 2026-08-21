package site.deforce.SMPS.modules.vanish.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import site.deforce.SMPS.modules.vanish.SM_Vanish;

public class VanishExpansion extends PlaceholderExpansion {
   private final SM_Vanish module;
   private final String identifier;

   public VanishExpansion(SM_Vanish module) {
      this(module, "vanish");
   }

   public VanishExpansion(SM_Vanish module, String identifier) {
      super();
      this.module = module;
      this.identifier = identifier;
   }

   public @NotNull String getIdentifier() {
      return this.identifier;
   }

   public @NotNull String getAuthor() {
      return this.module.getModuleInfo().getAuthor();
   }

   public @NotNull String getVersion() {
      return this.module.getModuleInfo().getVersion();
   }

   public boolean persist() {
      return true;
   }

   public boolean canRegister() {
      return true;
   }

   public String onPlaceholderRequest(Player player, @NotNull String identifier) {
      switch (identifier.toLowerCase()) {
         case "online":
         case "online_count":
            return String.valueOf(this.getVisibleOnlineCount());
         case "real_online":
            return String.valueOf(Bukkit.getOnlinePlayers().size());
         case "is_vanished":
            if (player == null) {
               return "false";
            }

            return this.module.isVanished(player) ? "true" : "false";
         case "is_incognito":
            if (player == null) {
               return "false";
            }

            return this.module.isIncognito(player) ? "true" : "false";
         case "is_hidden":
            if (player == null) {
               return "false";
            }

            return !this.module.isVanished(player) && !this.module.isIncognito(player) ? "false" : "true";
         case "state":
            if (player == null) {
               return "";
            } else {
               if (this.module.isIncognito(player)) {
                  return ChatColor.translateAlternateColorCodes('&', this.module.getModuleConfig().getString("placeholders.state.incognito", ""));
               }

               boolean isVanished = this.module.isVanished(player);
               String configKey = isVanished ? "vanished" : "visible";
               String defValue = isVanished ? "&7[Vanished]" : "";
               String rawValue = this.module.getModuleConfig().getString("placeholders.state." + configKey, defValue);
               return ChatColor.translateAlternateColorCodes('&', rawValue);
            }
         default:
            return null;
      }
   }

   public String onRequest(OfflinePlayer player, @NotNull String identifier) {
      if (player != null && player.isOnline()) {
         return this.onPlaceholderRequest(player.getPlayer(), identifier);
      } else {
         return !identifier.equalsIgnoreCase("online") && !identifier.equalsIgnoreCase("online_count") ? null : String.valueOf(this.getVisibleOnlineCount());
      }
   }

   private int getVisibleOnlineCount() {
      int visible = 0;

      for(Player online : Bukkit.getOnlinePlayers()) {
         if (!this.module.isVanished(online) && !this.module.isIncognito(online)) {
            ++visible;
         }
      }

      return visible;
   }
}
