package net.schalker.SMPS.modules.invsee;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marker holder that ties an open invsee GUI to the inspected player.
 * The listener uses {@code instanceof InvseeHolder} to recognise the menu
 * and {@link #getTargetId()} to know whose inventory to sync edits back to.
 */
public class InvseeHolder implements InventoryHolder {
   private final UUID targetId;
   private final String targetName;
   private Inventory inventory;

   public InvseeHolder(UUID targetId, String targetName) {
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
