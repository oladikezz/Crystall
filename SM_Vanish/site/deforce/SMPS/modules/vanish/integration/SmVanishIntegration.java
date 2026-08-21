package site.deforce.SMPS.modules.vanish.integration;

import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.integration.VanishIntegration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import site.deforce.SMPS.modules.vanish.SM_Vanish;

public class SmVanishIntegration extends VanishIntegration {
   private final SM_Vanish module;

   public SmVanishIntegration(SM_Vanish module) {
      super("SM_Vanish");
      this.module = module;
   }

   public boolean isVanished(@NotNull TabPlayer player) {
      return this.module.isVanished(player.getUniqueId());
   }

   public boolean canSee(@NotNull TabPlayer viewer, @NotNull TabPlayer target) {
      if (this.module.isVanished(target.getUniqueId())) {
         Player viewerPlayer = Bukkit.getPlayer(viewer.getUniqueId());
         if (viewerPlayer == null) {
            return false;
         } else {
            return viewerPlayer.hasPermission("smvanish.see") || viewerPlayer.isOp();
         }
      } else {
         return true;
      }
   }
}
