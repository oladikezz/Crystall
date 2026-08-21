package site.deforce.SM_Clans.managers;

import java.sql.SQLException;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.schalker.DoAPI.DoAPI;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import site.deforce.SM_Clans.SM_Clans;
import site.deforce.SM_Clans.logging.ClanAuditLogger;
import site.deforce.SM_Clans.models.Clan;

public class ClanTaxManager {
   private final SM_Clans module;
   private final DoAPI plugin;
   private final DatabaseManager databaseManager;
   private final Object lock = new Object();
   private long balance;
   private long totalCollected;

   public ClanTaxManager(SM_Clans module, DatabaseManager databaseManager) {
      super();
      this.module = module;
      this.plugin = module.getPlugin();
      this.databaseManager = databaseManager;
   }

   public void load() {
      try {
         long[] state = this.databaseManager.loadTaxState();
         synchronized(this.lock) {
            this.balance = state[0];
            this.totalCollected = state[1];
         }

         this.plugin.getDebugSystem().log("ClanTax", "Tax pool loaded: balance=" + state[0] + ", total=" + state[1]);
      } catch (SQLException exception) {
         this.plugin.getDebugSystem().logError("SM_Clans", "Failed to load tax pool", exception);
      }

   }

   public boolean isEnabled() {
      FileConfiguration config = this.module.getConfig();
      return config == null || config.getBoolean("economy.president-taxes", true);
   }

   public long getBalance() {
      synchronized(this.lock) {
         return this.balance;
      }
   }

   public long getTotalCollected() {
      synchronized(this.lock) {
         return this.totalCollected;
      }
   }

   public void collect(Clan clan, long amount) {
      if (amount > 0L && this.isEnabled()) {
         long newBalance;
         synchronized(this.lock) {
            this.balance += amount;
            this.totalCollected += amount;
            newBalance = this.balance;
         }

         this.persist();
         ClanAuditLogger logger = this.module.getAuditLogger();
         if (logger != null) {
            logger.logTaxCollect(clan, amount, newBalance);
         }

      }
   }

   public void withdraw(Player president, long amount) {
      ClanEconomyManager economy = this.module.getClanEconomyManager();
      if (economy != null) {
         long give;
         long newBalance;
         synchronized(this.lock) {
            long requested = amount <= 0L ? this.balance : Math.min(amount, this.balance);
            give = Math.max(0L, Math.min(requested, 2147483647L));
            this.balance -= give;
            newBalance = this.balance;
         }

         if (give <= 0L) {
            this.sendMessage(president, this.getMessage("taxes.withdraw-empty"));
         } else {
            economy.giveCurrency(president, (int)give);
            this.persist();
            this.sendMessage(president, this.getMessage("taxes.withdraw-success").replace("{amount}", String.valueOf(give)).replace("{balance}", String.valueOf(newBalance)));
            ClanAuditLogger logger = this.module.getAuditLogger();
            if (logger != null) {
               logger.logTaxWithdraw(president, give, newBalance);
            }

         }
      }
   }

   public void flush() {
      long bal;
      long total;
      synchronized(this.lock) {
         bal = this.balance;
         total = this.totalCollected;
      }

      try {
         this.databaseManager.saveTaxState(bal, total);
      } catch (SQLException exception) {
         this.plugin.getDebugSystem().logError("SM_Clans", "Failed to flush tax pool", exception);
      }

   }

   private void persist() {
      long bal;
      long total;
      synchronized(this.lock) {
         bal = this.balance;
         total = this.totalCollected;
      }

      this.plugin.getSchedulerManager().runAsync("clan-tax-save", () -> {
         try {
            this.databaseManager.saveTaxState(bal, total);
         } catch (SQLException exception) {
            this.plugin.getDebugSystem().logError("SM_Clans", "Failed to save tax pool", exception);
         }

      });
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
         this.module.getPlugin().getSchedulerManager().runEntityTask(player, "clan-tax-message", () -> {
            if (player.isOnline()) {
               player.sendMessage(LegacyComponentSerializer.legacySection().deserialize(message));
            }

         });
      }
   }
}
