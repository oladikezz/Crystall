package site.deforce.SM_Clans.managers;

import java.util.UUID;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.schalker.DoAPI.DoAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import site.deforce.SM_Clans.SM_Clans;
import site.deforce.SM_Clans.models.Clan;
import site.deforce.SM_Clans.models.ClanPrivacy;
import site.deforce.SM_Clans.models.DefaultClanRole;
import site.deforce.SM_Clans.util.StyleInput;

public class ClanAdminManager {
   private final SM_Clans module;
   private final ClanManager clanManager;

   public ClanAdminManager(SM_Clans module, ClanManager clanManager) {
      super();
      this.module = module;
      this.clanManager = clanManager;
   }

   public void setName(Player admin, Clan clan, String rawInput) {
      if (clan != null) {
         FileConfiguration config = this.module.getConfig();
         boolean allowColors = config != null && config.getBoolean("clans.allow-colored-names", false);
         int minLength = config != null ? config.getInt("clans.min-name-length", 1) : 1;
         int maxLength = config != null ? config.getInt("clans.max-name-length", 24) : 24;
         String newName = StyleInput.miniToLegacy(rawInput);
         String stripped = this.stripColorCodes(newName).trim();
         if (!allowColors) {
            newName = stripped;
         }

         if (!stripped.isEmpty() && stripped.length() >= minLength && stripped.length() <= maxLength) {
            if (this.clanManager.isClanNameTaken(newName, clan.getClanId())) {
               this.send(admin, this.getMessage("admin.name-already-taken"));
               this.reopenManage(admin, clan);
            } else {
               String oldName = clan.getName();
               clan.setName(newName);
               this.clanManager.saveClan(clan);
               this.send(admin, this.getMessage("admin.name-changed").replace("{old}", oldName).replace("{new}", newName));
               this.reopenManage(admin, clan);
            }
         } else {
            this.send(admin, this.getMessage("admin.invalid-name").replace("{min}", String.valueOf(minLength)).replace("{max}", String.valueOf(maxLength)));
            this.reopenManage(admin, clan);
         }
      }
   }

   public void setTag(Player admin, Clan clan, String rawInput) {
      if (clan != null) {
         FileConfiguration config = this.module.getConfig();
         int minLength = config != null ? config.getInt("clans.min-tag-length", 2) : 2;
         int maxLength = config != null ? config.getInt("clans.max-tag-length", 10) : 10;
         boolean allowColors = config == null || config.getBoolean("clans.allow-colored-tags", true);
         String newTag = this.normalizeHexColors(StyleInput.miniToLegacy(rawInput));
         String stripped = this.stripColorCodes(newTag).trim();
         if (!allowColors) {
            newTag = stripped;
         }

         int visible = stripped.codePointCount(0, stripped.length());
         boolean lengthOk = visible > 0 && (visible == 1 || visible >= minLength && visible <= maxLength);
         if (!lengthOk) {
            this.send(admin, this.getMessage("admin.invalid-tag").replace("{min}", String.valueOf(minLength)).replace("{max}", String.valueOf(maxLength)));
            this.reopenManage(admin, clan);
         } else {
            for(Clan other : this.clanManager.getAllClans()) {
               if (!other.getClanId().equals(clan.getClanId()) && this.stripColorCodes(other.getTag()).trim().equalsIgnoreCase(stripped)) {
                  this.send(admin, this.getMessage("admin.tag-already-taken"));
                  this.reopenManage(admin, clan);
                  return;
               }
            }

            String oldTag = clan.getTag();
            clan.setTag(newTag);
            this.clanManager.saveClan(clan);
            String var10002 = this.getMessage("admin.tag-changed");
            DoAPI var10004 = this.module.getPlugin();
            String var10005 = this.normalizeHexColors(oldTag);
            this.send(admin, var10002.replace("{old}", var10004.applyColors(var10005 + "&r")).replace("{new}", this.module.getPlugin().applyColors(newTag + "&r")));
            this.reopenManage(admin, clan);
         }
      }
   }

   public void setDescription(Player admin, Clan clan, String rawInput) {
      if (clan != null) {
         FileConfiguration config = this.module.getConfig();
         int minLength = config != null ? config.getInt("clans.min-description-length", 1) : 1;
         int maxLength = config != null ? config.getInt("clans.max-description-length", 128) : 128;
         int maxLines = config != null ? config.getInt("clans.max-description-lines", 20) : 20;
         String description = StyleInput.miniToLegacy(rawInput).replace("\\n", "\n");
         if (description.split("\n", -1).length > maxLines) {
            this.send(admin, this.getMessage("admin.description-too-many-lines").replace("{max}", String.valueOf(maxLines)));
            this.reopenManage(admin, clan);
         } else {
            String plain = PlainTextComponentSerializer.plainText().serialize(LegacyComponentSerializer.legacyAmpersand().deserialize(description));
            int visible = this.stripColorCodes(plain).trim().length();
            if (visible >= minLength && visible <= maxLength) {
               clan.setDescription(description);
               this.clanManager.saveClan(clan);
               this.send(admin, this.getMessage("admin.description-changed"));
               this.reopenManage(admin, clan);
            } else {
               this.send(admin, this.getMessage("admin.description-too-long").replace("{min}", String.valueOf(minLength)).replace("{max}", String.valueOf(maxLength)));
               this.reopenManage(admin, clan);
            }
         }
      }
   }

   public void setMaxMembers(Player admin, Clan clan, String rawInput) {
      if (clan != null) {
         int max;
         try {
            max = Integer.parseInt(rawInput.trim());
         } catch (NumberFormatException var6) {
            this.send(admin, this.getMessage("admin.invalid-number"));
            this.reopenManage(admin, clan);
            return;
         }

         if (max < clan.getMemberCount()) {
            this.send(admin, this.getMessage("admin.max-members-too-low").replace("{current}", String.valueOf(clan.getMemberCount())));
            this.reopenManage(admin, clan);
         } else {
            clan.setMaxMembers(max);
            this.clanManager.saveClan(clan);
            this.send(admin, this.getMessage("admin.max-members-changed").replace("{max}", String.valueOf(max)));
            this.reopenManage(admin, clan);
         }
      }
   }

   public void cyclePrivacy(Player admin, Clan clan) {
      if (clan != null) {
         ClanPrivacy var10000;
         switch (clan.getPrivacy()) {
            case PUBLIC -> var10000 = ClanPrivacy.INVITE_ONLY;
            case INVITE_ONLY -> var10000 = ClanPrivacy.PRIVATE;
            case PRIVATE -> var10000 = ClanPrivacy.PUBLIC;
            default -> throw new MatchException((String)null, (Throwable)null);
         }

         ClanPrivacy next = var10000;
         clan.setPrivacy(next);
         this.clanManager.saveClan(clan);
         this.reopenManage(admin, clan);
      }
   }

   public void forceAdd(Player admin, Clan clan, String playerName) {
      if (clan != null) {
         OfflinePlayer target = Bukkit.getOfflinePlayer(playerName.trim());
         Clan existing = this.clanManager.getPlayerClan(target.getUniqueId());
         if (existing != null) {
            this.send(admin, this.getMessage("admin.forceadd.already-in-clan").replace("{player}", playerName).replace("{clan}", existing.getName()));
         } else if (clan.getMemberCount() >= clan.getMaxMembers()) {
            this.send(admin, this.getMessage("admin.forceadd.clan-full"));
         } else {
            try {
               this.clanManager.addMember(clan.getClanId(), target.getUniqueId(), DefaultClanRole.MEMBER.getId());
               this.send(admin, this.getMessage("admin.forceadd.success").replace("{player}", playerName).replace("{clan}", clan.getName()));
               if (target.isOnline() && target.getPlayer() != null) {
                  this.send(target.getPlayer(), this.getMessage("admin.forceadd.you-were-added").replace("{clan}", clan.getName()));
               }
            } catch (Exception var7) {
               this.send(admin, this.getMessage("admin.forceadd.failed"));
            }
         }

         this.reopenManage(admin, clan);
      }
   }

   public void setLeader(Player admin, Clan clan, String playerName) {
      if (clan != null) {
         OfflinePlayer target = Bukkit.getOfflinePlayer(playerName.trim());
         if (!clan.hasMember(target.getUniqueId())) {
            this.send(admin, this.getMessage("admin.player-not-in-clan").replace("{player}", playerName));
            this.reopenManage(admin, clan);
         } else {
            UUID oldLeader = clan.getLeaderId();
            if (!oldLeader.equals(target.getUniqueId())) {
               this.clanManager.updateMemberRole(target.getUniqueId(), DefaultClanRole.LEADER.getId());
               this.clanManager.updateMemberRole(oldLeader, DefaultClanRole.CO_LEADER.getId());
               clan.setLeaderId(target.getUniqueId());
               this.clanManager.saveClan(clan);
            }

            this.send(admin, this.getMessage("admin.leader-changed").replace("{player}", playerName).replace("{clan}", clan.getName()));
            if (target.isOnline() && target.getPlayer() != null) {
               this.send(target.getPlayer(), this.getMessage("admin.leader-changed-you").replace("{clan}", clan.getName()));
            }

            this.reopenManage(admin, clan);
         }
      }
   }

   public void kick(Player admin, Clan clan, String playerName) {
      if (clan != null) {
         OfflinePlayer target = Bukkit.getOfflinePlayer(playerName.trim());
         if (!clan.hasMember(target.getUniqueId())) {
            this.send(admin, this.getMessage("admin.player-not-in-clan").replace("{player}", playerName));
         } else if (clan.getLeaderId().equals(target.getUniqueId())) {
            this.send(admin, this.getMessage("admin.cannot-kick-leader"));
         } else {
            this.clanManager.removeMember(target.getUniqueId());
            if (this.module.getAuditLogger() != null) {
               this.module.getAuditLogger().logKick(admin, clan, playerName);
            }

            this.send(admin, this.getMessage("admin.kicked-success").replace("{player}", playerName).replace("{clan}", clan.getName()));
            if (target.isOnline() && target.getPlayer() != null) {
               this.send(target.getPlayer(), this.getMessage("admin.kicked-you").replace("{clan}", clan.getName()));
            }
         }

         this.reopenManage(admin, clan);
      }
   }

   public void disband(Player admin, Clan clan) {
      if (clan != null) {
         String disbandMsg = this.getMessage("admin.disbanded-member").replace("{clan}", clan.getName());

         for(UUID memberId : clan.getMembers().keySet()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
               this.send(member, disbandMsg);
            }
         }

         if (this.module.getAuditLogger() != null) {
            this.module.getAuditLogger().logPlayerDisband(admin, clan);
         }

         this.clanManager.disbandClan(clan.getClanId());
         this.send(admin, this.getMessage("admin.disbanded-success").replace("{clan}", clan.getName()));
         this.module.getMenuManager().openAdminClanMenu(admin);
      }
   }

   private void reopenManage(Player admin, Clan clan) {
      this.module.getPlugin().getSchedulerManager().runEntityTask(admin, "admin-menu-reopen", () -> this.module.getMenuManager().openAdminClanManageMenu(admin, clan));
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

   private String getMessage(String key) {
      FileConfiguration messages = this.module.getMessages();
      if (messages == null) {
         return "§cMessage not found: " + key;
      } else {
         String message = messages.getString(key, "§cMessage not found: " + key);
         String prefix = messages.getString("prefix", "");
         FileConfiguration mainConfig = this.module.getConfig();
         if (mainConfig != null && !mainConfig.getBoolean("prefix.enabled", true)) {
            prefix = "";
         }

         return this.module.getPlugin().applyColors(message.replace("<prefix>", prefix));
      }
   }

   private void send(Player player, String message) {
      if (message != null && !message.isEmpty()) {
         this.module.getPlugin().getSchedulerManager().runEntityTask(player, "admin-msg", () -> {
            if (player.isOnline()) {
               player.sendMessage(LegacyComponentSerializer.legacySection().deserialize(message));
            }

         });
      }
   }
}
