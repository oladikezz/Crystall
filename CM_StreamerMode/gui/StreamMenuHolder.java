package net.schalker.SMPS.modules.streamermode.gui;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class StreamMenuHolder implements InventoryHolder {
   private final UUID owner;

   public StreamMenuHolder(UUID owner) {
      this.owner = owner;
   }

   public UUID getOwner() {
      return this.owner;
   }

   @Override
   public Inventory getInventory() {
      return null;
   }
}
