package site.deforce.SMPS.modules.vanish.listeners;

import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import java.util.Iterator;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockReceiveGameEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.metadata.FixedMetadataValue;
import site.deforce.SMPS.modules.vanish.SM_Vanish;

public class VanishListener implements Listener {
   private final SM_Vanish module;

   public VanishListener(SM_Vanish module) {
      super();
      this.module = module;
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onPlayerInteract(PlayerInteractEvent event) {
      if (event.getAction() == Action.PHYSICAL && this.module.isVanished(event.getPlayer())) {
         event.setCancelled(true);
      }

   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
      if (this.module.isVanished(event.getPlayer())) {
         event.setCancelled(true);
      }

   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onPlayerJoinSynchronousMetadata(PlayerJoinEvent event) {
      Player joiningPlayer = event.getPlayer();
      this.module.loadVanishStateSync(joiningPlayer);
      this.module.loadTabVisibilityOverrideSync(joiningPlayer);
      this.module.loadIncognitoStateSync(joiningPlayer);
      this.module.loadNameTagVisibilityStateSync(joiningPlayer);
      if (this.module.shouldSuppressPresenceMessages(joiningPlayer)) {
         joiningPlayer.setMetadata("vanished", new FixedMetadataValue(this.module.getHostPlugin(), true));
         event.joinMessage((Component)null);
         if (this.module.isVanished(joiningPlayer)) {
            this.module.applyListedState(joiningPlayer, false);
         }

         this.module.log("Join message suppressed (hidden state synchronous metadata applied): " + joiningPlayer.getName());
      }

   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onPlayerJoin(PlayerJoinEvent event) {
      Player joiningPlayer = event.getPlayer();
      this.module.updateSeePermission(joiningPlayer);
      if (this.module.isVanished(joiningPlayer)) {
         event.joinMessage((Component)null);

         for(Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(joiningPlayer) && !online.hasPermission("smvanish.see") && !online.isOp()) {
               this.module.hidePlayerFromViewer(online, joiningPlayer);
            }
         }
      } else if (this.module.shouldSuppressPresenceMessages(joiningPlayer)) {
         event.joinMessage((Component)null);
      }

      for(Player online : Bukkit.getOnlinePlayers()) {
         if (!online.equals(joiningPlayer)) {
            if (this.module.isVanished(online) && !joiningPlayer.hasPermission("smvanish.see") && !joiningPlayer.isOp()) {
               this.module.hidePlayerFromViewer(joiningPlayer, online);
            }

            if (!this.module.isTabVisible(online) && !this.module.isVanished(online) && !joiningPlayer.hasPermission("smvanish.incognito.see")) {
               this.module.scheduleEntityDelayedTask(joiningPlayer, () -> {
                  if (joiningPlayer.isOnline() && online.isOnline()) {
                     this.module.sendTabListUnlistPacket(joiningPlayer, online, false);
                  }

               }, 10L);
            }
         }
      }

      this.module.scheduleEntityDelayedTask(joiningPlayer, () -> {
         if (joiningPlayer.isOnline()) {
            if (this.module.isVanished(joiningPlayer)) {
               this.module.applyVanishEffects(joiningPlayer);
            } else if (this.module.isDebugBothHidden(joiningPlayer.getUniqueId())) {
               this.module.updateTabVisibility(joiningPlayer);
               this.module.enforceHiddenNameTag(joiningPlayer);
            } else if (this.module.isDebugNameTagHidden(joiningPlayer)) {
               this.module.enforceHiddenNameTag(joiningPlayer);
            }

         }
      }, 1L);
      if (this.module.isIncognito(joiningPlayer)) {
         this.module.scheduleEntityDelayedTask(joiningPlayer, () -> {
            if (joiningPlayer.isOnline()) {
               this.module.applyIncognitoEffects(joiningPlayer);
            }
         }, 1L);
      }

   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onPlayerQuitLowest(PlayerQuitEvent event) {
      Player player = event.getPlayer();
      if (this.module.shouldSuppressPresenceMessages(player)) {
         event.quitMessage((Component)null);
         player.setMetadata("vanished", new FixedMetadataValue(this.module.getHostPlugin(), true));
         this.module.log("Silent quit synchronous (hidden state): " + player.getName());
      }

   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onPlayerQuit(PlayerQuitEvent event) {
      Player player = event.getPlayer();
      this.module.removeFromSeeList(player.getUniqueId());
      this.module.evictTabVisibilityOverrideCache(player.getUniqueId());
      this.module.saveVanishStateSync(player);
      this.module.saveIncognitoStateSync(player);
      this.module.saveNameTagVisibilityStateSync(player);
      if (this.module.shouldSuppressPresenceMessages(player)) {
         event.quitMessage((Component)null);
      }

   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onPlayerJoinMonitor(PlayerJoinEvent event) {
      if (this.module.shouldSuppressPresenceMessages(event.getPlayer())) {
         event.joinMessage((Component)null);
      }

   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onPlayerQuitMonitor(PlayerQuitEvent event) {
      if (this.module.shouldSuppressPresenceMessages(event.getPlayer())) {
         event.quitMessage((Component)null);
      }

   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onPlayerDeath(PlayerDeathEvent event) {
      Player player = event.getEntity();
      if (this.module.isVanished(player) || this.module.isIncognito(player)) {
         event.deathMessage((Component)null);
         player.setMetadata("vanished", new FixedMetadataValue(this.module.getHostPlugin(), true));
         this.module.log("Silent death (vanish/incognito): " + player.getName());
      }

   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onEntityTarget(EntityTargetEvent event) {
      Entity target = event.getTarget();
      if (target instanceof Player player) {
         if (this.module.isVanished(player)) {
            event.setCancelled(true);
         }
      }

   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onEntityDamage(EntityDamageEvent event) {
      Entity entity = event.getEntity();
      if (entity instanceof Player player) {
         if (this.module.isVanished(player)) {
            event.setCancelled(true);
         }
      }

   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onFoodLevelChange(FoodLevelChangeEvent event) {
      HumanEntity var3 = event.getEntity();
      if (var3 instanceof Player player) {
         if (this.module.isVanished(player)) {
            event.setCancelled(true);
            if (player.getFoodLevel() < 20) {
               player.setFoodLevel(20);
            }
         }
      }

   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onEntityPickupItem(EntityPickupItemEvent event) {
      LivingEntity var3 = event.getEntity();
      if (var3 instanceof Player player) {
         if (this.module.isVanished(player)) {
            event.setCancelled(true);
         }
      }

   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onGameModeChange(PlayerGameModeChangeEvent event) {
      Player player = event.getPlayer();
      if (this.module.isVanished(player)) {
         if (event.getNewGameMode() == GameMode.SPECTATOR) {
            this.module.scheduleEntityDelayedTask(player, () -> {
               if (player.isOnline()) {
                  for(Player other : Bukkit.getOnlinePlayers()) {
                     if (!other.equals(player) && !other.hasPermission("smvanish.see")) {
                        this.module.hidePlayerFromViewer(other, player);
                     }
                  }

                  this.module.updateTabVisibility(player);
               }

            }, 1L);
            return;
         }

         this.module.scheduleEntityDelayedTask(player, () -> {
            if (player.isOnline() && this.module.isVanished(player)) {
               this.module.restoreVanishSettings(player);
               this.module.log("Vanish settings restored for " + player.getName() + " after gamemode change");
            }

         }, 1L);
      }

   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onPlayerAdvancementDone(PlayerAdvancementCriterionGrantEvent event) {
      if (this.module.isVanished(event.getPlayer()) || this.module.isIncognito(event.getPlayer())) {
         event.setCancelled(true);
      }

   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
      if (this.module.isVanished(event.getPlayer()) || this.module.isIncognito(event.getPlayer())) {
         event.message((Component)null);
      }

   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onBlockReceiveGameEvent(BlockReceiveGameEvent event) {
      Entity var3 = event.getEntity();
      if (var3 instanceof Player player) {
         if (this.module.isVanished(player)) {
            event.setCancelled(true);
         }
      }

   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onServerListPing(ServerListPingEvent event) {
      int hiddenCount = 0;

      for(Player online : Bukkit.getOnlinePlayers()) {
         if (!this.module.isTabVisible(online) || this.module.isIncognito(online)) {
            ++hiddenCount;
         }
      }

      if (hiddenCount > 0) {
         Iterator<Player> iterator = event.iterator();

         while(iterator.hasNext()) {
            Player p = (Player)iterator.next();
            if (!this.module.isTabVisible(p) || this.module.isIncognito(p)) {
               iterator.remove();
            }
         }
      }

   }

   @EventHandler
   public void onPlayerHit(EntityDamageEvent event) {
      Entity var3 = event.getEntity();
      if (var3 instanceof Player player) {
         if (this.module.isHitVanishEnabled(player) && !this.module.isVanished(player)) {
            SM_Vanish.HitVanishData data = this.module.getHitVanishData(player);
            if (data != null) {
               double maxHealth;
               try {
                  AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
                  if (attr != null) {
                     maxHealth = attr.getValue();
                  } else {
                     maxHealth = 20.0;
                  }
               } catch (Throwable var8) {
                  maxHealth = 20.0;
               }

               double expectedHealth = player.getHealth() - event.getFinalDamage();
               if (expectedHealth <= maxHealth - data.damageThreshold) {
                  event.setCancelled(true);
                  this.module.triggerHitVanish(player);
                  this.module.toggleSilentVanish(player);
               }
            }
         }
      }

   }
}
