package net.schalker.SMPS.modules.stats;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class TopMenuHolder implements InventoryHolder {
   private final StatsMetric metric;
   private final int page;

   public TopMenuHolder(StatsMetric metric, int page) {
      this.metric = metric;
      this.page = page;
   }

   public StatsMetric getMetric() {
      return this.metric;
   }

   public int getPage() {
      return this.page;
   }

   @Override
   public Inventory getInventory() {
      return null;
   }
}