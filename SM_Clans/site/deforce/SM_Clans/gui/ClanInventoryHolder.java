package site.deforce.SM_Clans.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class ClanInventoryHolder implements InventoryHolder {
   private final MenuType menuType;
   private final String extraData;
   private final int page;

   public ClanInventoryHolder(MenuType menuType) {
      super();
      this.menuType = menuType;
      this.extraData = null;
      this.page = 0;
   }

   public ClanInventoryHolder(MenuType menuType, String extraData) {
      super();
      this.menuType = menuType;
      this.extraData = extraData;
      this.page = 0;
   }

   public ClanInventoryHolder(MenuType menuType, String extraData, int page) {
      super();
      this.menuType = menuType;
      this.extraData = extraData;
      this.page = page;
   }

   public MenuType getMenuType() {
      return this.menuType;
   }

   public String getExtraData() {
      return this.extraData;
   }

   public int getPage() {
      return this.page;
   }

   public Inventory getInventory() {
      return null;
   }

   public static enum MenuType {
      MAIN,
      NO_CLAN,
      CLAN_LIST,
      PROFILE,
      MEMBERS,
      SETTINGS,
      BANNER_COLORS,
      ROLE_ASSIGN,
      PLAYER_MANAGE,
      ADMIN_CLAN,
      ADMIN_CLAN_MANAGE,
      ADMIN_MEMBERS,
      ADMIN_BANK,
      BANK,
      CONFIRM_PURCHASE;

      private MenuType() {
      }
   }
}
