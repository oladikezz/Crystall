package net.schalker.SMPS.modules.voodoo.listeners;

import net.schalker.SMPS.modules.voodoo.VoodooItem;
import net.schalker.SMPS.modules.voodoo.VoodooModule;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles all Voodoo item interactions.
 */
public class VoodooListener implements Listener {
   private final VoodooModule module;
   /** Cooldown tracker: playerUUID -> last use millis */
   private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
   /** Separate cooldown for Shift+LMB teleport */
   private final Map<UUID, Long> shiftCooldowns = new ConcurrentHashMap<>();
   /** Players currently being force-looked (can't look away) */
   private final Set<UUID> forceLooking = ConcurrentHashMap.newKeySet();
   /** Players currently forced to lie down */
   private final Set<UUID> layingDown = ConcurrentHashMap.newKeySet();
   /**
    * Drop guard — when a player presses Q with a voodoo item, the server fires
    * PlayerDropItemEvent (cancelled) AND PlayerInteractEvent (LEFT_CLICK_AIR).
    */
   private final Set<UUID> recentDropAttempts = ConcurrentHashMap.newKeySet();

   // -- Vanish integration (via SMPS ModuleManager)
   private Object vanishInstance;
   private Method vanishIsVanishedMethod;
   private boolean vanishResolved = false;

   public VoodooListener(VoodooModule module) {
      this.module = module;
      // Reset vanish cache on new listener instance (module reload/hotswap)
      vanishResolved = false;
      vanishInstance = null;
      vanishIsVanishedMethod = null;
   }

   /**
    * Get the human-readable effect name from messages.yml.
    * Falls back to the effect key itself if not configured.
    */
   private String getEffectName(String effectKey) {
      return this.module.getMessage("voodoo.effect." + effectKey, effectKey);
   }

   // ================================================================
   // Vanish check (via SMPS ModuleManager -- SM_Vanish is an SMPS module)
   // ================================================================
   private boolean isVanished(Player player) {
      if (!vanishResolved) {
         try {
            // Get SM_Vanish module through SMPS ModuleManager (same classloader)
            Object smVanishModule = this.module.getSmps().getModuleManager().getModule("SM_Vanish");
            if (smVanishModule == null) return false; // Not loaded yet, retry next call

            // Get the singleton instance via static getInstance()
            Method getInstanceMethod = smVanishModule.getClass().getMethod("getInstance");
            vanishInstance = getInstanceMethod.invoke(null);
            if (vanishInstance == null) return false;

            // Cache the isVanished(Player) method
            vanishIsVanishedMethod = vanishInstance.getClass().getMethod("isVanished", Player.class);
            vanishResolved = true;
         } catch (Exception ignored) {
            return false;
         }
      }
      if (vanishInstance == null || vanishIsVanishedMethod == null) return false;
      try {
         return (boolean) vanishIsVanishedMethod.invoke(vanishInstance, player);
      } catch (Exception e) {
         // Instance may be stale (SM_Vanish reloaded) -- reset and retry next time
         vanishResolved = false;
         vanishInstance = null;
         vanishIsVanishedMethod = null;
         return false;
      }
   }

   /**
    * Resolve the target player, taking vanish into account.
    * Returns null if the target is offline or effectively invisible to the owner.
    */
   private Player resolveTarget(Player owner, String targetName) {
      Player target = Bukkit.getPlayerExact(targetName);
      if (target == null || !target.isOnline()) return null;
      // If target is vanished and owner can't see vanished players -> treat as offline
      if (isVanished(target) && !owner.hasPermission("smvanish.see")) {
         return null;
      }
      return target;
   }

   // ================================================================
   // Patch Voodoo items on join
   // ================================================================
   @EventHandler(priority = EventPriority.MONITOR)
   public void onPlayerJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      this.module.getSmps().getSchedulerManager().runEntityTaskLater(
         player, "voodoo-patch-join-" + player.getUniqueId(),
         () -> patchPlayerInventory(player), 5L);
   }

   public void patchPlayerInventory(Player player) {
      if (!player.isOnline()) return;
      ConfigurationSection modelsSection =
         this.module.getModuleConfig().getConfigurationSection("models");
      int size = player.getInventory().getSize();
      int patched = 0;
      for (int i = 0; i < size; i++) {
         ItemStack item = player.getInventory().getItem(i);
         if (item != null && VoodooItem.isVoodoo(item)) {
            ItemStack clone = item.clone();
            if (VoodooItem.updateCustomModelData(clone, modelsSection)) {
               player.getInventory().setItem(i, clone);
               patched++;
            }
         }
      }
      if (patched > 0) {
         this.module.getSmps().getDebugSystem().log("Voodoo",
            "Patched " + patched + " voodoo item(s) for " + player.getName());
      }
   }

   // ================================================================
   // Disable totem resurrection for Voodoo items
   // ================================================================
   @EventHandler(priority = EventPriority.HIGHEST)
   public void onResurrect(EntityResurrectEvent event) {
      if (!(event.getEntity() instanceof Player player)) return;
      ItemStack mainHand = player.getInventory().getItemInMainHand();
      ItemStack offHand = player.getInventory().getItemInOffHand();
      if (VoodooItem.isVoodoo(mainHand) || VoodooItem.isVoodoo(offHand)) {
         event.setCancelled(true);
      }
   }

   // ================================================================
   // Prevent Voodoo items from being dropped on death
   // ================================================================
   @EventHandler(priority = EventPriority.HIGHEST)
   public void onDeath(PlayerDeathEvent event) {
      event.getDrops().removeIf(VoodooItem::isVoodoo);
      Player player = event.getEntity();
      List<ItemStack> kept = new ArrayList<>();
      for (ItemStack item : player.getInventory().getContents()) {
         if (VoodooItem.isVoodoo(item)) {
            kept.add(item.clone());
         }
      }
      if (!kept.isEmpty()) {
         this.module.getSmps().getSchedulerManager().runEntityTaskLater(
            player, "voodoo-keep-" + player.getUniqueId(),
            () -> {
               if (!player.isOnline()) return;
               for (ItemStack voodoo : kept) {
                  if (player.getInventory().firstEmpty() != -1) {
                     player.getInventory().addItem(voodoo);
                  }
               }
            }, 5L);
      }
   }

   // ================================================================
   // Prevent dropping
   // ================================================================
   @EventHandler(priority = EventPriority.HIGHEST)
   public void onDrop(PlayerDropItemEvent event) {
      if (VoodooItem.isVoodoo(event.getItemDrop().getItemStack())) {
         event.setCancelled(true);
         UUID uuid = event.getPlayer().getUniqueId();
         this.recentDropAttempts.add(uuid);
         this.module.getSmps().getSchedulerManager().runEntityTaskLater(
            event.getPlayer(), "voodoo-drop-guard-" + uuid,
            () -> this.recentDropAttempts.remove(uuid), 2L);
      }
   }

   // ================================================================
   // Prevent placing in item frames
   // ================================================================
   @EventHandler(priority = EventPriority.HIGHEST)
   public void onInteractEntity(PlayerInteractEntityEvent event) {
      Player player = event.getPlayer();
      ItemStack item = player.getInventory().getItemInMainHand();
      if (VoodooItem.isVoodoo(item) && event.getRightClicked().getType().name().contains("ITEM_FRAME")) {
         event.setCancelled(true);
      }
   }

   // ================================================================
   // Prevent moving to containers
   // ================================================================
   @EventHandler(priority = EventPriority.HIGHEST)
   public void onInventoryClick(InventoryClickEvent event) {
      if (!(event.getWhoClicked() instanceof Player player)) return;
      InventoryType topType = event.getView().getTopInventory().getType();
      if (topType == InventoryType.PLAYER || topType == InventoryType.ENDER_CHEST || topType == InventoryType.CRAFTING) {
         return;
      }
      if (event.getHotbarButton() >= 0) {
         ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
         if (VoodooItem.isVoodoo(hotbarItem)
            && event.getClickedInventory() != null
            && event.getClickedInventory().getType() != InventoryType.PLAYER) {
            event.setCancelled(true);
            return;
         }
      }
      ItemStack clicked = event.getCurrentItem();
      ItemStack cursor = event.getCursor();
      boolean clickedIsVoodoo = VoodooItem.isVoodoo(clicked);
      boolean cursorIsVoodoo = VoodooItem.isVoodoo(cursor);
      if (!clickedIsVoodoo && !cursorIsVoodoo) return;
      if (cursorIsVoodoo && event.getClickedInventory() != null
         && event.getClickedInventory().getType() != InventoryType.PLAYER) {
         event.setCancelled(true);
         return;
      }
      if (clickedIsVoodoo && event.isShiftClick()
         && event.getClickedInventory() != null
         && event.getClickedInventory().getType() == InventoryType.PLAYER) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onInventoryDrag(InventoryDragEvent event) {
      if (!(event.getWhoClicked() instanceof Player)) return;
      if (!VoodooItem.isVoodoo(event.getOldCursor())) return;
      InventoryType topType = event.getView().getTopInventory().getType();
      if (topType == InventoryType.PLAYER || topType == InventoryType.ENDER_CHEST || topType == InventoryType.CRAFTING) {
         return;
      }
      int topSize = event.getView().getTopInventory().getSize();
      for (int slot : event.getRawSlots()) {
         if (slot < topSize) {
            event.setCancelled(true);
            return;
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onSwapHands(PlayerSwapHandItemsEvent event) {
      // Allow swap no restriction
   }

   // ================================================================
   // LMB / Shift+LMB / RMB Apply effects
   // ================================================================
   @EventHandler(priority = EventPriority.NORMAL)
   public void onInteract(PlayerInteractEvent event) {
      Player player = event.getPlayer();
      ItemStack item = player.getInventory().getItemInMainHand();
      if (!VoodooItem.isVoodoo(item)) return;

      // Only process main hand — RIGHT_CLICK_BLOCK fires for both hands
      if (event.getHand() != EquipmentSlot.HAND) return;

      Action action = event.getAction();

      boolean isLeftClick = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
      boolean isRightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;

      if (!isLeftClick && !isRightClick) return;

      if (isRightClick) event.setCancelled(true);

      // If the player just tried to drop (Q key), ignore this LMB event
      if (isLeftClick && this.recentDropAttempts.remove(player.getUniqueId())) {
         return;
      }

      String targetName = VoodooItem.getTarget(item);
      String ownerName = VoodooItem.getOwner(item);
      if (targetName == null || ownerName == null) return;

      // Only the owner can use it
      if (!player.getName().equalsIgnoreCase(ownerName)) {
         event.setCancelled(true);
         return;
      }

      FileConfiguration config = this.module.getModuleConfig();

      // Shift+LMB Teleport
      if (isLeftClick && player.isSneaking()) {
         handleShiftTeleport(player, targetName, config);
         return;
      }

      // Normal LMB / RMB Effects
      int cooldownSec = config.getInt("settings.cooldown-seconds", 10);
      long now = System.currentTimeMillis();
      Long lastUse = this.cooldowns.get(player.getUniqueId());
      if (lastUse != null) {
         long remaining = (lastUse + cooldownSec * 1000L) - now;
         if (remaining > 0) {
            int secLeft = (int) Math.ceil(remaining / 1000.0);
            player.sendMessage(this.module.getMessage("voodoo.owner-cooldown",
               "&cCooldown! Wait &e{seconds} &csec.")
               .replace("{seconds}", String.valueOf(secLeft)));
            return;
         }
      }

      Player target = resolveTarget(player, targetName);
      if (target == null) {
         player.sendMessage(this.module.getMessage("voodoo.owner-target-offline",
            "&cPlayer &e{target} &cis not online!")
            .replace("{target}", targetName));
         return;
      }

      int maxDistance = config.getInt("settings.max-distance", 0);
      if (maxDistance > 0) {
         if (!player.getWorld().equals(target.getWorld())
            || player.getLocation().distance(target.getLocation()) > maxDistance) {
            player.sendMessage(this.module.getMessage("voodoo.owner-too-far",
               "&cPlayer &e{target} &cis too far!")
               .replace("{target}", targetName));
            return;
         }
      }

      this.cooldowns.put(player.getUniqueId(), now);

      if (isLeftClick) {
         applyNegativeEffect(player, target, config);
      } else {
         applyPositiveEffect(player, target, config);
      }
   }

   // ================================================================
   // Shift+LMB — Teleport target to owner
   // ================================================================
   private void handleShiftTeleport(Player owner, String targetName, FileConfiguration config) {
      if (!config.getBoolean("shift-ability.enabled", true)) return;

      int cooldownSec = config.getInt("shift-ability.cooldown-seconds", 60);
      long now = System.currentTimeMillis();
      Long lastUse = this.shiftCooldowns.get(owner.getUniqueId());
      if (lastUse != null) {
         long remaining = (lastUse + cooldownSec * 1000L) - now;
         if (remaining > 0) {
            int secLeft = (int) Math.ceil(remaining / 1000.0);
            owner.sendMessage(this.module.getMessage("voodoo.owner-shift-cooldown",
               "&cTeleport cooldown! Wait &e{seconds} &csec.")
               .replace("{seconds}", String.valueOf(secLeft)));
            return;
         }
      }

      Player target = resolveTarget(owner, targetName);
      if (target == null) {
         owner.sendMessage(this.module.getMessage("voodoo.owner-target-offline",
            "&cPlayer &e{target} &cis not online!")
            .replace("{target}", targetName));
         return;
      }

      this.shiftCooldowns.put(owner.getUniqueId(), now);
      Location destination = owner.getLocation();

      this.module.getSmps().getSchedulerManager().runEntityTask(target, "voodoo-tp-" + target.getName(), () -> {
         if (!target.isOnline()) return;
         target.teleportAsync(destination);
         target.sendMessage(this.module.getMessage("voodoo.target-shift-teleport",
            "&5! &dYou were teleported by a voodoo doll!"));
      });

      owner.sendMessage(this.module.getMessage("voodoo.owner-shift-teleport",
         "&5! &dYou teleported &e{target} &dto yourself!")
         .replace("{target}", targetName));
   }

   // ================================================================
   // Negative effects
   // ================================================================
   private void applyNegativeEffect(Player owner, Player target, FileConfiguration config) {
      ConfigurationSection section = config.getConfigurationSection("negative-effects");
      if (section == null) return;

      double minHealth = config.getDouble("settings.min-target-health", 2.0);
      double statusChance = section.getDouble("status-effect-chance", 0.5);

      // Roll: status effect or special effect?
      boolean rollStatus = ThreadLocalRandom.current().nextDouble() < statusChance;

      // Collect effects from the chosen pool
      String poolKey = rollStatus ? "status-effects" : "special-effects";
      ConfigurationSection poolSection = section.getConfigurationSection(poolKey);

      // Fallback: if the chosen pool is empty/missing, try the other one
      if (poolSection == null || poolSection.getKeys(false).isEmpty()) {
         poolKey = rollStatus ? "special-effects" : "status-effects";
         poolSection = section.getConfigurationSection(poolKey);
         if (poolSection == null) return;
         rollStatus = !rollStatus;
      }

      // Check if target has a droppable item in either hand (for filtering drop-item)
      ItemStack targetMain = target.getInventory().getItemInMainHand();
      ItemStack targetOff = target.getInventory().getItemInOffHand();
      boolean canDropMain = targetMain.getType() != Material.AIR && !VoodooItem.isVoodoo(targetMain);
      boolean canDropOff = targetOff.getType() != Material.AIR && !VoodooItem.isVoodoo(targetOff);
      boolean canDrop = canDropMain || canDropOff;

      List<EffectEntry> effects = new ArrayList<>();
      for (String key : poolSection.getKeys(false)) {
         // Skip drop-mainhand if target has nothing to drop
         if (key.equals("drop-mainhand") && !canDrop) continue;
         ConfigurationSection eff = poolSection.getConfigurationSection(key);
         if (eff != null && eff.getBoolean("enabled", true)) {
            effects.add(new EffectEntry(key, eff.getDouble("chance", 0.2), eff));
         }
      }

      // If the pool had no enabled effects, try the other pool
      if (effects.isEmpty()) {
         poolKey = rollStatus ? "special-effects" : "status-effects";
         poolSection = section.getConfigurationSection(poolKey);
         if (poolSection == null) return;
         rollStatus = !rollStatus;
         for (String key : poolSection.getKeys(false)) {
            if (key.equals("drop-mainhand") && !canDrop) continue;
            ConfigurationSection eff = poolSection.getConfigurationSection(key);
            if (eff != null && eff.getBoolean("enabled", true)) {
               effects.add(new EffectEntry(key, eff.getDouble("chance", 0.2), eff));
            }
         }
         if (effects.isEmpty()) return;
      }

      EffectEntry chosen = pickWeighted(effects);
      final boolean isStatusPool = rollStatus;

      this.module.getSmps().getSchedulerManager().runEntityTask(target, "voodoo-neg-" + target.getName(), () -> {
         if (!target.isOnline()) return;

         String effectKey = chosen.key;
         ConfigurationSection eff = chosen.config;

         if (isStatusPool) {
            // Status effect — apply potion
            PotionEffectType potionType = resolveNegativePotionType(effectKey);
            if (potionType != null) {
               int dur = eff.getInt("duration", 5) * 20;
               int amp = eff.getInt("amplifier", 0);
               target.addPotionEffect(new PotionEffect(potionType, dur, amp, false, true, true));
            }
         } else {
            // Special effect
            applyNegativeSpecial(owner, target, effectKey, eff, minHealth);
         }

         // Notify owner and target
         String effectName = getEffectName(effectKey);
         notifyOwner(owner, target.getName(), effectName, true);
         notifyTarget(target, effectName, true);
      });
   }

   /**
    * Resolve a negative status effect key to its PotionEffectType.
    */
   private PotionEffectType resolveNegativePotionType(String key) {
      return switch (key) {
         case "nausea" -> PotionEffectType.NAUSEA;
         case "slowness" -> PotionEffectType.SLOWNESS;
         case "blindness" -> PotionEffectType.BLINDNESS;
         case "hunger" -> PotionEffectType.HUNGER;
         case "weakness" -> PotionEffectType.WEAKNESS;
         case "mining-fatigue" -> PotionEffectType.MINING_FATIGUE;
         case "levitation" -> PotionEffectType.LEVITATION;
         default -> null;
      };
   }

   /**
    * Apply a negative special (non-potion) effect.
    */
   private void applyNegativeSpecial(Player owner, Player target, String effectKey,
                                     ConfigurationSection eff, double minHealth) {
      switch (effectKey) {
         case "drop-mainhand" -> {
            // Try main hand first, then off hand
            ItemStack main = target.getInventory().getItemInMainHand();
            ItemStack off = target.getInventory().getItemInOffHand();
            if (main.getType() != Material.AIR && !VoodooItem.isVoodoo(main)) {
               target.getWorld().dropItemNaturally(target.getLocation(), main.clone());
               target.getInventory().setItemInMainHand(null);
            } else if (off.getType() != Material.AIR && !VoodooItem.isVoodoo(off)) {
               target.getWorld().dropItemNaturally(target.getLocation(), off.clone());
               target.getInventory().setItemInOffHand(null);
            }
         }
         case "damage" -> {
            double amount = eff.getDouble("amount", 4.0);
            double currentHealth = target.getHealth();
            double maxDamage = currentHealth - minHealth;
            if (maxDamage > 0) {
               target.damage(Math.min(amount, maxDamage));
            }
         }
         case "freeze" -> {
            int ticks = eff.getInt("freeze-ticks", 140);
            target.setFreezeTicks(Math.min(target.getMaxFreezeTicks(), ticks));
         }
         case "force-look" -> {
            int durationSec = eff.getInt("duration", 5);
            applyForceLook(owner, target, durationSec);
         }
         case "lay-down" -> {
            int durationSec = eff.getInt("duration", 5);
            applyLayDown(target, durationSec);
         }
         case "inventory-shuffle" -> applyInventoryShuffle(target);
         case "elder-guardian" -> {
            int dur = eff.getInt("duration", 15) * 20;
            int amp = eff.getInt("amplifier", 2);
            applyElderGuardian(target, dur, amp);
         }
      }
   }

   // ================================================================
   // New negative effect implementations
   // ================================================================

   /**
    * Force the target to look at the owner for X seconds.
    * Uses setRotation so the player can still walk around, but camera is locked.
    */
   private void applyForceLook(Player owner, Player target, int durationSec) {
      UUID targetUuid = target.getUniqueId();
      if (this.forceLooking.contains(targetUuid)) return;
      this.forceLooking.add(targetUuid);

      String taskName = "voodoo-forcelook-" + targetUuid;
      final int[] ticksLeft = {durationSec * 20};

      this.module.getSmps().getSchedulerManager().runEntityTaskTimer(target, taskName, () -> {
         if (!target.isOnline() || !owner.isOnline() || ticksLeft[0] <= 0) {
            this.forceLooking.remove(targetUuid);
            this.module.getSmps().getSchedulerManager().cancelTask(taskName);
            return;
         }
         // Skip if players are in different worlds
         if (!target.getWorld().equals(owner.getWorld())) {
            ticksLeft[0]--;
            return;
         }
         try {
            Location ownerLoc = owner.getLocation().add(0, owner.getEyeHeight(), 0);
            Location targetLoc = target.getEyeLocation();
            org.bukkit.util.Vector diff = ownerLoc.toVector().subtract(targetLoc.toVector());
            double lenSq = diff.lengthSquared();
            if (lenSq < 0.001) {
               ticksLeft[0]--;
               return;
            }
            org.bukkit.util.Vector direction = diff.normalize();
            // Calculate yaw and pitch from direction vector
            double dx = direction.getX();
            double dy = direction.getY();
            double dz = direction.getZ();
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
               ticksLeft[0]--;
               return;
            }
            // Only change rotation, not position — player can still walk
            target.setRotation(yaw, pitch);
         } catch (Exception ignored) {
            // Silently skip this tick on any error
         }
         ticksLeft[0]--;
      }, 1L, 1L);
   }

   /**
    * Force the target into a crawling/swimming pose for X seconds.
    * Uses setSwimming(true) for the 1-block-height crawl animation.
    * Player can still move but stays in crawl pose.
    */
   private void applyLayDown(Player target, int durationSec) {
      UUID targetUuid = target.getUniqueId();
      if (this.layingDown.contains(targetUuid)) return;
      this.layingDown.add(targetUuid);

      target.setSwimming(true);

      String taskName = "voodoo-laydown-" + targetUuid;
      final int[] ticksLeft = {durationSec * 20};

      this.module.getSmps().getSchedulerManager().runEntityTaskTimer(target, taskName, () -> {
         if (!target.isOnline() || ticksLeft[0] <= 0) {
            this.layingDown.remove(targetUuid);
            target.setSwimming(false);
            this.module.getSmps().getSchedulerManager().cancelTask(taskName);
            return;
         }
         // Keep forcing crawl pose every tick
         if (!target.isSwimming()) {
            target.setSwimming(true);
         }
         ticksLeft[0]--;
      }, 1L, 1L);
   }

   /**
    * Shuffle all non-armor inventory slots randomly.
    * Includes hotbar (0-8), main inventory (9-35), and offhand (40).
    */
   private void applyInventoryShuffle(Player target) {
      PlayerInventory inv = target.getInventory();
      // Collect items from non-armor slots: 0-35 (hotbar + main) + 40 (offhand)
      List<Integer> slots = new ArrayList<>();
      for (int i = 0; i <= 35; i++) slots.add(i);
      slots.add(40); // offhand

      List<ItemStack> items = new ArrayList<>();
      for (int slot : slots) {
         ItemStack stack = inv.getItem(slot);
         items.add(stack != null ? stack.clone() : null);
      }

      Collections.shuffle(items, ThreadLocalRandom.current());

      for (int i = 0; i < slots.size(); i++) {
         inv.setItem(slots.get(i), items.get(i));
      }
   }

   /**
    * Play the Elder Guardian jumpscare effect and apply Mining Fatigue.
    */
   private void applyElderGuardian(Player target, int durationTicks, int amplifier) {
      // Show the elder guardian ghost/screamer
      target.showElderGuardian(true);
      // Apply mining fatigue
      target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, durationTicks, amplifier, false, true, true));
   }

   // ================================================================
   // Positive effects
   // ================================================================
   private void applyPositiveEffect(Player owner, Player target, FileConfiguration config) {
      ConfigurationSection section = config.getConfigurationSection("positive-effects");
      if (section == null) return;

      double statusChance = section.getDouble("status-effect-chance", 0.5);

      // Roll: status effect or special effect?
      boolean rollStatus = ThreadLocalRandom.current().nextDouble() < statusChance;

      String poolKey = rollStatus ? "status-effects" : "special-effects";
      ConfigurationSection poolSection = section.getConfigurationSection(poolKey);

      // Fallback: if chosen pool missing/empty, try the other
      if (poolSection == null || poolSection.getKeys(false).isEmpty()) {
         poolKey = rollStatus ? "special-effects" : "status-effects";
         poolSection = section.getConfigurationSection(poolKey);
         if (poolSection == null) return;
         rollStatus = !rollStatus;
      }

      List<EffectEntry> effects = new ArrayList<>();
      for (String key : poolSection.getKeys(false)) {
         ConfigurationSection eff = poolSection.getConfigurationSection(key);
         if (eff != null && eff.getBoolean("enabled", true)) {
            effects.add(new EffectEntry(key, eff.getDouble("chance", 0.2), eff));
         }
      }

      // If pool had no enabled effects, try the other pool
      if (effects.isEmpty()) {
         poolKey = rollStatus ? "special-effects" : "status-effects";
         poolSection = section.getConfigurationSection(poolKey);
         if (poolSection == null) return;
         rollStatus = !rollStatus;
         for (String key : poolSection.getKeys(false)) {
            ConfigurationSection eff = poolSection.getConfigurationSection(key);
            if (eff != null && eff.getBoolean("enabled", true)) {
               effects.add(new EffectEntry(key, eff.getDouble("chance", 0.2), eff));
            }
         }
         if (effects.isEmpty()) return;
      }

      EffectEntry chosen = pickWeighted(effects);
      final boolean isStatusPool = rollStatus;

      this.module.getSmps().getSchedulerManager().runEntityTask(target, "voodoo-pos-" + target.getName(), () -> {
         if (!target.isOnline()) return;

         String effectKey = chosen.key;
         ConfigurationSection eff = chosen.config;

         if (isStatusPool) {
            // Status effect -- apply potion
            PotionEffectType potionType = resolvePositivePotionType(effectKey);
            if (potionType != null) {
               int dur = eff.getInt("duration", 15) * 20;
               int amp = eff.getInt("amplifier", 1);
               target.addPotionEffect(new PotionEffect(potionType, dur, amp, false, true, true));
            }
         } else {
            // Special effect
            applyPositiveSpecial(target, effectKey, eff);
         }

         String effectName = getEffectName(effectKey);
         notifyOwner(owner, target.getName(), effectName, false);
         notifyTarget(target, effectName, false);
      });
   }

   /**
    * Resolve a positive status effect key to its PotionEffectType.
    */
   private PotionEffectType resolvePositivePotionType(String key) {
      return switch (key) {
         case "health-boost" -> PotionEffectType.HEALTH_BOOST;
         case "regeneration" -> PotionEffectType.REGENERATION;
         case "speed" -> PotionEffectType.SPEED;
         case "absorption" -> PotionEffectType.ABSORPTION;
         case "resistance" -> PotionEffectType.RESISTANCE;
         case "fire-resistance" -> PotionEffectType.FIRE_RESISTANCE;
         case "jump-boost" -> PotionEffectType.JUMP_BOOST;
         case "haste" -> PotionEffectType.HASTE;
         case "saturation" -> PotionEffectType.SATURATION;
         case "night-vision" -> PotionEffectType.NIGHT_VISION;
         default -> null;
      };
   }

   /**
    * Apply a positive special (cosmetic/non-potion) effect.
    */
   private void applyPositiveSpecial(Player target, String effectKey, ConfigurationSection eff) {
      switch (effectKey) {
         case "heart-particles" -> {
            int count = eff.getInt("count", 15);
            Location loc = target.getLocation().add(0, 1.0, 0);
            target.getWorld().spawnParticle(Particle.HEART, loc, count, 0.5, 0.5, 0.5);
            target.playSound(target.getLocation(), Sound.ENTITY_CAT_PURREOW, 1.0f, 1.2f);
         }
         case "firework" -> {
            Firework fw = target.getWorld().spawn(target.getLocation().add(0, 1, 0), Firework.class);
            FireworkMeta meta = fw.getFireworkMeta();
            Color[] colors = {Color.FUCHSIA, Color.AQUA, Color.YELLOW, Color.LIME, Color.RED};
            Color c1 = colors[ThreadLocalRandom.current().nextInt(colors.length)];
            Color c2 = colors[ThreadLocalRandom.current().nextInt(colors.length)];
            meta.addEffect(FireworkEffect.builder()
               .with(FireworkEffect.Type.BALL_LARGE)
               .withColor(c1)
               .withFade(c2)
               .trail(true)
               .flicker(true)
               .build());
            meta.setPower(0); // instant detonation
            fw.setFireworkMeta(meta);
            fw.detonate();
         }
         case "totem-animation" -> {
            // Spawn totem particles and play the sound
            Location loc = target.getLocation().add(0, 1.0, 0);
            target.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 50, 0.5, 1.0, 0.5, 0.5);
            target.playSound(target.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
         }
         case "glowing" -> {
            int durationSec = eff.getInt("duration", 10);
            target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, durationSec * 20, 0, false, false, true));
         }
         case "feed" -> {
            target.setFoodLevel(20);
            target.setSaturation(5.0f);
            target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_BURP, 1.0f, 1.0f);
         }
         case "extinguish" -> {
            if (target.getFireTicks() > 0) {
               target.setFireTicks(0);
               target.playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 1.0f, 1.0f);
            } else {
               // Not on fire -- give a brief fire resistance instead
               target.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 200, 0, false, true, true));
            }
         }
      }
   }

   // ================================================================
   // Chat notifications
   // ================================================================

   private void notifyOwner(Player owner, String targetName, String effectName, boolean negative) {
      if (!owner.isOnline()) return;
      String key = negative ? "voodoo.owner-negative" : "voodoo.owner-positive";
      String fallback = negative
         ? "&5* &dApplied &c{effect} &don &e{target}"
         : "&5* &dApplied &a{effect} &don &e{target}";
      this.module.getSmps().getSchedulerManager().runEntityTask(owner, "voodoo-notify-owner-" + owner.getUniqueId(), () -> {
         if (!owner.isOnline()) return;
         owner.sendMessage(this.module.getMessage(key, fallback)
            .replace("{effect}", effectName)
            .replace("{target}", targetName));
      });
   }

   private void notifyTarget(Player target, String effectName, boolean negative) {
      if (!target.isOnline()) return;
      String key = negative ? "voodoo.target-negative" : "voodoo.target-positive";
      String fallback = negative
         ? "&5* &dSomeone applied &c{effect} &don you!"
         : "&5* &dSomeone applied &a{effect} &don you!";
      target.sendMessage(this.module.getMessage(key, fallback)
         .replace("{effect}", effectName));
   }

   // ================================================================
   // Utility
   // ================================================================

   private EffectEntry pickWeighted(List<EffectEntry> effects) {
      double totalWeight = 0;
      for (EffectEntry e : effects) {
         totalWeight += e.chance;
      }
      double roll = ThreadLocalRandom.current().nextDouble() * totalWeight;
      double cumulative = 0;
      for (EffectEntry e : effects) {
         cumulative += e.chance;
         if (roll <= cumulative) {
            return e;
         }
      }
      return effects.getLast();
   }

   private record EffectEntry(String key, double chance, ConfigurationSection config) {}
}

