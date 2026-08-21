package net.schalker.SMPS.modules.flags.listeners;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.listener.BaseListener;
import net.schalker.SMPS.modules.flags.FlagEvent;
import net.schalker.SMPS.modules.flags.FlagType;
import net.schalker.SMPS.modules.flags.FlagsMenuHolder;
import net.schalker.SMPS.modules.flags.FlagsModule;
import net.schalker.SMPS.modules.flags.FlagsHistoryMenuHolder;
import net.schalker.SMPS.modules.flags.managers.FlagsManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Boat;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class FlagsListener extends BaseListener {
   private final FlagsModule module;
   private final FlagsManager manager;
   private final Map<UUID, String> lastMessages = new HashMap<>();
   private final Map<UUID, PendingChestBoatPlacement> pendingChestBoatPlacements = new HashMap<>();
   private final Map<UUID, UUID> witherOwners = new ConcurrentHashMap<>();
   private final Map<UUID, UUID> crystalOwners = new ConcurrentHashMap<>();
   private final Map<String, PendingLavaSource> pendingLavaSources = new ConcurrentHashMap<>();
   // Ore pickup accumulation: player UUID → OreAccumulator (tracks total picked up within time window)
   private final Map<UUID, OreAccumulator> oreAccumulators = new ConcurrentHashMap<>();
   private final Map<UUID, OreAccumulator> debrisAccumulators = new ConcurrentHashMap<>();
   // Cached rare item config (loaded from config, refreshed on reload)
   // Material → minimum amount required in inventory to trigger (1 for simple items)
   private volatile java.util.Map<Material, Integer> rareItemMinAmounts = java.util.Collections.emptyMap();
   // Enchantment key (lowercase, e.g. "mending") → minimum enchantment level for books
   private volatile java.util.Map<String, Integer> rareBookEnchantments = java.util.Collections.emptyMap();
   // Material → map of enchantment key → min level (for non-book enchanted items like bows)
   private volatile java.util.Map<Material, java.util.Map<String, Integer>> rareEnchantedItems = java.util.Collections.emptyMap();
   // Snapshot of rare items in player inventory when they open a container (for diff on close)
   private final Map<UUID, java.util.Map<String, Integer>> rareItemSnapshots = new ConcurrentHashMap<>();
   // Container drop detection: snapshot container + player inventory on open, compare on close
   private final Map<UUID, java.util.Map<Material, Integer>> containerDropContainerSnapshots = new ConcurrentHashMap<>();
   private final Map<UUID, java.util.Map<Material, Integer>> containerDropPlayerSnapshots = new ConcurrentHashMap<>();

   public FlagsListener(DoAPI plugin, FlagsModule module, FlagsManager manager) {
      super(plugin);
      this.module = module;
      this.manager = manager;
      this.reloadRareItems();
   }

   /**
    * Reload the cached rare item configuration from config.
    * Called on construction and can be called on module reload.
    */
   public void reloadRareItems() {
      // --- Material-based items ---
      java.util.Map<Material, Integer> minAmounts = new java.util.EnumMap<>(Material.class);

      // Simple items (min-amount = 1)
      java.util.List<String> simpleItems = this.module.getConfig().getStringList("flags.rare_item.items");
      if (simpleItems != null) {
         for (String name : simpleItems) {
            try {
               Material mat = Material.valueOf(name.toUpperCase().trim());
               minAmounts.put(mat, 1);
            } catch (IllegalArgumentException ignored) {}
         }
      }

      // Items with custom min-amount (added separately, does NOT override simple items)
      org.bukkit.configuration.ConfigurationSection minAmountSection =
         this.module.getConfig().getConfigurationSection("flags.rare_item.items-min-amount");
      if (minAmountSection != null) {
         for (String key : minAmountSection.getKeys(false)) {
            try {
               Material mat = Material.valueOf(key.toUpperCase().trim());
               int amount = minAmountSection.getInt(key, 1);
               minAmounts.put(mat, amount);
            } catch (IllegalArgumentException ignored) {}
         }
      }
      this.rareItemMinAmounts = minAmounts;

      // --- Enchanted books ---
      java.util.Map<String, Integer> bookEnchants = new java.util.HashMap<>();
      java.util.List<String> bookList = this.module.getConfig().getStringList("flags.rare_item.enchanted-books");
      if (bookList != null) {
         for (String entry : bookList) {
            String[] parts = entry.split(":");
            if (parts.length == 2) {
               try {
                  String enchName = parts[0].toLowerCase().trim();
                  int minLevel = Integer.parseInt(parts[1].trim());
                  bookEnchants.put(enchName, minLevel);
               } catch (NumberFormatException ignored) {}
            }
         }
      }
      this.rareBookEnchantments = bookEnchants;

      // --- Enchanted items (non-books) ---
      java.util.Map<Material, java.util.Map<String, Integer>> enchItems = new java.util.EnumMap<>(Material.class);
      org.bukkit.configuration.ConfigurationSection enchItemsSection =
         this.module.getConfig().getConfigurationSection("flags.rare_item.enchanted-items");
      if (enchItemsSection != null) {
         for (String matKey : enchItemsSection.getKeys(false)) {
            try {
               Material mat = Material.valueOf(matKey.toUpperCase().trim());
               java.util.Map<String, Integer> enchants = new java.util.HashMap<>();
               java.util.List<String> enchList = enchItemsSection.getStringList(matKey);
               if (enchList != null) {
                  for (String entry : enchList) {
                     String[] parts = entry.split(":");
                     if (parts.length == 2) {
                        try {
                           String enchName = parts[0].toLowerCase().trim();
                           int minLevel = Integer.parseInt(parts[1].trim());
                           enchants.put(enchName, minLevel);
                        } catch (NumberFormatException ignored) {}
                     }
                  }
               }
               if (!enchants.isEmpty()) {
                  enchItems.put(mat, enchants);
               }
            } catch (IllegalArgumentException ignored) {}
         }
      }
      this.rareEnchantedItems = enchItems;
   }

   // Pre-load playtime data on join so flag sensitivity is ready
   @EventHandler(priority = EventPriority.MONITOR)
   public void onPlayerJoin(PlayerJoinEvent event) {
      if (this.manager.getPlaytimeSensitivity() != null) {
         this.manager.getPlaytimeSensitivity().preloadAsync(event.getPlayer().getUniqueId());
      }
   }

   // Evict playtime cache on quit + cleanup
   @EventHandler(priority = EventPriority.MONITOR)
   public void onPlayerQuit(PlayerQuitEvent event) {
      UUID uuid = event.getPlayer().getUniqueId();
      if (this.manager.getPlaytimeSensitivity() != null) {
         this.manager.getPlaytimeSensitivity().evict(uuid);
      }
      this.rareItemSnapshots.remove(uuid);
      this.containerDropContainerSnapshots.remove(uuid);
      this.containerDropPlayerSnapshots.remove(uuid);
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onInventoryClick(InventoryClickEvent event) {
      if (!(event.getWhoClicked() instanceof Player player)) {
         return;
      }
      Inventory top = event.getView().getTopInventory();
      if (top == null) {
          return;
      }

      InventoryHolder topHolder = top.getHolder();

      if (this.isFlagsMenuHolder(topHolder)) {
         event.setCancelled(true);
         if (event.getRawSlot() >= top.getSize()) {
            return;
         }

         ItemStack item = event.getCurrentItem();
         if (item == null || item.getType().isAir()) {
            return;
         }

         ItemMeta meta = item.getItemMeta();
         if (meta == null) {
            return;
         }

         PersistentDataContainer container = meta.getPersistentDataContainer();
         String action = container.get(this.module.getActionKey(), PersistentDataType.STRING);
         if (action == null) {
            return;
         }

         int currentPage = topHolder instanceof FlagsMenuHolder holder ? holder.getPage() : 1;

         if (action.equals("toggle-flag")) {
            String flagKey = container.get(this.module.getFlagTypeKey(), PersistentDataType.STRING);
            FlagType flagType = FlagType.fromKey(flagKey);
            if (flagType != null) {
               this.manager.toggleFlag(player.getUniqueId(), flagType);
               final int p = currentPage;
               this.plugin.getSchedulerManager().runEntityTaskLater(player, "flags-reopen-" + player.getUniqueId(), () -> {
                  if (player.isOnline()) this.module.openFlagsMenu(player, p);
               }, 1L);
            }
         } else if (action.equals("toggle-all")) {
            boolean enable = event.isLeftClick();
            this.manager.setAllFlags(player.getUniqueId(), enable);
            final int p = currentPage;
            this.plugin.getSchedulerManager().runEntityTaskLater(player, "flags-reopen-" + player.getUniqueId(), () -> {
               if (player.isOnline()) this.module.openFlagsMenu(player, p);
            }, 1L);
         } else if (action.equals("toggle-actionbar")) {
            this.manager.toggleAdminActionBar(player.getUniqueId());
            final int p = currentPage;
            this.plugin.getSchedulerManager().runEntityTaskLater(player, "flags-reopen-" + player.getUniqueId(), () -> {
               if (player.isOnline()) this.module.openFlagsMenu(player, p);
            }, 1L);
         } else if (action.equals("toggle-chat")) {
            this.manager.toggleAdminChat(player.getUniqueId());
            final int p = currentPage;
            this.plugin.getSchedulerManager().runEntityTaskLater(player, "flags-reopen-" + player.getUniqueId(), () -> {
               if (player.isOnline()) this.module.openFlagsMenu(player, p);
            }, 1L);
         } else if (action.equals("prev")) {
            final int p = currentPage - 1;
            this.plugin.getSchedulerManager().runEntityTaskLater(player, "flags-reopen-" + player.getUniqueId(), () -> {
               if (player.isOnline()) this.module.openFlagsMenu(player, p);
            }, 1L);
         } else if (action.equals("next")) {
            final int p = currentPage + 1;
            this.plugin.getSchedulerManager().runEntityTaskLater(player, "flags-reopen-" + player.getUniqueId(), () -> {
               if (player.isOnline()) this.module.openFlagsMenu(player, p);
            }, 1L);
         }
         return;
      }

      if (this.isFlagsHistoryMenuHolder(topHolder)) {
         event.setCancelled(true);
         if (event.getRawSlot() >= top.getSize()) {
            return;
         }

         ItemStack item = event.getCurrentItem();
         if (item == null || item.getType().isAir()) {
            return;
         }

         ItemMeta meta = item.getItemMeta();
         if (meta == null) {
            return;
         }

         PersistentDataContainer container = meta.getPersistentDataContainer();
         String action = container.get(this.module.getActionKey(), PersistentDataType.STRING);
         if (action == null) {
            return;
         }

         int currentPage = topHolder instanceof FlagsHistoryMenuHolder holder ? holder.getPage() : 1;
         String targetPlayer = topHolder instanceof FlagsHistoryMenuHolder holder ? holder.getTargetPlayer() : null;

         if (action.equals("history-prev")) {
            final int p = currentPage - 1;
            final String tp = targetPlayer;
            this.plugin.getSchedulerManager().runEntityTaskLater(player, "flags-history-reopen-" + player.getUniqueId(), () -> {
               if (player.isOnline()) this.module.openHistoryMenu(player, tp, p);
            }, 1L);
         } else if (action.equals("history-next")) {
            final int p = currentPage + 1;
            final String tp = targetPlayer;
            this.plugin.getSchedulerManager().runEntityTaskLater(player, "flags-history-reopen-" + player.getUniqueId(), () -> {
               if (player.isOnline()) this.module.openHistoryMenu(player, tp, p);
            }, 1L);
         } else if (action.equals("expand-group")) {
            if (event.isRightClick()) {
               this.tryTeleportFromHistoryItem(player, container);
               return;
            }
            // Click on a stacked group → open expanded (individual events) view
            String expandFlagTypeKey = container.get(this.module.getFlagTypeKey(), PersistentDataType.STRING);
            String expandPlayerName = container.get(this.module.getPageKey(), PersistentDataType.STRING);
            if (expandFlagTypeKey != null && expandPlayerName != null) {
               final String tp = targetPlayer;
               final int parentPage = currentPage;
               this.plugin.getSchedulerManager().runEntityTaskLater(player, "flags-expand-" + player.getUniqueId(), () -> {
                  if (player.isOnline()) {
                     this.module.openExpandedHistoryMenu(player, tp, expandPlayerName, expandFlagTypeKey, 1, parentPage);
                  }
               }, 1L);
            }
         } else if (action.equals("expand-back")) {
            // Back button from expanded view → return to grouped history
            final String tp = targetPlayer;
            int pp = 1;
            if (topHolder instanceof FlagsHistoryMenuHolder h && h.getParentPage() > 0) {
               pp = h.getParentPage();
            }
            final int parentPage = pp;
            this.plugin.getSchedulerManager().runEntityTaskLater(player, "flags-history-reopen-" + player.getUniqueId(), () -> {
               if (player.isOnline()) this.module.openHistoryMenu(player, tp, parentPage);
            }, 1L);
         } else if (action.equals("expand-prev") || action.equals("expand-next")) {
            // Pagination within expanded view
            if (topHolder instanceof FlagsHistoryMenuHolder holder && holder.isExpanded()) {
               final String tp = targetPlayer;
               final String ep = holder.getExpandPlayer();
               final String ef = holder.getExpandFlagTypeKey();
               final int pp = holder.getParentPage();
               final int p = action.equals("expand-prev") ? currentPage - 1 : currentPage + 1;
               this.plugin.getSchedulerManager().runEntityTaskLater(player, "flags-expand-" + player.getUniqueId(), () -> {
                  if (player.isOnline()) this.module.openExpandedHistoryMenu(player, tp, ep, ef, p, pp);
               }, 1L);
            }
         } else if (action.equals("history-teleport")) {
            this.tryTeleportFromHistoryItem(player, container);
         }
         return;
      }

      if (player.hasPermission("smflags.bypass")) {
         return;
      }

      // Storage interact is now logged on open/close only
   }

   // TNT in inventory check
   @EventHandler(priority = EventPriority.MONITOR)
   public void onInventoryOpen(InventoryOpenEvent event) {
      if (!(event.getPlayer() instanceof Player player)) {
         return;
      }

      if (!player.hasPermission("smflags.bypass") && this.isFlagEnabled("boat_chest_interact")) {
         Inventory top = event.getInventory();
         if (this.isChestBoatInventory(top)) {
            Entity holder = this.getChestBoatHolder(top);
            Location location = holder != null ? holder.getLocation() : player.getLocation();
            String name = holder != null ? this.getEntityDisplayName(holder) : "транспорт с хранилищем";
            String items = holder != null ? this.scanEntityInventory(holder) : "";

            this.triggerFlag(FlagEvent.builder()
               .playerId(player.getUniqueId())
               .playerName(player.getName())
               .flagType(FlagType.BOAT_CHEST_INTERACT)
               .location(location)
               .details("Открыл инвентарь: " + name + items)
               .build());
         }
      }

      // TNT inventory check
      if (this.isFlagEnabled("tnt_inventory") && !player.hasPermission("smflags.bypass")) {
         int tntCount = 0;
         for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.TNT) {
               tntCount += item.getAmount();
            }
         }

         int threshold = this.getEffectiveThreshold("tnt_inventory", player.getUniqueId());
         if (tntCount >= threshold) {
            this.triggerFlag(FlagEvent.builder()
               .playerId(player.getUniqueId())
               .playerName(player.getName())
               .flagType(FlagType.TNT_INVENTORY)
               .location(player.getLocation())
               .value(tntCount)
               .details(tntCount + " TNT в инвентаре")
               .build());
         }
      }

      // Rare item — snapshot current rare items when opening a container (compared on close)
      if (this.isFlagEnabled("rare_item") && !player.hasPermission("smflags.bypass") && this.hasRareItemRules()) {
         Inventory top = event.getInventory();
         // Only snapshot when opening an external inventory (not the player's own)
         if (top.getHolder() != null && !(top.getHolder() instanceof Player)) {
            this.rareItemSnapshots.put(player.getUniqueId(), this.snapshotRareItems(player));
         }
      }

      // Container drop detection — snapshot container + player inventory on open
      if (this.isFlagEnabled("container_drop") && !player.hasPermission("smflags.bypass")) {
         Inventory top = event.getInventory();
         if (this.isExternalContainer(top)) {
            this.containerDropContainerSnapshots.put(player.getUniqueId(), this.snapshotInventoryContents(top));
            this.containerDropPlayerSnapshots.put(player.getUniqueId(), this.snapshotInventoryContents(player.getInventory()));
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onInventoryDrag(InventoryDragEvent event) {
      if (!(event.getWhoClicked() instanceof Player player)) {
         return;
      }

      Inventory top = event.getView().getTopInventory();

      // Cancel drag in menu inventories
      if (top.getHolder() instanceof FlagsMenuHolder || top.getHolder() instanceof FlagsHistoryMenuHolder) {
         event.setCancelled(true);
         return;
      }

      // No per-drag logging for storage — we log on open/close only
   }

   // Storage close + rare item diff check
   @EventHandler(priority = EventPriority.MONITOR)
   public void onInventoryClose(InventoryCloseEvent event) {
      if (!(event.getPlayer() instanceof Player player)) {
         return;
      }
      if (player.hasPermission("smflags.bypass")) {
         return;
      }

      // --- Boat/chest storage close logging ---
      if (this.isFlagEnabled("boat_chest_interact")) {
         Inventory top = event.getView().getTopInventory();
         if (this.isChestBoatInventory(top)) {
            Entity holder = this.getChestBoatHolder(top);
            Location location = holder != null ? holder.getLocation() : player.getLocation();
            String name = holder != null ? this.getEntityDisplayName(holder) : "транспорт с хранилищем";
            String items = holder != null ? this.scanEntityInventory(holder) : " [Пусто]";

            this.triggerFlag(FlagEvent.builder()
               .playerId(player.getUniqueId())
               .playerName(player.getName())
               .flagType(FlagType.BOAT_CHEST_INTERACT)
               .location(location)
               .details("Закрыл инвентарь: " + name + ". Осталось:" + items)
               .build());
         }
      }

      // --- Rare item diff check: compare snapshot from open with current inventory ---
      java.util.Map<String, Integer> snapshot = this.rareItemSnapshots.remove(player.getUniqueId());
      if (snapshot != null && this.isFlagEnabled("rare_item") && this.hasRareItemRules()) {
         java.util.Map<String, Integer> current = this.snapshotRareItems(player);

         int totalGained = 0;
         StringBuilder gained = new StringBuilder();
         int listed = 0;

         for (java.util.Map.Entry<String, Integer> entry : current.entrySet()) {
            String key = entry.getKey();
            int currentCount = entry.getValue();
            int beforeCount = snapshot.getOrDefault(key, 0);
            int diff = currentCount - beforeCount;
            if (diff > 0) {
               // For material items, check min-amount threshold
               if (!key.startsWith("BOOK:") && !key.startsWith("ITEM:")) {
                  int minAmount = this.rareItemMinAmounts.getOrDefault(Material.valueOf(key), 1);
                  if (currentCount < minAmount) continue; // Total still below threshold
               }
               totalGained += diff;
               if (listed < 5) {
                  if (listed > 0) gained.append(", ");
                  gained.append(this.formatRareItemKey(key)).append(" x").append(diff);
                  listed++;
               }
            }
         }

         if (totalGained > 0) {
            this.triggerFlag(FlagEvent.builder()
               .playerId(player.getUniqueId())
               .playerName(player.getName())
               .flagType(FlagType.RARE_ITEM)
               .location(player.getLocation())
               .value(totalGained)
               .details("Забрал из хранилища: " + gained)
               .build());
         }
      }

      // --- Container drop detection: items that left container but not in player inventory = dropped ---
      java.util.Map<Material, Integer> containerBefore = this.containerDropContainerSnapshots.remove(player.getUniqueId());
      java.util.Map<Material, Integer> playerBefore = this.containerDropPlayerSnapshots.remove(player.getUniqueId());
      if (containerBefore != null && playerBefore != null && this.isFlagEnabled("container_drop")) {
         Inventory topInv = event.getView().getTopInventory();
         java.util.Map<Material, Integer> containerNow = this.snapshotInventoryContents(topInv);
         java.util.Map<Material, Integer> playerNow = this.snapshotInventoryContents(player.getInventory());

         int totalDropped = 0;
         StringBuilder droppedDetails = new StringBuilder();
         int listed = 0;
         boolean rareItemDropped = false;

         for (java.util.Map.Entry<Material, Integer> entry : containerBefore.entrySet()) {
            Material mat = entry.getKey();
            int before = entry.getValue();
            int after = containerNow.getOrDefault(mat, 0);
            int removedFromContainer = before - after;
            if (removedFromContainer <= 0) continue;

            int playerGain = playerNow.getOrDefault(mat, 0) - playerBefore.getOrDefault(mat, 0);
            if (playerGain < 0) playerGain = 0;

            int dropped = removedFromContainer - playerGain;
            if (dropped > 0) {
               totalDropped += dropped;

               // If dropped item is configured as rare, trigger regardless of total threshold
               if (this.rareItemMinAmounts.containsKey(mat)) {
                  int minRare = this.rareItemMinAmounts.getOrDefault(mat, 1);
                  if (dropped >= minRare) {
                     rareItemDropped = true;
                  }
               }

               if (listed < 5) {
                  if (listed > 0) droppedDetails.append(", ");
                  droppedDetails.append(mat.name()).append(" x").append(dropped);
                  listed++;
               }
            }
         }

         int threshold = this.getEffectiveThreshold("container_drop", player.getUniqueId());
         if (totalDropped >= threshold || rareItemDropped) {
            FlagEvent.Builder builder = FlagEvent.builder()
               .playerId(player.getUniqueId())
               .playerName(player.getName())
               .flagType(FlagType.CONTAINER_DROP)
               .location(player.getLocation())
               .value(totalDropped)
               .details("Выбросил " + totalDropped + " предметов из хранилища: " + droppedDetails);
               
            if (rareItemDropped) {
               builder.resolvedSeverity(FlagType.FlagSeverity.HIGH);
            }
               
            this.triggerFlag(builder.build());
         }
      }
   }

   // TNT placement tracking
   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onBlockPlace(BlockPlaceEvent event) {
      Player player = event.getPlayer();
      if (player.hasPermission("smflags.bypass")) {
         return;
      }
      
      Material type = event.getBlock().getType();
      
      // TNT placement
      if (type == Material.TNT) {
         boolean isNether = event.getBlock().getWorld().getEnvironment() == org.bukkit.World.Environment.NETHER;
         String flagKey = isNether ? "tnt_placement_nether" : "tnt_placement_overworld";
         FlagType flagType = isNether ? FlagType.TNT_PLACEMENT_NETHER : FlagType.TNT_PLACEMENT_OVERWORLD;

         if (this.isFlagEnabled(flagKey)) {
            this.manager.getTracker().trackAction(player.getUniqueId(), flagType, System.currentTimeMillis());

            int threshold = this.getEffectiveThreshold(flagKey, player.getUniqueId());
            int timeWindow = this.getTimeWindow(flagKey);
            int count = this.manager.getTracker().getActionCount(player.getUniqueId(), flagType, timeWindow * 60000L);

            if (count >= threshold) {
               String worldName = isNether ? "Незер" : "Обычный мир";
               this.triggerFlag(FlagEvent.builder()
                  .playerId(player.getUniqueId())
                  .playerName(player.getName())
                  .flagType(flagType)
                  .location(event.getBlock().getLocation())
                  .value(count)
                  .details(count + " TNT за " + timeWindow + " минут (" + worldName + ")")
                  .build());
            }
         }
      }
      
      // Lava placement height check
      if ((type == Material.LAVA || type == Material.LAVA_CAULDRON) && this.isFlagEnabled("lava_placement")) {
         int minHeight = this.getLavaMinHeight();
         this.rememberLavaSource(player, event.getBlock());
         if (event.getBlock().getY() >= minHeight) {
            this.triggerFlag(FlagEvent.builder()
               .playerId(player.getUniqueId())
               .playerName(player.getName())
               .flagType(FlagType.LAVA_PLACEMENT)
               .location(event.getBlock().getLocation())
               .details("Лава на высоте " + event.getBlock().getY())
               .build());
         }
      }
      
      // End Crystal placement is tracked via EntityPlaceEvent, not here
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onBucketEmpty(PlayerBucketEmptyEvent event) {
      Player player = event.getPlayer();
      if (player == null || player.hasPermission("smflags.bypass")) {
         return;
      }
      if (!this.isFlagEnabled("lava_placement")) {
         return;
      }
      if (event.getBucket() != Material.LAVA_BUCKET) {
         return;
      }

      Block source = event.getBlock().getRelative(event.getBlockFace());
      this.rememberLavaSource(player, source);
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onLavaSpread(BlockFromToEvent event) {
      if (!this.isFlagEnabled("lava_placement")) {
         return;
      }

      Block from = event.getBlock();
      if (from.getType() != Material.LAVA) {
         return;
      }

      this.cleanupPendingLavaSources();

      String fromKey = this.toBlockKey(from);
      PendingLavaSource source = this.pendingLavaSources.get(fromKey);
      if (source == null) {
         return;
      }

      Block to = event.getToBlock();
      if (to == null) {
         return;
      }

      this.pendingLavaSources.put(this.toBlockKey(to), source);

      int minHeight = this.getLavaMinHeight();
      if (to.getY() >= minHeight) {
         this.triggerFlag(FlagEvent.builder()
            .playerId(source.playerId)
            .playerName(source.playerName)
            .flagType(FlagType.LAVA_PLACEMENT)
            .location(to.getLocation())
            .details("Растекание лавы на высоте " + to.getY())
            .build());
      }
   }

   // End Crystal placement — track who placed it
   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onEntityPlace(EntityPlaceEvent event) {
      if (!(event.getEntity() instanceof EnderCrystal crystal)) {
         return;
      }
      Player player = event.getPlayer();
      if (player == null) {
         return;
      }
      this.crystalOwners.put(crystal.getUniqueId(), player.getUniqueId());
   }

   // Fire ignite tracking
   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onBlockIgnite(BlockIgniteEvent event) {
      if (event.getPlayer() == null) {
         return;
      }
      Player player = event.getPlayer();
      if (player.hasPermission("smflags.bypass")) {
         return;
      }
      if (!this.isFlagEnabled("fire_ignite")) {
         return;
      }

      this.manager.getTracker().trackAction(player.getUniqueId(), FlagType.FIRE_IGNITE, System.currentTimeMillis());
      
      int threshold = this.getEffectiveThreshold("fire_ignite", player.getUniqueId());
      int timeWindow = this.getTimeWindow("fire_ignite");
      int count = this.manager.getTracker().getActionCount(player.getUniqueId(), FlagType.FIRE_IGNITE, timeWindow * 60000L);
      
      if (count >= threshold) {
         this.triggerFlag(FlagEvent.builder()
            .playerId(player.getUniqueId())
            .playerName(player.getName())
            .flagType(FlagType.FIRE_IGNITE)
            .location(event.getBlock().getLocation())
            .value(count)
            .details(count + " блоков поджжено за " + timeWindow + " минут")
            .build());
      }
   }

   // Villager kills
   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onEntityDeath(EntityDeathEvent event) {
      if (event.getEntity().getKiller() == null) {
         return;
      }
      Player player = event.getEntity().getKiller();
      if (player.hasPermission("smflags.bypass")) {
         return;
      }

      if (event.getEntity() instanceof Villager && this.isFlagEnabled("villager_kill")) {
         this.manager.getTracker().trackAction(player.getUniqueId(), FlagType.VILLAGER_KILL, System.currentTimeMillis());
         
         int threshold = this.getEffectiveThreshold("villager_kill", player.getUniqueId());
         int timeWindow = this.getTimeWindow("villager_kill");
         int count = this.manager.getTracker().getActionCount(player.getUniqueId(), FlagType.VILLAGER_KILL, timeWindow * 60000L);
         
         if (count >= threshold) {
            this.triggerFlag(FlagEvent.builder()
               .playerId(player.getUniqueId())
               .playerName(player.getName())
               .flagType(FlagType.VILLAGER_KILL)
               .location(event.getEntity().getLocation())
               .value(count)
               .details(count + " жителей убито за " + timeWindow + " минут")
               .build());
         }
      }
   }

   // Pet kills
   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
      if (!(event.getDamager() instanceof Player player)) {
         return;
      }
      if (player.hasPermission("smflags.bypass")) {
         return;
      }
      if (!this.isFlagEnabled("pet_kill")) {
         return;
      }

      Entity victim = event.getEntity();
      if (victim instanceof org.bukkit.entity.Tameable tameable) {
         if (tameable.isTamed() && tameable.getOwner() != null && !tameable.getOwner().equals(player)) {
            if (event.getFinalDamage() >= ((org.bukkit.entity.LivingEntity) victim).getHealth()) {
               String weapon = player.getInventory().getItemInMainHand().getType().name();
               String ownerName = tameable.getOwner() instanceof Player ? ((Player) tameable.getOwner()).getName() : "Unknown";
               String petType = victim.getType().name();

               this.triggerFlag(FlagEvent.builder()
                  .playerId(player.getUniqueId())
                  .playerName(player.getName())
                  .flagType(FlagType.PET_KILL)
                  .location(victim.getLocation())
                  .value(1)
                  .details("Убит питомец " + ownerName + " (" + petType + ") оружием " + weapon)
                  .build());
            }
         }
      }
   }

   // End Crystal damage tracking (entities and players)
   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onCrystalDamage(EntityDamageByEntityEvent event) {
      // Check if the damager is an ender crystal
      Entity damager = event.getDamager();
      if (!(damager instanceof EnderCrystal crystal)) {
         return;
      }

      UUID ownerUuid = this.crystalOwners.get(crystal.getUniqueId());
      if (ownerUuid == null) {
         return;
      }

      Player owner = this.plugin.getServer().getPlayer(ownerUuid);
      String ownerName = owner != null ? owner.getName() : "Unknown";
      if (owner != null && owner.hasPermission("smflags.bypass")) {
         return;
      }

      Entity victim = event.getEntity();
      double damage = Math.round(event.getFinalDamage() * 10.0) / 10.0;

      if (victim instanceof Player target) {
         // Crystal damaged a player
         if (!this.isFlagEnabled("end_crystal_damage_player")) return;

         this.manager.getTracker().trackAction(ownerUuid, FlagType.END_CRYSTAL_DAMAGE_PLAYER, System.currentTimeMillis());

         int threshold = this.getEffectiveThreshold("end_crystal_damage_player", ownerUuid);
         int timeWindow = this.getTimeWindow("end_crystal_damage_player");
         int count = this.manager.getTracker().getActionCount(ownerUuid, FlagType.END_CRYSTAL_DAMAGE_PLAYER, timeWindow * 60000L);

         if (count >= threshold) {
            this.triggerFlag(FlagEvent.builder()
               .playerId(ownerUuid)
               .playerName(ownerName)
               .flagType(FlagType.END_CRYSTAL_DAMAGE_PLAYER)
               .location(victim.getLocation())
               .value(count)
               .details("Кристалл нанёс " + damage + " урона игроку " + target.getName() + " (" + count + " попаданий за " + timeWindow + " мин)")
               .build());
         }
      } else {
         // Crystal damaged an entity (mob, animal, etc.)
         if (!this.isFlagEnabled("end_crystal_damage_entity")) return;

         this.manager.getTracker().trackAction(ownerUuid, FlagType.END_CRYSTAL_DAMAGE_ENTITY, System.currentTimeMillis());

         int threshold = this.getEffectiveThreshold("end_crystal_damage_entity", ownerUuid);
         int timeWindow = this.getTimeWindow("end_crystal_damage_entity");
         int count = this.manager.getTracker().getActionCount(ownerUuid, FlagType.END_CRYSTAL_DAMAGE_ENTITY, timeWindow * 60000L);

         if (count >= threshold) {
            String entityName = victim.getType().name();
            this.triggerFlag(FlagEvent.builder()
               .playerId(ownerUuid)
               .playerName(ownerName)
               .flagType(FlagType.END_CRYSTAL_DAMAGE_ENTITY)
               .location(victim.getLocation())
               .value(count)
               .details("Кристалл нанёс " + damage + " урона " + entityName + " (" + count + " попаданий за " + timeWindow + " мин)")
               .build());
         }
      }
   }

   // Player kills tracking
   @EventHandler(priority = EventPriority.MONITOR)
   public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
      Player victim = event.getEntity();
      Player killer = victim.getKiller();
      
      if (killer == null || killer.hasPermission("smflags.bypass")) {
         return;
      }
      if (!this.isFlagEnabled("player_kill")) {
         return;
      }

      this.manager.getTracker().trackAction(killer.getUniqueId(), FlagType.PLAYER_KILL, System.currentTimeMillis());
      
      int threshold = this.getEffectiveThreshold("player_kill", killer.getUniqueId());
      int timeWindow = this.getTimeWindow("player_kill");
      int count = this.manager.getTracker().getActionCount(killer.getUniqueId(), FlagType.PLAYER_KILL, timeWindow * 60000L);
      
      if (count >= threshold) {
         this.triggerFlag(FlagEvent.builder()
            .playerId(killer.getUniqueId())
            .playerName(killer.getName())
            .flagType(FlagType.PLAYER_KILL)
            .location(victim.getLocation())
            .value(count)
            .details(count + " игроков убито за " + timeWindow + " минут")
            .build());
      }
   }

   // Chat spam
   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onChat(AsyncPlayerChatEvent event) {
      Player player = event.getPlayer();
      if (player.hasPermission("smflags.bypass")) {
         return;
      }

      // Chat spam
      if (this.isFlagEnabled("chat_spam")) {
         this.manager.getTracker().trackAction(player.getUniqueId(), FlagType.CHAT_SPAM, System.currentTimeMillis());
         
         int threshold = this.getEffectiveThreshold("chat_spam", player.getUniqueId());
         int timeWindow = this.getTimeWindow("chat_spam");
         int count = this.manager.getTracker().getActionCount(player.getUniqueId(), FlagType.CHAT_SPAM, timeWindow * 60000L);
         
         if (count >= threshold) {
            this.triggerFlag(FlagEvent.builder()
               .playerId(player.getUniqueId())
               .playerName(player.getName())
               .flagType(FlagType.CHAT_SPAM)
               .value(count)
               .details(count + " сообщений за " + timeWindow + " минут")
               .build());
         }
      }

      // Repeat messages
      if (this.isFlagEnabled("chat_repeat")) {
         String lastMsg = this.lastMessages.get(player.getUniqueId());
         if (lastMsg != null && lastMsg.equals(event.getMessage())) {
            this.manager.getTracker().trackAction(player.getUniqueId(), FlagType.CHAT_REPEAT, System.currentTimeMillis());
            
            int threshold = this.getEffectiveThreshold("chat_repeat", player.getUniqueId());
             int timeWindow = this.getTimeWindow("chat_repeat");
             int count = this.manager.getTracker().getActionCount(player.getUniqueId(), FlagType.CHAT_REPEAT, timeWindow * 60000L);
            
            if (count >= threshold) {
               this.triggerFlag(FlagEvent.builder()
                  .playerId(player.getUniqueId())
                  .playerName(player.getName())
                  .flagType(FlagType.CHAT_REPEAT)
                  .value(count)
                  .details(count + " повторений за " + timeWindow + " минут")
                  .build());
            }
         }
         this.lastMessages.put(player.getUniqueId(), event.getMessage());
      }
   }

   // Boat with chest events
   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onVehicleEnter(VehicleEnterEvent event) {
      if (!(event.getEntered() instanceof Player player)) {
         return;
      }
      if (player.hasPermission("smflags.bypass")) {
         return;
      }
      if (!this.isFlagEnabled("boat_chest_enter")) {
         return;
      }

      Entity vehicle = event.getVehicle();

      if (this.isChestBoatEntity(vehicle)) {
         String name = this.getEntityDisplayName(vehicle);
         String items = this.scanEntityInventory(vehicle);
         this.triggerFlag(FlagEvent.builder()
            .playerId(player.getUniqueId())
            .playerName(player.getName())
            .flagType(FlagType.BOAT_CHEST_ENTER)
            .location(vehicle.getLocation())
            .details("Сел в " + name + items)
            .build());
      }

      if (vehicle instanceof ChestedHorse chestedHorse && chestedHorse.isCarryingChest()) {
         String name = this.getEntityDisplayName(vehicle);
         String items = this.scanEntityInventory(vehicle);
         this.triggerFlag(FlagEvent.builder()
            .playerId(player.getUniqueId())
            .playerName(player.getName())
            .flagType(FlagType.BOAT_CHEST_ENTER)
            .location(vehicle.getLocation())
            .details("Сел на " + name + items)
            .build());
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onVehicleExit(VehicleExitEvent event) {
      if (!(event.getExited() instanceof Player player)) {
         return;
      }
      if (player.hasPermission("smflags.bypass")) {
         return;
      }
      if (!this.isFlagEnabled("boat_chest_exit")) {
         return;
      }

      Entity vehicle = event.getVehicle();

      if (this.isChestBoatEntity(vehicle)) {
         String name = this.getEntityDisplayName(vehicle);
         String items = this.scanEntityInventory(vehicle);
         this.triggerFlag(FlagEvent.builder()
            .playerId(player.getUniqueId())
            .playerName(player.getName())
            .flagType(FlagType.BOAT_CHEST_EXIT)
            .location(vehicle.getLocation())
            .details("Вышел из " + name + items)
            .build());
      }

      if (vehicle instanceof ChestedHorse chestedHorse && chestedHorse.isCarryingChest()) {
         String name = this.getEntityDisplayName(vehicle);
         String items = this.scanEntityInventory(vehicle);
         this.triggerFlag(FlagEvent.builder()
            .playerId(player.getUniqueId())
            .playerName(player.getName())
            .flagType(FlagType.BOAT_CHEST_EXIT)
            .location(vehicle.getLocation())
            .details("Слез с " + name + items)
            .build());
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onEntityInteract(PlayerInteractEntityEvent event) {
      Player player = event.getPlayer();
      if (player.hasPermission("smflags.bypass")) {
         return;
      }

      // Storage interact is now logged on inventory open/close only
      // No per-interact logging needed
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onPlayerInteract(PlayerInteractEvent event) {
      Player player = event.getPlayer();
      if (player.hasPermission("smflags.bypass")) {
         return;
      }
      if (!this.isFlagEnabled("boat_chest_place")) {
         return;
      }

      Action action = event.getAction();
      if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
         return;
      }

      ItemStack item = event.getItem();
      if (!this.isChestBoatItem(item)) {
         return;
      }

      this.cleanupPendingChestBoatPlacements();

      Location baseLocation = event.getClickedBlock() != null
         ? event.getClickedBlock().getLocation().add(0.5, 1.0, 0.5)
         : player.getLocation();

      this.pendingChestBoatPlacements.put(player.getUniqueId(), new PendingChestBoatPlacement(
         player.getUniqueId(),
         player.getWorld().getName(),
         baseLocation.clone(),
         System.currentTimeMillis(),
         item.getType().name()
      ));
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onBigDataItemInteract(PlayerInteractEvent event) {
      Player player = event.getPlayer();
      if (player.hasPermission("smflags.bypass") || !this.isFlagEnabled("big_data_item_interact")) {
         return;
      }

      Action action = event.getAction();
      if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
         return;
      }
      if (event.getHand() != EquipmentSlot.HAND) {
         return;
      }

      ItemStack item = event.getItem();
      if (item == null || item.getType().isAir()) {
         return;
      }

      BigDataScanResult result = this.scanBigDataItem(item,
         this.getBigDataMaxDepth(),
         this.getBigDataMaxItemsScan());

      int threshold = Math.max(1, this.getThreshold("big_data_item_interact"));
      if (result == null || result.score < threshold) {
         return;
      }

      Location location = event.getClickedBlock() != null ? event.getClickedBlock().getLocation() : player.getLocation();
      this.triggerFlag(FlagEvent.builder()
         .playerId(player.getUniqueId())
         .playerName(player.getName())
         .flagType(FlagType.BIG_DATA_ITEM_INTERACT)
         .location(location)
         .value(result.score)
         .details(result.details)
         .build());
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onVehicleCreate(VehicleCreateEvent event) {
      if (!this.isFlagEnabled("boat_chest_place")) {
         return;
      }
      if (!this.isChestBoatEntity(event.getVehicle())) {
         return;
      }

      long now = System.currentTimeMillis();
      this.cleanupPendingChestBoatPlacements();

      PendingChestBoatPlacement matched = null;
      Iterator<PendingChestBoatPlacement> iterator = this.pendingChestBoatPlacements.values().iterator();
      while (iterator.hasNext()) {
         PendingChestBoatPlacement pending = iterator.next();
         if (now - pending.timestamp > 3000) {
            iterator.remove();
            continue;
         }

         if (!event.getVehicle().getWorld().getName().equals(pending.worldName)) {
            continue;
         }

         if (pending.location.distanceSquared(event.getVehicle().getLocation()) <= 16.0) {
            matched = pending;
            iterator.remove();
            break;
         }
      }

      if (matched == null) {
         return;
      }

      Player player = null;
      for (Player online : event.getVehicle().getWorld().getPlayers()) {
         if (online.getUniqueId().equals(matched.playerId)) {
            player = online;
            break;
         }
      }

      if (player == null || player.hasPermission("smflags.bypass")) {
         return;
      }

      this.triggerFlag(FlagEvent.builder()
         .playerId(player.getUniqueId())
         .playerName(player.getName())
         .flagType(FlagType.BOAT_CHEST_PLACE)
         .location(event.getVehicle().getLocation())
         .details("Разместил: " + this.getEntityDisplayName(event.getVehicle()))
         .build());
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onVehicleDestroy(VehicleDestroyEvent event) {
      if (!this.isFlagEnabled("boat_chest_interact")) {
         return;
      }

      Entity vehicle = event.getVehicle();
      boolean isStorageVehicle = this.isChestBoatEntity(vehicle)
         || (vehicle instanceof ChestedHorse ch && ch.isCarryingChest());

      if (!isStorageVehicle) {
         return;
      }

      Entity attacker = event.getAttacker();
      if (!(attacker instanceof Player player)) {
         return;
      }
      if (player.hasPermission("smflags.bypass")) {
         return;
      }

      String vehicleName = this.getEntityDisplayName(vehicle);
      String items = this.scanEntityInventory(vehicle);
      this.triggerFlag(FlagEvent.builder()
         .playerId(player.getUniqueId())
         .playerName(player.getName())
         .flagType(FlagType.BOAT_CHEST_INTERACT)
         .location(vehicle.getLocation())
         .details("Сломал " + vehicleName + items)
         .build());
   }

   // Wither summon
   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onCreatureSpawn(CreatureSpawnEvent event) {
      if (event.getEntity() instanceof Wither wither && event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.BUILD_WITHER) {
         // Try to find nearby player
         Player nearestPlayer = null;
         double minDistance = 10.0;
         Location loc = event.getLocation();
         
         for (Player player : loc.getWorld().getPlayers()) {
            double distance = player.getLocation().distance(loc);
            if (distance < minDistance) {
               minDistance = distance;
               nearestPlayer = player;
            }
         }
         
         if (nearestPlayer != null) {
            // Remember who spawned this wither
            this.witherOwners.put(wither.getUniqueId(), nearestPlayer.getUniqueId());

            if (!nearestPlayer.hasPermission("smflags.bypass")) {
               org.bukkit.World.Environment env = loc.getWorld().getEnvironment();
               FlagType witherFlagType;
               String flagKey;
               String worldName;
               switch (env) {
                  case NETHER -> { witherFlagType = FlagType.WITHER_SUMMON_NETHER; flagKey = "wither_summon_nether"; worldName = "Незер"; }
                  case THE_END -> { witherFlagType = FlagType.WITHER_SUMMON_END; flagKey = "wither_summon_end"; worldName = "Энд"; }
                  default -> { witherFlagType = FlagType.WITHER_SUMMON_OVERWORLD; flagKey = "wither_summon_overworld"; worldName = "Обычный мир"; }
               };

               if (this.isFlagEnabled(flagKey)) {
                  Player finalPlayer = nearestPlayer;
                  this.manager.getTracker().trackAction(finalPlayer.getUniqueId(), witherFlagType, System.currentTimeMillis());

                  int threshold = this.getEffectiveThreshold(flagKey, finalPlayer.getUniqueId());
                  int timeWindow = this.getTimeWindow(flagKey);
                  int count = this.manager.getTracker().getActionCount(finalPlayer.getUniqueId(), witherFlagType, timeWindow * 60000L);

                  if (count >= threshold) {
                     this.triggerFlag(FlagEvent.builder()
                        .playerId(finalPlayer.getUniqueId())
                        .playerName(finalPlayer.getName())
                        .flagType(witherFlagType)
                        .location(loc)
                        .value(count)
                        .details(count + " визеров призвано за " + timeWindow + " минут (" + worldName + ")")
                        .build());
                  }
               }
            }
         }
      }
   }

   // Container explosion/burn + crystal explosion
   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onEntityExplode(EntityExplodeEvent event) {
      // --- End Crystal block destruction ---
      if (event.getEntity() instanceof EnderCrystal crystal && this.isFlagEnabled("end_crystal_explode")) {
         UUID ownerUuid = this.crystalOwners.remove(crystal.getUniqueId());
         if (ownerUuid != null) {
            Player owner = this.plugin.getServer().getPlayer(ownerUuid);
            String ownerName = owner != null ? owner.getName() : "Unknown";

            int blocksDestroyed = event.blockList().size();
            this.manager.getTracker().trackAction(ownerUuid, FlagType.END_CRYSTAL_EXPLODE, System.currentTimeMillis());

            int threshold = this.getEffectiveThreshold("end_crystal_explode", ownerUuid);
            int timeWindow = this.getTimeWindow("end_crystal_explode");
            int count = this.manager.getTracker().getActionCount(ownerUuid, FlagType.END_CRYSTAL_EXPLODE, timeWindow * 60000L);

            if (count >= threshold) {
               this.triggerFlag(FlagEvent.builder()
                  .playerId(ownerUuid)
                  .playerName(ownerName)
                  .flagType(FlagType.END_CRYSTAL_EXPLODE)
                  .location(crystal.getLocation())
                  .value(count)
                  .details(count + " взрывов кристаллов за " + timeWindow + " мин. Блоков разрушено: " + blocksDestroyed)
                  .build());
            }
         }
      }

      // --- Container destruction tracking ---
      int containerCount = 0;
      Location firstContainer = null;
      
      for (org.bukkit.block.Block block : event.blockList()) {
         if (this.isContainer(block.getType())) {
            containerCount++;
            if (firstContainer == null) {
               firstContainer = block.getLocation();
            }
         }
      }

      if (containerCount == 0 || firstContainer == null) {
         return;
      }

      // Wither destroying containers
      if (event.getEntity() instanceof Wither wither && this.isFlagEnabled("wither_container_destroy")) {
         UUID ownerUuid = this.witherOwners.get(wither.getUniqueId());
         String ownerName = "Неизвестный";
         if (ownerUuid != null) {
            Player owner = this.plugin.getServer().getPlayer(ownerUuid);
            if (owner != null) {
               ownerName = owner.getName();
            }
         }
         this.triggerFlag(FlagEvent.builder()
            .playerId(ownerUuid)
            .playerName(ownerName)
            .flagType(FlagType.WITHER_CONTAINER_DESTROY)
            .location(firstContainer)
            .value(containerCount)
            .details("Визер уничтожил " + containerCount + " контейнеров. Призвал: " + ownerName)
            .build());
      }

      // Container explosion — any entity explosion type (TNT, Creeper, Fireball, Wind Charge, etc.)
      if (this.isFlagEnabled("container_explosion")) {
         Player responsible = this.resolveExplosionSource(event.getEntity());

         // Fallback: find nearest non-bypassed player within 16 blocks
         if (responsible == null) {
            responsible = this.findNearestNonBypassedPlayer(event.getLocation(), 16.0);
         }

         if (responsible != null) {
            int threshold = this.getEffectiveThreshold("container_explosion", responsible.getUniqueId());
            if (containerCount >= threshold) {
               String entityType = event.getEntity().getType().name().toLowerCase().replace('_', ' ');
               this.triggerFlag(FlagEvent.builder()
                  .playerId(responsible.getUniqueId())
                  .playerName(responsible.getName())
                  .flagType(FlagType.CONTAINER_EXPLOSION)
                  .location(firstContainer)
                  .value(containerCount)
                  .details(containerCount + " контейнеров уничтожено взрывом (" + entityType + ")")
                  .build());
            }
         } else {
            // No responsible player found — still fire with System so admins are notified
            int threshold = this.getThreshold("container_explosion");
            if (containerCount >= threshold) {
               String entityType = event.getEntity().getType().name().toLowerCase().replace('_', ' ');
               this.triggerFlag(FlagEvent.builder()
                  .playerId(null)
                  .playerName("System")
                  .flagType(FlagType.CONTAINER_EXPLOSION)
                  .location(firstContainer)
                  .value(containerCount)
                  .details(containerCount + " контейнеров уничтожено взрывом (" + entityType + "). Источник неизвестен")
                  .build());
            }
         }
      }
   }

   // Block explosion (beds in nether/end, respawn anchors)
   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onBlockExplode(org.bukkit.event.block.BlockExplodeEvent event) {
      if (!this.isFlagEnabled("container_explosion")) {
         return;
      }

      int containerCount = 0;
      Location firstContainer = null;

      for (Block block : event.blockList()) {
         if (this.isContainer(block.getType())) {
            containerCount++;
            if (firstContainer == null) {
               firstContainer = block.getLocation();
            }
         }
      }

      if (containerCount == 0 || firstContainer == null) {
         return;
      }

      // Find nearest non-bypassed player within 16 blocks
      Player responsible = this.findNearestNonBypassedPlayer(event.getBlock().getLocation(), 16.0);

      if (responsible != null) {
         int threshold = this.getEffectiveThreshold("container_explosion", responsible.getUniqueId());
         if (containerCount >= threshold) {
            String blockType = event.getBlock().getType().name().toLowerCase().replace('_', ' ');
            this.triggerFlag(FlagEvent.builder()
               .playerId(responsible.getUniqueId())
               .playerName(responsible.getName())
               .flagType(FlagType.CONTAINER_EXPLOSION)
               .location(firstContainer)
               .value(containerCount)
               .details(containerCount + " контейнеров уничтожено взрывом блока (" + blockType + ")")
               .build());
         }
      } else {
         int threshold = this.getThreshold("container_explosion");
         if (containerCount >= threshold) {
            String blockType = event.getBlock().getType().name().toLowerCase().replace('_', ' ');
            this.triggerFlag(FlagEvent.builder()
               .playerId(null)
               .playerName("System")
               .flagType(FlagType.CONTAINER_EXPLOSION)
               .location(firstContainer)
               .value(containerCount)
               .details(containerCount + " контейнеров уничтожено взрывом блока (" + blockType + "). Источник неизвестен")
               .build());
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onBlockBurn(BlockBurnEvent event) {
      if (!this.isFlagEnabled("container_burn")) {
         return;
      }
      
      if (this.isContainer(event.getBlock().getType())) {
         this.triggerFlag(FlagEvent.builder()
            .playerId(null)
            .playerName("System")
            .flagType(FlagType.CONTAINER_BURN)
            .location(event.getBlock().getLocation())
            .details("Контейнер сгорел")
            .build());
      }
   }

   // Container / shulker break by hand/tool (only if container had items)
   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onBlockBreak(BlockBreakEvent event) {
      Player player = event.getPlayer();
      if (player.hasPermission("smflags.bypass")) {
         return;
      }

      Block block = event.getBlock();
      Material mat = block.getType();
      if (!this.isContainer(mat)) {
         return;
      }

      // Determine which flag type to use
      boolean isShulker = this.isShulkerBox(mat);
      FlagType flagType = isShulker ? FlagType.SHULKER_BREAK : FlagType.CONTAINER_BREAK;
      String flagKey = flagType.getKey();

      if (!this.isFlagEnabled(flagKey)) {
         return;
      }

      // Check if the container had items
      if (!(block.getState() instanceof Container container)) {
         return;
      }

      Inventory containerInv = container.getInventory();
      boolean hasItems = false;
      StringBuilder itemsList = new StringBuilder();
      int itemCount = 0;
      for (ItemStack item : containerInv.getContents()) {
         if (item != null && !item.getType().isAir()) {
            hasItems = true;
            if (itemCount > 0) itemsList.append(", ");
            itemsList.append(item.getType().name()).append(" x").append(item.getAmount());
            itemCount++;
            if (itemCount >= 5) {
               itemsList.append("...");
               break;
            }
         }
      }

      if (!hasItems) {
         return;
      }

      this.manager.getTracker().trackAction(player.getUniqueId(), flagType, System.currentTimeMillis());

      int threshold = this.getEffectiveThreshold(flagKey, player.getUniqueId());
      int timeWindow = this.getTimeWindow(flagKey);
      int count = this.manager.getTracker().getActionCount(player.getUniqueId(), flagType, timeWindow * 60000L);

      String label = isShulker ? " шалкеров" : " контейнеров";

      if (count >= threshold) {
         this.triggerFlag(FlagEvent.builder()
            .playerId(player.getUniqueId())
            .playerName(player.getName())
            .flagType(flagType)
            .location(block.getLocation())
            .value(count)
            .details(count + label + " с предметами сломано за " + timeWindow + " мин. Предметы: [" + itemsList + "]")
            .build());
      }
   }

   // Admin commands (LiteBans)
   @EventHandler(priority = EventPriority.MONITOR)
   public void onCommand(PlayerCommandPreprocessEvent event) {
      Player player = event.getPlayer();
      if (!player.hasPermission("litebans.ban") && !player.hasPermission("litebans.warn") && !player.hasPermission("litebans.mute")) {
         return;
      }

      String command = event.getMessage().toLowerCase();
      String[] parts = command.split(" ");
      if (parts.length < 2) {
         return;
      }

      String cmd = parts[0].substring(1); // Remove /
      String target = parts[1];

      FlagType flagType = null;
      if (cmd.equals("ban") || cmd.equals("tempban")) {
         if (this.isFlagEnabled("admin_ban")) {
            flagType = FlagType.ADMIN_BAN;
         }
      } else if (cmd.equals("warn") || cmd.equals("tempwarn")) {
         if (this.isFlagEnabled("admin_warn")) {
            flagType = FlagType.ADMIN_WARN;
         }
      } else if (cmd.equals("mute") || cmd.equals("tempmute")) {
         if (this.isFlagEnabled("admin_mute")) {
            flagType = FlagType.ADMIN_MUTE;
         }
      }

      if (flagType != null) {
         FlagType finalType = flagType;
         this.manager.getTracker().trackAction(player.getUniqueId(), finalType, System.currentTimeMillis());
         
         int threshold = this.getEffectiveThreshold(finalType.getKey(), player.getUniqueId());
         int timeWindow = this.getTimeWindow(finalType.getKey());
         int count = this.manager.getTracker().getActionCount(player.getUniqueId(), finalType, timeWindow * 60000L);
         
         if (count >= threshold) {
            this.triggerFlag(FlagEvent.builder()
               .playerId(player.getUniqueId())
               .playerName(player.getName())
               .flagType(finalType)
               .value(count)
               .details(count + " наказаний за " + timeWindow + " минут. Последняя цель: " + target)
               .build());
         }
      }
   }

   // Ore pickup — accumulates across multiple pickup events within a 30-second window
   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onPlayerPickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
      if (!(event.getEntity() instanceof Player player)) {
         return;
      }
      if (player.hasPermission("smflags.bypass")) {
         return;
      }

      Material type = event.getItem().getItemStack().getType();
      int amount = event.getItem().getItemStack().getAmount();

      // Diamond ore
      if ((type == Material.DIAMOND_ORE || type == Material.DEEPSLATE_DIAMOND_ORE) && this.isFlagEnabled("ore_pickup")) {
         int threshold = this.getEffectiveThreshold("ore_pickup", player.getUniqueId());
         OreAccumulator acc = this.oreAccumulators.computeIfAbsent(player.getUniqueId(), k -> new OreAccumulator());
         int totalAmount = acc.add(amount);
         if (totalAmount >= threshold) {
            this.triggerFlag(FlagEvent.builder()
               .playerId(player.getUniqueId())
               .playerName(player.getName())
               .flagType(FlagType.ORE_PICKUP)
               .location(event.getItem().getLocation())
               .value(totalAmount)
               .details("Подобрал " + totalAmount + " алмазной руды")
               .build());
         }
      }

      // Ancient debris
      if (type == Material.ANCIENT_DEBRIS && this.isFlagEnabled("ancient_debris_pickup")) {
         int threshold = this.getEffectiveThreshold("ancient_debris_pickup", player.getUniqueId());
         OreAccumulator acc = this.debrisAccumulators.computeIfAbsent(player.getUniqueId(), k -> new OreAccumulator());
         int totalAmount = acc.add(amount);
         if (totalAmount >= threshold) {
            this.triggerFlag(FlagEvent.builder()
               .playerId(player.getUniqueId())
               .playerName(player.getName())
               .flagType(FlagType.ANCIENT_DEBRIS_PICKUP)
               .location(event.getItem().getLocation())
               .value(totalAmount)
               .details("Подобрал " + totalAmount + " древних обломков")
               .build());
         }
      }

      // Rare item pickup from ground
      if (this.isFlagEnabled("rare_item") && this.hasRareItemRules()) {
         ItemStack pickedUp = event.getItem().getItemStack();
         boolean triggered = false;
         String detail = null;

         // 1) Material-based check (with min-amount)
         Integer minAmount = this.rareItemMinAmounts.get(type);
         if (minAmount != null) {
            int totalInInventory = this.countMaterialInInventory(player, type) + amount;
            if (totalInInventory >= minAmount) {
               triggered = true;
               if (minAmount > 1) {
                  detail = type.name() + " x" + amount + " (всего: " + totalInInventory + ")";
               } else {
                  detail = type.name() + " x" + amount;
               }
            }
         }

         // 2) Enchanted book check
         if (!triggered && type == Material.ENCHANTED_BOOK && !this.rareBookEnchantments.isEmpty()) {
            if (pickedUp.getItemMeta() instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta meta) {
               for (java.util.Map.Entry<org.bukkit.enchantments.Enchantment, Integer> ench : meta.getStoredEnchants().entrySet()) {
                  String enchKey = ench.getKey().getKey().getKey();
                  Integer minLevel = this.rareBookEnchantments.get(enchKey);
                  if (minLevel != null && ench.getValue() >= minLevel) {
                     triggered = true;
                     detail = "Книга (" + enchKey + " " + ench.getValue() + ")";
                     break;
                  }
               }
            }
         }

         // 3) Enchanted item check (non-books — bows, crossbows, etc.)
         if (!triggered) {
            java.util.Map<String, Integer> reqEnchants = this.rareEnchantedItems.get(type);
            if (reqEnchants != null && !reqEnchants.isEmpty()) {
               for (java.util.Map.Entry<org.bukkit.enchantments.Enchantment, Integer> ench : pickedUp.getEnchantments().entrySet()) {
                  String enchKey = ench.getKey().getKey().getKey();
                  Integer minLevel = reqEnchants.get(enchKey);
                  if (minLevel != null && ench.getValue() >= minLevel) {
                     triggered = true;
                     detail = type.name() + " (" + enchKey + " " + ench.getValue() + ")";
                     break;
                  }
               }
            }
         }

         if (triggered) {
            this.triggerFlag(FlagEvent.builder()
               .playerId(player.getUniqueId())
               .playerName(player.getName())
               .flagType(FlagType.RARE_ITEM)
               .location(event.getItem().getLocation())
               .value(amount)
               .details("Подобрал с земли: " + detail)
               .build());
         }
      }
   }

   // Whitelist add
   @EventHandler(priority = EventPriority.MONITOR)
   public void onWhitelistCommand(PlayerCommandPreprocessEvent event) {
      if (!event.getPlayer().hasPermission("minecraft.command.whitelist")) {
         return;
      }
      if (!this.isFlagEnabled("whitelist_add")) {
         return;
      }

      String command = event.getMessage().toLowerCase();
      if (command.startsWith("/whitelist add ")) {
         String[] parts = command.split(" ");
         if (parts.length >= 3) {
            String target = parts[2];
            this.triggerFlag(FlagEvent.builder()
               .playerId(event.getPlayer().getUniqueId())
               .playerName(event.getPlayer().getName())
               .flagType(FlagType.WHITELIST_ADD)
               .details("Добавил " + target + " в вайтлист")
               .build());
         }
      }
   }

   // OP grant
   @EventHandler(priority = EventPriority.MONITOR)
   public void onOpCommand(PlayerCommandPreprocessEvent event) {
      if (!event.getPlayer().hasPermission("minecraft.command.op")) {
         return;
      }
      if (!this.isFlagEnabled("op_grant")) {
         return;
      }

      String command = event.getMessage().toLowerCase();
      if (command.startsWith("/op ")) {
         String[] parts = command.split(" ");
         if (parts.length >= 2) {
            String target = parts[1];
            this.triggerFlag(FlagEvent.builder()
               .playerId(event.getPlayer().getUniqueId())
               .playerName(event.getPlayer().getName())
               .flagType(FlagType.OP_GRANT)
               .details("Выдал OP игроку " + target)
               .build());
         }
      }
   }

   private void cleanupPendingChestBoatPlacements() {
      long now = System.currentTimeMillis();
      Iterator<Map.Entry<UUID, PendingChestBoatPlacement>> iterator = this.pendingChestBoatPlacements.entrySet().iterator();
      while (iterator.hasNext()) {
         Map.Entry<UUID, PendingChestBoatPlacement> entry = iterator.next();
         if (now - entry.getValue().timestamp > 3000) {
            iterator.remove();
         }
      }
   }

   private boolean isChestBoatEntity(Entity entity) {
      if (entity == null) return false;
      String name = entity.getType().name();
      return name.equals("CHEST_BOAT") || name.endsWith("_CHEST_BOAT") || name.endsWith("_CHEST_RAFT");
   }

   private boolean isChestBoatItem(ItemStack item) {
      if (item == null || item.getType().isAir()) {
         return false;
      }
      String name = item.getType().name();
      return name.endsWith("_CHEST_BOAT") || name.endsWith("_CHEST_RAFT") || name.equals("CHEST_BOAT");
   }

   private boolean isChestBoatInventory(Inventory inventory) {
      if (inventory == null) {
         return false;
      }
      InventoryHolder holder = inventory.getHolder();
      if (holder instanceof Entity entity && this.isChestBoatEntity(entity)) {
         return true;
      }
      if (holder instanceof ChestedHorse chestedHorse && chestedHorse.isCarryingChest()) {
         return true;
      }
      return inventory.getType().name().equals("CHEST_BOAT");
   }

   private Entity getChestBoatHolder(Inventory inventory) {
      if (inventory == null) {
         return null;
      }
      InventoryHolder holder = inventory.getHolder();
      if (holder instanceof Entity entity && this.isChestBoatEntity(entity)) {
         return entity;
      }
      if (holder instanceof ChestedHorse chestedHorse && chestedHorse.isCarryingChest()) {
         return chestedHorse;
      }
      return null;
   }

   private String describeInventoryClick(InventoryClickEvent event) {
      ItemStack current = event.getCurrentItem();
      ItemStack cursor = event.getCursor();
      if ((current == null || current.getType().isAir()) && (cursor == null || cursor.getType().isAir())) {
         return null;
      }

      StringBuilder builder = new StringBuilder("Действие: ").append(event.getAction());
      builder.append(", клик: ").append(event.getClick());
      if (current != null && !current.getType().isAir()) {
         builder.append(", слот: ").append(this.describeItem(current));
      }
      if (cursor != null && !cursor.getType().isAir()) {
         builder.append(", курсор: ").append(this.describeItem(cursor));
      }
      return builder.toString();
   }

   private String describeItem(ItemStack item) {
      if (item == null || item.getType().isAir()) {
         return "пусто";
      }
      return item.getType().name() + " x" + item.getAmount();
   }

   private boolean isFlagsMenuHolder(InventoryHolder holder) {
      if (holder == null) {
         return false;
      }
      if (holder instanceof FlagsMenuHolder) {
         return true;
      }
      return holder.getClass().getName().equals(FlagsMenuHolder.class.getName());
   }

   private boolean isFlagsHistoryMenuHolder(InventoryHolder holder) {
      if (holder == null) {
         return false;
      }
      if (holder instanceof FlagsHistoryMenuHolder) {
         return true;
      }
      return holder.getClass().getName().equals(FlagsHistoryMenuHolder.class.getName());
   }

   private void tryTeleportFromHistoryItem(Player player, PersistentDataContainer container) {
      if (container == null) {
         return;
      }

      String worldName = container.get(this.module.getHistoryWorldKey(), PersistentDataType.STRING);
      Double x = container.get(this.module.getHistoryXKey(), PersistentDataType.DOUBLE);
      Double y = container.get(this.module.getHistoryYKey(), PersistentDataType.DOUBLE);
      Double z = container.get(this.module.getHistoryZKey(), PersistentDataType.DOUBLE);

      if (worldName == null || x == null || y == null || z == null) {
         player.sendMessage(this.plugin.applyColors("&cДля этого события нет сохраненных координат."));
         return;
      }

      World world = this.plugin.getServer().getWorld(worldName);
      if (world == null) {
         player.sendMessage(this.plugin.applyColors("&cМир события недоступен: &f" + worldName));
         return;
      }

      String dimensionKey = world.getKey().asString();
      String tpCommand = "execute in " + dimensionKey + " run tp " + player.getName() + " " + x + " " + y + " " + z;

      this.plugin.getSchedulerManager().runGlobalTask("flags-history-teleport-" + player.getUniqueId(), () -> {
         boolean executed = this.plugin.getServer().dispatchCommand(this.plugin.getServer().getConsoleSender(), tpCommand);

         this.plugin.getSchedulerManager().runEntityTask(player, "flags-history-teleport-result-" + player.getUniqueId(), () -> {
            if (!player.isOnline()) {
               return;
            }

            if (!executed) {
               player.sendMessage(this.plugin.applyColors("&cНе удалось выполнить телепортацию через консоль."));
               return;
            }

            player.sendMessage(this.plugin.applyColors("&aТелепортировано в &f" + world.getName() + " &7(" + x.intValue() + ", " + y.intValue() + ", " + z.intValue() + ")"));
         });
      });
   }

   private BigDataScanResult scanBigDataItem(ItemStack root, int maxDepth, int maxItems) {
      BigDataScanBudget budget = new BigDataScanBudget(Math.max(8, maxItems));
      return this.scanBigDataItemRecursive(root, 0, Math.max(1, maxDepth), budget);
   }

   private BigDataScanResult scanBigDataItemRecursive(ItemStack item, int depth, int maxDepth, BigDataScanBudget budget) {
      if (item == null || item.getType().isAir() || !budget.tryConsume()) {
         return null;
      }

      BigDataScanResult direct = this.analyzeBookItem(item);
      if (direct != null) {
         return direct;
      }

      if (depth >= maxDepth) {
         return null;
      }

      if (!(item.getItemMeta() instanceof BundleMeta bundleMeta)) {
         return null;
      }

      int suspicious = 0;
      int totalScore = 0;
      List<String> snippets = new java.util.ArrayList<>();

      for (ItemStack nested : bundleMeta.getItems()) {
         BigDataScanResult nestedResult = this.scanBigDataItemRecursive(nested, depth + 1, maxDepth, budget);
         if (nestedResult == null) {
            continue;
         }
         suspicious++;
         totalScore += nestedResult.score;
         if (snippets.size() < 3) {
            snippets.add(nestedResult.shortText);
         }
      }

      int minSuspiciousItems = this.getBigDataBundleMinSuspiciousItems();
      int minTotalScore = this.getBigDataBundleMinTotalScore();
      if (suspicious < minSuspiciousItems && totalScore < minTotalScore) {
         return null;
      }

      String details = "Подозрительный bundle: вложений=" + suspicious + ", score=" + totalScore;
      if (!snippets.isEmpty()) {
         details += ", примеры: " + String.join("; ", snippets);
      }

      return new BigDataScanResult(
         Math.max(totalScore, suspicious * 5000),
         details,
         "bundle x" + suspicious
      );
   }

   private BigDataScanResult analyzeBookItem(ItemStack item) {
      Material type = item.getType();
      if (type != Material.WRITTEN_BOOK && type != Material.WRITABLE_BOOK) {
         return null;
      }

      if (!(item.getItemMeta() instanceof BookMeta bookMeta)) {
         return null;
      }

      int pages = bookMeta.getPageCount();
      int chars = 0;
      for (String page : bookMeta.getPages()) {
         if (page != null) {
            chars += page.length();
         }
      }

      int minPages = this.getBigDataMinBookPages();
      int minChars = this.getBigDataMinBookChars();
      if (pages < minPages && chars < minChars) {
         return null;
      }

      int score = Math.max(chars, pages * 400);
      String shortText = type.name() + " pages=" + pages + " chars=" + chars;
      String details = "Подозрительная книга: " + shortText + ", stack=" + item.getAmount();
      return new BigDataScanResult(score, details, shortText);
   }

   private int getBigDataMinBookPages() {
      return this.module.getConfig().getInt("flags.big_data_item_interact.min-book-pages", 40);
   }

   private int getBigDataMinBookChars() {
      return this.module.getConfig().getInt("flags.big_data_item_interact.min-book-characters", 8000);
   }

   private int getBigDataBundleMinSuspiciousItems() {
      return this.module.getConfig().getInt("flags.big_data_item_interact.bundle.min-suspicious-items", 4);
   }

   private int getBigDataBundleMinTotalScore() {
      return this.module.getConfig().getInt("flags.big_data_item_interact.bundle.min-total-score", 30000);
   }

   private int getBigDataMaxDepth() {
      return this.module.getConfig().getInt("flags.big_data_item_interact.bundle.max-depth", 3);
   }

   private int getBigDataMaxItemsScan() {
      return this.module.getConfig().getInt("flags.big_data_item_interact.bundle.max-items-scan", 128);
   }

   private void triggerFlag(FlagEvent event) {
      // Per-flag group restriction: if groups are configured, only trigger for matching tiers
      if (event.getPlayerId() != null) {
         java.util.List<String> groups = this.module.getConfig().getStringList(
            "flags." + event.getFlagType().getKey() + ".groups");
         if (groups != null && !groups.isEmpty()) {
            String tierKey = this.manager.getTierKey(event.getPlayerId());
            if (tierKey == null || !groups.contains(tierKey)) {
               return; // Player's tier is not in the allowed groups — skip
            }
         }
      }
      this.manager.triggerFlag(event);
   }

   private boolean isFlagEnabled(String flagKey) {
      return this.module.getConfig().getBoolean("flags." + flagKey + ".enabled", true);
   }

   private int getThreshold(String flagKey) {
      return this.module.getConfig().getInt("flags." + flagKey + ".threshold", 5);
   }

   /**
    * Get the effective threshold for a flag, adjusted by the player's playtime tier.
    * Lower playtime → lower threshold → flags fire more easily.
    */
   private int getEffectiveThreshold(String flagKey, UUID playerId) {
      // Spam/Flood/Container flags should be consistent for everyone (no newcomer penalty)
      if (flagKey.equals("chat_spam") || flagKey.equals("chat_repeat") || flagKey.equals("container_drop")) {
         return this.getThreshold(flagKey);
      }

      int baseThreshold = this.getThreshold(flagKey);
      double multiplier = this.manager.getThresholdMultiplier(playerId);
      int effective = (int) Math.max(1, Math.round(baseThreshold * multiplier));
      return effective;
   }

   private int getTimeWindow(String flagKey) {
      return this.module.getConfig().getInt("flags." + flagKey + ".time-window", 5);
   }

   private int getLavaMinHeight() {
      return this.module.getConfig().getInt("flags.lava_placement.min-height", 70);
   }

   private void rememberLavaSource(Player player, Block block) {
      if (player == null || block == null) {
         return;
      }
      this.cleanupPendingLavaSources();
      this.pendingLavaSources.put(this.toBlockKey(block), new PendingLavaSource(
         player.getUniqueId(),
         player.getName(),
         System.currentTimeMillis()
      ));
   }

   private void cleanupPendingLavaSources() {
      long now = System.currentTimeMillis();
      long ttlMillis = 10 * 60 * 1000L;
      Iterator<Map.Entry<String, PendingLavaSource>> iterator = this.pendingLavaSources.entrySet().iterator();
      while (iterator.hasNext()) {
         Map.Entry<String, PendingLavaSource> entry = iterator.next();
         if (now - entry.getValue().timestamp > ttlMillis) {
            iterator.remove();
         }
      }
   }

   private String toBlockKey(Block block) {
      return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
   }

   private static class PendingChestBoatPlacement {
      private final UUID playerId;
      private final String worldName;
      private final Location location;
      private final long timestamp;
      private final String itemType;

      private PendingChestBoatPlacement(UUID playerId, String worldName, Location location, long timestamp, String itemType) {
         this.playerId = playerId;
         this.worldName = worldName;
         this.location = location;
         this.timestamp = timestamp;
         this.itemType = itemType;
      }
   }

   private static class PendingLavaSource {
      private final UUID playerId;
      private final String playerName;
      private final long timestamp;

      private PendingLavaSource(UUID playerId, String playerName, long timestamp) {
         this.playerId = playerId;
         this.playerName = playerName;
         this.timestamp = timestamp;
      }
   }

   private static class BigDataScanBudget {
      private int remaining;

      private BigDataScanBudget(int remaining) {
         this.remaining = remaining;
      }

      private boolean tryConsume() {
         if (this.remaining <= 0) {
            return false;
         }
         this.remaining--;
         return true;
      }
   }

   private static class BigDataScanResult {
      private final int score;
      private final String details;
      private final String shortText;

      private BigDataScanResult(int score, String details, String shortText) {
         this.score = score;
         this.details = details;
         this.shortText = shortText;
      }
   }

   private String getEntityDisplayName(Entity entity) {
      if (entity == null) return "неизвестно";
      String typeName = entity.getType().name();
      return switch (typeName) {
         case "DONKEY" -> "Осёл";
         case "MULE" -> "Мул";
         case "LLAMA" -> "Лама";
         case "TRADER_LLAMA" -> "Лама торговца";
         default -> {
            if (this.isChestBoatEntity(entity)) yield "Лодка с сундуком";
            yield typeName;
         }
      };
   }

   private String scanEntityInventory(Entity entity) {
      if (!(entity instanceof InventoryHolder holder)) return "";
      Inventory inv = holder.getInventory();
      StringBuilder items = new StringBuilder();
      int count = 0;
      for (ItemStack item : inv.getContents()) {
         if (item != null && !item.getType().isAir()) {
            if (count > 0) items.append(", ");
            items.append(item.getType().name()).append(" x").append(item.getAmount());
            count++;
         }
      }
      if (count == 0) return " [Пусто]";
      return " [" + items + "]";
   }

   /**
    * Resolve the player responsible for an entity explosion.
    * Handles TNTPrimed (walks source chain), Creepers, Fireballs, WitherSkulls, End Crystals.
    */
   private Player resolveExplosionSource(Entity entity) {
      if (entity instanceof org.bukkit.entity.TNTPrimed tnt) {
         // Walk the source chain — TNT can ignite TNT
         Entity source = tnt.getSource();
         int depth = 0;
         while (source != null && depth < 16) {
            if (source instanceof Player player) {
               return player.hasPermission("smflags.bypass") ? null : player;
            }
            if (source instanceof org.bukkit.entity.TNTPrimed chainTnt) {
               source = chainTnt.getSource();
               depth++;
            } else {
               break;
            }
         }
      } else if (entity instanceof org.bukkit.entity.Creeper) {
         // Creeper explosion — find the target/nearest player
         return this.findNearestNonBypassedPlayer(entity.getLocation(), 10.0);
      } else if (entity instanceof org.bukkit.entity.Fireball fireball) {
         if (fireball.getShooter() instanceof Player player) {
            return player.hasPermission("smflags.bypass") ? null : player;
         }
      } else if (entity instanceof org.bukkit.entity.WitherSkull skull) {
         if (skull.getShooter() instanceof Player player) {
            return player.hasPermission("smflags.bypass") ? null : player;
         }
      } else if (entity instanceof EnderCrystal crystal) {
         UUID ownerUuid = this.crystalOwners.get(crystal.getUniqueId());
         if (ownerUuid != null) {
            Player owner = this.plugin.getServer().getPlayer(ownerUuid);
            if (owner != null && !owner.hasPermission("smflags.bypass")) {
               return owner;
            }
         }
      }
      return null;
   }

   /**
    * Find the nearest online player without smflags.bypass within the given radius.
    */
   private Player findNearestNonBypassedPlayer(Location center, double radius) {
      if (center == null || center.getWorld() == null) return null;
      Player nearest = null;
      double minDist = radius;
      for (Player player : center.getWorld().getPlayers()) {
         if (player.hasPermission("smflags.bypass")) continue;
         double dist = player.getLocation().distance(center);
         if (dist < minDist) {
            minDist = dist;
            nearest = player;
         }
      }
      return nearest;
   }

   private boolean isContainer(Material material) {
      return switch (material) {
         case CHEST, TRAPPED_CHEST, BARREL, SHULKER_BOX, WHITE_SHULKER_BOX,
              ORANGE_SHULKER_BOX, MAGENTA_SHULKER_BOX, LIGHT_BLUE_SHULKER_BOX,
              YELLOW_SHULKER_BOX, LIME_SHULKER_BOX, PINK_SHULKER_BOX,
              GRAY_SHULKER_BOX, LIGHT_GRAY_SHULKER_BOX, CYAN_SHULKER_BOX,
              PURPLE_SHULKER_BOX, BLUE_SHULKER_BOX, BROWN_SHULKER_BOX,
              GREEN_SHULKER_BOX, RED_SHULKER_BOX, BLACK_SHULKER_BOX,
              FURNACE, BLAST_FURNACE, SMOKER, HOPPER, DROPPER, DISPENSER -> true;
         default -> false;
      };
   }

   private boolean isShulkerBox(Material material) {
      return switch (material) {
         case SHULKER_BOX, WHITE_SHULKER_BOX, ORANGE_SHULKER_BOX,
              MAGENTA_SHULKER_BOX, LIGHT_BLUE_SHULKER_BOX,
              YELLOW_SHULKER_BOX, LIME_SHULKER_BOX, PINK_SHULKER_BOX,
              GRAY_SHULKER_BOX, LIGHT_GRAY_SHULKER_BOX, CYAN_SHULKER_BOX,
              PURPLE_SHULKER_BOX, BLUE_SHULKER_BOX, BROWN_SHULKER_BOX,
              GREEN_SHULKER_BOX, RED_SHULKER_BOX, BLACK_SHULKER_BOX -> true;
         default -> false;
      };
   }

   /**
    * Snapshot rare items currently in the player's inventory.
    * Returns a map of String key → total amount.
    * Keys: "MATERIAL_NAME" for materials, "BOOK:enchant" for enchanted books,
    *       "ITEM:MATERIAL:enchant" for enchanted items.
    */
   private java.util.Map<String, Integer> snapshotRareItems(Player player) {
      java.util.Map<String, Integer> snapshot = new java.util.HashMap<>();
      for (ItemStack item : player.getInventory().getContents()) {
         if (item == null || item.getType() == Material.AIR) continue;
         Material type = item.getType();
         int amount = item.getAmount();

         // Material-based rare items
         if (this.rareItemMinAmounts.containsKey(type)) {
            snapshot.merge(type.name(), amount, Integer::sum);
         }

         // Enchanted books — check stored enchantments
         if (type == Material.ENCHANTED_BOOK && !this.rareBookEnchantments.isEmpty()) {
            if (item.getItemMeta() instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta meta) {
               for (java.util.Map.Entry<org.bukkit.enchantments.Enchantment, Integer> ench : meta.getStoredEnchants().entrySet()) {
                  String enchKey = ench.getKey().getKey().getKey();
                  Integer minLevel = this.rareBookEnchantments.get(enchKey);
                  if (minLevel != null && ench.getValue() >= minLevel) {
                     snapshot.merge("BOOK:" + enchKey, amount, Integer::sum);
                  }
               }
            }
         }

         // Enchanted items (non-books) — check item enchantments
         java.util.Map<String, Integer> requiredEnchants = this.rareEnchantedItems.get(type);
         if (requiredEnchants != null && !requiredEnchants.isEmpty()) {
            for (java.util.Map.Entry<org.bukkit.enchantments.Enchantment, Integer> ench : item.getEnchantments().entrySet()) {
               String enchKey = ench.getKey().getKey().getKey();
               Integer minLevel = requiredEnchants.get(enchKey);
               if (minLevel != null && ench.getValue() >= minLevel) {
                  snapshot.merge("ITEM:" + type.name() + ":" + enchKey, amount, Integer::sum);
                  break; // Count item once even if multiple enchants match
               }
            }
         }
      }
      return snapshot;
   }

   /**
    * Check if any rare item rules are configured.
    */
   private boolean hasRareItemRules() {
      return !this.rareItemMinAmounts.isEmpty()
         || !this.rareBookEnchantments.isEmpty()
         || !this.rareEnchantedItems.isEmpty();
   }

   /**
    * Count total amount of a specific material in the player's inventory.
    */
   private int countMaterialInInventory(Player player, Material material) {
      int count = 0;
      for (ItemStack item : player.getInventory().getContents()) {
         if (item != null && item.getType() == material) {
            count += item.getAmount();
         }
      }
      return count;
   }

   /**
    * Format a rare item snapshot key into a human-readable string.
    */
   private String formatRareItemKey(String key) {
      if (key.startsWith("BOOK:")) {
         return "Книга (" + key.substring(5) + ")";
      } else if (key.startsWith("ITEM:")) {
         // ITEM:BOW:power → BOW (power)
         String[] parts = key.split(":", 3);
         return parts.length >= 3 ? parts[1] + " (" + parts[2] + ")" : key;
      } else {
         return key;
      }
   }

   /**
    * Snapshot the contents of an inventory as a material → total amount map.
    */
   private java.util.Map<Material, Integer> snapshotInventoryContents(Inventory inventory) {
      java.util.Map<Material, Integer> snapshot = new java.util.EnumMap<>(Material.class);
      for (ItemStack item : inventory.getContents()) {
         if (item != null && item.getType() != Material.AIR) {
            snapshot.merge(item.getType(), item.getAmount(), Integer::sum);
         }
      }
      return snapshot;
   }

   /**
    * Check if the inventory is an external container (not the player's own, not a plugin menu).
    */
   private boolean isExternalContainer(Inventory inventory) {
      InventoryHolder holder = inventory.getHolder();
      if (holder == null) return false;
      if (holder instanceof Player) return false;
      if (holder instanceof FlagsMenuHolder) return false;
      if (holder instanceof FlagsHistoryMenuHolder) return false;
      if (this.isFlagsMenuHolder(holder)) return false;
      if (this.isFlagsHistoryMenuHolder(holder)) return false;
      return true;
   }

   /**
    * Accumulates ore pickup amounts within a 30-second rolling window.
    * This ensures that picking up 3 stacks of 64 within 30 seconds counts as 192, not 64.
    */
   private static class OreAccumulator {
      private static final long WINDOW_MS = 30_000; // 30 seconds
      private final java.util.List<long[]> entries = new java.util.ArrayList<>(); // [timestamp, amount]

      /**
       * Add an amount and return the total accumulated amount within the time window.
       */
      public synchronized int add(int amount) {
         long now = System.currentTimeMillis();
         // Prune expired entries
         entries.removeIf(e -> (now - e[0]) > WINDOW_MS);
         // Add new entry
         entries.add(new long[] { now, amount });
         // Sum all entries in window
         int total = 0;
         for (long[] entry : entries) {
            total += (int) entry[1];
         }
         return total;
      }
   }
}

