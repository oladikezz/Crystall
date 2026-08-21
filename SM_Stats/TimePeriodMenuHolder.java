package net.schalker.SMPS.modules.stats;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class TimePeriodMenuHolder implements InventoryHolder {
   private final StatsCategory category;

   public TimePeriodMenuHolder(StatsCategory category) {
      this.category = category;
   }

   public StatsCategory getCategory() {
      return this.category;
   }

   @Override
   public Inventory getInventory() {
      return null;
   }
}
