package site.deforce.SM_Clans.models;

import java.util.HashSet;
import java.util.Set;

public class ClanRole {
   private final String roleId;
   private final String clanId;
   private String displayName;
   private int priority;
   private final Set<ClanPermission> permissions;
   private boolean isDefault;

   public ClanRole(String roleId, String clanId, String displayName, int priority) {
      super();
      this.roleId = roleId;
      this.clanId = clanId;
      this.displayName = displayName;
      this.priority = priority;
      this.permissions = new HashSet();
      this.isDefault = false;
   }

   public String getRoleId() {
      return this.roleId;
   }

   public String getClanId() {
      return this.clanId;
   }

   public String getDisplayName() {
      return this.displayName;
   }

   public void setDisplayName(String displayName) {
      this.displayName = displayName;
   }

   public int getPriority() {
      return this.priority;
   }

   public void setPriority(int priority) {
      this.priority = priority;
   }

   public Set<ClanPermission> getPermissions() {
      return this.permissions;
   }

   public void addPermission(ClanPermission permission) {
      this.permissions.add(permission);
   }

   public void removePermission(ClanPermission permission) {
      this.permissions.remove(permission);
   }

   public boolean hasPermission(ClanPermission permission) {
      return this.permissions.contains(permission);
   }

   public boolean isDefault() {
      return this.isDefault;
   }

   public void setDefault(boolean isDefault) {
      this.isDefault = isDefault;
   }
}
