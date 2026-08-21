package net.schalker.SMPS.modules.checker.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.listener.BaseListener;
import net.schalker.SMPS.modules.checker.CheckerModule;
import net.schalker.SMPS.modules.checker.managers.CheckManager;

public class CheckListener extends BaseListener {
   private final CheckManager checkManager;
   private final CheckerModule module;

   public CheckListener(DoAPI plugin, CheckManager checkManager, CheckerModule module) {
      super(plugin);
      this.checkManager = checkManager;
      this.module = module;
   }

   @EventHandler(priority = EventPriority.HIGH)
   public void onMove(PlayerMoveEvent event) {
      if (!this.isActionsBlocked()) {
         return;
      }
      Player player = event.getPlayer();
      var session = this.checkManager.getSession(player.getUniqueId());
      if (session == null) {
         return;
      }
      if (session.isDenyInProgress()) {
         return;
      }
      if (event.getTo() != null && (event.getFrom().getX() != event.getTo().getX()
         || event.getFrom().getY() != event.getTo().getY()
         || event.getFrom().getZ() != event.getTo().getZ())) {
         event.setTo(event.getFrom());
      }
   }

   @EventHandler(priority = EventPriority.HIGH)
   public void onInteract(PlayerInteractEvent event) {
      if (!this.isActionsBlocked()) {
         return;
      }
      if (this.checkManager.isChecking(event.getPlayer().getUniqueId())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.HIGH)
   public void onInteractEntity(PlayerInteractEntityEvent event) {
      if (!this.isActionsBlocked()) {
         return;
      }
      if (this.checkManager.isChecking(event.getPlayer().getUniqueId())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.HIGH)
   public void onBlockBreak(BlockBreakEvent event) {
      if (!this.isActionsBlocked()) {
         return;
      }
      if (this.checkManager.isChecking(event.getPlayer().getUniqueId())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.HIGH)
   public void onBlockPlace(BlockPlaceEvent event) {
      if (!this.isActionsBlocked()) {
         return;
      }
      if (this.checkManager.isChecking(event.getPlayer().getUniqueId())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.HIGH)
   public void onDrop(PlayerDropItemEvent event) {
      if (!this.isActionsBlocked()) {
         return;
      }
      if (this.checkManager.isChecking(event.getPlayer().getUniqueId())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.HIGH)
   public void onPickup(EntityPickupItemEvent event) {
      if (!this.isActionsBlocked()) {
         return;
      }
      if (event.getEntity() instanceof Player player) {
         if (this.checkManager.isChecking(player.getUniqueId())) {
            event.setCancelled(true);
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGH)
   public void onInventoryClick(InventoryClickEvent event) {
      if (!this.isActionsBlocked()) {
         return;
      }
      if (event.getWhoClicked() instanceof Player player) {
         if (this.checkManager.isChecking(player.getUniqueId())) {
            event.setCancelled(true);
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGH)
   public void onInventoryDrag(InventoryDragEvent event) {
      if (!this.isActionsBlocked()) {
         return;
      }
      if (event.getWhoClicked() instanceof Player player) {
         if (this.checkManager.isChecking(player.getUniqueId())) {
            event.setCancelled(true);
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGH)
   public void onSwap(PlayerSwapHandItemsEvent event) {
      if (!this.isActionsBlocked()) {
         return;
      }
      if (this.checkManager.isChecking(event.getPlayer().getUniqueId())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.HIGH)
   public void onDamage(EntityDamageByEntityEvent event) {
      if (!this.isActionsBlocked()) {
         return;
      }
      if (event.getDamager() instanceof Player player) {
         if (this.checkManager.isChecking(player.getUniqueId())) {
            event.setCancelled(true);
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onCheckedPlayerTakeDamage(EntityDamageEvent event) {
      if (event.getEntity() instanceof Player player && this.checkManager.isChecking(player.getUniqueId())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.HIGH)
   public void onCommand(PlayerCommandPreprocessEvent event) {
      if (!this.isCommandsBlocked()) {
         return;
      }
      Player player = event.getPlayer();
      if (this.checkManager.isChecking(player.getUniqueId())) {
         String message = event.getMessage().toLowerCase();
         if (message.startsWith("/check ")) {
            String[] parts = message.split("\\s+");
            if (parts.length >= 3) {
               String action = parts[2];
               if (action.equals("pass") || action.equals("failed") || action.equals("denied") || action.equals("rejected")) {
                  return;
               }
            }
         }
         if (message.startsWith("/check confirm") || message.startsWith("/check deny")) {
            return;
         }
         event.setCancelled(true);
         player.sendMessage(this.module.getMessage("blocked-command"));
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onQuit(PlayerQuitEvent event) {
      Player player = event.getPlayer();
      this.checkManager.clearStaffData(player.getUniqueId());
      if (this.checkManager.isChecking(player.getUniqueId())) {
         this.checkManager.handleQuit(player);
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      this.plugin.getSchedulerManager().runEntityTask(player, "checker-join-" + player.getName(), () -> {
         if (!this.checkManager.isChecking(player.getUniqueId())) {
            this.checkManager.resetPlayerState(player);
         }
      });
   }

   private boolean isActionsBlocked() {
      return this.module.isFeatureEnabled("block-actions", true);
   }

   private boolean isCommandsBlocked() {
      return this.module.isFeatureEnabled("block-commands", true);
   }
}
