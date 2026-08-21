package site.deforce.SM_Clans.listeners;

import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.listener.BaseListener;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import site.deforce.SM_Clans.SM_Clans;
import site.deforce.SM_Clans.models.Clan;

public class ClanDamageListener extends BaseListener {
   private final SM_Clans module;

   public ClanDamageListener(DoAPI plugin, SM_Clans module) {
      super(plugin);
      this.module = module;
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onEntityDamage(EntityDamageByEntityEvent event) {
      if (event.getEntity() instanceof Player) {
         Player victim = (Player)event.getEntity();
         Player attacker = null;
         if (event.getDamager() instanceof Player) {
            attacker = (Player)event.getDamager();
         } else if (event.getDamager() instanceof Projectile && ((Projectile)event.getDamager()).getShooter() instanceof Player) {
            attacker = (Player)((Projectile)event.getDamager()).getShooter();
         }

         if (attacker != null && !attacker.equals(victim)) {
            Clan victimClan = this.module.getClanManager().getPlayerClan(victim.getUniqueId());
            Clan attackerClan = this.module.getClanManager().getPlayerClan(attacker.getUniqueId());
            if (victimClan != null && attackerClan != null && victimClan.getClanId().equals(attackerClan.getClanId()) && !victimClan.isFriendlyFire()) {
               event.setCancelled(true);
               attacker.sendMessage(this.module.getMenuManager().getMessage("friendly-fire-disabled"));
            }

         }
      }
   }
}
