package net.schalker.SMPS.modules.spit.listeners;

import net.schalker.SMPS.modules.spit.SpitModule;
import org.bukkit.entity.LlamaSpit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;

public class SpitListener implements Listener {
   private final SpitModule module;

   public SpitListener(SpitModule module) {
      this.module = module;
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
      if (!(event.getDamager() instanceof LlamaSpit spit)) {
         return;
      }

      Byte marker = spit.getPersistentDataContainer().get(this.module.getSpitKey(), PersistentDataType.BYTE);
      if (marker == null || marker != (byte) 1) {
         return;
      }

      event.setCancelled(true);
      event.setDamage(0.0D);
   }
}
