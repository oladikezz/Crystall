package net.schalker.SMPS.modules.debugstick.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.schalker.SMPS.modules.debugstick.DebugStickModule;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class DebugStickListener implements Listener {
   private final DebugStickModule module;

   public DebugStickListener(DebugStickModule module) {
      this.module = module;
   }

   /**
    * Cancel debug stick interaction with blacklisted blocks,
    * protect waterlogged state, and prevent slab double duplication.
    */
   @EventHandler(priority = EventPriority.LOWEST)
   public void onPlayerInteract(PlayerInteractEvent event) {
      if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
         return;
      }

      Player player = event.getPlayer();
      ItemStack item = player.getInventory().getItemInMainHand();

      if (item.getType() != Material.DEBUG_STICK) {
         return;
      }

      Block block = event.getClickedBlock();
      if (block == null) {
         return;
      }

      // Blacklisted block — cancel entirely
      if (this.module.isBlacklisted(block.getType())) {
         event.setCancelled(true);
         String message = this.module.getBlacklistMessage();
         if (message != null && !message.isEmpty()) {
            Component component = LegacyComponentSerializer.legacySection().deserialize(message);
            player.sendActionBar(component);
         }
         return;
      }

      // Protect block state changes (Waterlogged & Slab DOUBLE duplication)
      BlockData data = block.getBlockData();
      boolean checkWaterlogged = data instanceof Waterlogged;
      boolean checkSlab = this.module.isProtectSlabs() && data instanceof Slab;

      if (checkWaterlogged || checkSlab) {
         boolean wasWaterlogged = checkWaterlogged && ((Waterlogged) data).isWaterlogged();
         Slab.Type wasSlabType = checkSlab ? ((Slab) data).getType() : null;

         this.module.getSmps().getSchedulerManager().runRegionTaskLater(
            block.getLocation(), "debugstick-state-guard", () -> {
            BlockData current = block.getBlockData();
            boolean modified = false;

            if (checkWaterlogged && current instanceof Waterlogged currentWl) {
               if (currentWl.isWaterlogged() != wasWaterlogged) {
                  currentWl.setWaterlogged(wasWaterlogged);
                  modified = true;
               }
            }

            if (checkSlab && current instanceof Slab currentSlab && wasSlabType != null) {
               if (currentSlab.getType() == Slab.Type.DOUBLE || wasSlabType == Slab.Type.DOUBLE) {
                  currentSlab.setType(wasSlabType);
                  modified = true;
               }
            }

            if (modified) {
               block.setBlockData(current, false);
            }
         }, 1L);
      }
   }
}
