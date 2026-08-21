package net.schalker.SMPS.modules.trafficoptimizer.listeners;

import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.listener.BaseListener;
import net.schalker.SMPS.modules.trafficoptimizer.TrafficOptimizerModule;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class NetworkListener extends BaseListener {

   private final TrafficOptimizerModule module;

   public NetworkListener(DoAPI plugin, TrafficOptimizerModule module) {
      super(plugin);
      this.module = module;
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onJoin(PlayerJoinEvent event) {
      this.module.handleJoin(event.getPlayer());
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onQuit(PlayerQuitEvent event) {
      this.module.handleQuit(event.getPlayer().getUniqueId());
   }
}
