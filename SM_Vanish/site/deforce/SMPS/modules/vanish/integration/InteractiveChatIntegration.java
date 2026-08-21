package site.deforce.SMPS.modules.vanish.integration;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import site.deforce.SMPS.modules.vanish.SM_Vanish;

public class InteractiveChatIntegration implements Listener {
   private final SM_Vanish module;

   public InteractiveChatIntegration(SM_Vanish module) {
      super();
      this.module = module;
   }

   @EventHandler(
      priority = EventPriority.LOWEST,
      ignoreCancelled = true
   )
   public void onAsyncChat(AsyncChatEvent event) {
      Player player = event.getPlayer();
      if (this.module.isVanished(player)) {
         event.viewers().removeIf((audience) -> {
            if (audience instanceof Player viewer) {
               UUID viewerId = viewer.getUniqueId();
               return !this.module.canSeeVanish(viewerId);
            } else {
               return false;
            }
         });
         this.module.log("Chat message from " + player.getName() + " hidden (vanished)");
      }
   }
}
