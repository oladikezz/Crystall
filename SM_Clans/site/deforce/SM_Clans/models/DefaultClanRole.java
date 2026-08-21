package site.deforce.SM_Clans.models;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum DefaultClanRole {
   LEADER("leader", "Leader", 4),
   CO_LEADER("co_leader", "Co-Leader", 3),
   MODERATOR("moderator", "Moderator", 2),
   MEMBER("member", "Member", 1);

   private final String id;
   private final String displayName;
   private final int priority;
   private final Set<ClanPermission> permissions;

   private DefaultClanRole(String id, String displayName, int priority) {
      this.id = id;
      this.displayName = displayName;
      this.priority = priority;
      this.permissions = EnumSet.noneOf(ClanPermission.class);
      this.initializePermissions();
   }

   private void initializePermissions() {
      switch (this.ordinal()) {
         case 0:
            Collections.addAll(this.permissions, ClanPermission.values());
            break;
         case 1:
            this.permissions.add(ClanPermission.INVITE_MEMBERS);
            this.permissions.add(ClanPermission.KICK_MEMBERS);
            this.permissions.add(ClanPermission.CHANGE_PRIVACY);
            this.permissions.add(ClanPermission.CHANGE_NAME);
            this.permissions.add(ClanPermission.CHANGE_TAG);
            this.permissions.add(ClanPermission.CHANGE_DESCRIPTION);
            this.permissions.add(ClanPermission.ASSIGN_ROLES);
            break;
         case 2:
            this.permissions.add(ClanPermission.INVITE_MEMBERS);
            this.permissions.add(ClanPermission.KICK_MEMBERS);
         case 3:
      }

   }

   public String getId() {
      return this.id;
   }

   public String getDisplayName() {
      return this.displayName;
   }

   public int getPriority() {
      return this.priority;
   }

   public Set<ClanPermission> getPermissions() {
      return Collections.unmodifiableSet(this.permissions);
   }

   public static DefaultClanRole fromId(String id) {
      for(DefaultClanRole role : values()) {
         if (role.id.equals(id)) {
            return role;
         }
      }

      return MEMBER;
   }
}
