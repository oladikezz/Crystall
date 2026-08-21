package site.deforce.SM_Clans;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import site.deforce.SM_Clans.models.Clan;
import site.deforce.SM_Clans.models.ClanMember;
import site.deforce.SM_Clans.models.ClanRole;

public class ClanPlaceholders extends PlaceholderExpansion {
   private final SM_Clans module;

   public ClanPlaceholders(SM_Clans module) {
      super();
      this.module = module;
   }

   public @NotNull String getIdentifier() {
      return "clans";
   }

   public @NotNull String getAuthor() {
      return "deforce_";
   }

   public @NotNull String getVersion() {
      return "1.0.0";
   }

   public boolean persist() {
      return true;
   }

   public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
      if (player == null) {
         return "";
      } else {
         Clan clan = this.module.getClanManager().getPlayerClan(player.getUniqueId());
         switch (params.toLowerCase()) {
            case "tag":
               return clan != null && clan.isTagEnabled() ? this.formatLegacyTag(clan.getTag()) + " " : "";
            case "name":
               return clan != null ? this.stripFormatting(clan.getName()) : "";
            case "name_formatted":
               if (clan != null) {
                  return clan.getName() + "&r";
               }

               return "";
            case "role":
               if (clan != null) {
                  ClanMember member = clan.getMember(player.getUniqueId());
                  if (member != null) {
                     ClanRole role = clan.getRole(member.getRoleId());
                     return role != null ? role.getDisplayName() : "";
                  }
               }

               return "";
            case "members":
               return clan != null ? String.valueOf(clan.getMemberCount()) : "0";
            case "members_max":
               return clan != null ? String.valueOf(clan.getMaxMembers()) : "0";
            case "leader":
               if (clan != null) {
                  OfflinePlayer leader = this.module.getClanManager().getPlugin().getServer().getOfflinePlayer(clan.getLeaderId());
                  return leader.getName() != null ? leader.getName() : "Unknown";
               }

               return "";
            case "privacy":
               return clan != null ? clan.getPrivacy().name().toLowerCase() : "";
            case "has_clan":
               return clan != null ? "true" : "false";
            case "is_leader":
               return clan != null && clan.getLeaderId().equals(player.getUniqueId()) ? "true" : "false";
            case "tag_with_brackets":
               return clan != null && clan.isTagEnabled() ? this.wrapTag(clan.getTag()) : "";
            case "full_name":
               if (clan != null) {
                  if (clan.isTagEnabled()) {
                     String tag = this.wrapTag(clan.getTag());
                     return tag + " " + this.stripFormatting(clan.getName());
                  }

                  return this.stripFormatting(clan.getName());
               }

               return "";
            case "full_name_formatted":
               if (clan != null) {
                  if (clan.isTagEnabled()) {
                     String var10000 = this.wrapTag(clan.getTag());
                     return var10000 + " " + clan.getName() + "&r";
                  }

                  return clan.getName() + "&r";
               }

               return "";
            default:
               return null;
         }
      }
   }

   private String stripFormatting(String text) {
      return text == null ? "" : text.replaceAll("(?i)[&§][0-9a-fk-or]", "");
   }

   private String normalizeHexColors(String text) {
      return text == null ? "" : text.replaceAll("(?<![&§])(#[0-9a-fA-F]{6})", "&$1");
   }

   private String formatLegacyTag(String text) {
      if (text == null) {
         return "";
      } else {
         String var10000 = this.normalizeHexColors(text);
         return var10000 + "&r";
      }
   }

   private String wrapTag(String text) {
      String tag = text == null ? "" : this.normalizeHexColors(text);
      return "&7[" + tag + "&7]&r";
   }

   public SM_Clans getModule() {
      return this.module;
   }
}
