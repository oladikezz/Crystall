package site.deforce.SM_Clans.managers;

import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.schalker.DoAPI.DoAPI;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import site.deforce.SM_Clans.SM_Clans;
import site.deforce.SM_Clans.models.Clan;
import site.deforce.SM_Clans.models.ClanMember;
import site.deforce.SM_Clans.models.DefaultClanRole;

public class ClanManager {
   private final DoAPI plugin;
   private final SM_Clans module;
   private final DatabaseManager databaseManager;
   private final Map<String, Clan> clans;
   private final Map<UUID, String> playerClans;
   private volatile boolean initialLoadComplete = false;

   public ClanManager(DoAPI plugin, SM_Clans module, DatabaseManager databaseManager) {
      super();
      this.plugin = plugin;
      this.module = module;
      this.databaseManager = databaseManager;
      this.clans = new ConcurrentHashMap();
      this.playerClans = new ConcurrentHashMap();
   }

   public void loadClans() {
      this.plugin.getSchedulerManager().runAsync("load-clans", () -> {
         try {
            List<Clan> loadedClans = this.databaseManager.loadAllClans();
            Map<String, Clan> loadedClanMap = new HashMap();
            Map<UUID, String> loadedPlayerClans = new HashMap();

            for(Clan clan : loadedClans) {
               loadedClanMap.put(clan.getClanId(), clan);

               for(UUID playerId : clan.getMembers().keySet()) {
                  loadedPlayerClans.put(playerId, clan.getClanId());
               }
            }

            this.plugin.getSchedulerManager().runTaskLater("apply-loaded-clans", () -> {
               if (!this.initialLoadComplete) {
                  this.clans.clear();
                  this.playerClans.clear();
                  this.clans.putAll(loadedClanMap);
                  this.playerClans.putAll(loadedPlayerClans);
                  this.initialLoadComplete = true;
               } else {
                  for(Map.Entry<String, Clan> entry : loadedClanMap.entrySet()) {
                     this.clans.putIfAbsent((String)entry.getKey(), (Clan)entry.getValue());
                  }

                  for(Map.Entry<UUID, String> entry : loadedPlayerClans.entrySet()) {
                     this.playerClans.putIfAbsent((UUID)entry.getKey(), (String)entry.getValue());
                  }
               }

               for(Clan clan : this.clans.values()) {
                  this.applyTagActivation(clan, false);
               }

               this.plugin.getDebugSystem().log("ClanManager", "Loaded " + loadedClans.size() + " clans");
            }, 1L);
         } catch (SQLException exception) {
            this.plugin.getDebugSystem().logError("Failed to load clans", exception);
         }

      });
   }

   public Clan createClan(String tag, String name, UUID leaderId) {
      String strippedTag = this.stripColorCodes(tag);
      if (tag != null && !tag.isEmpty() && !strippedTag.isEmpty()) {
         if (name != null && !name.isEmpty() && name.length() <= 255) {
            FileConfiguration config = this.module.getConfig();
            int minLength = config != null ? config.getInt("clans.min-tag-length", 2) : 2;
            int maxLength = config != null ? config.getInt("clans.max-tag-length", 10) : 10;
            int strippedLength = strippedTag.codePointCount(0, strippedTag.length());
            boolean lengthOk = strippedLength > 0 && (strippedLength == 1 || strippedLength >= minLength && strippedLength <= maxLength);
            if (!lengthOk) {
               throw new IllegalArgumentException("Invalid clan tag");
            } else if (this.getPlayerClan(leaderId) != null) {
               throw new IllegalArgumentException("Player is already in a clan");
            } else if (this.isClanNameTaken(name, (String)null)) {
               throw new IllegalArgumentException("Clan name already exists");
            } else if (this.getClanByTag(strippedTag) != null) {
               throw new IllegalArgumentException("Clan tag already exists");
            } else {
               String clanId = UUID.randomUUID().toString();
               Clan clan = new Clan(clanId, tag, name, leaderId);
               if (config != null) {
                  clan.setMaxMembers(config.getInt("clans.default-member-slots", 4));
               }

               ClanMember leader = new ClanMember(leaderId, clanId, DefaultClanRole.LEADER.getId());
               clan.addMember(leader);
               this.applyTagActivation(clan, false);
               this.plugin.getSchedulerManager().runAsync("save-clan", () -> {
                  try {
                     this.databaseManager.saveClan(clan);
                     this.plugin.getDebugSystem().log("ClanManager", "Clan created: " + tag + " by " + String.valueOf(leaderId));
                  } catch (SQLException exception) {
                     this.plugin.getDebugSystem().logError("Failed to save clan", exception);
                  }

               });
               this.clans.put(clanId, clan);
               this.playerClans.put(leaderId, clanId);
               return clan;
            }
         } else {
            throw new IllegalArgumentException("Invalid clan name");
         }
      } else {
         throw new IllegalArgumentException("Invalid clan tag");
      }
   }

   public void registerRestoredClan(Clan clan) {
      this.clans.put(clan.getClanId(), clan);

      for(UUID playerId : clan.getMembers().keySet()) {
         this.playerClans.put(playerId, clan.getClanId());
      }

      this.saveClan(clan);
   }

   public void disbandClan(String clanId) {
      Clan clan = (Clan)this.clans.get(clanId);
      if (clan != null) {
         for(UUID playerId : clan.getMembers().keySet()) {
            this.playerClans.remove(playerId);
         }

         if (clan.getLeaderId() != null) {
            this.playerClans.remove(clan.getLeaderId());
         }

         this.clans.remove(clanId);
         this.plugin.getSchedulerManager().runAsync("delete-clan", () -> {
            try {
               this.databaseManager.deleteClan(clanId);
               this.plugin.getDebugSystem().log("ClanManager", "Clan disbanded: " + clan.getTag());
            } catch (SQLException exception) {
               this.plugin.getDebugSystem().logError("Failed to disband clan", exception);
            }

         });
      }

   }

   public void addMember(String clanId, UUID playerId, String roleId) {
      Clan clan = (Clan)this.clans.get(clanId);
      if (clan != null) {
         if (clan.isFull()) {
            throw new IllegalStateException("Clan is full");
         }

         if (this.playerClans.containsKey(playerId)) {
            throw new IllegalStateException("Player is already in a clan");
         }

         ClanMember member = new ClanMember(playerId, clanId, roleId);
         clan.addMember(member);
         this.playerClans.put(playerId, clanId);
         this.applyTagActivation(clan, true);
         this.plugin.getSchedulerManager().runAsync("save-member", () -> {
            try {
               this.databaseManager.saveClanMember(member);
            } catch (SQLException exception) {
               this.plugin.getDebugSystem().logError("Failed to save clan member", exception);
            }

         });
      }

   }

   public void removeMember(UUID playerId) {
      String clanId = (String)this.playerClans.get(playerId);
      if (clanId != null) {
         Clan clan = (Clan)this.clans.get(clanId);
         if (clan != null) {
            if (clan.getLeaderId().equals(playerId)) {
               throw new IllegalStateException("Cannot remove clan leader");
            }

            clan.removeMember(playerId);
            this.playerClans.remove(playerId);
            this.applyTagActivation(clan, true);
            this.plugin.getSchedulerManager().runAsync("delete-member", () -> {
               try {
                  this.databaseManager.deleteClanMember(playerId);
               } catch (SQLException exception) {
                  this.plugin.getDebugSystem().logError("Failed to delete clan member", exception);
               }

            });
         }
      }

   }

   public Clan getPlayerClan(UUID playerId) {
      String clanId = (String)this.playerClans.get(playerId);
      return clanId == null ? null : (Clan)this.clans.get(clanId);
   }

   public Clan getClan(String clanId) {
      return (Clan)this.clans.get(clanId);
   }

   public Clan getClanByTag(String tag) {
      String cleanTag = this.stripColorCodes(tag);

      for(Clan clan : this.clans.values()) {
         if (this.stripColorCodes(clan.getTag()).equalsIgnoreCase(cleanTag)) {
            return clan;
         }
      }

      return null;
   }

   public boolean isClanNameTaken(String name, String excludeClanId) {
      String cleanName = this.stripColorCodes(name).trim();

      for(Clan clan : this.clans.values()) {
         if ((excludeClanId == null || !clan.getClanId().equals(excludeClanId)) && this.stripColorCodes(clan.getName()).trim().equalsIgnoreCase(cleanName)) {
            return true;
         }
      }

      return false;
   }

   private String stripColorCodes(String text) {
      if (text == null) {
         return "";
      } else {
         String noLegacy = text.replaceAll("(?i)[&§][0-9a-fk-orx]", "");
         return noLegacy.replaceAll("(?i)(?:&#|#)[0-9a-f]{6}", "");
      }
   }

   private String normalizeHexColors(String text) {
      return text == null ? "" : text.replaceAll("(?<![&§])(#[0-9a-fA-F]{6})", "&$1");
   }

   public Collection<Clan> getAllClans() {
      return this.clans.values();
   }

   public void saveClan(Clan clan) {
      this.plugin.getSchedulerManager().runAsync("save-clan", () -> {
         try {
            this.databaseManager.saveClan(clan);
         } catch (SQLException exception) {
            this.plugin.getDebugSystem().logError("Failed to save clan", exception);
         }

      });
   }

   public void updateMemberRole(UUID playerId, String newRoleId) {
      Clan clan = this.getPlayerClan(playerId);
      if (clan != null) {
         ClanMember member = clan.getMember(playerId);
         if (member != null) {
            member.setRoleId(newRoleId);
            this.plugin.getSchedulerManager().runAsync("update-member-role", () -> {
               try {
                  this.databaseManager.saveClanMember(member);
               } catch (SQLException exception) {
                  this.plugin.getDebugSystem().logError("Failed to update member role", exception);
               }

            });
         }
      }

   }

   public boolean hasPlayerClan(UUID playerId) {
      return this.playerClans.containsKey(playerId);
   }

   public DoAPI getPlugin() {
      return this.plugin;
   }

   private void applyTagActivation(Clan clan, boolean notifyLeader) {
      FileConfiguration config = this.module.getConfig();
      if (config != null && config.getBoolean("clans.tag-activation.enabled", false)) {
         int minMembers = Math.max(1, config.getInt("clans.tag-activation.min-members", 3));
         int currentMembers = clan.getMemberCount();
         boolean shouldEnable = currentMembers >= minMembers;
         if (clan.isTagEnabled() != shouldEnable) {
            clan.setTagEnabled(shouldEnable);
            this.saveClan(clan);
            if (notifyLeader) {
               int needed = Math.max(0, minMembers - currentMembers);
               String messageKey = shouldEnable ? "tag-activation.enabled" : "tag-activation.disabled";
               this.notifyLeader(clan, messageKey, minMembers, currentMembers, needed);
            }
         }

      }
   }

   private void notifyLeader(Clan clan, String messageKey, int required, int current, int needed) {
      Player leader = this.plugin.getServer().getPlayer(clan.getLeaderId());
      if (leader != null && leader.isOnline()) {
         String var10000 = this.getMessage(messageKey).replace("{clan}", clan.getName());
         String var10002 = this.normalizeHexColors(clan.getTag());
         String message = var10000.replace("{tag}", var10002 + "&r").replace("{required}", String.valueOf(required)).replace("{current}", String.valueOf(current)).replace("{needed}", String.valueOf(needed));
         if (message != null && !message.isEmpty()) {
            leader.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message.replace("§", "&")));
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
         if (mainConfig != null) {
            boolean prefixEnabled = mainConfig.getBoolean("prefix.enabled", true);
            if (!prefixEnabled) {
               prefix = "";
            }
         }

         String combined = message.replace("<prefix>", prefix);
         return this.module.getPlugin().applyColors(combined);
      }
   }
}
