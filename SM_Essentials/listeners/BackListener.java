package net.schalker.SMPS.modules.essentials.listeners;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerTeleportEvent;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.listener.BaseListener;
import net.schalker.SMPS.modules.essentials.BackTracker;

public class BackListener extends BaseListener {
   public BackListener(DoAPI plugin) {
      super(plugin);
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onTeleport(PlayerTeleportEvent event) {
      Player player = event.getPlayer();
      if (BackTracker.consumeSkipNext(player.getUniqueId())) {
         return;
      }

      Location from = event.getFrom();
      Location to = event.getTo();
      if (from == null) {
         return;
      }
      if (to != null && from.getWorld() != null && to.getWorld() != null && from.getWorld().equals(to.getWorld())) {
         double dx = from.getX() - to.getX();
         double dy = from.getY() - to.getY();
         double dz = from.getZ() - to.getZ();
         if ((dx * dx + dy * dy + dz * dz) < 0.01) {
            return;
         }
      }

      BackTracker.record(this.plugin, player, from);
   }
}
