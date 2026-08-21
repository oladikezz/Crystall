package net.schalker.SMPS.modules.streamermode.listeners;

import net.schalker.SMPS.SMPS;
import net.schalker.SMPS.core.listener.BaseListener;
import net.schalker.SMPS.modules.streamermode.StreamerModeModule;
import net.schalker.SMPS.modules.streamermode.gui.StreamMenuHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;

public class StreamMenuListener extends BaseListener {
   private final StreamerModeModule module;

   public StreamMenuListener(SMPS plugin, StreamerModeModule module) {
      super(plugin);
      this.module = module;
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onClick(InventoryClickEvent event) {
      if (!(event.getInventory().getHolder() instanceof StreamMenuHolder)) {
         return;
      }

      event.setCancelled(true);
      if (!(event.getWhoClicked() instanceof Player player)) {
         return;
      }

      int slot = event.getRawSlot();
      if (slot == 11) {
         boolean enabledNow = this.module.isStreamEnabled(player.getUniqueId());
         if (!enabledNow && this.module.getChannelLink(player.getUniqueId()) == null) {
            player.sendMessage(this.module.getMessage("stream.link-required", "&[SECONDARY]First link your channel: &[MAIN]/stream link <url>"));
            return;
         }

         this.module.setStreamEnabled(player.getUniqueId(), player.getName(), !enabledNow);
         if (!enabledNow) {
            this.module.broadcastStreamStart(player);
            player.sendMessage(this.module.getMessage("stream.enabled-target", "&[SECONDARY]Streamer mode enabled."));
         } else {
            player.sendMessage(this.module.getMessage("stream.disabled-target", "&[SECONDARY]Streamer mode disabled."));
         }
         this.module.openMenu(player);
         return;
      }

      if (slot == 15) {
         boolean newState = !this.module.isChatFilterEnabled(player.getUniqueId());
         this.module.setChatFilterEnabled(player.getUniqueId(), newState);
         player.sendMessage(this.module.getMessage(
            newState ? "stream.filter-enabled" : "stream.filter-disabled",
            newState ? "&[SECONDARY]Chat filter enabled." : "&[SECONDARY]Chat filter disabled."
         ));
         this.module.openMenu(player);
         return;
      }

      if (slot == 22) {
         player.closeInventory();
      }
   }
}
