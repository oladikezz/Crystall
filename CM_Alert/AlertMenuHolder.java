package net.schalker.SMPS.modules.alert;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class AlertMenuHolder implements InventoryHolder {
   private final UUID ownerId;

   public AlertMenuHolder(UUID ownerId) {
      this.ownerId = ownerId;
   }

   public UUID getOwnerId() {
      return this.ownerId;
   }

   @Override
   public Inventory getInventory() {
      return null;
   }
}