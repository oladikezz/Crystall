package site.deforce.SM_Clans.managers;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.schalker.DoAPI.DoAPI;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import site.deforce.SM_Clans.SM_Clans;
import site.deforce.SM_Clans.models.Clan;
import site.deforce.SM_Clans.models.ClanMember;
import site.deforce.SM_Clans.models.DefaultClanRole;

public class ClanRentManager {
   private static final String RENT_TASK = "clan-rent-check";
   public static final String REASON_RENT = "rent";
   private final SM_Clans module;
   private final DoAPI plugin;
   private final ClanManager clanManager;
   private final DatabaseManager databaseManager;
   private final ClanEconomyManager economyManager;

   public ClanRentManager(SM_Clans module, ClanManager clanManager, DatabaseManager databaseManager, ClanEconomyManager economyManager) {
      super();
      this.module = module;
      this.plugin = module.getPlugin();
      this.clanManager = clanManager;
      this.databaseManager = databaseManager;
      this.economyManager = economyManager;
   }

   public void start() {
      long interval = this.economyManager.getRentCheckIntervalTicks();
      this.plugin.getSchedulerManager().runTaskTimer("clan-rent-check", this::processRent, interval, interval);
      this.plugin.getDebugSystem().log("ClanRent", "Rent task scheduled every " + interval + " ticks");
   }

   public void stop() {
      this.plugin.getSchedulerManager().cancelTask("clan-rent-check");
   }

   public void processRent() {
      if (this.economyManager.isRentEnabled()) {
         long now = System.currentTimeMillis();
         long period = this.economyManager.getRentPeriodMillis();
         List<Clan> due = new ArrayList();

         for(Clan clan : this.clanManager.getAllClans()) {
            if (now - clan.getLastRentAt() >= period) {
               due.add(clan);
            }
         }

         for(Clan clan : due) {
            long rent = this.economyManager.getRentForMembers(clan.getMemberCount());
            if (this.economyManager.chargeTreasury(clan, rent)) {
               clan.setLastRentAt(now);
               this.clanManager.saveClan(clan);
               this.notifyMembers(clan, this.getMessage("economy.rent.paid").replace("{amount}", String.valueOf(rent)).replace("{balance}", String.valueOf(clan.getBalance())));
               if (this.module.getAuditLogger() != null) {
                  this.module.getAuditLogger().logRent(clan, rent, clan.getBalance());
               }
            } else {
               this.archiveAndDisband(clan, "rent");
            }
         }

      }
   }

   private void archiveAndDisband(Clan clan, String reason) {
      this.plugin.getSchedulerManager().runAsync("clan-archive", () -> {
         try {
            this.databaseManager.archiveClan(clan, reason);
         } catch (SQLException exception) {
            this.plugin.getDebugSystem().logError("SM_Clans", "Failed to archive clan before disband", exception);
            return;
         }

         this.plugin.getSchedulerManager().runGlobalTask("clan-rent-disband", () -> {
            this.notifyMembers(clan, this.getMessage("economy.rent.disbanded").replace("{clan}", clan.getName()));
            this.clanManager.disbandClan(clan.getClanId());
            if (this.module.getAuditLogger() != null) {
               this.module.getAuditLogger().logDisband(clan, "Не оплачена аренда");
            }

            this.plugin.getDebugSystem().log("ClanRent", "Clan " + clan.getTag() + " disbanded (unpaid rent) and archived");
         });
      });
   }

   public void listArchived(Player admin) {
      this.plugin.getSchedulerManager().runAsync("clan-restore-list", () -> {
         List<Clan> archived;
         try {
            archived = this.databaseManager.loadAllArchivedClans();
         } catch (SQLException exception) {
            this.plugin.getDebugSystem().logError("SM_Clans", "Failed to list archived clans", exception);
            this.sendMessage(admin, this.getMessage("economy.restore.error"));
            return;
         }

         if (archived.isEmpty()) {
            this.sendMessage(admin, this.getMessage("economy.restore.list-empty"));
         } else {
            this.sendMessage(admin, this.getMessage("economy.restore.list-header"));

            for(Clan clan : archived) {
               String clanId = clan.getClanId();
               String shortId = clanId.length() > 8 ? clanId.substring(0, 8) : clanId;
               this.sendMessage(admin, this.getMessage("economy.restore.list-entry").replace("{id}", shortId).replace("{tag}", clan.getTag()).replace("{name}", clan.getName()).replace("{members}", String.valueOf(clan.getMembers().size())));
            }

         }
      });
   }

   public void restore(Player admin, String tag) {
      this.plugin.getSchedulerManager().runAsync("clan-restore-load", () -> {
         Clan archived;
         try {
            archived = this.databaseManager.loadArchivedClanByToken(tag);
         } catch (SQLException exception) {
            this.plugin.getDebugSystem().logError("SM_Clans", "Failed to load archived clan", exception);
            this.sendMessage(admin, this.getMessage("economy.restore.error"));
            return;
         }

         this.plugin.getSchedulerManager().runGlobalTask("clan-restore-apply", () -> this.applyRestore(admin, archived));
      });
   }

   private void applyRestore(Player admin, Clan archived) {
      if (archived == null) {
         this.sendMessage(admin, this.getMessage("economy.restore.not-found"));
      } else if (this.clanManager.getClanByTag(archived.getTag()) != null) {
         this.sendMessage(admin, this.getMessage("economy.restore.tag-taken"));
      } else if (this.clanManager.isClanNameTaken(archived.getName(), (String)null)) {
         this.sendMessage(admin, this.getMessage("economy.restore.name-taken"));
      } else {
         List<ClanMember> restorable = new ArrayList();

         for(ClanMember member : archived.getMembers().values()) {
            if (!this.clanManager.hasPlayerClan(member.getPlayerId())) {
               restorable.add(member);
            }
         }

         if (restorable.isEmpty()) {
            this.sendMessage(admin, this.getMessage("economy.restore.no-members"));
         } else {
            UUID originalLeader = archived.getLeaderId();
            ClanMember leaderMember = null;

            for(ClanMember member : restorable) {
               if (member.getPlayerId().equals(originalLeader)) {
                  leaderMember = member;
                  break;
               }
            }

            boolean promoted = false;
            if (leaderMember == null) {
               for(ClanMember member : restorable) {
                  if (leaderMember == null || this.rolePriority(member) > this.rolePriority(leaderMember) || this.rolePriority(member) == this.rolePriority(leaderMember) && member.getJoinedAt() < leaderMember.getJoinedAt()) {
                     leaderMember = member;
                  }
               }

               promoted = true;
            }

            Clan clan = new Clan(archived.getClanId(), archived.getTag(), archived.getName(), leaderMember.getPlayerId());
            clan.setDescription(archived.getDescription());
            clan.setPrivacy(archived.getPrivacy());
            clan.setCreatedAt(archived.getCreatedAt());
            clan.setMaxMembers(archived.getMaxMembers());
            clan.setChatEnabled(archived.isChatEnabled());
            clan.setBannerColor(archived.getBannerColor());
            clan.setTagEnabled(archived.isTagEnabled());
            clan.setProfilePublic(archived.isProfilePublic());
            clan.setFriendlyFire(archived.isFriendlyFire());
            clan.setFlagData(archived.getFlagData());
            clan.setBalance(archived.getBalance());
            clan.setTreasuryData(archived.getTreasuryData());
            clan.setLastRentAt(System.currentTimeMillis());

            for(ClanMember member : restorable) {
               String roleId = member.getPlayerId().equals(leaderMember.getPlayerId()) ? DefaultClanRole.LEADER.getId() : member.getRoleId();
               ClanMember restored = new ClanMember(member.getPlayerId(), clan.getClanId(), roleId);
               restored.setJoinedAt(member.getJoinedAt());
               restored.setLastSeen(member.getLastSeen());
               clan.addMember(restored);
            }

            this.clanManager.registerRestoredClan(clan);
            String clanId = archived.getClanId();
            this.plugin.getSchedulerManager().runAsync("clan-restore-cleanup", () -> {
               try {
                  this.databaseManager.deleteArchivedClan(clanId);
               } catch (SQLException exception) {
                  this.plugin.getDebugSystem().logError("SM_Clans", "Failed to delete archive after restore", exception);
               }

            });
            this.sendMessage(admin, this.getMessage("economy.restore.success").replace("{clan}", clan.getName()).replace("{count}", String.valueOf(restorable.size())));
            if (promoted) {
               String leaderName = Bukkit.getOfflinePlayer(leaderMember.getPlayerId()).getName();
               this.sendMessage(admin, this.getMessage("economy.restore.leader-promoted").replace("{player}", leaderName != null ? leaderName : "Unknown"));
            }

            this.notifyMembers(clan, this.getMessage("economy.restore.member-notified").replace("{clan}", clan.getName()));
         }
      }
   }

   private int rolePriority(ClanMember member) {
      return DefaultClanRole.fromId(member.getRoleId()).getPriority();
   }

   private void notifyMembers(Clan clan, String message) {
      if (message != null && !message.isEmpty()) {
         for(UUID memberId : clan.getMembers().keySet()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
               this.sendMessage(member, message);
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
         this.plugin.getSchedulerManager().runEntityTask(player, "clan-rent-message", () -> {
            if (player.isOnline()) {
               player.sendMessage(LegacyComponentSerializer.legacySection().deserialize(message));
            }

         });
      }
   }
}
