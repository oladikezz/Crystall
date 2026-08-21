package site.deforce.SM_Clans.models;

import java.util.UUID;

public class ClanMember {
   private final UUID playerId;
   private final String clanId;
   private String roleId;
   private long joinedAt;
   private long lastSeen;

   public ClanMember(UUID playerId, String clanId, String roleId) {
      super();
      this.playerId = playerId;
      this.clanId = clanId;
      this.roleId = roleId;
      this.joinedAt = System.currentTimeMillis();
      this.lastSeen = System.currentTimeMillis();
   }

   public UUID getPlayerId() {
      return this.playerId;
   }

   public String getClanId() {
      return this.clanId;
   }

   public String getRoleId() {
      return this.roleId;
   }

   public void setRoleId(String roleId) {
      this.roleId = roleId;
   }

   public long getJoinedAt() {
      return this.joinedAt;
   }

   public void setJoinedAt(long joinedAt) {
      this.joinedAt = joinedAt;
   }

   public long getLastSeen() {
      return this.lastSeen;
   }

   public void setLastSeen(long lastSeen) {
      this.lastSeen = lastSeen;
   }
}
