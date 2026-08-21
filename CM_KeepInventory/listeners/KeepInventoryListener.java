package net.schalker.SMPS.modules.keepinventory.listeners;

import net.schalker.DoAPI.core.listener.BaseListener;
import net.schalker.DoAPI.DoAPI;
import net.schalker.SMPS.modules.keepinventory.KeepInventoryModule;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;

public class KeepInventoryListener extends BaseListener {
   private final KeepInventoryModule module;

   public KeepInventoryListener(DoAPI plugin, KeepInventoryModule module) {
      super(plugin);
      this.module = module;
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onPlayerDeath(PlayerDeathEvent event) {
      Player player = event.getEntity();
      if (!this.module.isKeepInventoryEnabled(player.getUniqueId())) {
         return;
      }

      event.setKeepInventory(true);
      event.getDrops().clear();
   }
}
