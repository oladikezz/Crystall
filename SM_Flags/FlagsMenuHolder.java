package net.schalker.SMPS.modules.flags;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class FlagsMenuHolder implements InventoryHolder {
   private final UUID playerId;
   private final int page;

   public FlagsMenuHolder(UUID playerId, int page) {
      this.playerId = playerId;
      this.page = page;
   }

   public UUID getPlayerId() {
      return this.playerId;
   }

   public int getPage() {
      return this.page;
   }

   @Override
   public Inventory getInventory() {
      return null;
   }
}
