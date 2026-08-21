package net.schalker.SMPS.modules.essentials.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.listener.BaseListener;
import net.schalker.SMPS.modules.essentials.EssentialsModule;

public class GodModeListener extends BaseListener {
   private final EssentialsModule module;

   public GodModeListener(DoAPI plugin, EssentialsModule module) {
      super(plugin);
      this.module = module;
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onDamage(EntityDamageEvent event) {
      if (!(event.getEntity() instanceof Player player)) {
         return;
      }
      if (!this.module.isGod(player.getUniqueId())) {
         return;
      }
      event.setCancelled(true);
   }
}
