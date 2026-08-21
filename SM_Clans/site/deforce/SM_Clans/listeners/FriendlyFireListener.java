package site.deforce.SM_Clans.listeners;

import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.listener.BaseListener;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import site.deforce.SM_Clans.SM_Clans;
import site.deforce.SM_Clans.models.Clan;

public class FriendlyFireListener extends BaseListener {
   private final SM_Clans module;

   public FriendlyFireListener(DoAPI plugin, SM_Clans module) {
      super(plugin);
      this.module = module;
   }

   @EventHandler
   public void onEntityDamage(EntityDamageByEntityEvent event) {
      if (event.getEntity() instanceof Player) {
         Player victim = (Player)event.getEntity();
         Player damager = null;
         if (event.getDamager() instanceof Player) {
            damager = (Player)event.getDamager();
         } else if (event.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile)event.getDamager();
            if (projectile.getShooter() instanceof Player) {
               damager = (Player)projectile.getShooter();
            }
         }

         if (damager != null) {
            Clan damagerClan = this.module.getClanManager().getPlayerClan(damager.getUniqueId());
            Clan victimClan = this.module.getClanManager().getPlayerClan(victim.getUniqueId());
            if (damagerClan != null && victimClan != null && damagerClan.getClanId().equals(victimClan.getClanId()) && !damagerClan.isFriendlyFire()) {
               event.setCancelled(true);
            }

         }
      }
   }
}
