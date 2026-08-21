package net.schalker.SMPS.modules.stats.listeners;

import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.listener.BaseListener;
import net.schalker.SMPS.modules.stats.StatsCategory;
import net.schalker.SMPS.modules.stats.StatsMenuHolder;
import net.schalker.SMPS.modules.stats.StatsMetric;
import net.schalker.SMPS.modules.stats.StatsModule;
import net.schalker.SMPS.modules.stats.TimePeriodMenuHolder;
import net.schalker.SMPS.modules.stats.TopMenuHolder;
import net.schalker.SMPS.modules.stats.TopSelectMenuHolder;
import net.schalker.SMPS.modules.stats.managers.StatsManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class StatsListener extends BaseListener {
   private final StatsManager manager;
   private final StatsModule module;

   public StatsListener(DoAPI plugin, StatsManager manager, StatsModule module) {
      super(plugin);
      this.manager = manager;
      this.module = module;
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onJoin(PlayerJoinEvent event) {
      this.manager.handleJoin(event.getPlayer());
      if (this.module.isTrackEnabled("achievements")) {
         this.module.refreshAchievements(event.getPlayer());
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onAdvancement(PlayerAdvancementDoneEvent event) {
      if (!this.module.isTrackEnabled("achievements")) {
         return;
      }
      this.module.refreshAchievements(event.getPlayer());
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onQuit(PlayerQuitEvent event) {
      this.manager.handleQuit(event.getPlayer());
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onMove(PlayerMoveEvent event) {
      if (!this.module.isTrackEnabled("distance")) {
         return;
      }
      this.manager.recordMove(event.getPlayer(), event.getFrom(), event.getTo());
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onBlockBreak(BlockBreakEvent event) {
      if (!this.module.isTrackEnabled("blocks-broken")) {
         return;
      }
      this.manager.incrementBlocksBroken(event.getPlayer());
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onBlockPlace(BlockPlaceEvent event) {
      if (!this.module.isTrackEnabled("blocks-placed")) {
         return;
      }
      this.manager.incrementBlocksPlaced(event.getPlayer());
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onDeath(PlayerDeathEvent event) {
      if (!this.module.isTrackEnabled("deaths")) {
         return;
      }
      Player player = event.getEntity();
      this.manager.incrementDeaths(player);
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onEntityDeath(EntityDeathEvent event) {
      Player killer = event.getEntity().getKiller();
      if (killer == null) {
         return;
      }
      if (event.getEntity() instanceof Player) {
         if (!this.module.isTrackEnabled("player-kills")) {
            return;
         }
         this.manager.incrementPlayerKills(killer);
      } else {
         if (!this.module.isTrackEnabled("mob-kills")) {
            return;
         }
         this.manager.incrementMobKills(killer);
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onCraft(CraftItemEvent event) {
      if (!this.module.isTrackEnabled("items-crafted")) {
         return;
      }
      if (!(event.getWhoClicked() instanceof Player player)) {
         return;
      }
      int amount = 0;
      if (event.getRecipe() != null && event.getRecipe().getResult() != null) {
         amount = event.getRecipe().getResult().getAmount();
      }
      if (amount <= 0) {
         amount = 1;
      }
      this.manager.incrementItemsCrafted(player, amount);
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onChat(AsyncPlayerChatEvent event) {
      if (!this.module.isTrackEnabled("chat-messages")) {
         return;
      }
      this.manager.incrementChat(event.getPlayer());
   }

   @EventHandler
   public void onInventoryClick(InventoryClickEvent event) {
      if (!(event.getWhoClicked() instanceof Player player)) {
         return;
      }
      Inventory top = event.getView().getTopInventory();
      if (top == null || top.getHolder() == null) {
         return;
      }

      if (top.getHolder() instanceof StatsMenuHolder
         || top.getHolder() instanceof TopSelectMenuHolder
         || top.getHolder() instanceof TimePeriodMenuHolder
         || top.getHolder() instanceof TopMenuHolder) {
         event.setCancelled(true);
      } else {
         return;
      }

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

      if (action.equals("open-stats")) {
         String uuidRaw = container.get(this.module.getTargetKey(), PersistentDataType.STRING);
         if (uuidRaw != null) {
            try {
               this.module.openStatsByUuid(player, java.util.UUID.fromString(uuidRaw));
               return;
            } catch (IllegalArgumentException ignored) {
               // fall through
            }
         }
         return;
      }

      if (top.getHolder() instanceof StatsMenuHolder holder) {
         if (action.equals("top-select")) {
            this.module.openTopSelect(player);
         } else if (action.equals("open-top")) {
            String metricKey = container.get(this.module.getMetricKey(), PersistentDataType.STRING);
            StatsMetric metric = StatsMetric.fromKey(metricKey);
            if (metric != null) {
               this.module.openTop(player, metric, 1);
            }
         } else if (action.equals("open-category-top")) {
            String categoryId = container.get(this.module.getCategoryKey(), PersistentDataType.STRING);
            StatsCategory category = this.module.getCategory(categoryId);
            if (category != null) {
               // Check if this is the time category - open period selection menu
               if (category.getId().equals("time")) {
                  this.module.openTimePeriodMenu(player, category, holder.getTargetId());
               } else {
                  this.module.openCategoryTop(player, category);
               }
            }
         }
         return;
      }

      if (top.getHolder() instanceof TopSelectMenuHolder) {
         if (action.equals("open-category")) {
            String categoryId = container.get(this.module.getCategoryKey(), PersistentDataType.STRING);
            StatsCategory category = this.module.getCategory(categoryId);
            if (category != null) {
               this.module.openCategoryMenu(player, category);
            }
            return;
         }
         if (action.equals("back-categories")) {
            this.module.openTopSelect(player);
            return;
         }
         if (action.equals("open-top")) {
            String metricKey = container.get(this.module.getMetricKey(), PersistentDataType.STRING);
            StatsMetric metric = StatsMetric.fromKey(metricKey);
            if (metric != null) {
               this.module.openTop(player, metric, 1);
            }
         }
         return;
      }

      if (top.getHolder() instanceof TimePeriodMenuHolder) {
         if (action.equals("open-top")) {
            String metricKey = container.get(this.module.getMetricKey(), PersistentDataType.STRING);
            StatsMetric metric = StatsMetric.fromKey(metricKey);
            if (metric != null) {
               this.module.openTop(player, metric, 1);
            }
         } else if (action.equals("back-to-profile")) {
            String uuidRaw = container.get(this.module.getTargetKey(), PersistentDataType.STRING);
            if (uuidRaw != null) {
               try {
                  this.module.openStatsByUuid(player, java.util.UUID.fromString(uuidRaw));
               } catch (IllegalArgumentException ignored) {
                  this.module.openStats(player, player.getName());
               }
            } else {
               this.module.openStats(player, player.getName());
            }
         }
         return;
      }

      if (top.getHolder() instanceof TopMenuHolder holder) {
         if (action.equals("open-top")) {
            String metricKey = container.get(this.module.getMetricKey(), PersistentDataType.STRING);
            StatsMetric selectedMetric = StatsMetric.fromKey(metricKey);
            if (selectedMetric != null) {
               this.module.openTop(player, selectedMetric, 1);
            }
            return;
         }

         StatsMetric metric = holder.getMetric();
         int page = holder.getPage();

         if (action.equals("top-back")) {
            this.module.openStats(player, player.getName());
            return;
         }

         if (metric == null) {
            return;
         }

         if (action.equals("top-prev")) {
            int targetPage = Math.max(1, page - 1);
            this.module.openTop(player, metric, targetPage);
         } else if (action.equals("top-next")) {
            int targetPage = page + 1;
            this.module.openTop(player, metric, targetPage);
         }
      }
   }

   @EventHandler
   public void onInventoryDrag(InventoryDragEvent event) {
      Inventory top = event.getView().getTopInventory();
      if (top == null || top.getHolder() == null) {
         return;
      }
      if (top.getHolder() instanceof StatsMenuHolder
         || top.getHolder() instanceof TopSelectMenuHolder
         || top.getHolder() instanceof TimePeriodMenuHolder
         || top.getHolder() instanceof TopMenuHolder) {
         event.setCancelled(true);
      }
   }
}
