package site.deforce.SM_Clans.managers;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import site.deforce.SM_Clans.SM_Clans;
import site.deforce.SM_Clans.logging.ClanAuditLogger;
import site.deforce.SM_Clans.models.Clan;
import site.deforce.SM_Clans.models.ClanMember;
import site.deforce.SM_Clans.models.DefaultClanRole;

public class ClanEconomyManager {
   private final SM_Clans module;
   private final ClanManager clanManager;

   public ClanEconomyManager(SM_Clans module, ClanManager clanManager) {
      super();
      this.module = module;
      this.clanManager = clanManager;
   }

   public boolean isEnabled() {
      FileConfiguration config = this.module.getConfig();
      return config != null && config.getBoolean("economy.enabled", true);
   }

   private Set<Material> currencyMaterials() {
      Set<Material> result = new LinkedHashSet();
      FileConfiguration config = this.module.getConfig();
      List<String> names = config != null ? config.getStringList("economy.currency-materials") : null;
      if (names != null && !names.isEmpty()) {
         for(String name : names) {
            try {
               result.add(Material.valueOf(name.trim().toUpperCase()));
            } catch (IllegalArgumentException var7) {
            }
         }

         if (result.isEmpty()) {
            result.add(Material.DIAMOND_ORE);
         }

         return result;
      } else {
         result.add(Material.DIAMOND_ORE);
         result.add(Material.DEEPSLATE_DIAMOND_ORE);
         return result;
      }
   }

   private Material payoutMaterial() {
      FileConfiguration config = this.module.getConfig();
      String name = config != null ? config.getString("economy.payout-material", "DIAMOND_ORE") : "DIAMOND_ORE";

      try {
         return Material.valueOf(name.trim().toUpperCase());
      } catch (IllegalArgumentException var4) {
         return Material.DIAMOND_ORE;
      }
   }

   private int cost(String path, int def) {
      FileConfiguration config = this.module.getConfig();
      return config != null ? config.getInt("economy.costs." + path, def) : def;
   }

   public int getCreationCost() {
      return this.cost("clan-creation", 16);
   }

   public int getNameCost() {
      return this.cost("settings.name", 8);
   }

   public int getTagCost() {
      return this.cost("settings.tag", 8);
   }

   public int getDescriptionCost() {
      return this.cost("settings.description", 4);
   }

   public int getBannerColorCost() {
      return this.cost("settings.banner-color", 4);
   }

   public int getCustomBannerCost() {
      return this.cost("custom-banner", 16);
   }

   public int getSlotCostPerSlot() {
      return this.cost("slot-increase.cost-per-slot", 4);
   }

   public int getSlotsPerPurchase() {
      return Math.max(1, this.cost("slot-increase.slots-per-purchase", 1));
   }

   public int getMaxSlots() {
      return this.cost("slot-increase.max-slots", 50);
   }

   public boolean isRentEnabled() {
      FileConfiguration config = this.module.getConfig();
      return this.isEnabled() && config != null && config.getBoolean("economy.rent.enabled", true);
   }

   public long getRentAmount() {
      FileConfiguration config = this.module.getConfig();
      return config != null ? config.getLong("economy.rent.amount", 32L) : 32L;
   }

   public long getRentForMembers(int members) {
      int count = Math.max(1, members);
      FileConfiguration config = this.module.getConfig();
      if (config != null) {
         ConfigurationSection table = config.getConfigurationSection("economy.rent.amount-by-members");
         if (table != null && !table.getKeys(false).isEmpty()) {
            int maxTier = 0;

            for(String key : table.getKeys(false)) {
               try {
                  maxTier = Math.max(maxTier, Integer.parseInt(key.trim()));
               } catch (NumberFormatException var10) {
               }
            }

            if (maxTier > 0) {
               if (count > maxTier) {
                  long base = table.getLong(String.valueOf(maxTier));
                  long perExtra = config.getLong("economy.rent.per-extra-member", 12L);
                  return base + (long)(count - maxTier) * perExtra;
               }

               for(int tier = count; tier >= 1; --tier) {
                  if (table.contains(String.valueOf(tier))) {
                     return table.getLong(String.valueOf(tier));
                  }
               }
            }
         }
      }

      return this.getRentAmount();
   }

   public long getRentPeriodMillis() {
      FileConfiguration config = this.module.getConfig();
      int days = config != null ? config.getInt("economy.rent.period-days", 7) : 7;
      return (long)Math.max(1, days) * 24L * 60L * 60L * 1000L;
   }

   public long getRentCheckIntervalTicks() {
      FileConfiguration config = this.module.getConfig();
      int minutes = config != null ? config.getInt("economy.rent.check-interval-minutes", 30) : 30;
      return (long)Math.max(1, minutes) * 60L * 20L;
   }

   public boolean isLeaderOrCoLeader(Clan clan, UUID playerId) {
      if (clan != null && playerId != null) {
         if (clan.getLeaderId().equals(playerId)) {
            return true;
         } else {
            ClanMember member = clan.getMember(playerId);
            return member != null && DefaultClanRole.CO_LEADER.getId().equalsIgnoreCase(member.getRoleId());
         }
      } else {
         return false;
      }
   }

   public boolean isCurrency(Material material) {
      return this.currencyMaterials().contains(material);
   }

   public int countCurrency(Player player) {
      Set<Material> currency = this.currencyMaterials();
      int total = 0;

      for(ItemStack item : player.getInventory().getStorageContents()) {
         if (item != null && currency.contains(item.getType())) {
            total += item.getAmount();
         }
      }

      return total;
   }

   public int removeCurrency(Player player, int amount) {
      if (amount <= 0) {
         return 0;
      } else {
         Set<Material> currency = this.currencyMaterials();
         PlayerInventory inv = player.getInventory();
         ItemStack[] contents = inv.getStorageContents();
         int remaining = amount;

         for(int i = 0; i < contents.length && remaining > 0; ++i) {
            ItemStack item = contents[i];
            if (item != null && currency.contains(item.getType())) {
               int stack = item.getAmount();
               if (stack <= remaining) {
                  remaining -= stack;
                  inv.setItem(i, (ItemStack)null);
               } else {
                  item.setAmount(stack - remaining);
                  inv.setItem(i, item);
                  remaining = 0;
               }
            }
         }

         player.updateInventory();
         return amount - remaining;
      }
   }

   public void giveCurrency(Player player, int amount) {
      if (amount > 0) {
         Material payout = this.payoutMaterial();

         int stackSize;
         for(int remaining = amount; remaining > 0; remaining -= stackSize) {
            stackSize = Math.min(payout.getMaxStackSize(), remaining);
            ItemStack give = new ItemStack(payout, stackSize);
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack[]{give});

            for(ItemStack lo : leftover.values()) {
               player.getWorld().dropItemNaturally(player.getLocation(), lo);
            }
         }

         player.updateInventory();
      }
   }

   public Map<Material, Integer> removeCurrencyByType(Player player, int amount) {
      Map<Material, Integer> removed = new LinkedHashMap();
      if (amount <= 0) {
         return removed;
      } else {
         Set<Material> currency = this.currencyMaterials();
         PlayerInventory inv = player.getInventory();
         ItemStack[] contents = inv.getStorageContents();
         int remaining = amount;

         for(int i = 0; i < contents.length && remaining > 0; ++i) {
            ItemStack item = contents[i];
            if (item != null && currency.contains(item.getType())) {
               int stack = item.getAmount();
               int take = Math.min(stack, remaining);
               removed.merge(item.getType(), take, Integer::sum);
               remaining -= take;
               if (take >= stack) {
                  inv.setItem(i, (ItemStack)null);
               } else {
                  item.setAmount(stack - take);
                  inv.setItem(i, item);
               }
            }
         }

         player.updateInventory();
         return removed;
      }
   }

   public void giveCurrencyByType(Player player, Map<Material, Integer> payout) {
      if (payout != null && !payout.isEmpty()) {
         for(Map.Entry<Material, Integer> entry : payout.entrySet()) {
            Material material = (Material)entry.getKey();

            int stackSize;
            for(int remaining = (Integer)entry.getValue(); remaining > 0; remaining -= stackSize) {
               stackSize = Math.min(material.getMaxStackSize(), remaining);
               ItemStack give = new ItemStack(material, stackSize);
               HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack[]{give});

               for(ItemStack lo : leftover.values()) {
                  player.getWorld().dropItemNaturally(player.getLocation(), lo);
               }
            }
         }

         player.updateInventory();
      }
   }

   private Map<Material, Integer> toPayoutMap(Map<String, Integer> drained, int totalGive) {
      Map<Material, Integer> payout = new LinkedHashMap();
      int accounted = 0;

      for(Map.Entry<String, Integer> entry : drained.entrySet()) {
         int count = (Integer)entry.getValue();
         accounted += count;

         Material material;
         try {
            material = Material.valueOf((String)entry.getKey());
         } catch (IllegalArgumentException var10) {
            material = this.payoutMaterial();
         }

         payout.merge(material, count, Integer::sum);
      }

      if (accounted < totalGive) {
         payout.merge(this.payoutMaterial(), totalGive - accounted, Integer::sum);
      }

      return payout;
   }

   public boolean chargeTreasury(Clan clan, long amount) {
      if (amount <= 0L) {
         return true;
      } else {
         synchronized(clan) {
            if (clan.getBalance() < amount) {
               return false;
            }

            clan.setBalance(clan.getBalance() - amount);
            clan.drainTreasury((int)Math.min(amount, 2147483647L));
         }

         ClanTaxManager taxManager = this.module.getTaxManager();
         if (taxManager != null) {
            taxManager.collect(clan, amount);
         }

         return true;
      }
   }

   public boolean canAfford(Clan clan, long cost) {
      if (this.isEnabled() && cost > 0L) {
         return clan != null && clan.getBalance() >= cost;
      } else {
         return true;
      }
   }

   public void notifyNotEnoughTreasury(Player player, Clan clan, long cost) {
      long balance = clan != null ? clan.getBalance() : 0L;
      this.sendMessage(player, this.getMessage("economy.not-enough-treasury").replace("{cost}", String.valueOf(cost)).replace("{balance}", String.valueOf(balance)));
   }

   public boolean tryCharge(Player actor, Clan clan, long cost) {
      if (this.isEnabled() && cost > 0L) {
         if (!this.chargeTreasury(clan, cost)) {
            this.sendMessage(actor, this.getMessage("economy.not-enough-treasury").replace("{cost}", String.valueOf(cost)).replace("{balance}", String.valueOf(clan.getBalance())));
            return false;
         } else {
            this.sendMessage(actor, this.getMessage("economy.treasury-charged").replace("{cost}", String.valueOf(cost)).replace("{balance}", String.valueOf(clan.getBalance())));
            return true;
         }
      } else {
         return true;
      }
   }

   public void deposit(Player player, int amount) {
      Clan clan = this.clanManager.getPlayerClan(player.getUniqueId());
      if (clan == null) {
         this.sendMessage(player, this.getMessage("not-in-clan"));
      } else if (!this.isEnabled()) {
         this.sendMessage(player, this.getMessage("economy.disabled"));
      } else if (!this.isLeaderOrCoLeader(clan, player.getUniqueId())) {
         this.sendMessage(player, this.getMessage("economy.bank-no-permission"));
      } else {
         int available = this.countCurrency(player);
         if (available <= 0) {
            this.sendMessage(player, this.getMessage("economy.deposit-no-ore"));
         } else {
            int toDeposit = amount <= 0 ? available : Math.min(amount, available);
            if (toDeposit <= 0) {
               this.sendMessage(player, this.getMessage("economy.deposit-no-ore"));
            } else {
               Map<Material, Integer> removedByType = this.removeCurrencyByType(player, toDeposit);
               int removed = removedByType.values().stream().mapToInt(Integer::intValue).sum();
               if (removed <= 0) {
                  this.sendMessage(player, this.getMessage("economy.deposit-no-ore"));
               } else {
                  synchronized(clan) {
                     for(Map.Entry<Material, Integer> entry : removedByType.entrySet()) {
                        clan.addTreasury(((Material)entry.getKey()).name(), (Integer)entry.getValue());
                     }

                     clan.setBalance(clan.getBalance() + (long)removed);
                  }

                  this.clanManager.saveClan(clan);
                  this.sendMessage(player, this.getMessage("economy.deposit-success").replace("{amount}", String.valueOf(removed)).replace("{balance}", String.valueOf(clan.getBalance())));
                  if (this.module.getAuditLogger() != null) {
                     this.module.getAuditLogger().logDeposit(player, clan, (long)removed, clan.getBalance());
                  }

               }
            }
         }
      }
   }

   public void withdraw(Player player, int amount) {
      Clan clan = this.clanManager.getPlayerClan(player.getUniqueId());
      if (clan == null) {
         this.sendMessage(player, this.getMessage("not-in-clan"));
      } else if (!this.isEnabled()) {
         this.sendMessage(player, this.getMessage("economy.disabled"));
      } else if (!this.isLeaderOrCoLeader(clan, player.getUniqueId())) {
         this.sendMessage(player, this.getMessage("economy.bank-no-permission"));
      } else {
         Map<Material, Integer> payout = null;
         int give;
         synchronized(clan) {
            long balance = clan.getBalance();
            long requested = amount <= 0 ? balance : (long)amount;
            give = (int)Math.min(requested, balance);
            if (give <= 0) {
               give = 0;
            } else {
               clan.setBalance(balance - (long)give);
               payout = this.toPayoutMap(clan.drainTreasury(give), give);
            }
         }

         if (give <= 0) {
            this.sendMessage(player, this.getMessage("economy.withdraw-empty"));
         } else {
            this.giveCurrencyByType(player, payout);
            this.clanManager.saveClan(clan);
            this.sendMessage(player, this.getMessage("economy.withdraw-success").replace("{amount}", String.valueOf(give)).replace("{balance}", String.valueOf(clan.getBalance())));
            if (this.module.getAuditLogger() != null) {
               this.module.getAuditLogger().logWithdraw(player, clan, (long)give, clan.getBalance());
            }

         }
      }
   }

   public void adminAddTreasury(Player admin, Clan clan, long amount) {
      if (clan != null && amount > 0L) {
         synchronized(clan) {
            clan.setBalance(clan.getBalance() + amount);
            clan.addTreasury(this.payoutMaterial().name(), (int)Math.min(amount, 2147483647L));
         }

         this.clanManager.saveClan(clan);
         ClanAuditLogger logger = this.module.getAuditLogger();
         if (logger != null) {
            logger.logAdminTreasury(admin, clan, "ADMIN_ADD", amount, clan.getBalance());
         }

         this.sendMessage(admin, this.module.getPlugin().applyColors("&a[Админ] Казне выдано &f" + amount + "&a ар. Баланс: &f" + clan.getBalance()));
      }
   }

   public void adminRemoveTreasury(Player admin, Clan clan, long amount) {
      if (clan != null && amount > 0L) {
         long removed;
         synchronized(clan) {
            long balance = clan.getBalance();
            removed = Math.min(amount, balance);
            clan.setBalance(balance - removed);
            clan.drainTreasury((int)Math.min(removed, 2147483647L));
         }

         if (removed <= 0L) {
            this.sendMessage(admin, this.module.getPlugin().applyColors("&c[Админ] Казна уже пуста."));
         } else {
            this.clanManager.saveClan(clan);
            ClanAuditLogger logger = this.module.getAuditLogger();
            if (logger != null) {
               logger.logAdminTreasury(admin, clan, "ADMIN_REMOVE", removed, clan.getBalance());
            }

            this.sendMessage(admin, this.module.getPlugin().applyColors("&c[Админ] Из казны изъято &f" + removed + "&c ар. Баланс: &f" + clan.getBalance()));
         }
      }
   }

   public void buySlots(Player player) {
      Clan clan = this.clanManager.getPlayerClan(player.getUniqueId());
      if (clan == null) {
         this.sendMessage(player, this.getMessage("not-in-clan"));
      } else if (!this.isEnabled()) {
         this.sendMessage(player, this.getMessage("economy.disabled"));
      } else if (!this.isLeaderOrCoLeader(clan, player.getUniqueId())) {
         this.sendMessage(player, this.getMessage("economy.slots-no-permission"));
      } else {
         int maxSlots = this.getMaxSlots();
         int current = clan.getMaxMembers();
         if (current >= maxSlots) {
            this.sendMessage(player, this.getMessage("economy.slots-maxed").replace("{max}", String.valueOf(maxSlots)));
         } else {
            int slotsToAdd = Math.min(this.getSlotsPerPurchase(), maxSlots - current);
            long totalCost = (long)this.getSlotCostPerSlot() * (long)slotsToAdd;
            if (!this.chargeTreasury(clan, totalCost)) {
               this.sendMessage(player, this.getMessage("economy.not-enough-treasury").replace("{cost}", String.valueOf(totalCost)).replace("{balance}", String.valueOf(clan.getBalance())));
            } else {
               clan.setMaxMembers(current + slotsToAdd);
               this.clanManager.saveClan(clan);
               this.sendMessage(player, this.getMessage("economy.slots-bought").replace("{slots}", String.valueOf(slotsToAdd)).replace("{max}", String.valueOf(clan.getMaxMembers())).replace("{cost}", String.valueOf(totalCost)).replace("{balance}", String.valueOf(clan.getBalance())));
               ClanAuditLogger logger = this.module.getAuditLogger();
               if (logger != null) {
                  logger.logPurchase(player, clan, "BUY_SLOTS", "+" + slotsToAdd + " слот(ов) (всего " + clan.getMaxMembers() + ")", totalCost, clan.getBalance());
               }

            }
         }
      }
   }

   private String getMessage(String key) {
      FileConfiguration config = this.module.getMessages();
      if (config == null) {
         return "§cMessage not found: " + key;
      } else {
         String message = config.getString(key, "§cMessage not found: " + key);
         String prefix = config.getString("prefix", "");
         FileConfiguration mainConfig = this.module.getConfig();
         if (mainConfig != null && !mainConfig.getBoolean("prefix.enabled", true)) {
            prefix = "";
         }

         String combined = message.replace("<prefix>", prefix);
         return this.module.getPlugin().applyColors(combined);
      }
   }

   private void sendMessage(Player player, String message) {
      if (message != null && !message.isEmpty()) {
         this.module.getPlugin().getSchedulerManager().runEntityTask(player, "clan-economy-message", () -> {
            if (player.isOnline()) {
               player.sendMessage(LegacyComponentSerializer.legacySection().deserialize(message));
            }

         });
      }
   }
}
