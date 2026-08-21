package net.schalker.SMPS.modules.cosmetics.listeners;

import net.schalker.SMPS.modules.cosmetics.models.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.GameMode;
import net.schalker.DoAPI.DoAPI;
import net.schalker.SMPS.modules.cosmetics.CosmeticsModule;
import net.schalker.SMPS.modules.cosmetics.models.*;

import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 *    
 *       ProCosmetics
 */
public class CosmeticsListener implements Listener {
    private final DoAPI plugin;
    private final CosmeticsModule module;
    
    //     (UUID  -> UUID )
    private final Map<UUID, UUID> arrowOwners = new ConcurrentHashMap<>();
    //     (UUID  -> UUID )
    private final Map<UUID, UUID> tridentOwners = new ConcurrentHashMap<>();
    //     (UUID  -> task name)
    private final Map<UUID, String> riptideTrailTasks = new ConcurrentHashMap<>();
    private final Map<UUID, MaceHitRecord> lastMaceHits = new ConcurrentHashMap<>();
    private final Set<UUID> vanishedPlayers = ConcurrentHashMap.newKeySet();

    public CosmeticsListener(DoAPI plugin, CosmeticsModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    /**
     *        Spectator
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() == GameMode.SPECTATOR) {
            module.getUserCosmeticsManager().unequipAllTemporary(event.getPlayer());
        } else if (event.getPlayer().getGameMode() == GameMode.SPECTATOR) {
            //     Spectator  -  
            module.getUserCosmeticsManager().reequipSavedCosmetics(event.getPlayer(), true);
        }
    }

    /**
     *       (/vanish, /v)
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage().toLowerCase(Locale.ROOT);

        // Fallback when Brigadier registration failed in current lifecycle context.
        if (!module.isCommandRegistered() && message.startsWith("/")) {
            String commandLine = message.substring(1).trim();
            if (!commandLine.isEmpty()) {
                String[] args = commandLine.split("\\s+");
                String label = args[0];
                if (label.equals("cosmetics") || label.equals("cos") || label.equals("cosmetic")) {
                    event.setCancelled(true);
                    Player player = event.getPlayer();

                    if (!player.hasPermission("SMPS.cosmetics")) {
                        module.getMessageManager().send(player, "general.no-permission");
                        return;
                    }

                    if (args.length == 1 || args[1].equals("menu")) {
                        if (!module.ensureReady() || module.getMenuManager() == null) {
                            player.sendMessage("§eCosmetics module is still loading, try again in a moment.");
                            return;
                        }
                        try {
                            module.getMenuManager().openMainMenu(player);
                        } catch (Throwable throwable) {
                            player.sendMessage("§eCosmetics are temporarily unavailable.");
                            this.plugin.getDebugSystem().log("CosmeticsListener", "Suppressed fallback menu exception: " + throwable.getClass().getSimpleName());
                        }
                        return;
                    }

                    if (args[1].equals("clear")) {
                        module.getUserCosmeticsManager().unequipAll(player);
                        module.getMessageManager().send(player, "unequip.all-cleared");
                        return;
                    }

                    if (args[1].equals("reload")) {
                        if (!player.hasPermission("SMPS.cosmetics.admin")) {
                            module.getMessageManager().send(player, "general.no-permission");
                            return;
                        }
                        try {
                            module.reload();
                            module.getMessageManager().send(player, "general.reloaded");
                        } catch (Exception exception) {
                            player.sendMessage("§cCosmetics reload failed. Check console.");
                            this.plugin.getLogger().severe("Cosmetics fallback reload failed: " + exception.getMessage());
                            this.plugin.getDebugSystem().logError("Cosmetics fallback reload failed", exception);
                        }
                        return;
                    }

                    module.getMessageManager().send(player, "general.fallback-help");
                    return;
                }
            }
        }

        if (message.startsWith("/vanish") || message.startsWith("/v ") || message.equals("/v") || message.startsWith("/gv") || message.startsWith("/ev") || message.startsWith("/sv") || message.startsWith("/silentvanish")) {
            Player player = event.getPlayer();
            this.plugin.getSchedulerManager().runTaskLater(player, () -> {
                if (!player.isOnline()) {
                    return;
                }
                UUID playerId = player.getUniqueId();
                if (this.shouldEnableVanish(message, playerId)) {
                    this.vanishedPlayers.add(playerId);
                    this.module.getUserCosmeticsManager().unequipAllTemporary(player);
                } else {
                    this.vanishedPlayers.remove(playerId);
                    this.module.getUserCosmeticsManager().reequipSavedCosmeticsForce(player);
                }
            }, 2L);
        }
    }

    private boolean shouldEnableVanish(String commandMessage, UUID playerId) {
        String[] split = commandMessage.trim().split("\\s+");
        String arg = split.length > 1 ? split[1].toLowerCase(Locale.ROOT) : "";
        if (arg.equals("off") || arg.equals("disable") || arg.equals("0") || arg.equals("false")) {
            return false;
        }
        if (arg.equals("on") || arg.equals("enable") || arg.equals("1") || arg.equals("true")) {
            return true;
        }
        return !this.vanishedPlayers.contains(playerId);
    }

    private boolean isPlayerCosmeticsHidden(Player player) {
        if (player.getGameMode() == GameMode.SPECTATOR || player.isInvisible()) {
            return true;
        }
        for (Player other : this.plugin.getServer().getOnlinePlayers()) {
            if (other.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            if (!other.canSee(player)) {
                return true;
            }
        }
        return false;
    }
    
    // ============    (  ProCosmetics) ============
    
    /**
     *     
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (isCosmeticEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /**
     *      (   ).
     */
    // Cancel cosmetic parrots dropped from player shoulders
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SHOULDER_ENTITY) {
            if (PetCosmetic.isCosmeticParrot(event.getEntity())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityKnockback(EntityKnockbackEvent event) {
        if (isCosmeticEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }
    
    /**
     *      /
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        Entity damager = event.getDamager();
        
        // ?????? ????????????? ?????????
        if (isCosmeticEntity(entity) || isCosmeticEntity(damager)) {
            event.setCancelled(true);
            return;
        }

        if (!(entity instanceof LivingEntity victim)) {
            return;
        }
        if (!(damager instanceof Player attacker)) {
            return;
        }
        if (attacker.getInventory().getItemInMainHand().getType() != Material.MACE) {
            return;
        }

        this.lastMaceHits.put(victim.getUniqueId(), new MaceHitRecord(attacker.getUniqueId(), System.currentTimeMillis()));

        ArrowEffectCosmetic maceEffect = ArrowEffectCosmetic.getActiveMaceEffect(attacker.getUniqueId());
        if (maceEffect != null && attacker.getFallDistance() >= 1.5f) {
            UserCosmeticSettings settings = this.getSettings(attacker.getUniqueId());
            if (!this.canOwnerSeeCategory(attacker, CosmeticCategory.ARROW_EFFECT, settings)) {
                return;
            }
            Location impact = victim.getLocation().clone().add(0.0, 0.5, 0.0);
            this.plugin.getSchedulerManager().runRegionTaskLater(impact, "mace-impact-" + attacker.getUniqueId(), () -> {
                this.renderMaceImpactToAudience(attacker, maceEffect, impact);
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetEvent event) {
        if (isCosmeticEntity(event.getEntity()) || isCosmeticEntity(event.getTarget())) {
            event.setCancelled(true);
        }
    }
    
    /**
     * :    LivingEntity
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTargetLivingEntity(EntityTargetLivingEntityEvent event) {
        if (isCosmeticEntity(event.getEntity()) || isCosmeticEntity(event.getTarget())) {
            event.setCancelled(true);
        }
    }
    
    /**
     *    
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityCombust(EntityCombustEvent event) {
        if (isCosmeticEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }
    
    /**
     *    (    )
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockForm(EntityBlockFormEvent event) {
        if (isCosmeticEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }
    
    /**
     *    
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (isCosmeticEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }
    
    /**
     *    
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        if (isCosmeticEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }
    
    /**
     *   ( ->   ..)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTransform(EntityTransformEvent event) {
        if (isCosmeticEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }
    
    /**
     *   
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Allay) {
            return;
        }
        if (isCosmeticEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }
    
    /**
     *   
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDropItem(EntityDropItemEvent event) {
        if (isCosmeticEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }
    
    /**
     *    ()
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (isCosmeticEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }
    
    /**
     *    (, )
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEntity(PlayerBucketEntityEvent event) {
        if (isCosmeticEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }
    
    /**
     *   
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProjectileHitFish(ProjectileHitEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.FishHook) {
            Entity hitEntity = event.getHitEntity();
            if (hitEntity != null && isCosmeticEntity(hitEntity)) {
                event.getEntity().remove();
            }
        }
    }
    
    /**
     *   / 
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (isCosmeticEntity(event.getEntity()) || 
            (event.getRemover() != null && isCosmeticEntity(event.getRemover()))) {
            event.setCancelled(true);
        }
    }
    
    /**
     *    
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (isCosmeticEntity(event.getVehicle()) && !(event.getEntered() instanceof Player)) {
            event.setCancelled(true);
        }
    }
    
    /**
     *   
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        if (isCosmeticEntity(event.getVehicle())) {
            event.setCancelled(true);
        }
    }
    
    /**
     *   
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (isCosmeticEntity(event.getVehicle()) || 
            (event.getAttacker() != null && isCosmeticEntity(event.getAttacker()))) {
            event.setCancelled(true);
        }
    }
    
    /**
     *  ArmorStand'  
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (isCosmeticEntity(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }
    
    /**
     *      (  )
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractAtEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!isCosmeticEntity(entity)) return;
        
        //     (  ..)
        event.setCancelled(true);
    }
    
    /**
     *     (,   )
     *    (   )
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();
        
        if (!isCosmeticEntity(entity)) return;
        
        //    (,   ..)
        event.setCancelled(true);
        
        //  -  ?
        UUID petOwnerId = PetCosmetic.getOwnerOfPet(entity.getUniqueId());
        if (petOwnerId != null) {
            if (petOwnerId.equals(player.getUniqueId()) && this.tryRenamePetWithNameTag(player)) {
                event.setCancelled(true);
                return;
            }
            //  !
            // Shift+     ( )
            if (player.isSneaking() && petOwnerId.equals(player.getUniqueId()) 
                && entity instanceof org.bukkit.entity.Parrot) {
                PetCosmetic.parrotToShoulder(player);
            } else {
                //   - 
                PetCosmetic.petInteract(player, entity);
            }
            return;
        }
        
        //  -  leash entity ?
        UUID balloonOwnerId = BalloonCosmetic.getOwnerOfLeashEntity(entity.getUniqueId());
        if (balloonOwnerId != null) {
            //    -  
            event.setCancelled(true);
            return;
        }
    }
    
    /**
     *       
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerUnleash(PlayerUnleashEntityEvent event) {
        if (isCosmeticEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }
    
    /**
     *  / 
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerLeashEntity(PlayerLeashEntityEvent event) {
        if (isCosmeticEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }
    
    /**
     * ,    
     */
    private boolean isCosmeticEntity(Entity entity) {
        if (entity == null) return false;
        UUID entityId = entity.getUniqueId();
        
        //  
        for (UUID playerId : PetCosmetic.getAllActivePets()) {
            UUID petId = PetCosmetic.getActivePetId(org.bukkit.Bukkit.getPlayer(playerId));
            if (petId != null && petId.equals(entityId)) {
                return true;
            }
        }
        
        //  
        for (UUID playerId : BalloonCosmetic.getAllActiveBalloons()) {
            UUID balloonId = BalloonCosmetic.getActiveBalloonId(playerId);
            UUID leashId = BalloonCosmetic.getLeashEntityId(playerId);
            if ((balloonId != null && balloonId.equals(entityId)) ||
                (leashId != null && leashId.equals(entityId))) {
                return true;
            }
        }
        
        return false;
    }

    private boolean tryRenamePetWithNameTag(Player player) {
        if (player == null) {
            return false;
        }
        org.bukkit.inventory.ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() != Material.NAME_TAG) {
            return false;
        }
        if (!hand.hasItemMeta() || hand.getItemMeta() == null || !hand.getItemMeta().hasDisplayName()) {
            return false;
        }

        String rawName = hand.getItemMeta().getDisplayName();
        if (rawName == null || rawName.isBlank()) {
            return false;
        }

        PetCosmetic.setCustomPetName(player.getUniqueId(), rawName);
        this.module.getUserCosmeticsManager().savePlayerState(player);
        this.module.getMessageManager().send(player, "pet.rename.success", "name", rawName.replace('§', '&'));

        if (player.getGameMode() != GameMode.CREATIVE) {
            int amount = hand.getAmount();
            if (amount <= 1) {
                player.getInventory().setItemInMainHand(null);
            } else {
                hand.setAmount(amount - 1);
                player.getInventory().setItemInMainHand(hand);
            }
        }
        return true;
    }

    // ============   ============

    /**
     *   
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        //  Entity scheduler     Folia
        this.plugin.getSchedulerManager().runEntityTask(player, "cosmetics-join-" + player.getName(), () -> {
            //     
            this.module.getUserCosmeticsManager().getOrCreate(player.getUniqueId());
            this.module.getUserCosmeticsManager().getOrCreateSettings(player.getUniqueId());
            
            this.plugin.getDebugSystem().log("CosmeticsListener", 
                "Player " + player.getName() + " joined, cosmetics data initialized");
            
            //        
            this.plugin.getSchedulerManager().runEntityTaskLater(player, "cosmetics-load-" + player.getName(), () -> {
                if (player.isOnline()) {
                    this.module.getUserCosmeticsManager().loadPlayerCosmetics(player);
                }
            }, 20L); // 1      
        });
    }

    /**
     *   
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        this.vanishedPlayers.remove(playerId);
        
        //    ( , ..   )
        this.module.getUserCosmeticsManager().handlePlayerQuit(player);
        
        //  GUI 
        this.module.getMenuManager().closeMenu(playerId);
        
        //     (     -  )
        this.arrowOwners.entrySet().removeIf(entry -> entry.getValue().equals(playerId));
        //    
        this.tridentOwners.entrySet().removeIf(entry -> entry.getValue().equals(playerId));
        //   
        String riptideTask = this.riptideTrailTasks.remove(playerId);
        if (riptideTask != null) {
            this.plugin.getSchedulerManager().cancelTask(riptideTask);
        }
        
        this.plugin.getDebugSystem().log("CosmeticsListener", 
            "Player " + player.getName() + " left, cosmetics data cleared");
    }

    /**
     *    -   (  ProCosmetics)
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        Location deathLoc = victim.getLocation().clone();
        
        this.plugin.getDebugSystem().log("CosmeticsListener", "onPlayerDeath: victim=" + victim.getName() + ", killer=" + (killer != null ? killer.getName() : "null"));

        //  ID      
        // Arrow Effects  Death Effects    
        this.module.getUserCosmeticsManager().unequipTemporary(victim, CosmeticCategory.PET);
        this.module.getUserCosmeticsManager().unequipTemporary(victim, CosmeticCategory.BALLOON);
        // Particle Effects    -  
        
        //     ,   
        DeathEffectCosmetic victimEffect = DeathEffectCosmetic.getActiveEffect(victim.getUniqueId());
        if (victimEffect != null) {
            UserCosmeticSettings victimSettings = this.getSettings(victim.getUniqueId());
            this.plugin.getDebugSystem().log("CosmeticsListener", "Victim has effect: " + victimEffect.getName() + ", showOnKill=" + victimEffect.isShowOnKill());
            this.plugin.getSchedulerManager().runRegionTaskLater(deathLoc, "death-effect-" + victim.getUniqueId(), () -> {
                if (!this.canOwnerSeeCategory(victim, CosmeticCategory.DEATH_EFFECT, victimSettings)) {
                    return;
                }
                this.renderDeathEffectToAudience(victim, victimEffect, deathLoc);
            }, 1L);
        }
        
        //     ( showOnKill)
        if (killer != null) {
            DeathEffectCosmetic killerEffect = DeathEffectCosmetic.getActiveEffect(killer.getUniqueId());
            boolean maceKill = this.isMaceKill(victim, killer);
            this.lastMaceHits.remove(victim.getUniqueId());

            if (maceKill) {
                ArrowEffectCosmetic maceEffect = ArrowEffectCosmetic.getActiveMaceEffect(killer.getUniqueId());
                if (maceEffect != null) {
                    UserCosmeticSettings killerSettings = this.getSettings(killer.getUniqueId());
                    this.plugin.getSchedulerManager().runRegionTaskLater(deathLoc, "mace-hit-effect-" + killer.getUniqueId(), () -> {
                        if (!this.canOwnerSeeCategory(killer, CosmeticCategory.ARROW_EFFECT, killerSettings)) {
                            return;
                        }
                        this.renderMaceImpactToAudience(killer, maceEffect, deathLoc);
                    }, 1L);
                }
            }

            if (killerEffect != null) {
                this.plugin.getDebugSystem().log("CosmeticsListener", "Killer has effect: " + killerEffect.getName() + ", showOnKill=" + killerEffect.isShowOnKill());
                if (killerEffect.isShowOnKill()) {
                    boolean maceOnlyEffect = this.isMaceOnlyEffect(killerEffect);
                    if (maceOnlyEffect && !maceKill) {
                        this.plugin.getDebugSystem().log("CosmeticsListener", "Skipping mace-only effect: kill was not by mace");
                    } else {
                        UserCosmeticSettings killerSettings = this.getSettings(killer.getUniqueId());
                        this.plugin.getSchedulerManager().runRegionTaskLater(deathLoc, "kill-effect-" + killer.getUniqueId(), () -> {
                            this.plugin.getDebugSystem().log("CosmeticsListener", "Playing killer effect at " + deathLoc);
                            if (!this.canOwnerSeeCategory(killer, CosmeticCategory.DEATH_EFFECT, killerSettings)) {
                                return;
                            }
                            this.renderDeathEffectToAudience(killer, killerEffect, deathLoc);
                        }, 1L);
                    }
                }
            } else {
                 this.plugin.getDebugSystem().log("CosmeticsListener", "Killer has no active death effect");
            }
        }
    }

    private boolean isMaceOnlyEffect(DeathEffectCosmetic effect) {
        return effect != null && effect.getId().startsWith("mace_");
    }

    private boolean isMaceKill(Player victim, Player killer) {
        MaceHitRecord recent = this.lastMaceHits.get(victim.getUniqueId());
        if (recent != null) {
            boolean sameAttacker = recent.attackerId().equals(killer.getUniqueId());
            boolean freshHit = System.currentTimeMillis() - recent.atMillis() <= 5000L;
            if (sameAttacker && freshHit) {
                return true;
            }
        }

        EntityDamageEvent damageEvent = victim.getLastDamageCause();
        if (!(damageEvent instanceof EntityDamageByEntityEvent byEntity)) {
            return false;
        }
        if (!(byEntity.getDamager() instanceof Player damager) || !damager.getUniqueId().equals(killer.getUniqueId())) {
            return false;
        }
        return damager.getInventory().getItemInMainHand().getType() == Material.MACE;
    }

    private record MaceHitRecord(UUID attackerId, long atMillis) {}

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        
        // :  Folia  entity scheduler ,   global scheduler
        //           
        this.plugin.getSchedulerManager().runTaskLater(player, () -> {
            //  ( )   
            this.module.getUserCosmeticsManager().reequipSavedCosmetics(player, true);
        }, 10L); //    
    }

    // ============   ============
    
    /**
     *     -   
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getProjectile() instanceof Arrow arrow)) return;
        
        ArrowEffectCosmetic effect = ArrowEffectCosmetic.getActiveBowTrailEffect(player.getUniqueId());
        if (effect == null) return;
        
        this.arrowOwners.put(arrow.getUniqueId(), player.getUniqueId());
        this.startArrowTrailTask(player, arrow, effect);
    }
    
    /**
     *    ( )
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) return;
        if (!(arrow.getShooter() instanceof Player player)) return;
        
        if (this.arrowOwners.containsKey(arrow.getUniqueId())) return;
        
        ArrowEffectCosmetic effect = ArrowEffectCosmetic.getActiveBowTrailEffect(player.getUniqueId());
        if (effect == null) return;
        
        this.arrowOwners.put(arrow.getUniqueId(), player.getUniqueId());
        this.startArrowTrailTask(player, arrow, effect);
    }

    /**
     *    -   
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTridentLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Trident trident)) return;
        if (!(trident.getShooter() instanceof Player player)) return;

        ArrowEffectCosmetic effect = ArrowEffectCosmetic.getActiveTridentThrowTrailEffect(player.getUniqueId());
        if (effect == null) return;

        this.tridentOwners.put(trident.getUniqueId(), player.getUniqueId());
        this.startTridentTrailTask(player, trident, effect);
    }

    /**
     *    -   
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Arrow arrow) {
            UUID playerId = this.arrowOwners.remove(arrow.getUniqueId());
            if (playerId == null) return;

            ArrowEffectCosmetic effect = ArrowEffectCosmetic.getActiveBowHitEffect(playerId);
            if (effect == null) return;
            Player owner = this.plugin.getServer().getPlayer(playerId);
            if (owner == null || !owner.isOnline()) return;
            UserCosmeticSettings ownerSettings = this.getSettings(playerId);
            if (!this.canOwnerSeeCategory(owner, CosmeticCategory.ARROW_EFFECT, ownerSettings)) return;

            Location hitLoc = arrow.getLocation();
            this.plugin.getSchedulerManager().runTaskLater("arrow-hit-" + arrow.getUniqueId(), () -> {
                this.renderArrowHitToAudience(owner, effect, hitLoc);
            }, 1L);
            return;
        }

        if (event.getEntity() instanceof Trident trident) {
            UUID playerId = this.tridentOwners.remove(trident.getUniqueId());
            if (playerId == null) return;

            ArrowEffectCosmetic effect = ArrowEffectCosmetic.getActiveTridentThrowHitEffect(playerId);
            if (effect == null) return;
            Player owner = this.plugin.getServer().getPlayer(playerId);
            if (owner == null || !owner.isOnline()) return;
            UserCosmeticSettings ownerSettings = this.getSettings(playerId);
            if (!this.canOwnerSeeCategory(owner, CosmeticCategory.ARROW_EFFECT, ownerSettings)) return;

            Location hitLoc = trident.getLocation();
            this.plugin.getSchedulerManager().runTaskLater("trident-hit-" + trident.getUniqueId(), () -> {
                this.renderArrowHitToAudience(owner, effect, hitLoc);
            }, 1L);
        }
    }

    /**
     *     
     */
    private void startArrowTrailTask(Player owner, Arrow arrow, ArrowEffectCosmetic effect) {
        String taskName = "arrow-trail-" + arrow.getUniqueId();
        final int[] visualTicks = {0};
        
        //  Entity Scheduler     Folia
        this.plugin.getSchedulerManager().runEntityTaskTimer(arrow, taskName, () -> {
            if (!arrow.isValid() || arrow.isOnGround() || arrow.isDead()) {
                this.plugin.getSchedulerManager().cancelTask(taskName);
                this.arrowOwners.remove(arrow.getUniqueId());
                return;
            }
            
            UserCosmeticSettings settings = this.getSettings(owner.getUniqueId());
            if (!this.canOwnerSeeCategory(owner, CosmeticCategory.ARROW_EFFECT, settings)) {
                return;
            }
            if (settings.isReducedEffects() && (++visualTicks[0] % 2 != 0)) {
                return;
            }
            this.renderArrowTrailToAudience(owner, effect, arrow.getLocation());
        }, 1L, 1L);
    }

    /**
     *     
     */
    private void startTridentTrailTask(Player owner, Trident trident, ArrowEffectCosmetic effect) {
        String taskName = "trident-trail-" + trident.getUniqueId();
        final int[] visualTicks = {0};

        this.plugin.getSchedulerManager().runEntityTaskTimer(trident, taskName, () -> {
            if (!trident.isValid() || trident.isDead() || trident.isInBlock()) {
                this.plugin.getSchedulerManager().cancelTask(taskName);
                this.tridentOwners.remove(trident.getUniqueId());
                return;
            }

            UserCosmeticSettings settings = this.getSettings(owner.getUniqueId());
            if (!this.canOwnerSeeCategory(owner, CosmeticCategory.ARROW_EFFECT, settings)) {
                return;
            }
            if (settings.isReducedEffects() && (++visualTicks[0] % 2 != 0)) {
                return;
            }
            this.renderArrowTrailToAudience(owner, effect, trident.getLocation());
        }, 1L, 1L);
    }

    /**
     *   -   
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerRiptide(PlayerRiptideEvent event) {
        Player player = event.getPlayer();
        ArrowEffectCosmetic effect = ArrowEffectCosmetic.getActiveTridentRiptideEffect(player.getUniqueId());
        if (effect == null) return;

        String oldTask = this.riptideTrailTasks.remove(player.getUniqueId());
        if (oldTask != null) {
            this.plugin.getSchedulerManager().cancelTask(oldTask);
        }

        final String taskName = "riptide-trail-" + player.getUniqueId();
        this.riptideTrailTasks.put(player.getUniqueId(), taskName);
        final int[] ticks = {0};
        final int[] visualTicks = {0};

        this.plugin.getSchedulerManager().runEntityTaskTimer(player, taskName, () -> {
            if (!player.isOnline() || player.isDead() || ticks[0]++ > 45) {
                this.plugin.getSchedulerManager().cancelTask(taskName);
                this.riptideTrailTasks.remove(player.getUniqueId(), taskName);
                return;
            }

            UserCosmeticSettings settings = this.getSettings(player.getUniqueId());
            if (!this.canOwnerSeeCategory(player, CosmeticCategory.ARROW_EFFECT, settings)) {
                return;
            }
            if (settings.isReducedEffects() && (++visualTicks[0] % 2 != 0)) {
                return;
            }
            var velocity = player.getVelocity();
            var direction = velocity.lengthSquared() > 0.01 ? velocity.clone().normalize() : player.getLocation().getDirection().normalize();
            Location behind = player.getLocation().clone().subtract(direction.multiply(0.9));
            this.renderArrowTrailToAudience(player, effect, behind);
        }, 1L, 1L);
    }

    /**
     *   
     */
    private UserCosmeticSettings getSettings(UUID playerId) {
        UserCosmeticSettings settings = this.module.getUserCosmeticsManager().getSettings(playerId);
        if (settings == null) {
            settings = this.module.getUserCosmeticsManager().getOrCreateSettings(playerId);
        }
        return settings;
    }

    private boolean canOwnerSeeCategory(Player owner, CosmeticCategory category, UserCosmeticSettings settings) {
        if (owner == null || settings == null) {
            return false;
        }
        if (!settings.isCategoryEnabled(category)) {
            return false;
        }
        return settings.shouldSeeEffect(owner.getUniqueId(), category);
    }

    private java.util.List<Player> getEffectAudience(Player owner, CosmeticCategory category) {
        java.util.List<Player> viewers = new java.util.ArrayList<>();
        if (owner == null || !owner.isOnline()) {
            return viewers;
        }
        UserCosmeticSettings ownerSettings = this.getSettings(owner.getUniqueId());
        for (Player viewer : owner.getWorld().getPlayers()) {
            UserCosmeticSettings viewerSettings = this.getSettings(viewer.getUniqueId());
            if (!viewerSettings.shouldSeeEffect(owner.getUniqueId(), category)) {
                continue;
            }
            if (!ownerSettings.shouldViewerSeeMyEffect(viewer.getUniqueId(), category)) {
                continue;
            }
            viewers.add(viewer);
        }
        return viewers;
    }

    private void renderArrowTrailToAudience(Player owner, ArrowEffectCosmetic effect, Location location) {
        int count = Math.max(1, effect.getTrailCount() / 2);
        for (Player viewer : this.getEffectAudience(owner, CosmeticCategory.ARROW_EFFECT)) {
            this.spawnParticleForPlayer(viewer, effect.getTrailParticle(), location, count, 0.05, 0.05, 0.05, 0.0);
        }
    }

    private void renderArrowHitToAudience(Player owner, ArrowEffectCosmetic effect, Location location) {
        int count = Math.max(2, effect.getHitCount() / 2);
        double spread = Math.max(0.1, effect.getHitSpread() * 0.75);
        for (Player viewer : this.getEffectAudience(owner, CosmeticCategory.ARROW_EFFECT)) {
            this.spawnParticleForPlayer(viewer, effect.getHitParticle(), location, count, spread, spread, spread, 0.05);
            this.spawnParticleForPlayer(viewer, org.bukkit.Particle.CLOUD, location, Math.max(3, count / 3), spread * 0.8, 0.2, spread * 0.8, 0.01);
        }
    }

    private void renderMaceImpactToAudience(Player owner, ArrowEffectCosmetic effect, Location location) {
        for (Player viewer : this.getEffectAudience(owner, CosmeticCategory.ARROW_EFFECT)) {
            this.spawnParticleForPlayer(viewer, effect.getHitParticle(), location, 12, 0.35, 0.2, 0.35, 0.04);
            this.spawnParticleForPlayer(viewer, org.bukkit.Particle.CLOUD, location, 10, 0.3, 0.15, 0.3, 0.01);
            this.spawnParticleForPlayer(viewer, org.bukkit.Particle.FLASH, location, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private void renderDeathEffectToAudience(Player owner, DeathEffectCosmetic effect, Location location) {
        Location visualLoc = location.clone().add(0.0, 1.0, 0.0);
        int count = Math.max(6, effect.getParticleCount());
        double spread = Math.max(0.3, effect.getSpread());
        for (Player viewer : this.getEffectAudience(owner, CosmeticCategory.DEATH_EFFECT)) {
            this.spawnParticleForPlayer(viewer, effect.getParticle(), visualLoc, count, spread, spread, spread, 0.05);
            this.spawnParticleForPlayer(viewer, org.bukkit.Particle.CLOUD, visualLoc, Math.max(4, count / 4), spread * 0.7, 0.2, spread * 0.7, 0.01);
            this.spawnParticleForPlayer(viewer, org.bukkit.Particle.FLASH, visualLoc, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private void spawnParticleForPlayer(Player player, org.bukkit.Particle particle, Location location,
                                        int count, double offsetX, double offsetY, double offsetZ, double extra) {
        if (player == null || !player.isOnline() || location == null || location.getWorld() == null) {
            return;
        }
        try {
            player.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
        } catch (Exception ignored) {
            try {
                player.spawnParticle(org.bukkit.Particle.ENCHANTED_HIT, location, Math.max(1, count / 2), offsetX, offsetY, offsetZ, extra);
            } catch (Exception ignoredAgain) {
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        String toWorld = player.getWorld().getName();
        
        var config = this.module.getConfig();
        if (config != null) {
            var blacklistedWorlds = config.getStringList("blacklisted-worlds");
            
            if (blacklistedWorlds.contains(toWorld)) {
                this.module.getUserCosmeticsManager().unequipAll(player);
                
                this.plugin.getDebugSystem().log("CosmeticsListener", 
                    "Player " + player.getName() + " entered blocked world " + toWorld);
            }
        }
    }
    
    /**
     *   -     
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        
        //         - 
        if (to == null) return;
        if (from.getWorld().equals(to.getWorld()) && from.distanceSquared(to) < 128 * 128) {
            return;
        }
        
        Player player = event.getPlayer();
        
        //     
        this.plugin.getSchedulerManager().runTaskLater("teleport-balloon-" + player.getUniqueId(), () -> {
            BalloonCosmetic.respawnBalloon(player);
        }, 2L);
        
        //   -   
        this.plugin.getSchedulerManager().runTaskLater("teleport-pet-" + player.getUniqueId(), () -> {
            PetCosmetic.teleportPetToPlayer(player);
        }, 2L);
    }
}



