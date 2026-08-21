package net.schalker.SMPS.modules.alert.listeners;

import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.listener.BaseListener;
import net.schalker.SMPS.modules.alert.AlertMenuHolder;
import net.schalker.SMPS.modules.alert.AlertModule;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class AlertListener extends BaseListener {
   private final AlertModule module;

   public AlertListener(DoAPI plugin, AlertModule module) {
      super(plugin);
      this.module = module;
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onCommand(PlayerCommandPreprocessEvent event) {
      Player sender = event.getPlayer();
      String message = event.getMessage();
      if (message == null || message.length() < 2 || message.charAt(0) != '/') {
         return;
      }
      String trimmed = message.substring(1).trim();
      if (trimmed.isEmpty()) {
         return;
      }
      String[] parts = trimmed.split("\\s+");
      String label = parts[0].toLowerCase();
      if (label.isEmpty()) {
         return;
      }
      int colon = label.indexOf(':');
      if (colon >= 0 && colon < label.length() - 1) {
         label = label.substring(colon + 1);
      }
      String key = this.module.resolveCommandKey(label);
      if (key == null) {
         return;
      }
      this.module.sendCommandLog(sender, message, key);
   }

   @EventHandler
   public void onInventoryClick(InventoryClickEvent event) {
      if (!(event.getWhoClicked() instanceof Player player)) {
         return;
      }
      Inventory top = event.getView().getTopInventory();
      if (!(top.getHolder() instanceof AlertMenuHolder)) {
         return;
      }
      event.setCancelled(true);

      if (!player.hasPermission("smalert.atoggle")) {
         this.module.clearPlayerState(player.getUniqueId());
         this.module.refreshMenu(top, player);
         return;
      }

      if (event.getRawSlot() >= top.getSize()) {
         return;
      }

      ItemStack item = event.getCurrentItem();
      if (item == null || item.getType() == Material.AIR) {
         return;
      }
      ItemMeta meta = item.getItemMeta();
      if (meta == null) {
         return;
      }
      PersistentDataContainer container = meta.getPersistentDataContainer();
      if (container.has(this.module.getToggleAllKey(), PersistentDataType.BYTE)) {
         this.module.toggleAllCommands(player.getUniqueId());
         this.module.refreshMenu(top, player);
         return;
      }
      if (container.has(this.module.getToggleKey(), PersistentDataType.BYTE)) {
         this.module.toggleLog(player.getUniqueId());
         this.module.refreshMenu(top, player);
         return;
      }
      String commandKey = container.get(this.module.getCommandKey(), PersistentDataType.STRING);
      if (commandKey != null) {
         this.module.toggleCommand(player.getUniqueId(), commandKey);
         this.module.refreshMenu(top, player);
      }
   }

   @EventHandler
   public void onInventoryDrag(InventoryDragEvent event) {
      Inventory top = event.getView().getTopInventory();
      if (top.getHolder() instanceof AlertMenuHolder) {
         event.setCancelled(true);
      }
   }
}
