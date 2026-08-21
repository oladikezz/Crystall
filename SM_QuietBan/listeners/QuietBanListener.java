package net.schalker.SMPS.modules.quietban.listeners;

import net.schalker.SMPS.modules.quietban.QuietBanManager;
import net.schalker.SMPS.modules.quietban.QuietBanModule;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class QuietBanListener implements Listener {

   private final QuietBanModule module;

   public QuietBanListener(QuietBanModule module) {
      this.module = module;
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onJoin(PlayerJoinEvent event) {
      QuietBanManager manager = this.module.getManager();
      if (manager == null) {
         return;
      }

      Player player = event.getPlayer();
      if (this.module.isImmune(player)) {
         return;
      }

      String ip = manager.addressOf(player);
      this.module.runAsync("quietban-join-" + player.getUniqueId(),
         () -> manager.handleJoin(player, ip));
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onQuit(PlayerQuitEvent event) {
      QuietBanManager manager = this.module.getManager();
      if (manager != null) {
         manager.handleQuit(event.getPlayer().getUniqueId());
      }
   }
}
