package net.schalker.SMPS.modules.crowns.listeners;

import net.schalker.DoAPI.DoAPI;
import net.schalker.SMPS.modules.crowns.CrownsModule;
import net.schalker.SMPS.modules.crowns.managers.CrownsManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class CrownsListener implements Listener {
   private final DoAPI plugin;
   private final CrownsModule module;
   private final CrownsManager manager;

   public CrownsListener(DoAPI plugin, CrownsModule module, CrownsManager manager) {
      this.plugin = plugin;
      this.module = module;
      this.manager = manager;
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onPlayerJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      this.plugin.getSchedulerManager().runEntityTaskLater(player,
         "crowns-join-" + player.getUniqueId().toString().substring(0, 8), () -> {
         if (player.isOnline()) {
            this.manager.handlePlayerJoin(player);
         }
      }, 10L);
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onPlayerQuit(PlayerQuitEvent event) {
      this.manager.handlePlayerQuit(event.getPlayer());
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onPlayerDeath(PlayerDeathEvent event) {
      this.manager.handlePlayerDeath(event.getEntity());
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onPlayerRespawn(PlayerRespawnEvent event) {
      Player player = event.getPlayer();
      this.plugin.getSchedulerManager().runEntityTaskLater(player,
         "crowns-respawn-" + player.getUniqueId().toString().substring(0, 8), () -> {
         if (player.isOnline()) {
            this.manager.handlePlayerRespawn(player);
         }
      }, 5L);
   }

   /**
    * LOWEST priority: remove passenger BEFORE server processes TP.
    * Then respawn crown at new location after TP completes.
    */
   @EventHandler(priority = EventPriority.LOWEST)
   public void onPlayerTeleport(PlayerTeleportEvent event) {
      Player player = event.getPlayer();

      this.manager.handlePreTeleport(player);

      this.plugin.getSchedulerManager().runEntityTaskLater(player,
         "crowns-tp-" + player.getUniqueId().toString().substring(0, 8), () -> {
         if (player.isOnline()) {
            this.manager.handlePostTeleport(player);
         }
      }, 3L);
   }
}
