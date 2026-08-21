package site.deforce.SM_Clans.models;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class Clan {
   private final String clanId;
   private String tag;
   private String name;
   private String description;
   private UUID leaderId;
   private ClanPrivacy privacy;
   private long createdAt;
   private final Map<UUID, ClanMember> members;
   private int maxMembers;
   private boolean chatEnabled;
   private String bannerColor;
   private boolean tagEnabled;
   private boolean profilePublic;
   private boolean friendlyFire;
   private String flagData;
   private long balance;
   private long lastRentAt;
   private final Map<String, Integer> treasury = new LinkedHashMap();

   public Clan(String clanId, String tag, String name, UUID leaderId) {
      super();
      this.clanId = clanId;
      this.tag = tag;
      this.name = name;
      this.description = "";
      this.leaderId = leaderId;
      this.privacy = ClanPrivacy.INVITE_ONLY;
      this.createdAt = System.currentTimeMillis();
      this.members = new HashMap();
      this.maxMembers = 4;
      this.chatEnabled = true;
      this.bannerColor = "WHITE";
      this.tagEnabled = true;
      this.profilePublic = true;
      this.friendlyFire = false;
      this.flagData = null;
      this.balance = 0L;
      this.lastRentAt = this.createdAt;
   }

   public String getClanId() {
      return this.clanId;
   }

   public String getTag() {
      return this.tag;
   }

   public void setTag(String tag) {
      this.tag = tag;
   }

   public String getName() {
      return this.name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public UUID getLeaderId() {
      return this.leaderId;
   }

   public void setLeaderId(UUID leaderId) {
      this.leaderId = leaderId;
   }

   public ClanPrivacy getPrivacy() {
      return this.privacy;
   }

   public void setPrivacy(ClanPrivacy privacy) {
      this.privacy = privacy;
   }

   public long getCreatedAt() {
      return this.createdAt;
   }

   public void setCreatedAt(long createdAt) {
      this.createdAt = createdAt;
   }

   public Map<UUID, ClanMember> getMembers() {
      return this.members;
   }

   public void addMember(ClanMember member) {
      this.members.put(member.getPlayerId(), member);
   }

   public void removeMember(UUID playerId) {
      this.members.remove(playerId);
   }

   public ClanMember getMember(UUID playerId) {
      return (ClanMember)this.members.get(playerId);
   }

   public boolean hasMember(UUID playerId) {
      return this.members.containsKey(playerId);
   }

   public int getMemberCount() {
      return this.members.size();
   }

   public ClanRole getRole(String roleId) {
      if (roleId == null) {
         return null;
      } else {
         DefaultClanRole defRole = DefaultClanRole.fromId(roleId);
         ClanRole role = new ClanRole(defRole.getId(), this.clanId, defRole.getDisplayName(), defRole.getPriority());
         role.setDefault(true);

         for(ClanPermission perm : defRole.getPermissions()) {
            role.addPermission(perm);
         }

         return role;
      }
   }

   public int getMaxMembers() {
      return this.maxMembers;
   }

   public void setMaxMembers(int maxMembers) {
      this.maxMembers = maxMembers;
   }

   public boolean isFull() {
      return this.members.size() >= this.maxMembers;
   }

   public boolean isChatEnabled() {
      return this.chatEnabled;
   }

   public void setChatEnabled(boolean chatEnabled) {
      this.chatEnabled = chatEnabled;
   }

   public String getDescription() {
      return this.description != null ? this.description : "";
   }

   public void setDescription(String description) {
      this.description = description;
   }

   public String getBannerColor() {
      return this.bannerColor != null ? this.bannerColor : "WHITE";
   }

   public void setBannerColor(String bannerColor) {
      this.bannerColor = bannerColor;
   }

   public boolean isTagEnabled() {
      return this.tagEnabled;
   }

   public void setTagEnabled(boolean tagEnabled) {
      this.tagEnabled = tagEnabled;
   }

   public boolean isProfilePublic() {
      return this.profilePublic;
   }

   public void setProfilePublic(boolean profilePublic) {
      this.profilePublic = profilePublic;
   }

   public boolean isFriendlyFire() {
      return this.friendlyFire;
   }

   public void setFriendlyFire(boolean friendlyFire) {
      this.friendlyFire = friendlyFire;
   }

   public String getFlagData() {
      return this.flagData;
   }

   public void setFlagData(String flagData) {
      this.flagData = flagData;
   }

   public long getBalance() {
      return this.balance;
   }

   public void setBalance(long balance) {
      this.balance = Math.max(0L, balance);
   }

   public long getLastRentAt() {
      return this.lastRentAt;
   }

   public void setLastRentAt(long lastRentAt) {
      this.lastRentAt = lastRentAt;
   }

   public Map<String, Integer> getTreasury() {
      return this.treasury;
   }

   public void addTreasury(String material, int amount) {
      if (material != null && amount > 0) {
         this.treasury.merge(material, amount, Integer::sum);
      }
   }

   public Map<String, Integer> drainTreasury(int amount) {
      Map<String, Integer> taken = new LinkedHashMap();
      if (amount <= 0) {
         return taken;
      } else {
         int remaining = amount;
         Iterator<Map.Entry<String, Integer>> iterator = this.treasury.entrySet().iterator();

         while(iterator.hasNext() && remaining > 0) {
            Map.Entry<String, Integer> entry = (Map.Entry)iterator.next();
            int have = (Integer)entry.getValue();
            int take = Math.min(have, remaining);
            taken.merge((String)entry.getKey(), take, Integer::sum);
            remaining -= take;
            if (take >= have) {
               iterator.remove();
            } else {
               entry.setValue(have - take);
            }
         }

         return taken;
      }
   }

   public String getTreasuryData() {
      StringBuilder builder = new StringBuilder();

      for(Map.Entry<String, Integer> entry : this.treasury.entrySet()) {
         if (entry.getValue() != null && (Integer)entry.getValue() > 0) {
            if (builder.length() > 0) {
               builder.append(",");
            }

            builder.append((String)entry.getKey()).append(":").append(entry.getValue());
         }
      }

      return builder.toString();
   }

   public void setTreasuryData(String data) {
      this.treasury.clear();
      if (data != null && !data.isBlank()) {
         for(String part : data.split(",")) {
            String[] kv = part.split(":");
            if (kv.length == 2) {
               try {
                  int count = Integer.parseInt(kv[1].trim());
                  if (count > 0) {
                     this.treasury.merge(kv[0].trim(), count, Integer::sum);
                  }
               } catch (NumberFormatException var8) {
               }
            }
         }

      }
   }
}
