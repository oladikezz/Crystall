package net.schalker.SMPS.modules.essentials.listeners;

import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.listener.BaseListener;
import net.schalker.SMPS.modules.essentials.EssentialsModule;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;

public class FreezeListener extends BaseListener {
   private final EssentialsModule module;

   public FreezeListener(DoAPI plugin, EssentialsModule module) {
      super(plugin);
      this.module = module;
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onMove(PlayerMoveEvent event) {
      Player player = event.getPlayer();
      if (!this.module.isFrozen(player.getUniqueId())) {
         return;
      }

      if (event.getFrom().getX() == event.getTo().getX()
         && event.getFrom().getY() == event.getTo().getY()
         && event.getFrom().getZ() == event.getTo().getZ()) {
         return;
      }

      var from = event.getFrom().clone();
      from.setYaw(event.getTo().getYaw());
      from.setPitch(event.getTo().getPitch());
      event.setTo(from);
   }
}
