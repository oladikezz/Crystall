package net.schalker.SMPS.modules.stats;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class StatsMenuHolder implements InventoryHolder {
   private final UUID targetId;

   public StatsMenuHolder(UUID targetId) {
      this.targetId = targetId;
   }

   public UUID getTargetId() {
      return this.targetId;
   }

   @Override
   public Inventory getInventory() {
      return null;
   }
}