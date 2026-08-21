package net.schalker.SMPS.modules.invsee;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.module.BaseModule;
import net.schalker.DoAPI.core.module.ModuleInfo;
import net.schalker.SMPS.modules.invsee.commands.EnderseeCommand;
import net.schalker.SMPS.modules.invsee.commands.InvseeCommand;
import net.schalker.SMPS.modules.invsee.listeners.InvseeListener;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public class InvseeModule extends BaseModule {

   public static final String PERMISSION_COMMAND = "sminvsee.command";
   public static final String PERMISSION_EDIT = "sminvsee.edit";
   public static final String PERMISSION_ENDER_COMMAND = "sminvsee.endersee";
   public static final String PERMISSION_ENDER_EDIT = "sminvsee.endersee.edit";

   public static final int MENU_SIZE = 54;
   public static final int ENDER_SIZE = 27;
   public static final int SLOT_INFO = 53;

   private FileConfiguration config;
   private InvseeListener listener;
   private PlayerDataInventoryStore playerDataStore;

   public InvseeModule(DoAPI plugin) {
      super(plugin, new ModuleInfo("SM_Invsee", "1.0.0", "SchalkerMC",
         "Просмотр и редактирование инвентаря игрока через меню /invsee"));
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.config = this.plugin.getModuleManager().loadModuleConfig("SM_Invsee");
      if (this.config == null) {
         this.config = new YamlConfiguration();
      }
      this.playerDataStore = new PlayerDataInventoryStore(this.plugin);

      this.listener = new InvseeListener(this.plugin, this);
      this.plugin.getListenerManager().registerListener(this.listener);
      this.plugin.getCommandManager().registerModuleCommand(new InvseeCommand(this.plugin, this));
      this.plugin.getCommandManager().registerModuleCommand(new EnderseeCommand(this.plugin, this));

      this.plugin.getDebugSystem().log("Invsee", "Модуль Invsee включен");
   }

   @Override
   public void onDisable() {
      super.onDisable();
      if (this.listener != null) {
         this.plugin.getListenerManager().unregisterListener(this.listener);
      }
      for (Player online : this.plugin.getServer().getOnlinePlayers()) {
         var holder = online.getOpenInventory().getTopInventory().getHolder();
         if (holder instanceof InvseeHolder || holder instanceof EnderseeHolder) {
            UUID id = online.getUniqueId();
            this.plugin.getSchedulerManager().runEntityTask(online, "invsee-close-" + id, online::closeInventory);
         }
      }
      this.plugin.getDebugSystem().log("Invsee", "Модуль Invsee выключен");
   }

   @Override
   public void reload() {
      super.reload();
      this.config = this.plugin.getModuleManager().loadModuleConfig("SM_Invsee");
      if (this.config == null) {
         this.config = new YamlConfiguration();
      }
   }

   public String getMessage(String key, String fallback) {
      String message = this.config != null ? this.config.getString("messages." + key) : null;
      if (message == null || message.isEmpty()) {
         message = fallback;
      }
      return this.plugin.applyColors(message);
   }

   public Target resolveTarget(String targetName) {
      Player exact = this.plugin.getServer().getPlayerExact(targetName);
      Player online = exact != null ? exact : this.plugin.getServer().getPlayer(targetName);
      if (online != null && online.isOnline()) {
         return new Target(online.getUniqueId(), online.getName(), online);
      }

      OfflinePlayer cached = this.plugin.getServer().getOfflinePlayerIfCached(targetName);
      OfflinePlayer offline = cached;
      if (offline == null || !this.playerDataStore.hasPlayerData(offline.getUniqueId())) {
         offline = this.plugin.getServer().getOfflinePlayer(targetName);
      }

      UUID targetId = offline.getUniqueId();
      if (!this.playerDataStore.hasPlayerData(targetId)) {
         return null;
      }
      String name = offline.getName() != null ? offline.getName() : targetName;
      return new Target(targetId, name, null);
   }

   public void openInvsee(Player viewer, Target target) {
      Player online = target.onlinePlayer();
      if (online != null && online.isOnline()) {
         openLiveInvsee(viewer, online);
         return;
      }
      openOfflineInvsee(viewer, target.targetId(), target.targetName());
   }

   public void scheduleSyncToTarget(Player viewer, InvseeHolder holder) {
      UUID targetId = holder.getTargetId();
      this.plugin.getSchedulerManager().runEntityTaskLater(viewer, "invsee-grab-" + viewer.getUniqueId(), () -> {
         Inventory inv = holder.getInventory();
         if (inv == null) {
            return;
         }
         InventorySnapshot snap = snapshotOfGui(inv);
         Player target = this.plugin.getServer().getPlayer(targetId);
         if (target != null && target.isOnline()) {
            this.plugin.getSchedulerManager().runEntityTask(target, "invsee-apply-" + targetId, () -> {
               if (!target.isOnline()) {
                  saveOfflineInventory(viewer, targetId, holder.getTargetName(), snap);
                  return;
               }
               applyToPlayer(target, snap);
               target.updateInventory();
            });
            return;
         }
         saveOfflineInventory(viewer, targetId, holder.getTargetName(), snap);
      }, 1L);
   }

   public void openEndersee(Player viewer, Target target) {
      Player online = target.onlinePlayer();
      if (online != null && online.isOnline()) {
         openLiveEndersee(viewer, online);
         return;
      }
      openOfflineEndersee(viewer, target.targetId(), target.targetName());
   }

   public void scheduleSyncEnderToTarget(Player viewer, EnderseeHolder holder) {
      UUID targetId = holder.getTargetId();
      this.plugin.getSchedulerManager().runEntityTaskLater(viewer, "endersee-grab-" + viewer.getUniqueId(), () -> {
         Inventory inv = holder.getInventory();
         if (inv == null) {
            return;
         }
         ItemStack[] snap = snapshotEnderGui(inv);
         Player target = this.plugin.getServer().getPlayer(targetId);
         if (target != null && target.isOnline()) {
            this.plugin.getSchedulerManager().runEntityTask(target, "endersee-apply-" + targetId, () -> {
               if (!target.isOnline()) {
                  saveOfflineEnder(viewer, targetId, holder.getTargetName(), snap);
                  return;
               }
               Inventory ender = target.getEnderChest();
               for (int i = 0; i < ENDER_SIZE; i++) {
                  ender.setItem(i, snap[i]);
               }
            });
            return;
         }
         saveOfflineEnder(viewer, targetId, holder.getTargetName(), snap);
      }, 1L);
   }

   public boolean isEditableSlot(int slot) {
      return slot >= 0 && slot <= 35;
   }

   private void openLiveInvsee(Player viewer, Player target) {
      UUID targetId = target.getUniqueId();
      String targetName = target.getName();

      this.plugin.getSchedulerManager().runEntityTask(target, "invsee-read-" + targetId, () -> {
         InventorySnapshot snap = snapshotOfPlayer(target);
         this.plugin.getSchedulerManager().runEntityTask(viewer, "invsee-open-" + viewer.getUniqueId(), () -> {
            if (viewer.isOnline()) {
               openInvseeMenu(viewer, targetId, targetName, snap);
            }
         });
      });
   }

   private void openOfflineInvsee(Player viewer, UUID targetId, String targetName) {
      this.plugin.getSchedulerManager().runEntityTask(viewer, "invsee-offline-open-" + viewer.getUniqueId(), () -> {
         if (!viewer.isOnline()) {
            return;
         }

         Player online = this.plugin.getServer().getPlayer(targetId);
         if (online != null && online.isOnline()) {
            openLiveInvsee(viewer, online);
            return;
         }

         try {
            InventorySnapshot snap = this.playerDataStore.loadInventory(targetId);
            openInvseeMenu(viewer, targetId, targetName, snap);
         } catch (IOException | RuntimeException ex) {
            sendPlayerDataError(viewer, targetName, "playerdata-load-failed",
               "&cНе удалось прочитать playerdata игрока &f{player}", ex);
         }
      });
   }

   private void openInvseeMenu(Player viewer, UUID targetId, String targetName, InventorySnapshot snap) {
      InvseeHolder holder = new InvseeHolder(targetId, targetName);
      String title = this.getMessage("menu-title", "&8Инвентарь: &f{player}")
         .replace("{player}", targetName);
      Inventory inv = Bukkit.createInventory(holder, MENU_SIZE, title);
      holder.setInventory(inv);
      applySnapshot(inv, snap);
      decorate(inv, targetId, targetName);
      viewer.openInventory(inv);
   }

   private void openLiveEndersee(Player viewer, Player target) {
      UUID targetId = target.getUniqueId();
      String targetName = target.getName();

      this.plugin.getSchedulerManager().runEntityTask(target, "endersee-read-" + targetId, () -> {
         ItemStack[] snap = new ItemStack[ENDER_SIZE];
         Inventory ender = target.getEnderChest();
         for (int i = 0; i < ENDER_SIZE; i++) {
            snap[i] = cloneItem(ender.getItem(i));
         }
         this.plugin.getSchedulerManager().runEntityTask(viewer, "endersee-open-" + viewer.getUniqueId(), () -> {
            if (viewer.isOnline()) {
               openEnderMenu(viewer, targetId, targetName, snap);
            }
         });
      });
   }

   private void openOfflineEndersee(Player viewer, UUID targetId, String targetName) {
      this.plugin.getSchedulerManager().runEntityTask(viewer, "endersee-offline-open-" + viewer.getUniqueId(), () -> {
         if (!viewer.isOnline()) {
            return;
         }

         Player online = this.plugin.getServer().getPlayer(targetId);
         if (online != null && online.isOnline()) {
            openLiveEndersee(viewer, online);
            return;
         }

         try {
            ItemStack[] snap = this.playerDataStore.loadEnderChest(targetId);
            openEnderMenu(viewer, targetId, targetName, snap);
         } catch (IOException | RuntimeException ex) {
            sendPlayerDataError(viewer, targetName, "playerdata-load-failed",
               "&cНе удалось прочитать playerdata игрока &f{player}", ex);
         }
      });
   }

   private void openEnderMenu(Player viewer, UUID targetId, String targetName, ItemStack[] snap) {
      EnderseeHolder holder = new EnderseeHolder(targetId, targetName);
      String title = this.getMessage("ender-menu-title", "&8Эндер-сундук: &f{player}")
         .replace("{player}", targetName);
      Inventory inv = Bukkit.createInventory(holder, ENDER_SIZE, title);
      holder.setInventory(inv);
      for (int i = 0; i < ENDER_SIZE; i++) {
         inv.setItem(i, cloneItem(snap[i]));
      }
      viewer.openInventory(inv);
   }

   private void saveOfflineInventory(Player viewer, UUID targetId, String targetName, InventorySnapshot snap) {
      try {
         this.playerDataStore.saveInventory(targetId, snap);
      } catch (IOException | RuntimeException ex) {
         sendPlayerDataError(viewer, targetName, "playerdata-save-failed",
            "&cНе удалось сохранить playerdata игрока &f{player}", ex);
      }
   }

   private void saveOfflineEnder(Player viewer, UUID targetId, String targetName, ItemStack[] snap) {
      try {
         this.playerDataStore.saveEnderChest(targetId, snap);
      } catch (IOException | RuntimeException ex) {
         sendPlayerDataError(viewer, targetName, "playerdata-save-failed",
            "&cНе удалось сохранить playerdata игрока &f{player}", ex);
      }
   }

   private void sendPlayerDataError(Player viewer, String targetName, String key, String fallback, Exception ex) {
      this.plugin.getDebugSystem().log("Invsee", "Playerdata error for " + targetName + ": " + ex.getMessage());
      if (viewer.isOnline()) {
         viewer.sendMessage(this.getMessage(key, fallback).replace("{player}", targetName));
      }
   }

   private InventorySnapshot snapshotOfPlayer(Player target) {
      PlayerInventory inv = target.getInventory();
      InventorySnapshot snap = new InventorySnapshot();
      for (int i = 0; i < InventorySnapshot.MAIN_SIZE; i++) {
         snap.main[i] = cloneItem(inv.getItem(i));
      }
      return snap;
   }

   private InventorySnapshot snapshotOfGui(Inventory inv) {
      InventorySnapshot snap = new InventorySnapshot();
      for (int i = 0; i < InventorySnapshot.MAIN_SIZE; i++) {
         snap.main[i] = cloneItem(inv.getItem(i));
      }
      return snap;
   }

   private ItemStack[] snapshotEnderGui(Inventory inv) {
      ItemStack[] snap = new ItemStack[ENDER_SIZE];
      for (int i = 0; i < ENDER_SIZE; i++) {
         snap[i] = cloneItem(inv.getItem(i));
      }
      return snap;
   }

   private void applySnapshot(Inventory inv, InventorySnapshot snap) {
      for (int i = 0; i < InventorySnapshot.MAIN_SIZE; i++) {
         inv.setItem(i, cloneItem(snap.main[i]));
      }
   }

   private void applyToPlayer(Player target, InventorySnapshot snap) {
      PlayerInventory inv = target.getInventory();
      for (int i = 0; i < InventorySnapshot.MAIN_SIZE; i++) {
         inv.setItem(i, cloneItem(snap.main[i]));
      }
   }

   private void decorate(Inventory inv, UUID targetId, String targetName) {
      ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
      ItemMeta fillerMeta = filler.getItemMeta();
      if (fillerMeta != null) {
         fillerMeta.setDisplayName(" ");
         filler.setItemMeta(fillerMeta);
      }
      for (int i = 36; i <= 53; i++) {
         if (i != SLOT_INFO) {
            inv.setItem(i, filler);
         }
      }
      inv.setItem(SLOT_INFO, createInfoHead(targetId, targetName));
   }

   private ItemStack createInfoHead(UUID targetId, String targetName) {
      ItemStack head = new ItemStack(Material.PLAYER_HEAD);
      SkullMeta meta = (SkullMeta) head.getItemMeta();
      if (meta != null) {
         OfflinePlayer owner = Bukkit.getOfflinePlayer(targetId);
         meta.setOwningPlayer(owner);
         meta.setDisplayName(this.plugin.applyColors("&e" + targetName));
         meta.setLore(List.of(
            this.plugin.applyColors("&7Инвентарь игрока &f" + targetName),
            this.plugin.applyColors("&8Изменения применяются мгновенно")
         ));
         head.setItemMeta(meta);
      }
      return head;
   }

   private static ItemStack cloneItem(ItemStack item) {
      return item == null ? null : item.clone();
   }

   public record Target(UUID targetId, String targetName, Player onlinePlayer) {
   }
}
