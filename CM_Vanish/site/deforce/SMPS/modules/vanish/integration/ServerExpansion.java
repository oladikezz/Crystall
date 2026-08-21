package site.deforce.SMPS.modules.vanish.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import site.deforce.SMPS.modules.vanish.SM_Vanish;

public class ServerExpansion extends PlaceholderExpansion {
   private final SM_Vanish module;
   private final String identifier;

   public ServerExpansion(SM_Vanish module) {
      this(module, "server");
   }

   public ServerExpansion(SM_Vanish module, String identifier) {
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

   public String onRequest(OfflinePlayer player, @NotNull String identifier) {
      switch (identifier.toLowerCase()) {
         case "online":
         case "online_players":
            return String.valueOf(this.getVisibleOnlineCount());
         case "max_players":
            return String.valueOf(Bukkit.getMaxPlayers());
         case "name":
            return Bukkit.getServer().getName();
         case "tps":
            double[] tps = Bukkit.getTPS();
            return String.format("%.2f", tps != null && tps.length > 0 ? tps[0] : 20.0);
         case "online_real":
            return String.valueOf(Bukkit.getOnlinePlayers().size());
         case "vanished_count":
            return String.valueOf(this.module.getVanishedPlayers().size());
         default:
            return null;
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
