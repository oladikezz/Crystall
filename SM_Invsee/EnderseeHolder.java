package net.schalker.SMPS.modules.invsee;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marker holder that ties an open endersee GUI to the inspected player's
 * ender chest. Recognised by the listener via {@code instanceof EnderseeHolder}.
 */
public class EnderseeHolder implements InventoryHolder {
   private final UUID targetId;
   private final String targetName;
   private Inventory inventory;

   public EnderseeHolder(UUID targetId, String targetName) {
      this.targetId = targetId;
      this.targetName = targetName;
   }

   public UUID getTargetId() {
      return this.targetId;
   }

   public String getTargetName() {
      return this.targetName;
   }

   public void setInventory(Inventory inventory) {
      this.inventory = inventory;
   }

   @Override
   public Inventory getInventory() {
      return this.inventory;
   }
}
