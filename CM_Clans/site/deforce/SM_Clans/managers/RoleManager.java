package site.deforce.SM_Clans.managers;

import java.util.UUID;
import site.deforce.SM_Clans.models.Clan;
import site.deforce.SM_Clans.models.ClanMember;
import site.deforce.SM_Clans.models.ClanPermission;
import site.deforce.SM_Clans.models.ClanRole;

public class RoleManager {
   private final ClanManager clanManager;

   public RoleManager(ClanManager clanManager) {
      super();
      this.clanManager = clanManager;
   }

   public boolean hasPermission(UUID playerId, ClanPermission permission) {
      Clan clan = this.clanManager.getPlayerClan(playerId);
      if (clan == null) {
         return false;
      } else {
         ClanMember member = clan.getMember(playerId);
         if (member == null) {
            return false;
         } else {
            ClanRole role = clan.getRole(member.getRoleId());
            return role == null ? false : role.hasPermission(permission);
         }
      }
   }

   public boolean isLeader(UUID playerId) {
      Clan clan = this.clanManager.getPlayerClan(playerId);
      return clan == null ? false : clan.getLeaderId().equals(playerId);
   }

   public ClanRole getMemberRole(UUID playerId) {
      Clan clan = this.clanManager.getPlayerClan(playerId);
      if (clan == null) {
         return null;
      } else {
         ClanMember member = clan.getMember(playerId);
         return member == null ? null : clan.getRole(member.getRoleId());
      }
   }
}
