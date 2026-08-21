package site.deforce.SM_Clans.listeners;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.listener.BaseListener;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import site.deforce.SM_Clans.SM_Clans;
import site.deforce.SM_Clans.managers.ClanEconomyManager;
import site.deforce.SM_Clans.models.Clan;
import site.deforce.SM_Clans.util.ClanUpkeep;

public class ClanUpkeepListener extends BaseListener {
   private final SM_Clans module;

   public ClanUpkeepListener(DoAPI plugin, SM_Clans module) {
      super(plugin);
      this.module = module;
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      ClanEconomyManager econ = this.module.getClanEconomyManager();
      if (econ != null && econ.isRentEnabled()) {
         this.plugin.getSchedulerManager().runEntityTaskLater(player, "clan-upkeep-reminder", () -> {
            if (player.isOnline()) {
               Clan clan = this.module.getClanManager().getPlayerClan(player.getUniqueId());
               if (clan != null && econ.isLeaderOrCoLeader(clan, player.getUniqueId())) {
                  long remaining = ClanUpkeep.remainingMillis(clan, econ);
                  String key;
                  if (remaining <= 0L) {
                     key = "economy.rent.reminder-overdue";
                  } else if (remaining <= 86400000L) {
                     key = "economy.rent.reminder-1day";
                  } else {
                     if (remaining > 259200000L) {
                        return;
                     }

                     key = "economy.rent.reminder-3days";
                  }

                  long cost = econ.getRentForMembers(clan.getMemberCount());
                  String message = this.getMessage(key).replace("{clan}", clan.getName()).replace("{time}", ClanUpkeep.formatDuration(remaining)).replace("{cost}", String.valueOf(cost)).replace("{balance}", String.valueOf(clan.getBalance()));
                  this.sendMessage(player, message);
               }
            }
         }, 40L);
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

         return message.replace("<prefix>", prefix);
      }
   }

   private void sendMessage(Player player, String message) {
      if (message != null && !message.isEmpty()) {
         try {
            String colored = this.module.getPlugin().applyColors(message);
            player.sendMessage(LegacyComponentSerializer.legacySection().deserialize(colored));
         } catch (Exception var4) {
            player.sendMessage(message);
         }

      }
   }
}
