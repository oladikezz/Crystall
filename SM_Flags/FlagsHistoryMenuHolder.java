package net.schalker.SMPS.modules.flags;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class FlagsHistoryMenuHolder implements InventoryHolder {
   private final int page;
   private final String targetPlayer;
   // Expanded (unstacked) view fields
   private final String expandPlayer;
   private final String expandFlagTypeKey;
   private final int parentPage;

   public FlagsHistoryMenuHolder(int page, String targetPlayer) {
      this(page, targetPlayer, null, null, -1);
   }

   public FlagsHistoryMenuHolder(int page, String targetPlayer,
                                  String expandPlayer, String expandFlagTypeKey, int parentPage) {
      this.page = page;
      this.targetPlayer = targetPlayer;
      this.expandPlayer = expandPlayer;
      this.expandFlagTypeKey = expandFlagTypeKey;
      this.parentPage = parentPage;
   }

   public int getPage() {
      return this.page;
   }

   public String getTargetPlayer() {
      return this.targetPlayer;
   }

   public boolean isExpanded() {
      return this.expandPlayer != null && this.expandFlagTypeKey != null;
   }

   public String getExpandPlayer() {
      return this.expandPlayer;
   }

   public String getExpandFlagTypeKey() {
      return this.expandFlagTypeKey;
   }

   public int getParentPage() {
      return this.parentPage;
   }

   @Override
   public Inventory getInventory() {
      return null;
   }
}
