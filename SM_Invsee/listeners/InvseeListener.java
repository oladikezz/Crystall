package net.schalker.SMPS.modules.invsee.listeners;

import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.listener.BaseListener;
import net.schalker.SMPS.modules.invsee.EnderseeHolder;
import net.schalker.SMPS.modules.invsee.InvseeHolder;
import net.schalker.SMPS.modules.invsee.InvseeModule;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public class InvseeListener extends BaseListener {
   private final InvseeModule module;

   public InvseeListener(DoAPI plugin, InvseeModule module) {
      super(plugin);
      this.module = module;
   }

   @EventHandler
   public void onInventoryClick(InventoryClickEvent event) {
      if (!(event.getWhoClicked() instanceof Player viewer)) {
         return;
      }
      Inventory top = event.getView().getTopInventory();
      var topHolder = top.getHolder();

      if (topHolder instanceof InvseeHolder holder) {
         // View-only unless the viewer has the edit permission.
         if (!viewer.hasPermission(InvseeModule.PERMISSION_EDIT)) {
            event.setCancelled(true);
            return;
         }
         // Block interaction with locked decoration slots in the top inventory
         // (separators / info head). Editable slots and the player's own
         // inventory below stay fully interactive.
         int raw = event.getRawSlot();
         if (raw >= 0 && raw < top.getSize() && !this.module.isEditableSlot(raw)) {
            event.setCancelled(true);
            return;
         }
         // Allowed edit — mirror the resulting GUI state back onto the player.
         this.module.scheduleSyncToTarget(viewer, holder);
         return;
      }

      if (topHolder instanceof EnderseeHolder holder) {
         if (!viewer.hasPermission(InvseeModule.PERMISSION_ENDER_EDIT)) {
            event.setCancelled(true);
            return;
         }
         // Every slot of an ender-chest view is editable.
         this.module.scheduleSyncEnderToTarget(viewer, holder);
      }
   }

   @EventHandler
   public void onInventoryDrag(InventoryDragEvent event) {
      if (!(event.getWhoClicked() instanceof Player viewer)) {
         return;
      }
      Inventory top = event.getView().getTopInventory();
      var topHolder = top.getHolder();

      if (topHolder instanceof InvseeHolder holder) {
         if (!viewer.hasPermission(InvseeModule.PERMISSION_EDIT)) {
            event.setCancelled(true);
            return;
         }
         for (int raw : event.getRawSlots()) {
            if (raw >= 0 && raw < top.getSize() && !this.module.isEditableSlot(raw)) {
               event.setCancelled(true);
               return;
            }
         }
         this.module.scheduleSyncToTarget(viewer, holder);
         return;
      }

      if (topHolder instanceof EnderseeHolder holder) {
         if (!viewer.hasPermission(InvseeModule.PERMISSION_ENDER_EDIT)) {
            event.setCancelled(true);
            return;
         }
         this.module.scheduleSyncEnderToTarget(viewer, holder);
      }
   }

   @EventHandler
   public void onInventoryClose(InventoryCloseEvent event) {
      if (!(event.getPlayer() instanceof Player viewer)) {
         return;
      }
      var topHolder = event.getView().getTopInventory().getHolder();

      if (topHolder instanceof InvseeHolder holder) {
         if (!viewer.hasPermission(InvseeModule.PERMISSION_EDIT)) {
            return;
         }
         // Final reconciliation in case the last interaction was still settling.
         this.module.scheduleSyncToTarget(viewer, holder);
         return;
      }

      if (topHolder instanceof EnderseeHolder holder) {
         if (!viewer.hasPermission(InvseeModule.PERMISSION_ENDER_EDIT)) {
            return;
         }
         this.module.scheduleSyncEnderToTarget(viewer, holder);
      }
   }
}
