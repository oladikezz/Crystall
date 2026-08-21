package site.deforce.SM_Clans.gui;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import site.deforce.SM_Clans.SM_Clans;
import site.deforce.SM_Clans.managers.ClanEconomyManager;
import site.deforce.SM_Clans.managers.ClanManager;
import site.deforce.SM_Clans.managers.RoleManager;
import site.deforce.SM_Clans.models.Clan;
import site.deforce.SM_Clans.models.ClanMember;
import site.deforce.SM_Clans.models.ClanPermission;
import site.deforce.SM_Clans.models.ClanPrivacy;
import site.deforce.SM_Clans.models.ClanRole;
import site.deforce.SM_Clans.models.DefaultClanRole;
import site.deforce.SM_Clans.models.PendingPurchase;
import site.deforce.SM_Clans.util.ClanUpkeep;

public class ClanMenuManager {
   private final SM_Clans module;
   private final ClanManager clanManager;
   private final RoleManager roleManager;
   private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");
   private static final String[] EMPTY_SKIN = new String[0];
   private final Map<UUID, String[]> skinCache = new ConcurrentHashMap();
   private final Set<String> loggedTables = ConcurrentHashMap.newKeySet();
   private static FileConfiguration defaultGuiConfig = null;
   private static FileConfiguration defaultMessagesConfig = null;

   public ClanMenuManager(SM_Clans module, ClanManager clanManager, RoleManager roleManager) {
      super();
      this.module = module;
      this.clanManager = clanManager;
      this.roleManager = roleManager;
   }

   private boolean canOpenSettings(Player player) {
      UUID playerId = player.getUniqueId();
      return this.roleManager.isLeader(playerId) || this.roleManager.hasPermission(playerId, ClanPermission.CHANGE_PRIVACY) || this.roleManager.hasPermission(playerId, ClanPermission.CHANGE_NAME) || this.roleManager.hasPermission(playerId, ClanPermission.CHANGE_TAG) || this.roleManager.hasPermission(playerId, ClanPermission.ASSIGN_ROLES);
   }

   private String legacyToMiniMessage(String text) {
      return text == null ? "" : text.replace("&", "§");
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

   private boolean isColoredTagsEnabled() {
      FileConfiguration config = this.module.getConfig();
      return config == null || config.getBoolean("clans.allow-colored-tags", true);
   }

   private boolean isColoredNamesEnabled() {
      FileConfiguration config = this.module.getConfig();
      return config != null && config.getBoolean("clans.allow-colored-names", false);
   }

   public void playSuccessSound(Player player) {
      player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.2F);
   }

   public void playClickSound(Player player) {
      player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5F, 1.0F);
   }

   public void playErrorSound(Player player) {
      player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
   }

   private boolean canToggleTag(Clan clan) {
      FileConfiguration config = this.module.getConfig();
      if (config == null) {
         return true;
      } else {
         boolean tagActivationEnabled = config.getBoolean("clans.tag-activation.enabled", false);
         if (!tagActivationEnabled) {
            return true;
         } else {
            int minMembers = config.getInt("clans.tag-activation.min-members", 3);
            return clan.getMemberCount() >= minMembers;
         }
      }
   }

   private int getMinMembersForTag() {
      FileConfiguration config = this.module.getConfig();
      return config == null ? 3 : config.getInt("clans.tag-activation.min-members", 3);
   }

   private boolean isTagActivationEnabled() {
      FileConfiguration config = this.module.getConfig();
      return config != null && config.getBoolean("clans.tag-activation.enabled", false);
   }

   private String formatClanTag(Clan clan) {
      String tag = clan.getTag();
      tag = this.normalizeHexColors(tag);
      if (!this.isColoredTagsEnabled()) {
         tag = this.stripColorCodes(tag);
      }

      tag = this.module.getPlugin().applyColors(tag);
      return this.legacyToMiniMessage(tag);
   }

   private String formatClanName(Clan clan) {
      String name = clan.getName();
      if (!this.isColoredNamesEnabled()) {
         name = this.stripColorCodes(name);
      } else {
         name = this.normalizeHexColors(name);
         name = this.module.getPlugin().applyColors(name);
      }

      return this.legacyToMiniMessage(name);
   }

   private String formatUiTitle(String rawTitle) {
      return rawTitle == null ? "" : this.module.getPlugin().applyColors(rawTitle);
   }

   private void fillBorders(Inventory inv) {
      ItemStack glass = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
      ItemMeta glassMeta = glass.getItemMeta();
      glassMeta.displayName(this.deserialize(" "));
      glass.setItemMeta(glassMeta);
      int size = inv.getSize();

      for(int i = 0; i < 9; ++i) {
         if (inv.getItem(i) == null) {
            inv.setItem(i, glass.clone());
         }
      }

      int bottomRowStart = size - 9;

      for(int i = bottomRowStart; i < size; ++i) {
         if (inv.getItem(i) == null) {
            inv.setItem(i, glass.clone());
         }
      }

   }

   private void fillEmpty(Inventory inv) {
      ItemStack glass = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
      ItemMeta glassMeta = glass.getItemMeta();
      glassMeta.displayName(this.deserialize(" "));
      glass.setItemMeta(glassMeta);

      for(int i = 0; i < inv.getSize(); ++i) {
         if (inv.getItem(i) == null) {
            inv.setItem(i, glass.clone());
         }
      }

   }

   private ItemStack createPageIndicator(int currentPage, int totalPages) {
      ItemStack item = new ItemStack(Material.PAPER);
      ItemMeta meta = item.getItemMeta();
      meta.displayName(this.deserialize(this.getGuiMessage("common.page-indicator").replace("{current}", String.valueOf(currentPage + 1)).replace("{total}", String.valueOf(totalPages))));
      item.setItemMeta(meta);
      return item;
   }

   public void openMainMenu(Player player) {
      Clan clan = this.clanManager.getPlayerClan(player.getUniqueId());
      if (clan == null) {
         this.openNoClanMenu(player);
      } else {
         String menuTitleTemplate = this.formatUiTitle(this.getGuiMessage("main.menu-title"));
         String menuTitle = this.formatUiTitle(menuTitleTemplate.replace("{clan}", this.formatClanName(clan)).replace("{tag}", this.formatClanTag(clan)));
         Inventory inv = Bukkit.createInventory(new ClanInventoryHolder(ClanInventoryHolder.MenuType.MAIN), 45, this.deserialize(menuTitle));
         ItemStack clanInfo = this.createClanBanner(clan);
         ItemMeta infoMeta = clanInfo.getItemMeta();
         infoMeta.displayName(this.deserialize(this.getGuiMessage("main.clan-info")));
         List<Component> lore = new ArrayList();
         lore.add(this.deserialize(this.getGuiMessage("main.clan-tag").replace("{tag}", this.formatClanTag(clan))));
         lore.add(this.deserialize(this.getGuiMessage("main.clan-name").replace("{name}", this.formatClanName(clan))));
         lore.add(this.deserialize(""));
         lore.add(this.deserialize(this.getGuiMessage("main.clan-members").replace("{current}", String.valueOf(clan.getMemberCount())).replace("{max}", String.valueOf(clan.getMaxMembers()))));
         lore.add(this.deserialize(this.getGuiMessage("main.clan-privacy").replace("{privacy}", this.getPrivacyName(clan.getPrivacy()))));
         if (clan.getDescription() != null && !clan.getDescription().isEmpty()) {
            lore.add(this.deserialize(this.getGuiMessage("main.clan-description-title")));
            this.addDescriptionLines(lore, this.getGuiMessage("main.clan-description"), clan.getDescription());
         } else {
            lore.add(this.deserialize(this.getGuiMessage("main.clan-description-none")));
         }

         lore.add(this.deserialize(this.getGuiMessage("main.clan-created").replace("{date}", this.dateFormat.format(new Date(clan.getCreatedAt())))));
         this.addUpkeepLore(lore, clan);
         ClanMember member = clan.getMember(player.getUniqueId());
         if (member != null) {
            ClanRole role = clan.getRole(member.getRoleId());
            lore.add(this.deserialize(""));
            lore.add(this.deserialize(this.getGuiMessage("main.your-role").replace("{role}", this.legacyToMiniMessage(role.getDisplayName()))));
         }

         infoMeta.lore(lore);
         clanInfo.setItemMeta(infoMeta);
         inv.setItem(4, clanInfo);
         inv.setItem(10, this.createItem(Material.PLAYER_HEAD, this.getGuiMessage("main.members-list")));
         inv.setItem(12, this.createItem(Material.COMPASS, this.getGuiMessage("main.browse-clans")));
         inv.setItem(14, this.createItem(Material.BOOK, this.getGuiMessage("main.profile")));
         if (this.canOpenSettings(player)) {
            inv.setItem(16, this.createItem(Material.COMPARATOR, this.getGuiMessage("main.settings")));
         }

         ClanEconomyManager mainEcon = this.module.getClanEconomyManager();
         if (mainEcon != null && mainEcon.isEnabled()) {
            inv.setItem(13, this.createItem(Material.GOLD_BLOCK, this.getGuiMessage("main.bank"), this.getGuiMessage("main.bank-balance").replace("{balance}", String.valueOf(clan.getBalance()))));
         }

         if (this.roleManager.isLeader(player.getUniqueId())) {
            inv.setItem(31, this.createItem(Material.BARRIER, this.getGuiMessage("main.disband-clan")));
         } else {
            inv.setItem(31, this.createItem(Material.RED_WOOL, this.getGuiMessage("main.leave-clan")));
         }

         this.fillBorders(inv);
         player.openInventory(inv);
      }

   }

   private void addUpkeepLore(List<Component> lore, Clan clan) {
      ClanEconomyManager econ = this.module.getClanEconomyManager();
      if (econ != null && econ.isRentEnabled()) {
         long cost = econ.getRentForMembers(clan.getMemberCount());
         long remaining = ClanUpkeep.remainingMillis(clan, econ);
         lore.add(this.deserialize(""));
         lore.add(this.deserialize(this.getGuiMessage("main.clan-upkeep").replace("{cost}", String.valueOf(cost))));
         if (remaining <= 0L) {
            lore.add(this.deserialize(this.getGuiMessage("main.clan-upkeep-overdue")));
         } else {
            String time = ClanUpkeep.formatDuration(remaining);
            String key = remaining <= 259200000L ? "main.clan-upkeep-soon" : "main.clan-upkeep-due";
            lore.add(this.deserialize(this.getGuiMessage(key).replace("{time}", time)));
         }
      }
   }

   private void openNoClanMenu(Player player) {
      Inventory inv = Bukkit.createInventory(new ClanInventoryHolder(ClanInventoryHolder.MenuType.NO_CLAN), 27, this.deserialize(this.formatUiTitle(this.getGuiMessage("noclan.menu-title"))));
      inv.setItem(4, this.createItem(Material.NETHER_STAR, this.getGuiMessage("noclan.title-item")));
      ItemStack createBtn = this.createItem(Material.EMERALD, this.getGuiMessage("noclan.create"));
      ItemMeta createMeta = createBtn.getItemMeta();
      List<Component> createLore = new ArrayList();
      createLore.add(this.deserialize(this.getGuiMessage("noclan.create-lore")));
      ClanEconomyManager econ = this.module.getClanEconomyManager();
      if (econ != null && econ.isEnabled() && econ.getCreationCost() > 0) {
         long cost = (long)econ.getCreationCost();
         int have = econ.countCurrency(player);
         createLore.add(this.deserialize(this.getGuiMessage("noclan.create-cost").replace("{cost}", String.valueOf(cost))));
         createLore.add(this.deserialize((long)have >= cost ? this.getGuiMessage("noclan.create-affordable") : this.getGuiMessage("noclan.create-unaffordable").replace("{have}", String.valueOf(have))));
      }

      createMeta.lore(createLore);
      createBtn.setItemMeta(createMeta);
      inv.setItem(11, createBtn);
      inv.setItem(15, this.createItem(Material.COMPASS, this.getGuiMessage("noclan.browse"), this.getGuiMessage("noclan.browse-lore")));
      this.fillBorders(inv);
      player.openInventory(inv);
   }

   public void openClanList(Player player) {
      this.openClanList(player, 0);
   }

   public void openClanList(Player player, int page) {
      List<Clan> allClans = this.clanManager.getAllClans().stream().filter((c) -> c.getPrivacy() != ClanPrivacy.PRIVATE || c.hasMember(player.getUniqueId())).sorted((c1, c2) -> Integer.compare(c2.getMemberCount(), c1.getMemberCount())).toList();
      int[] availableSlots = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
      int clansPerPage = availableSlots.length;
      int totalPages = Math.max(1, (int)Math.ceil((double)allClans.size() / (double)clansPerPage));
      page = Math.max(0, Math.min(page, totalPages - 1));
      int startIndex = page * clansPerPage;
      int endIndex = Math.min(startIndex + clansPerPage, allClans.size());
      Inventory inv = Bukkit.createInventory(new ClanInventoryHolder(ClanInventoryHolder.MenuType.CLAN_LIST, (String)null, page), 54, this.deserialize(this.formatUiTitle(this.getGuiMessage("list.menu-title"))));
      inv.setItem(4, this.createItem(Material.BEACON, this.getGuiMessage("list.title-item"), this.getGuiMessage("list.title-lore").replace("{total}", String.valueOf(allClans.size()))));
      int slotIndex = 0;

      for(int i = startIndex; i < endIndex && slotIndex < availableSlots.length; ++i) {
         Clan clan = (Clan)allClans.get(i);
         int slot = availableSlots[slotIndex++];
         ItemStack item = this.createClanBanner(clan);
         ItemMeta meta = item.getItemMeta();
         meta.displayName(this.deserialize(this.getGuiMessage("list.clan-name").replace("{tag}", this.formatClanTag(clan)).replace("{name}", this.formatClanName(clan))));
         List<Component> lore = new ArrayList();
         lore.add(this.deserialize(this.getGuiMessage("list.clan-members").replace("{count}", String.valueOf(clan.getMemberCount()))));
         lore.add(this.deserialize(this.getGuiMessage("list.clan-privacy").replace("{privacy}", this.getPrivacyName(clan.getPrivacy()))));
         if (!clan.getDescription().isEmpty()) {
            lore.add(this.deserialize(""));
            lore.add(this.deserialize(this.getGuiMessage("list.description")));
            this.addDescriptionLines(lore, "&f{description}", clan.getDescription());
         }

         lore.add(this.deserialize(""));
         lore.add(this.deserialize(this.getGuiMessage("list.click-info")));
         meta.lore(lore);
         item.setItemMeta(meta);
         inv.setItem(slot, item);
      }

      inv.setItem(49, this.createItem(Material.DARK_OAK_DOOR, this.getGuiMessage("common.back")));
      if (page > 0) {
         ItemStack prevBtn = new ItemStack(Material.ARROW);
         ItemMeta prevMeta = prevBtn.getItemMeta();
         prevMeta.displayName(this.deserialize(this.getGuiMessage("list.prev-page")));
         prevBtn.setItemMeta(prevMeta);
         inv.setItem(48, prevBtn);
      }

      if (page < totalPages - 1) {
         ItemStack nextBtn = new ItemStack(Material.ARROW);
         ItemMeta nextMeta = nextBtn.getItemMeta();
         nextMeta.displayName(this.deserialize(this.getGuiMessage("list.next-page")));
         nextBtn.setItemMeta(nextMeta);
         inv.setItem(50, nextBtn);
      }

      if (totalPages > 1) {
         inv.setItem(53, this.createPageIndicator(page, totalPages));
      }

      this.fillBorders(inv);
      player.openInventory(inv);
   }

   public void openClanProfile(Player player, Clan clan) {
      if (clan == null) {
         this.sendMessage(player, this.getMessage("clan-not-found"));
      } else {
         boolean isInClan = clan.hasMember(player.getUniqueId());
         if (clan.getPrivacy() == ClanPrivacy.PRIVATE && !isInClan) {
            this.sendMessage(player, this.getMessage("clan-is-private"));
         } else {
            String profileTitleTemplate = this.getGuiMessage("profile.menu-title");
            String profileTitle = this.formatUiTitle(profileTitleTemplate.replace("{clan}", this.formatClanName(clan)).replace("{tag}", this.formatClanTag(clan)));
            Inventory inv = Bukkit.createInventory(new ClanInventoryHolder(ClanInventoryHolder.MenuType.PROFILE, clan.getTag()), 54, this.deserialize(profileTitle));
            ItemStack info = this.createItem(Material.NETHER_STAR, this.getGuiMessage("profile.title-item").replace("{tag}", this.formatClanTag(clan)).replace("{clan}", this.formatClanName(clan)));
            ItemMeta infoMeta = info.getItemMeta();
            List<Component> infoLore = new ArrayList();
            infoLore.add(this.deserialize(""));
            infoLore.add(this.deserialize(this.getGuiMessage("profile.created").replace("{date}", this.dateFormat.format(new Date(clan.getCreatedAt())))));
            infoLore.add(this.deserialize(this.getGuiMessage("profile.privacy").replace("{privacy}", this.getPrivacyName(clan.getPrivacy()))));
            if (!clan.getDescription().isEmpty()) {
               infoLore.add(this.deserialize(""));
               infoLore.add(this.deserialize(this.getGuiMessage("profile.description-title")));
               this.addDescriptionLines(infoLore, this.getGuiMessage("profile.description"), clan.getDescription());
            }

            infoMeta.lore(infoLore);
            info.setItemMeta(infoMeta);
            inv.setItem(4, info);
            ItemStack membersInfo = this.createItem(Material.PLAYER_HEAD, this.getGuiMessage("profile.members-title"));
            ItemMeta membersMeta = membersInfo.getItemMeta();
            List<Component> membersLore = new ArrayList();
            membersLore.add(this.deserialize(this.getGuiMessage("profile.members-count").replace("{current}", String.valueOf(clan.getMemberCount())).replace("{max}", String.valueOf(clan.getMaxMembers()))));
            membersLore.add(this.deserialize(""));
            membersLore.add(this.deserialize(this.getGuiMessage("profile.members-list")));
            List<ClanMember> membersList = new ArrayList(clan.getMembers().values());
            membersList.sort((m1, m2) -> {
               ClanRole role1 = clan.getRole(m1.getRoleId());
               ClanRole role2 = clan.getRole(m2.getRoleId());
               return Integer.compare(role2.getPriority(), role1.getPriority());
            });
            int count = 0;

            for(ClanMember member : membersList) {
               ++count;
               if (count >= 5) {
                  membersLore.add(this.deserialize(this.getGuiMessage("members.and-more").replace("{count}", String.valueOf(membersList.size() - 4))));
                  break;
               }

               String pName = Bukkit.getOfflinePlayer(member.getPlayerId()).getName();
               ClanRole role = clan.getRole(member.getRoleId());
               String displayName = pName != null ? pName : this.getGuiMessage("members.unknown");
               membersLore.add(this.deserialize("&7* &f" + displayName + " &8- " + this.legacyToMiniMessage(role.getDisplayName())));
            }

            membersMeta.lore(membersLore);
            membersInfo.setItemMeta(membersMeta);
            inv.setItem(20, membersInfo);
            Player leader = Bukkit.getPlayer(clan.getLeaderId());
            ItemStack leaderItem = this.createPlayerHeadForUUID(clan.getLeaderId());
            ItemMeta leaderMeta = leaderItem.getItemMeta();
            String leaderName = Bukkit.getOfflinePlayer(clan.getLeaderId()).getName();
            leaderMeta.displayName(this.deserialize(this.getGuiMessage("profile.leader").replace("{leader}", leaderName != null ? leaderName : this.getGuiMessage("members.unknown"))));
            List<Component> leaderLore = new ArrayList();
            if (leader != null && leader.isOnline()) {
               leaderLore.add(this.deserialize(this.getGuiMessage("members.online")));
            } else {
               leaderLore.add(this.deserialize(this.getGuiMessage("members.offline")));
            }

            leaderMeta.lore(leaderLore);
            leaderItem.setItemMeta(leaderMeta);
            inv.setItem(22, leaderItem);
            Material privacyMat = clan.getPrivacy() == ClanPrivacy.PUBLIC ? Material.LIME_DYE : (clan.getPrivacy() == ClanPrivacy.INVITE_ONLY ? Material.YELLOW_DYE : Material.RED_DYE);
            ItemStack privacyItem = this.createItem(privacyMat, this.getGuiMessage("profile.privacy-title").replace("{privacy}", this.getPrivacyName(clan.getPrivacy())));
            inv.setItem(24, privacyItem);
            if (!isInClan && clan.getPrivacy() == ClanPrivacy.PUBLIC) {
               ItemStack join = this.createItem(Material.EMERALD, this.getGuiMessage("profile.join"), this.getGuiMessage("profile.join-lore"));
               inv.setItem(31, join);
            }

            inv.setItem(49, this.createItem(Material.DARK_OAK_DOOR, this.getGuiMessage("common.back")));
            this.fillBorders(inv);
            player.openInventory(inv);
         }
      }
   }

   public void openOwnClanProfile(Player player) {
      Clan clan = this.clanManager.getPlayerClan(player.getUniqueId());
      if (clan == null) {
         this.sendMessage(player, this.getMessage("not-in-clan"));
      } else {
         this.openClanProfile(player, clan);
      }

   }

   public void openClanProfileByTag(Player player, String tag) {
      Clan clan = this.clanManager.getClanByTag(tag.trim());
      if (clan == null) {
         this.sendMessage(player, this.getMessage("clan-not-found"));
      } else {
         this.openClanProfile(player, clan);
      }

   }

   public void openSettingsMenu(Player player) {
      Clan clan = this.clanManager.getPlayerClan(player.getUniqueId());
      if (clan == null) {
         this.sendMessage(player, this.getMessage("not-in-clan"));
      } else if (!this.canOpenSettings(player)) {
         this.sendMessage(player, this.getMessage("no-permission"));
      } else {
         String settingsTitle = this.formatUiTitle(this.getGuiMessage("settings.menu-title"));
         Inventory inv = Bukkit.createInventory(new ClanInventoryHolder(ClanInventoryHolder.MenuType.SETTINGS), 54, this.deserialize(settingsTitle + " " + this.formatClanTag(clan)));
         inv.setItem(4, this.createItem(Material.COMPARATOR, this.getGuiMessage("settings.title-item"), this.getGuiMessage("settings.title-lore").replace("{clan}", this.formatClanName(clan))));
         ClanPrivacy currentPrivacy = clan.getPrivacy();
         Material publicMat = currentPrivacy == ClanPrivacy.PUBLIC ? Material.LIME_WOOL : Material.WHITE_WOOL;
         ItemStack publicBtn = this.createItem(publicMat, this.getGuiMessage("settings.privacy-public"));
         ItemMeta publicMeta = publicBtn.getItemMeta();
         List<Component> publicLore = new ArrayList();
         publicLore.add(this.deserialize(this.getGuiMessage("settings.privacy-public-lore")));
         if (currentPrivacy == ClanPrivacy.PUBLIC) {
            publicLore.add(this.deserialize(this.getGuiMessage("settings.privacy-current")));
         }

         publicMeta.lore(publicLore);
         publicBtn.setItemMeta(publicMeta);
         inv.setItem(19, publicBtn);
         Material inviteMat = currentPrivacy == ClanPrivacy.INVITE_ONLY ? Material.LIME_WOOL : Material.WHITE_WOOL;
         ItemStack inviteBtn = this.createItem(inviteMat, this.getGuiMessage("settings.privacy-invite"));
         ItemMeta inviteMeta = inviteBtn.getItemMeta();
         List<Component> inviteLore = new ArrayList();
         inviteLore.add(this.deserialize(this.getGuiMessage("settings.privacy-invite-lore")));
         if (currentPrivacy == ClanPrivacy.INVITE_ONLY) {
            inviteLore.add(this.deserialize(this.getGuiMessage("settings.privacy-current")));
         }

         inviteMeta.lore(inviteLore);
         inviteBtn.setItemMeta(inviteMeta);
         inv.setItem(22, inviteBtn);
         Material privateMat = currentPrivacy == ClanPrivacy.PRIVATE ? Material.LIME_WOOL : Material.WHITE_WOOL;
         ItemStack privateBtn = this.createItem(privateMat, this.getGuiMessage("settings.privacy-private"));
         ItemMeta privateMeta = privateBtn.getItemMeta();
         List<Component> privateLore = new ArrayList();
         privateLore.add(this.deserialize(this.getGuiMessage("settings.privacy-private-lore")));
         if (currentPrivacy == ClanPrivacy.PRIVATE) {
            privateLore.add(this.deserialize(this.getGuiMessage("settings.privacy-current")));
         }

         privateMeta.lore(privateLore);
         privateBtn.setItemMeta(privateMeta);
         inv.setItem(25, privateBtn);
         ClanEconomyManager econ = this.module.getClanEconomyManager();
         ItemStack nameBtn = this.createItem(Material.WRITABLE_BOOK, this.getGuiMessage("settings.change-name"));
         ItemMeta nameMeta = nameBtn.getItemMeta();
         List<Component> nameLore = new ArrayList();
         nameLore.add(this.deserialize(this.getGuiMessage("settings.change-name-lore")));
         nameLore.add(this.deserialize(this.getGuiMessage("settings.name-current").replace("{name}", this.formatClanName(clan))));
         this.addPriceLore(nameLore, clan, econ != null ? (long)econ.getNameCost() : 0L);
         nameMeta.lore(nameLore);
         nameBtn.setItemMeta(nameMeta);
         inv.setItem(29, nameBtn);
         ItemStack tagBtn = this.createItem(Material.NAME_TAG, this.getGuiMessage("settings.change-tag"));
         ItemMeta tagMeta = tagBtn.getItemMeta();
         List<Component> tagLore = new ArrayList();
         tagLore.add(this.deserialize(this.getGuiMessage("settings.change-tag-lore")));
         tagLore.add(this.deserialize(this.getGuiMessage("settings.tag-current").replace("{tag}", this.formatClanTag(clan))));
         this.addPriceLore(tagLore, clan, econ != null ? (long)econ.getTagCost() : 0L);
         tagMeta.lore(tagLore);
         tagBtn.setItemMeta(tagMeta);
         inv.setItem(31, tagBtn);
         ItemStack descBtn = this.createItem(Material.BOOK, this.getGuiMessage("settings.change-description"));
         ItemMeta descMeta = descBtn.getItemMeta();
         List<Component> descLore = new ArrayList();
         descLore.add(this.deserialize(this.getGuiMessage("settings.change-description-lore")));
         String currentDesc = clan.getDescription();
         if (currentDesc.isEmpty()) {
            descLore.add(this.deserialize(this.getGuiMessage("settings.description-none")));
         } else {
            this.addDescriptionLines(descLore, this.getGuiMessage("settings.description-current"), currentDesc);
         }

         this.addPriceLore(descLore, clan, econ != null ? (long)econ.getDescriptionCost() : 0L);
         descMeta.lore(descLore);
         descBtn.setItemMeta(descMeta);
         inv.setItem(33, descBtn);
         Material bannerMaterial = this.getBannerMaterial(clan.getBannerColor());
         ItemStack colorBtn = this.createItem(bannerMaterial, this.getGuiMessage("settings.change-banner"));
         ItemMeta colorMeta = colorBtn.getItemMeta();
         List<Component> colorLore = new ArrayList();
         colorLore.add(this.deserialize(this.getGuiMessage("settings.change-banner-lore")));
         colorLore.add(this.deserialize(this.getGuiMessage("settings.banner-current").replace("{color}", clan.getBannerColor())));
         this.addPriceLore(colorLore, clan, econ != null ? (long)econ.getBannerColorCost() : 0L);
         colorMeta.lore(colorLore);
         colorBtn.setItemMeta(colorMeta);
         inv.setItem(35, colorBtn);
         if (this.roleManager.hasPermission(player.getUniqueId(), ClanPermission.ASSIGN_ROLES)) {
            ItemStack manageMembersBtn = this.createItem(Material.PLAYER_HEAD, this.getGuiMessage("settings.manage-roles"));
            inv.setItem(40, manageMembersBtn);
         }

         boolean canToggle = this.canToggleTag(clan);
         ItemStack tagToggleBtn;
         if (canToggle) {
            tagToggleBtn = this.createItem(clan.isTagEnabled() ? Material.LIME_DYE : Material.GRAY_DYE, this.getGuiMessage("settings.settings-tag-toggle"), clan.isTagEnabled() ? this.getGuiMessage("settings.toggle-enabled") : this.getGuiMessage("settings.toggle-disabled"), this.getGuiMessage("settings.toggle-click"));
         } else {
            int minMembers = this.getMinMembersForTag();
            int currentMembers = clan.getMemberCount();
            tagToggleBtn = this.createItem(Material.BARRIER, this.getGuiMessage("settings.settings-tag-toggle"), this.getGuiMessage("settings.tag-toggle-disabled-lore").replace("{min}", String.valueOf(minMembers)).replace("{current}", String.valueOf(currentMembers)));
         }

         inv.setItem(41, tagToggleBtn);
         ItemStack friendlyFireBtn = this.createItem(clan.isFriendlyFire() ? Material.RED_DYE : Material.GRAY_DYE, this.getGuiMessage("settings.friendly-fire"), clan.isFriendlyFire() ? this.getGuiMessage("settings.friendly-fire-enabled") : this.getGuiMessage("settings.friendly-fire-disabled"), this.getGuiMessage("settings.toggle-click"));
         inv.setItem(42, friendlyFireBtn);
         ItemStack setFlagBtn = this.createClanBanner(clan);
         ItemMeta setFlagMeta = setFlagBtn.getItemMeta();
         setFlagMeta.displayName(this.deserialize(this.getGuiMessage("settings.set-flag")));
         List<Component> setFlagLore = new ArrayList();
         setFlagLore.add(this.deserialize(this.getGuiMessage("settings.set-flag-lore")));
         if (clan.getFlagData() != null && !clan.getFlagData().isEmpty()) {
            setFlagLore.add(this.deserialize(this.getGuiMessage("settings.flag-custom")));
            setFlagLore.add(this.deserialize(this.getGuiMessage("settings.flag-clear-hint")));
         } else {
            setFlagLore.add(this.deserialize(this.getGuiMessage("settings.flag-simple").replace("{color}", clan.getBannerColor())));
         }

         this.addPriceLore(setFlagLore, clan, econ != null ? (long)econ.getCustomBannerCost() : 0L);
         setFlagMeta.lore(setFlagLore);
         setFlagBtn.setItemMeta(setFlagMeta);
         inv.setItem(37, setFlagBtn);
         if (econ != null && econ.isEnabled()) {
            long slotPackageCost = (long)econ.getSlotCostPerSlot() * (long)econ.getSlotsPerPurchase();
            ItemStack buySlotsBtn = this.createItem(Material.ANVIL, this.getGuiMessage("settings.buy-slots"));
            ItemMeta buySlotsMeta = buySlotsBtn.getItemMeta();
            List<Component> buySlotsLore = new ArrayList();
            buySlotsLore.add(this.deserialize(this.getGuiMessage("settings.buy-slots-lore").replace("{slots}", String.valueOf(econ.getSlotsPerPurchase())).replace("{cost}", String.valueOf(slotPackageCost))));
            buySlotsLore.add(this.deserialize(this.getGuiMessage("settings.slots-current").replace("{current}", String.valueOf(clan.getMaxMembers())).replace("{max}", String.valueOf(econ.getMaxSlots()))));
            this.addAffordabilityLore(buySlotsLore, clan, slotPackageCost);
            buySlotsMeta.lore(buySlotsLore);
            buySlotsBtn.setItemMeta(buySlotsMeta);
            inv.setItem(38, buySlotsBtn);
            ItemStack bankBtn = this.createItem(Material.GOLD_BLOCK, this.getGuiMessage("settings.bank"), this.getGuiMessage("settings.bank-lore").replace("{balance}", String.valueOf(clan.getBalance())));
            inv.setItem(39, bankBtn);
         }

         inv.setItem(49, this.createItem(Material.DARK_OAK_DOOR, this.getGuiMessage("common.back")));
         this.fillBorders(inv);
         player.openInventory(inv);
      }

   }

   public void openConfirmPurchase(Player player, PendingPurchase purchase) {
      Clan clan = this.clanManager.getPlayerClan(player.getUniqueId());
      long balance = clan != null ? clan.getBalance() : 0L;
      long cost = purchase.getCost();
      Inventory inv = Bukkit.createInventory(new ClanInventoryHolder(ClanInventoryHolder.MenuType.CONFIRM_PURCHASE), 27, this.deserialize(this.formatUiTitle(this.getGuiMessage("confirm.menu-title"))));
      ItemStack info = this.createItem(Material.PAPER, this.getGuiMessage("confirm.info"));
      ItemMeta infoMeta = info.getItemMeta();
      List<Component> infoLore = new ArrayList();
      infoLore.add(this.deserialize(this.getGuiMessage("confirm.info-what").replace("{label}", purchase.getLabel())));
      infoLore.add(this.deserialize(this.getGuiMessage("confirm.info-cost").replace("{cost}", String.valueOf(cost))));
      infoLore.add(this.deserialize(this.getGuiMessage("confirm.info-balance").replace("{balance}", String.valueOf(balance))));
      infoLore.add(this.deserialize(balance >= cost ? this.getGuiMessage("confirm.affordable") : this.getGuiMessage("confirm.unaffordable")));
      infoMeta.lore(infoLore);
      info.setItemMeta(infoMeta);
      inv.setItem(13, info);
      inv.setItem(11, this.createItem(Material.LIME_WOOL, this.getGuiMessage("confirm.confirm"), this.getGuiMessage("confirm.confirm-lore").replace("{cost}", String.valueOf(cost))));
      inv.setItem(15, this.createItem(Material.RED_WOOL, this.getGuiMessage("confirm.cancel"), this.getGuiMessage("confirm.cancel-lore")));
      this.fillBorders(inv);
      player.openInventory(inv);
   }

   public void openMembersList(Player player, Clan clan) {
      this.openMembersList(player, clan, 0);
   }

   public void openMembersList(Player player, Clan clan, int page) {
      boolean canManage = this.roleManager.hasPermission(player.getUniqueId(), ClanPermission.ASSIGN_ROLES) || this.roleManager.hasPermission(player.getUniqueId(), ClanPermission.KICK_MEMBERS) || this.roleManager.isLeader(player.getUniqueId());
      boolean canAssignRoles = this.roleManager.hasPermission(player.getUniqueId(), ClanPermission.ASSIGN_ROLES);
      boolean canKick = this.roleManager.hasPermission(player.getUniqueId(), ClanPermission.KICK_MEMBERS);
      boolean isLeader = this.roleManager.isLeader(player.getUniqueId());
      ClanMember viewerMember = clan.getMember(player.getUniqueId());
      ClanRole viewerRole = viewerMember != null ? clan.getRole(viewerMember.getRoleId()) : null;
      int viewerPriority = viewerRole != null ? viewerRole.getPriority() : 0;
      List<ClanMember> members = new ArrayList(clan.getMembers().values());
      members.sort((m1, m2) -> {
         ClanRole role1 = clan.getRole(m1.getRoleId());
         ClanRole role2 = clan.getRole(m2.getRoleId());
         return Integer.compare(role2.getPriority(), role1.getPriority());
      });
      int membersPerPage = 4;
      int totalPages = Math.max(1, (int)Math.ceil((double)members.size() / (double)membersPerPage));
      page = Math.max(0, Math.min(page, totalPages - 1));
      int startIndex = page * membersPerPage;
      int endIndex = Math.min(startIndex + membersPerPage, members.size());
      String membersTitle = this.formatUiTitle(this.getGuiMessage("members.menu-title"));
      Inventory inv = Bukkit.createInventory(new ClanInventoryHolder(ClanInventoryHolder.MenuType.MEMBERS, (String)null, page), 54, this.deserialize(membersTitle));
      inv.setItem(4, this.createItem(Material.PLAYER_HEAD, this.getGuiMessage("members.title-item"), this.getGuiMessage("members.title-lore").replace("{current}", String.valueOf(clan.getMemberCount())).replace("{max}", String.valueOf(clan.getMaxMembers()))));

      for(int i = startIndex; i < endIndex; ++i) {
         ClanMember member = (ClanMember)members.get(i);
         int rowIndex = i - startIndex;
         int baseSlot = (rowIndex + 1) * 9;
         Player memberPlayer = Bukkit.getPlayer(member.getPlayerId());
         OfflinePlayer offlineMember = Bukkit.getOfflinePlayer(member.getPlayerId());
         String playerName = offlineMember.getName() != null ? offlineMember.getName() : "Unknown";
         ClanRole memberRole = clan.getRole(member.getRoleId());
         boolean isMemberLeader = clan.getLeaderId().equals(member.getPlayerId());
         boolean isSelf = player.getUniqueId().equals(member.getPlayerId());
         boolean canManageThisMember = canManage && !isMemberLeader && !isSelf && (isLeader || viewerPriority > memberRole.getPriority());
         ItemStack head = this.createPlayerHeadForUUID(member.getPlayerId());
         ItemMeta headMeta = head.getItemMeta();
         headMeta.displayName(this.deserialize("&b" + playerName));
         List<Component> headLore = new ArrayList();
         String roleColor = this.getRoleColor(memberRole.getRoleId());
         headLore.add(this.deserialize(this.getGuiMessage("members.role").replace("{role}", roleColor + this.stripColorCodes(memberRole.getDisplayName()))));
         headLore.add(this.deserialize(this.getGuiMessage("members.joined").replace("{date}", this.dateFormat.format(new Date(member.getJoinedAt())))));
         if (memberPlayer != null && memberPlayer.isOnline()) {
            headLore.add(this.deserialize(this.getGuiMessage("members.online")));
         } else {
            headLore.add(this.deserialize(this.getGuiMessage("members.offline").replace("{time}", this.dateFormat.format(new Date(member.getLastSeen())))));
         }

         headLore.add(this.deserialize("&8UUID: " + member.getPlayerId().toString()));
         headMeta.lore(headLore);
         head.setItemMeta(headMeta);
         inv.setItem(baseSlot, head);
         Material roleIndicator = this.getRoleIndicatorMaterial(memberRole.getRoleId());
         ItemStack roleItem = new ItemStack(roleIndicator);
         ItemMeta roleMeta = roleItem.getItemMeta();
         roleMeta.displayName(this.deserialize(roleColor + this.stripColorCodes(memberRole.getDisplayName())));
         List<Component> roleLore = new ArrayList();
         roleLore.add(this.deserialize(this.getGuiMessage("members.priority").replace("{priority}", String.valueOf(memberRole.getPriority()))));
         roleLore.add(this.deserialize(""));
         String roleDesc = this.getGuiMessage("roles." + memberRole.getRoleId().toLowerCase() + ".description");
         if (!roleDesc.startsWith("§cMessage not found")) {
            roleLore.add(this.deserialize(roleDesc));
            roleLore.add(this.deserialize(""));
         }

         List<String> rolePerms = this.getRolePermissionsLore(memberRole.getRoleId());
         if (!rolePerms.isEmpty()) {
            roleLore.add(this.deserialize(this.getGuiMessage("roles.permissions-header")));

            for(String perm : rolePerms) {
               roleLore.add(this.deserialize(perm));
            }
         }

         roleMeta.lore(roleLore);
         roleItem.setItemMeta(roleMeta);
         inv.setItem(baseSlot + 1, roleItem);
         if (canAssignRoles && canManageThisMember) {
            ItemStack changeRoleBtn = new ItemStack(Material.COMPARATOR);
            ItemMeta changeMeta = changeRoleBtn.getItemMeta();
            changeMeta.displayName(this.deserialize(this.getGuiMessage("members.change-role-btn")));
            List<Component> changeLore = new ArrayList();
            changeLore.add(this.deserialize(this.getGuiMessage("members.change-role-left")));
            changeLore.add(this.deserialize(this.getGuiMessage("members.change-role-right")));
            changeLore.add(this.deserialize("&8UUID: " + member.getPlayerId().toString()));
            changeLore.add(this.deserialize("&8RoleID: " + memberRole.getRoleId()));
            changeMeta.lore(changeLore);
            changeRoleBtn.setItemMeta(changeMeta);
            inv.setItem(baseSlot + 2, changeRoleBtn);
         }

         if (canKick && canManageThisMember) {
            ItemStack kickBtn = new ItemStack(Material.BARRIER);
            ItemMeta kickMeta = kickBtn.getItemMeta();
            kickMeta.displayName(this.deserialize(this.getGuiMessage("members.kick-btn")));
            List<Component> kickLore = new ArrayList();
            kickLore.add(this.deserialize(this.getGuiMessage("members.kick-btn-lore")));
            kickLore.add(this.deserialize("&8UUID: " + member.getPlayerId().toString()));
            kickLore.add(this.deserialize("&8Name: " + playerName));
            kickMeta.lore(kickLore);
            kickBtn.setItemMeta(kickMeta);
            inv.setItem(baseSlot + 4, kickBtn);
         }

         if (isLeader && !isMemberLeader && !isSelf) {
            ItemStack promoteBtn = new ItemStack(Material.GOLDEN_HELMET);
            ItemMeta promoteMeta = promoteBtn.getItemMeta();
            promoteMeta.displayName(this.deserialize(this.getGuiMessage("members.promote-btn")));
            List<Component> promoteLore = new ArrayList();
            promoteLore.add(this.deserialize(this.getGuiMessage("members.promote-btn-lore")));
            promoteLore.add(this.deserialize(this.getGuiMessage("members.promote-btn-warning")));
            promoteLore.add(this.deserialize("&8UUID: " + member.getPlayerId().toString()));
            promoteLore.add(this.deserialize("&8Name: " + playerName));
            promoteMeta.lore(promoteLore);
            promoteBtn.setItemMeta(promoteMeta);
            inv.setItem(baseSlot + 5, promoteBtn);
         }
      }

      if (page > 0) {
         ItemStack prevBtn = new ItemStack(Material.ARROW);
         ItemMeta prevMeta = prevBtn.getItemMeta();
         prevMeta.displayName(this.deserialize(this.getGuiMessage("members.prev-page")));
         prevBtn.setItemMeta(prevMeta);
         inv.setItem(48, prevBtn);
      }

      inv.setItem(49, this.createItem(Material.DARK_OAK_DOOR, this.getGuiMessage("common.back")));
      if (page < totalPages - 1) {
         ItemStack nextBtn = new ItemStack(Material.ARROW);
         ItemMeta nextMeta = nextBtn.getItemMeta();
         nextMeta.displayName(this.deserialize(this.getGuiMessage("members.next-page")));
         nextBtn.setItemMeta(nextMeta);
         inv.setItem(50, nextBtn);
      }

      if (totalPages > 1) {
         inv.setItem(53, this.createPageIndicator(page, totalPages));
      }

      this.fillBorders(inv);
      player.openInventory(inv);
   }

   public void openBannerColorMenu(Player player) {
      Inventory inv = Bukkit.createInventory(new ClanInventoryHolder(ClanInventoryHolder.MenuType.BANNER_COLORS), 27, this.deserialize(this.formatUiTitle(this.getGuiMessage("settings.banner-menu-title"))));
      Material[] banners = new Material[]{Material.WHITE_BANNER, Material.ORANGE_BANNER, Material.MAGENTA_BANNER, Material.LIGHT_BLUE_BANNER, Material.YELLOW_BANNER, Material.LIME_BANNER, Material.PINK_BANNER, Material.GRAY_BANNER, Material.LIGHT_GRAY_BANNER, Material.CYAN_BANNER, Material.PURPLE_BANNER, Material.BLUE_BANNER, Material.BROWN_BANNER, Material.GREEN_BANNER, Material.RED_BANNER, Material.BLACK_BANNER};

      for(int i = 0; i < banners.length; ++i) {
         String colorName = banners[i].name().replace("_BANNER", "");
         ItemStack item = new ItemStack(banners[i]);
         ItemMeta meta = item.getItemMeta();
         meta.displayName(this.deserialize("&f" + colorName));
         item.setItemMeta(meta);
         inv.setItem(i, item);
      }

      inv.setItem(22, this.createItem(Material.DARK_OAK_DOOR, this.getGuiMessage("common.back")));
      this.fillBorders(inv);
      player.openInventory(inv);
   }

   public void openBankMenu(Player player) {
      Clan clan = this.clanManager.getPlayerClan(player.getUniqueId());
      if (clan == null) {
         this.sendMessage(player, this.getMessage("not-in-clan"));
      } else {
         ClanEconomyManager econ = this.module.getClanEconomyManager();
         Inventory inv = Bukkit.createInventory(new ClanInventoryHolder(ClanInventoryHolder.MenuType.BANK), 27, this.deserialize(this.formatUiTitle(this.getGuiMessage("bank.menu-title"))));
         inv.setItem(4, this.buildBankInfoItem(clan, econ));
         boolean canTransact = econ != null && econ.isLeaderOrCoLeader(clan, player.getUniqueId());
         if (canTransact) {
            int[] amounts = new int[]{1, 16, 32, 64};
            int[] depositSlots = new int[]{9, 10, 11, 12};
            int[] withdrawSlots = new int[]{14, 15, 16, 17};

            for(int i = 0; i < amounts.length; ++i) {
               inv.setItem(depositSlots[i], this.makeBankButton(Material.LIME_STAINED_GLASS_PANE, "DEPOSIT", amounts[i]));
               inv.setItem(withdrawSlots[i], this.makeBankButton(Material.RED_STAINED_GLASS_PANE, "WITHDRAW", amounts[i]));
            }

            inv.setItem(3, this.makeBankButton(Material.LIME_STAINED_GLASS_PANE, "DEPOSIT", -1));
            inv.setItem(5, this.makeBankButton(Material.RED_STAINED_GLASS_PANE, "WITHDRAW", -1));
            inv.setItem(21, this.createItem(Material.PAPER, this.getGuiMessage("bank.deposit-custom"), "&8Action: DEPOSIT", "&8Amount: custom"));
            inv.setItem(23, this.createItem(Material.PAPER, this.getGuiMessage("bank.withdraw-custom"), "&8Action: WITHDRAW", "&8Amount: custom"));
         } else {
            inv.setItem(13, this.createItem(Material.PAPER, this.getGuiMessage("bank.view-only")));
         }

         inv.setItem(22, this.createItem(Material.DARK_OAK_DOOR, this.getGuiMessage("common.back")));
         this.fillBorders(inv);
         player.openInventory(inv);
      }
   }

   private ItemStack buildBankInfoItem(Clan clan, ClanEconomyManager econ) {
      ItemStack info = new ItemStack(Material.GOLD_BLOCK);
      ItemMeta infoMeta = info.getItemMeta();
      infoMeta.displayName(this.deserialize(this.getGuiMessage("bank.title-item")));
      List<Component> infoLore = new ArrayList();
      infoLore.add(this.deserialize(this.getGuiMessage("bank.balance").replace("{balance}", String.valueOf(clan.getBalance()))));
      if (econ != null && econ.isRentEnabled()) {
         infoLore.add(this.deserialize(""));
         infoLore.add(this.deserialize(this.getGuiMessage("bank.rent-amount").replace("{amount}", String.valueOf(econ.getRentForMembers(clan.getMemberCount())))));
         long next = clan.getLastRentAt() + econ.getRentPeriodMillis();
         infoLore.add(this.deserialize(this.getGuiMessage("bank.rent-next").replace("{date}", this.dateFormat.format(new Date(next)))));
      }

      infoMeta.lore(infoLore);
      info.setItemMeta(infoMeta);
      return info;
   }

   public void refreshBankInfo(Inventory inv, Player player) {
      if (inv != null && player != null) {
         Clan clan = this.clanManager.getPlayerClan(player.getUniqueId());
         if (clan != null) {
            inv.setItem(4, this.buildBankInfoItem(clan, this.module.getClanEconomyManager()));
         }
      }
   }

   private ItemStack makeBankButton(Material material, String action, int amount) {
      String amountLabel = amount < 0 ? "all" : String.valueOf(amount);
      String nameKey = action.equals("DEPOSIT") ? (amount < 0 ? "bank.deposit-all" : "bank.deposit") : (amount < 0 ? "bank.withdraw-all" : "bank.withdraw");
      ItemStack item = new ItemStack(material);
      if (amount > 0) {
         item.setAmount(Math.max(1, Math.min(material.getMaxStackSize(), amount)));
      }

      ItemMeta meta = item.getItemMeta();
      meta.displayName(this.deserialize(this.getGuiMessage(nameKey).replace("{amount}", amountLabel)));
      List<Component> lore = new ArrayList();
      lore.add(this.deserialize("&8Action: " + action));
      lore.add(this.deserialize("&8Amount: " + amountLabel));
      meta.lore(lore);
      if (amount < 0) {
         meta.addEnchant(Enchantment.UNBREAKING, 1, true);
         meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ENCHANTS});
      }

      item.setItemMeta(meta);
      return item;
   }

   public void openAdminBankMenu(Player admin, Clan clan) {
      if (clan != null) {
         ClanInventoryHolder var10000 = new ClanInventoryHolder(ClanInventoryHolder.MenuType.ADMIN_BANK, clan.getTag());
         String var10004 = this.formatClanTag(clan);
         Inventory inv = Bukkit.createInventory(var10000, 27, this.deserialize(this.formatUiTitle("&6Казна: " + var10004)));
         inv.setItem(4, this.buildAdminBankInfoItem(clan));
         inv.setItem(5, this.makeAdminBankButton(Material.RED_STAINED_GLASS_PANE, "REMOVEALL", -1, "&cОпустошить казну"));
         int[] amounts = new int[]{1, 16, 32, 64};
         int[] addSlots = new int[]{9, 10, 11, 12};
         int[] removeSlots = new int[]{14, 15, 16, 17};

         for(int i = 0; i < amounts.length; ++i) {
            inv.setItem(addSlots[i], this.makeAdminBankButton(Material.LIME_STAINED_GLASS_PANE, "ADD", amounts[i], "&aДобавить " + amounts[i]));
            inv.setItem(removeSlots[i], this.makeAdminBankButton(Material.RED_STAINED_GLASS_PANE, "REMOVE", amounts[i], "&cЗабрать " + amounts[i]));
         }

         inv.setItem(21, this.makeAdminBankButton(Material.PAPER, "ADDCUSTOM", 0, "&aДобавить (ввести число)"));
         inv.setItem(23, this.makeAdminBankButton(Material.PAPER, "REMOVECUSTOM", 0, "&cЗабрать (ввести число)"));
         inv.setItem(22, this.createItem(Material.DARK_OAK_DOOR, this.getGuiMessage("common.back")));
         this.fillEmpty(inv);
         admin.openInventory(inv);
      }
   }

   private ItemStack buildAdminBankInfoItem(Clan clan) {
      ItemStack info = new ItemStack(Material.GOLD_BLOCK);
      ItemMeta meta = info.getItemMeta();
      String var10002 = this.formatClanTag(clan);
      meta.displayName(this.deserialize("&6Казна &f[" + var10002 + "&f]"));
      List<Component> lore = new ArrayList();
      lore.add(this.deserialize("&7Баланс: &f" + clan.getBalance() + " ар"));
      meta.lore(lore);
      info.setItemMeta(meta);
      return info;
   }

   public void refreshAdminBankInfo(Inventory inv, Clan clan) {
      if (inv != null && clan != null) {
         inv.setItem(4, this.buildAdminBankInfoItem(clan));
      }
   }

   private ItemStack makeAdminBankButton(Material material, String action, int amount, String name) {
      String amountLabel = amount < 0 ? "all" : (amount == 0 ? "custom" : String.valueOf(amount));
      ItemStack item = new ItemStack(material);
      if (amount > 0) {
         item.setAmount(Math.max(1, Math.min(material.getMaxStackSize(), amount)));
      }

      ItemMeta meta = item.getItemMeta();
      meta.displayName(this.deserialize(name));
      List<Component> lore = new ArrayList();
      lore.add(this.deserialize("&8Action: " + action));
      lore.add(this.deserialize("&8Amount: " + amountLabel));
      meta.lore(lore);
      if (amount < 0) {
         meta.addEnchant(Enchantment.UNBREAKING, 1, true);
         meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ENCHANTS});
      }

      item.setItemMeta(meta);
      return item;
   }

   public void openMemberRoleMenu(Player player, UUID targetPlayerId) {
      Clan clan = this.clanManager.getPlayerClan(player.getUniqueId());
      if (clan == null) {
         this.sendMessage(player, this.getMessage("not-in-clan"));
      } else {
         ClanMember targetMember = clan.getMember(targetPlayerId);
         if (targetMember == null) {
            this.sendMessage(player, this.getMessage("player-not-in-clan"));
         } else {
            String targetName = Bukkit.getOfflinePlayer(targetPlayerId).getName();
            if (targetName == null) {
               targetName = "Unknown";
            }

            Inventory inv = Bukkit.createInventory(new ClanInventoryHolder(ClanInventoryHolder.MenuType.ROLE_ASSIGN, targetPlayerId.toString()), 27, this.deserialize(this.formatUiTitle(this.getGuiMessage("members.role-menu-title"))));
            inv.setItem(4, this.createItem(Material.PLAYER_HEAD, "&b" + targetName, this.getGuiMessage("members.role").replace("{role}", this.legacyToMiniMessage(clan.getRole(targetMember.getRoleId()).getDisplayName()))));
            DefaultClanRole[] assignable = new DefaultClanRole[]{DefaultClanRole.CO_LEADER, DefaultClanRole.MODERATOR, DefaultClanRole.MEMBER};
            int slot = 10;

            for(DefaultClanRole defRole : assignable) {
               boolean isCurrent = defRole.getId().equals(targetMember.getRoleId());
               Material mat = isCurrent ? Material.LIME_WOOL : Material.WHITE_WOOL;
               ItemStack roleItem = new ItemStack(mat);
               ItemMeta meta = roleItem.getItemMeta();
               meta.displayName(this.deserialize("&e" + defRole.getDisplayName()));
               List<Component> lore = new ArrayList();
               lore.add(this.deserialize(this.getGuiMessage("members.priority").replace("{priority}", String.valueOf(defRole.getPriority()))));
               if (isCurrent) {
                  lore.add(this.deserialize(this.getGuiMessage("settings.privacy-current")));
               }

               lore.add(this.deserialize("&8RoleID: " + defRole.getId()));
               lore.add(this.deserialize("&8TargetUUID: " + String.valueOf(targetPlayerId)));
               meta.lore(lore);
               roleItem.setItemMeta(meta);
               inv.setItem(slot, roleItem);
               slot += 2;
            }

            inv.setItem(22, this.createItem(Material.DARK_OAK_DOOR, this.getGuiMessage("common.back")));
            this.fillBorders(inv);
            player.openInventory(inv);
         }
      }
   }

   public void openPlayerManageMenu(Player player, UUID targetPlayerId) {
      Clan clan = this.clanManager.getPlayerClan(player.getUniqueId());
      if (clan == null) {
         this.sendMessage(player, this.getMessage("not-in-clan"));
      } else {
         ClanMember targetMember = clan.getMember(targetPlayerId);
         if (targetMember == null) {
            this.sendMessage(player, this.getMessage("player-not-in-clan"));
         } else {
            String targetName = Bukkit.getOfflinePlayer(targetPlayerId).getName();
            if (targetName == null) {
               targetName = "Unknown";
            }

            boolean isLeader = this.roleManager.isLeader(player.getUniqueId());
            boolean canAssignRoles = this.roleManager.hasPermission(player.getUniqueId(), ClanPermission.ASSIGN_ROLES);
            boolean canKick = this.roleManager.hasPermission(player.getUniqueId(), ClanPermission.KICK_MEMBERS);
            boolean isTargetLeader = clan.getLeaderId().equals(targetPlayerId);
            boolean isSelf = player.getUniqueId().equals(targetPlayerId);
            if (isSelf) {
               this.sendMessage(player, this.getMessage("cannot-manage-self"));
            } else {
               String manageTitle = this.formatUiTitle(this.getGuiMessage("player-manage.menu-title"));
               Inventory inv = Bukkit.createInventory(new ClanInventoryHolder(ClanInventoryHolder.MenuType.PLAYER_MANAGE, targetPlayerId.toString()), 27, this.deserialize(manageTitle.replace("{player}", targetName)));
               ClanRole targetRole = clan.getRole(targetMember.getRoleId());
               ItemStack playerHead = this.createPlayerHeadForUUID(targetPlayerId);
               ItemMeta headMeta = playerHead.getItemMeta();
               headMeta.displayName(this.deserialize("&b" + targetName));
               List<Component> headLore = new ArrayList();
               headLore.add(this.deserialize(this.getGuiMessage("members.role").replace("{role}", this.legacyToMiniMessage(targetRole.getDisplayName()))));
               headLore.add(this.deserialize(this.getGuiMessage("members.joined").replace("{date}", this.dateFormat.format(new Date(targetMember.getJoinedAt())))));
               Player targetPlayer = Bukkit.getPlayer(targetPlayerId);
               if (targetPlayer != null && targetPlayer.isOnline()) {
                  headLore.add(this.deserialize(this.getGuiMessage("members.online")));
               } else {
                  headLore.add(this.deserialize(this.getGuiMessage("members.offline").replace("{time}", this.dateFormat.format(new Date(targetMember.getLastSeen())))));
               }

               headMeta.lore(headLore);
               playerHead.setItemMeta(headMeta);
               inv.setItem(4, playerHead);
               if (canAssignRoles && !isTargetLeader) {
                  ItemStack roleBtn = this.createItem(Material.NAME_TAG, this.getGuiMessage("player-manage.change-role"), this.getGuiMessage("player-manage.change-role-lore"));
                  inv.setItem(11, roleBtn);
               }

               if (canKick && !isTargetLeader) {
                  ItemStack kickBtn = this.createItem(Material.BARRIER, this.getGuiMessage("player-manage.kick"), this.getGuiMessage("player-manage.kick-lore"));
                  inv.setItem(13, kickBtn);
               }

               if (isLeader && !isTargetLeader) {
                  ItemStack transferBtn = this.createItem(Material.GOLDEN_HELMET, this.getGuiMessage("player-manage.transfer-leadership"), this.getGuiMessage("player-manage.transfer-leadership-lore"), this.getGuiMessage("player-manage.transfer-leadership-warning"));
                  inv.setItem(15, transferBtn);
               }

               inv.setItem(22, this.createItem(Material.DARK_OAK_DOOR, this.getGuiMessage("common.back")));
               this.fillBorders(inv);
               player.openInventory(inv);
            }
         }
      }
   }

   public void openAdminMembersList(Player admin, Clan clan) {
      this.openAdminMembersList(admin, clan, 0);
   }

   public void openAdminMembersList(Player admin, Clan clan, int page) {
      List<ClanMember> members = new ArrayList(clan.getMembers().values());
      members.sort((m1, m2) -> {
         ClanRole role1 = clan.getRole(m1.getRoleId());
         ClanRole role2 = clan.getRole(m2.getRoleId());
         return Integer.compare(role2.getPriority(), role1.getPriority());
      });
      int membersPerPage = 4;
      int totalPages = Math.max(1, (int)Math.ceil((double)members.size() / (double)membersPerPage));
      page = Math.max(0, Math.min(page, totalPages - 1));
      int startIndex = page * membersPerPage;
      int endIndex = Math.min(startIndex + membersPerPage, members.size());
      String membersTitle = this.formatUiTitle(this.getGuiMessage("members.menu-title"));
      Inventory inv = Bukkit.createInventory(new ClanInventoryHolder(ClanInventoryHolder.MenuType.ADMIN_MEMBERS, clan.getTag(), page), 54, this.deserialize(membersTitle));
      inv.setItem(4, this.createItem(Material.PLAYER_HEAD, this.getGuiMessage("members.title-item"), this.getGuiMessage("members.title-lore").replace("{current}", String.valueOf(clan.getMemberCount())).replace("{max}", String.valueOf(clan.getMaxMembers()))));

      for(int i = startIndex; i < endIndex; ++i) {
         ClanMember member = (ClanMember)members.get(i);
         int rowIndex = i - startIndex;
         int baseSlot = (rowIndex + 1) * 9;
         Player memberPlayer = Bukkit.getPlayer(member.getPlayerId());
         OfflinePlayer offlineMember = Bukkit.getOfflinePlayer(member.getPlayerId());
         String playerName = offlineMember.getName() != null ? offlineMember.getName() : "Unknown";
         ClanRole memberRole = clan.getRole(member.getRoleId());
         boolean isMemberLeader = clan.getLeaderId().equals(member.getPlayerId());
         ItemStack head = this.createPlayerHeadForUUID(member.getPlayerId());
         ItemMeta headMeta = head.getItemMeta();
         headMeta.displayName(this.deserialize("&b" + playerName));
         List<Component> headLore = new ArrayList();
         String roleColor = this.getRoleColor(memberRole.getRoleId());
         headLore.add(this.deserialize(this.getGuiMessage("members.role").replace("{role}", roleColor + this.stripColorCodes(memberRole.getDisplayName()))));
         headLore.add(this.deserialize(this.getGuiMessage("members.joined").replace("{date}", this.dateFormat.format(new Date(member.getJoinedAt())))));
         if (memberPlayer != null && memberPlayer.isOnline()) {
            headLore.add(this.deserialize(this.getGuiMessage("members.online")));
         } else {
            headLore.add(this.deserialize(this.getGuiMessage("members.offline").replace("{time}", this.dateFormat.format(new Date(member.getLastSeen())))));
         }

         headLore.add(this.deserialize("&8UUID: " + member.getPlayerId().toString()));
         headMeta.lore(headLore);
         head.setItemMeta(headMeta);
         inv.setItem(baseSlot, head);
         Material roleIndicator = this.getRoleIndicatorMaterial(memberRole.getRoleId());
         ItemStack roleItem = new ItemStack(roleIndicator);
         ItemMeta roleMeta = roleItem.getItemMeta();
         roleMeta.displayName(this.deserialize(roleColor + this.stripColorCodes(memberRole.getDisplayName())));
         List<Component> roleLore = new ArrayList();
         roleLore.add(this.deserialize(this.getGuiMessage("members.priority").replace("{priority}", String.valueOf(memberRole.getPriority()))));
         roleLore.add(this.deserialize(""));
         String roleDesc = this.getGuiMessage("roles." + memberRole.getRoleId().toLowerCase() + ".description");
         if (!roleDesc.startsWith("§cMessage not found")) {
            roleLore.add(this.deserialize(roleDesc));
            roleLore.add(this.deserialize(""));
         }

         List<String> rolePerms = this.getRolePermissionsLore(memberRole.getRoleId());
         if (!rolePerms.isEmpty()) {
            roleLore.add(this.deserialize(this.getGuiMessage("roles.permissions-header")));

            for(String perm : rolePerms) {
               roleLore.add(this.deserialize(perm));
            }
         }

         roleMeta.lore(roleLore);
         roleItem.setItemMeta(roleMeta);
         inv.setItem(baseSlot + 1, roleItem);
         if (!isMemberLeader) {
            ItemStack changeRoleBtn = new ItemStack(Material.COMPARATOR);
            ItemMeta changeMeta = changeRoleBtn.getItemMeta();
            changeMeta.displayName(this.deserialize(this.getGuiMessage("members.change-role-btn")));
            List<Component> changeLore = new ArrayList();
            changeLore.add(this.deserialize(this.getGuiMessage("members.change-role-left")));
            changeLore.add(this.deserialize(this.getGuiMessage("members.change-role-right")));
            changeLore.add(this.deserialize("&8UUID: " + member.getPlayerId().toString()));
            changeLore.add(this.deserialize("&8RoleID: " + memberRole.getRoleId()));
            changeMeta.lore(changeLore);
            changeRoleBtn.setItemMeta(changeMeta);
            inv.setItem(baseSlot + 2, changeRoleBtn);
         }

         if (!isMemberLeader) {
            ItemStack kickBtn = new ItemStack(Material.BARRIER);
            ItemMeta kickMeta = kickBtn.getItemMeta();
            kickMeta.displayName(this.deserialize(this.getGuiMessage("members.kick-btn")));
            List<Component> kickLore = new ArrayList();
            kickLore.add(this.deserialize(this.getGuiMessage("members.kick-btn-lore")));
            kickLore.add(this.deserialize("&8UUID: " + member.getPlayerId().toString()));
            kickLore.add(this.deserialize("&8Name: " + playerName));
            kickMeta.lore(kickLore);
            kickBtn.setItemMeta(kickMeta);
            inv.setItem(baseSlot + 4, kickBtn);
         }
      }

      if (page > 0) {
         ItemStack prevBtn = new ItemStack(Material.ARROW);
         ItemMeta prevMeta = prevBtn.getItemMeta();
         prevMeta.displayName(this.deserialize(this.getGuiMessage("members.prev-page")));
         prevBtn.setItemMeta(prevMeta);
         inv.setItem(48, prevBtn);
      }

      inv.setItem(49, this.createItem(Material.DARK_OAK_DOOR, this.getGuiMessage("common.back")));
      if (page < totalPages - 1) {
         ItemStack nextBtn = new ItemStack(Material.ARROW);
         ItemMeta nextMeta = nextBtn.getItemMeta();
         nextMeta.displayName(this.deserialize(this.getGuiMessage("members.next-page")));
         nextBtn.setItemMeta(nextMeta);
         inv.setItem(50, nextBtn);
      }

      if (totalPages > 1) {
         inv.setItem(53, this.createPageIndicator(page, totalPages));
      }

      this.fillBorders(inv);
      admin.openInventory(inv);
   }

   public void openAdminClanMenu(Player admin) {
      this.openAdminClanMenu(admin, 0);
   }

   public void openAdminClanMenu(Player admin, int page) {
      List<Clan> allClans = new ArrayList(this.clanManager.getAllClans());
      allClans.sort((c1, c2) -> Integer.compare(c2.getMemberCount(), c1.getMemberCount()));
      int[] availableSlots = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
      int clansPerPage = availableSlots.length;
      int totalPages = Math.max(1, (int)Math.ceil((double)allClans.size() / (double)clansPerPage));
      page = Math.max(0, Math.min(page, totalPages - 1));
      int startIndex = page * clansPerPage;
      int endIndex = Math.min(startIndex + clansPerPage, allClans.size());
      Inventory inv = Bukkit.createInventory(new ClanInventoryHolder(ClanInventoryHolder.MenuType.ADMIN_CLAN, (String)null, page), 54, this.deserialize(this.getGuiMessage("admin.menu-title")));
      inv.setItem(4, this.createItem(Material.COMMAND_BLOCK, this.getGuiMessage("admin.title-item"), this.getGuiMessage("admin.title-lore").replace("{total}", String.valueOf(allClans.size()))));

      for(int i = startIndex; i < endIndex; ++i) {
         Clan clan = (Clan)allClans.get(i);
         int slot = availableSlots[i - startIndex];
         ItemStack item = this.createClanBanner(clan);
         ItemMeta meta = item.getItemMeta();
         meta.displayName(this.deserialize(this.getGuiMessage("list.clan-name").replace("{tag}", this.formatClanTag(clan)).replace("{name}", this.formatClanName(clan))));
         List<Component> lore = new ArrayList();
         lore.add(this.deserialize(this.getGuiMessage("list.clan-members").replace("{count}", String.valueOf(clan.getMemberCount()))));
         lore.add(this.deserialize(this.getGuiMessage("list.clan-privacy").replace("{privacy}", this.getPrivacyName(clan.getPrivacy()))));
         OfflinePlayer leaderOff = Bukkit.getOfflinePlayer(clan.getLeaderId());
         String leaderName = leaderOff.getName() != null ? leaderOff.getName() : "Unknown";
         lore.add(this.deserialize("&7Лидер: &f" + leaderName));
         lore.add(this.deserialize(""));
         lore.add(this.deserialize(this.getGuiMessage("admin.click-manage")));
         lore.add(this.deserialize("&8Tag: " + clan.getTag()));
         meta.lore(lore);
         item.setItemMeta(meta);
         inv.setItem(slot, item);
      }

      if (page > 0) {
         ItemStack prevBtn = new ItemStack(Material.ARROW);
         ItemMeta m = prevBtn.getItemMeta();
         m.displayName(this.deserialize(this.getGuiMessage("list.prev-page")));
         prevBtn.setItemMeta(m);
         inv.setItem(48, prevBtn);
      }

      inv.setItem(49, this.createItem(Material.DARK_OAK_DOOR, this.getGuiMessage("common.back")));
      if (page < totalPages - 1) {
         ItemStack nextBtn = new ItemStack(Material.ARROW);
         ItemMeta m = nextBtn.getItemMeta();
         m.displayName(this.deserialize(this.getGuiMessage("list.next-page")));
         nextBtn.setItemMeta(m);
         inv.setItem(50, nextBtn);
      }

      if (totalPages > 1) {
         inv.setItem(53, this.createPageIndicator(page, totalPages));
      }

      this.fillBorders(inv);
      admin.openInventory(inv);
   }

   public void openAdminClanManageMenu(Player admin, Clan clan) {
      if (clan != null) {
         String title = this.formatUiTitle(this.getGuiMessage("admin.manage-title").replace("{clan}", this.formatClanName(clan)).replace("{tag}", this.formatClanTag(clan)));
         Inventory inv = Bukkit.createInventory(new ClanInventoryHolder(ClanInventoryHolder.MenuType.ADMIN_CLAN_MANAGE, clan.getTag()), 54, this.deserialize(title));
         ItemStack infoItem = this.createClanBanner(clan);
         ItemMeta infoMeta = infoItem.getItemMeta();
         infoMeta.displayName(this.deserialize(this.getGuiMessage("list.clan-name").replace("{tag}", this.formatClanTag(clan)).replace("{name}", this.formatClanName(clan))));
         List<Component> infoLore = new ArrayList();
         infoLore.add(this.deserialize(this.getGuiMessage("list.clan-members").replace("{count}", String.valueOf(clan.getMemberCount()))));
         OfflinePlayer leaderOff = Bukkit.getOfflinePlayer(clan.getLeaderId());
         String leaderName = leaderOff.getName() != null ? leaderOff.getName() : "Unknown";
         infoLore.add(this.deserialize("&7Лидер: &f" + leaderName));
         String var10002 = this.getPrivacyName(clan.getPrivacy());
         infoLore.add(this.deserialize("&7Приватность: &f" + var10002));
         var10002 = this.formatClanTag(clan);
         infoLore.add(this.deserialize("&7Тег: &f" + var10002));
         infoLore.add(this.deserialize("&7Макс. участников: &f" + clan.getMaxMembers()));
         infoMeta.lore(infoLore);
         infoItem.setItemMeta(infoMeta);
         inv.setItem(4, infoItem);
         inv.setItem(11, this.createItem(Material.WRITABLE_BOOK, this.getGuiMessage("admin.btn.setname"), this.getGuiMessage("settings.name-current").replace("{name}", this.formatClanName(clan)), "&8Tag: " + clan.getTag()));
         inv.setItem(12, this.createItem(Material.NAME_TAG, this.getGuiMessage("admin.btn.settag"), this.getGuiMessage("settings.tag-current").replace("{tag}", this.formatClanTag(clan)), "&8Tag: " + clan.getTag()));
         inv.setItem(13, this.createItem(Material.BOOK, this.getGuiMessage("admin.btn.setdescription"), clan.getDescription().isEmpty() ? this.getGuiMessage("settings.description-none") : this.getGuiMessage("settings.description-current").replace("{description}", clan.getDescription()), "&8Tag: " + clan.getTag()));
         Material bannerMat = this.getBannerMaterial(clan.getBannerColor());
         ItemStack bannerBtn = new ItemStack(bannerMat);
         ItemMeta bannerMeta = bannerBtn.getItemMeta();
         bannerMeta.displayName(this.deserialize(this.getGuiMessage("admin.btn.setbanner")));
         List<Component> bannerLore = new ArrayList();
         bannerLore.add(this.deserialize(this.getGuiMessage("settings.banner-current").replace("{color}", clan.getBannerColor())));
         bannerLore.add(this.deserialize("&8Tag: " + clan.getTag()));
         bannerMeta.lore(bannerLore);
         bannerBtn.setItemMeta(bannerMeta);
         inv.setItem(14, bannerBtn);
         inv.setItem(15, this.createItem(Material.ANVIL, this.getGuiMessage("admin.btn.setmaxmembers"), "&7Текущий максимум: &f" + clan.getMaxMembers(), this.getGuiMessage("admin.btn.click-hint"), "&8Tag: " + clan.getTag()));
         inv.setItem(20, this.createItem(Material.PLAYER_HEAD, this.getGuiMessage("admin.btn.members"), this.getGuiMessage("admin.btn.members-lore"), "&8Tag: " + clan.getTag()));
         inv.setItem(21, this.createItem(Material.EMERALD, this.getGuiMessage("admin.btn.forceadd"), this.getGuiMessage("admin.btn.forceadd-lore"), "&8Tag: " + clan.getTag()));
         inv.setItem(22, this.createItem(Material.GOLDEN_HELMET, this.getGuiMessage("admin.btn.setleader"), "&7Текущий лидер: &f" + leaderName, this.getGuiMessage("admin.btn.setleader-lore"), "&8Tag: " + clan.getTag()));
         inv.setItem(23, this.createItem(Material.GOLD_BLOCK, "&6Казна клана", "&7Баланс: &f" + clan.getBalance() + " ар", "&eНажмите, чтобы выдать/изъять ары", "&8Action: ADMINBANK", "&8Tag: " + clan.getTag()));
         inv.setItem(24, this.createItem(Material.BARRIER, this.getGuiMessage("admin.btn.kick"), this.getGuiMessage("admin.btn.kick-lore"), "&8Tag: " + clan.getTag()));
         ClanPrivacy privacy = clan.getPrivacy();
         Material var10000;
         switch (privacy) {
            case PUBLIC -> var10000 = Material.LIME_DYE;
            case INVITE_ONLY -> var10000 = Material.YELLOW_DYE;
            case PRIVATE -> var10000 = Material.RED_DYE;
            default -> throw new MatchException((String)null, (Throwable)null);
         }

         Material privacyMat = var10000;
         String var10004 = this.getGuiMessage("profile.privacy-title").replace("{privacy}", this.getPrivacyName(privacy));
         String[] var10005 = new String[]{this.getGuiMessage("settings.toggle-click"), "&8Toggle: PRIVACY", null};
         String var10008 = clan.getTag();
         var10005[2] = "&8Tag: " + var10008;
         inv.setItem(29, this.createItem(privacyMat, var10004, var10005));
         Material var10003 = clan.isTagEnabled() ? Material.LIME_DYE : Material.GRAY_DYE;
         var10004 = this.getGuiMessage("settings.settings-tag-toggle");
         var10005 = new String[]{clan.isTagEnabled() ? this.getGuiMessage("settings.toggle-enabled") : this.getGuiMessage("settings.toggle-disabled"), this.getGuiMessage("settings.toggle-click"), "&8Toggle: TAGENABLED", null};
         var10008 = clan.getTag();
         var10005[3] = "&8Tag: " + var10008;
         inv.setItem(30, this.createItem(var10003, var10004, var10005));
         var10003 = clan.isFriendlyFire() ? Material.RED_DYE : Material.GRAY_DYE;
         var10004 = this.getGuiMessage("settings.friendly-fire");
         var10005 = new String[]{clan.isFriendlyFire() ? this.getGuiMessage("settings.friendly-fire-enabled") : this.getGuiMessage("settings.friendly-fire-disabled"), this.getGuiMessage("settings.toggle-click"), "&8Toggle: FRIENDLYFIRE", null};
         var10008 = clan.getTag();
         var10005[3] = "&8Tag: " + var10008;
         inv.setItem(32, this.createItem(var10003, var10004, var10005));
         inv.setItem(33, this.createItem(clan.isChatEnabled() ? Material.LIME_DYE : Material.GRAY_DYE, this.getGuiMessage("admin.btn.togglechat"), clan.isChatEnabled() ? this.getGuiMessage("settings.toggle-enabled") : this.getGuiMessage("settings.toggle-disabled"), this.getGuiMessage("settings.toggle-click"), "&8Toggle: CHATENABLED", "&8Tag: " + clan.getTag()));
         inv.setItem(40, this.createItem(Material.TNT, this.getGuiMessage("admin.btn.disband"), this.getGuiMessage("admin.btn.disband-lore"), "&8Tag: " + clan.getTag()));
         inv.setItem(49, this.createItem(Material.DARK_OAK_DOOR, this.getGuiMessage("common.back")));
         this.fillEmpty(inv);
         admin.openInventory(inv);
      }
   }

   private String getRoleColor(String roleId) {
      if (roleId == null) {
         return "&7";
      } else {
         String var10000;
         switch (roleId.toLowerCase()) {
            case "leader":
            case "owner":
               var10000 = "&e";
               break;
            case "officer":
            case "co-leader":
            case "coleader":
            case "co_leader":
               var10000 = "&c";
               break;
            case "moderator":
            case "veteran":
            case "elite":
               var10000 = "&5";
               break;
            case "member":
               var10000 = "&a";
               break;
            default:
               var10000 = "&7";
         }

         return var10000;
      }
   }

   private Material getRoleIndicatorMaterial(String roleId) {
      if (roleId == null) {
         return Material.GRAY_CONCRETE;
      } else {
         Material var10000;
         switch (roleId.toLowerCase()) {
            case "leader":
            case "owner":
               var10000 = Material.YELLOW_CONCRETE;
               break;
            case "officer":
            case "co-leader":
            case "coleader":
            case "co_leader":
               var10000 = Material.RED_CONCRETE;
               break;
            case "moderator":
            case "veteran":
            case "elite":
               var10000 = Material.PURPLE_CONCRETE;
               break;
            case "member":
               var10000 = Material.LIME_CONCRETE;
               break;
            default:
               var10000 = Material.GRAY_CONCRETE;
         }

         return var10000;
      }
   }

   public List<String> getGuiStringList(String key) {
      FileConfiguration config = this.module.getGui();
      if (config == null) {
         return new ArrayList();
      } else {
         List<String> list = config.getStringList(key);
         if (list == null || list.isEmpty()) {
            if (defaultGuiConfig == null) {
               defaultGuiConfig = loadDefaultResource("gui.yml");
            }

            if (defaultGuiConfig != null) {
               return defaultGuiConfig.getStringList(key);
            }
         }

         return list;
      }
   }

   private List<String> getRolePermissionsLore(String roleId) {
      if (roleId == null) {
         return new ArrayList();
      } else {
         List<String> staticPerms = this.getGuiStringList("roles." + roleId.toLowerCase() + ".permissions");
         if (staticPerms != null && !staticPerms.isEmpty()) {
            List<String> colored = new ArrayList();

            for(String s : staticPerms) {
               colored.add(this.module.getPlugin().applyColors(s));
            }

            return colored;
         } else {
            List<String> perms = new ArrayList();

            for(Clan c : this.clanManager.getAllClans()) {
               ClanRole role = c.getRole(roleId);
               if (role != null && role.getPermissions() != null) {
                  for(ClanPermission perm : role.getPermissions()) {
                     String permName = this.getGuiMessage("roles.permissions." + perm.name());
                     if (permName.startsWith("§cMessage not found")) {
                        permName = perm.name();
                     }

                     perms.add("&7• &f" + permName);
                  }
                  break;
               }
            }

            return perms;
         }
      }
   }

   private ItemStack createItem(Material material, String name) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      meta.displayName(this.deserialize(name));
      item.setItemMeta(meta);
      return item;
   }

   private ItemStack createItem(Material material, String name, String... lore) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      meta.displayName(this.deserialize(name));
      List<Component> loreList = new ArrayList();

      for(String line : lore) {
         loreList.add(this.deserialize(line));
      }

      meta.lore(loreList);
      item.setItemMeta(meta);
      return item;
   }

   private ItemStack createPlayerHead(Player player) {
      ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
      if (player != null) {
         SkullMeta meta = (SkullMeta)skull.getItemMeta();
         meta.setOwningPlayer(player);
         skull.setItemMeta(meta);
      }

      return skull;
   }

   public ItemStack createPlayerHeadForUUID(UUID playerId) {
      ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
      SkullMeta meta = (SkullMeta)skull.getItemMeta();
      String[] skinData = this.fetchSkinFromSkinsRestorerDb(playerId);
      if (skinData != null && skinData[0] != null) {
         try {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
            String name = offlinePlayer.getName();
            PlayerProfile profile = Bukkit.createProfile(playerId, name != null ? name : "");
            profile.setProperty(new ProfileProperty("textures", skinData[0], skinData[1] != null ? skinData[1] : ""));
            meta.setPlayerProfile(profile);
            skull.setItemMeta(meta);
            return skull;
         } catch (Exception e) {
            Logger var10000 = this.module.getPlugin().getLogger();
            String var10001 = String.valueOf(playerId);
            var10000.warning("[SM_Clans] Failed to apply SR skin for " + var10001 + ": " + e.getMessage());
         }
      }

      meta.setOwningPlayer(Bukkit.getOfflinePlayer(playerId));
      skull.setItemMeta(meta);
      return skull;
   }

   private String[] fetchSkinFromSkinsRestorerDb(UUID playerId) {
      if (this.skinCache.containsKey(playerId)) {
         String[] cached = (String[])this.skinCache.get(playerId);
         return cached == EMPTY_SKIN ? null : cached;
      } else {
         FileConfiguration config = this.module.getConfig();
         if (config == null) {
            this.module.getPlugin().getLogger().warning("[SM_Clans] SR skin lookup: config is null");
            return null;
         } else if (!config.getBoolean("skinsrestorer-db.enabled", true)) {
            this.module.getPlugin().getLogger().warning("[SM_Clans] SR skin lookup: skinsrestorer-db.enabled is false");
            return null;
         } else {
            String dbKey = config.getString("skinsrestorer-db.database-key", "database2");
            String tableName = config.getString("skinsrestorer-db.table", "sr_player_skins");

            try {
               label130: {
                  Object smpsDatabaseManager = this.module.getPlugin().getDatabaseManager();
                  Class<?> dbClass = smpsDatabaseManager.getClass();

                  Method isConnectedMethod;
                  try {
                     isConnectedMethod = dbClass.getMethod("isConnected", String.class);
                  } catch (NoSuchMethodException var20) {
                     this.module.getPlugin().getLogger().warning("[SM_Clans] DoAPI DatabaseManager has no isConnected(String) method. Available methods:");

                     for(Method m : dbClass.getMethods()) {
                        Logger var31 = this.module.getPlugin().getLogger();
                        String var32 = m.getName();
                        var31.warning("[SM_Clans]   " + var32 + "(" + Arrays.toString(m.getParameterTypes()) + ") -> " + m.getReturnType().getSimpleName());
                     }

                     return null;
                  }

                  boolean connected = (Boolean)isConnectedMethod.invoke(smpsDatabaseManager, dbKey);
                  if (!connected) {
                     this.module.getPlugin().getLogger().warning("[SM_Clans] SR DB (" + dbKey + ") is not connected.");
                     return null;
                  }

                  Method getConnectionMethod;
                  try {
                     getConnectionMethod = dbClass.getMethod("getConnection", String.class);
                  } catch (NoSuchMethodException var19) {
                     this.module.getPlugin().getLogger().warning("[SM_Clans] DoAPI DatabaseManager has no getConnection(String) method.");
                     return null;
                  }

                  Connection conn = (Connection)getConnectionMethod.invoke(smpsDatabaseManager, dbKey);

                  String[] var17;
                  label132: {
                     label133: {
                        String uuidStr;
                        label116: {
                           try {
                              if (conn == null) {
                                 this.module.getPlugin().getLogger().warning("[SM_Clans] SR DB getConnection(" + dbKey + ") returned null");
                                 uuidStr = null;
                                 break label116;
                              }

                              uuidStr = playerId.toString();
                              String uuidNoDashes = uuidStr.replace("-", "");
                              OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
                              String playerName = offlinePlayer.getName();
                              String playersTable = tableName.replace("player_skins", "players");
                              String[] result = this.querySrTwoTable(conn, playersTable, tableName, uuidStr, uuidNoDashes, playerName);
                              if (result != null) {
                                 this.skinCache.put(playerId, result);
                                 var17 = result;
                                 break label133;
                              }

                              result = this.querySrSkinDirect(conn, tableName, uuidStr, uuidNoDashes, playerName);
                              if (result != null) {
                                 this.skinCache.put(playerId, result);
                                 var17 = result;
                                 break label132;
                              }

                              this.module.getPlugin().getLogger().warning("[SM_Clans] SR DB: no skin for uuid=" + uuidStr + " / name=" + playerName);
                           } catch (Throwable var21) {
                              if (conn != null) {
                                 try {
                                    conn.close();
                                 } catch (Throwable var18) {
                                    var21.addSuppressed(var18);
                                 }
                              }

                              throw var21;
                           }

                           if (conn != null) {
                              conn.close();
                           }
                           break label130;
                        }

                        if (conn != null) {
                           conn.close();
                        }

                        return null;
                     }

                     if (conn != null) {
                        conn.close();
                     }

                     return var17;
                  }

                  if (conn != null) {
                     conn.close();
                  }

                  return var17;
               }
            } catch (Exception e) {
               Logger var10000 = this.module.getPlugin().getLogger();
               String var10001 = String.valueOf(playerId);
               var10000.warning("[SM_Clans] SR DB lookup failed for " + var10001 + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
               e.printStackTrace();
            }

            this.skinCache.put(playerId, EMPTY_SKIN);
            return null;
         }
      }
   }

   public void clearSkinCache() {
      this.skinCache.clear();
      this.loggedTables.clear();
   }

   private static String getColumnSafe(ResultSet rs, String columnName) {
      try {
         return rs.getString(columnName);
      } catch (Exception var3) {
         return null;
      }
   }

   private String[] extractSkinFromRow(ResultSet rs) throws SQLException {
      ResultSetMetaData rsMeta = rs.getMetaData();
      StringBuilder cols = new StringBuilder();

      for(int i = 1; i <= rsMeta.getColumnCount(); ++i) {
         if (i > 1) {
            cols.append(", ");
         }

         cols.append(rsMeta.getColumnName(i));
      }

      this.module.getPlugin().getLogger().info("[SM_Clans] SR DB columns: " + String.valueOf(cols));
      String value = getColumnSafe(rs, "value");
      if (value == null) {
         value = getColumnSafe(rs, "skin_value");
      }

      String signature = getColumnSafe(rs, "signature");
      if (signature == null) {
         signature = getColumnSafe(rs, "skin_signature");
      }

      if (value != null && !value.isEmpty()) {
         this.module.getPlugin().getLogger().info("[SM_Clans] SR DB texture found (" + value.length() + " chars)");
         return new String[]{value, signature};
      } else {
         return null;
      }
   }

   private String[] querySrTwoTable(Connection conn, String playersTable, String skinsTable, String uuidStr, String uuidNoDashes, String playerName) {
      String skinIdentifier = null;
      String sql1 = "SELECT * FROM " + playersTable + " WHERE uuid = ? OR uuid = ? LIMIT 1";

      try {
         PreparedStatement ps = conn.prepareStatement(sql1);

         try {
            ps.setString(1, uuidStr);
            ps.setString(2, uuidNoDashes);
            ResultSet rs = ps.executeQuery();

            try {
               if (rs.next()) {
                  this.logColumns(rs, playersTable);
                  skinIdentifier = getColumnSafe(rs, "skin_identifier");
                  if (skinIdentifier == null) {
                     skinIdentifier = getColumnSafe(rs, "skin");
                  }

                  this.module.getPlugin().getLogger().info("[SM_Clans] SR players: found by uuid, skin_identifier=" + skinIdentifier);
               }
            } catch (Throwable var26) {
               if (rs != null) {
                  try {
                     rs.close();
                  } catch (Throwable var19) {
                     var26.addSuppressed(var19);
                  }
               }

               throw var26;
            }

            if (rs != null) {
               rs.close();
            }
         } catch (Throwable var27) {
            if (ps != null) {
               try {
                  ps.close();
               } catch (Throwable var18) {
                  var27.addSuppressed(var18);
               }
            }

            throw var27;
         }

         if (ps != null) {
            ps.close();
         }
      } catch (Exception e) {
         this.module.getPlugin().getLogger().warning("[SM_Clans] SR players uuid query error: " + e.getMessage());
      }

      if (skinIdentifier == null && playerName != null && !playerName.isEmpty()) {
         String sql2 = "SELECT * FROM " + playersTable + " WHERE uuid = ? OR LOWER(uuid) = LOWER(?) LIMIT 1";

         try {
            PreparedStatement ps = conn.prepareStatement(sql2);

            try {
               ps.setString(1, playerName);
               ps.setString(2, playerName);
               ResultSet rs = ps.executeQuery();

               try {
                  if (rs.next()) {
                     this.logColumns(rs, playersTable);
                     skinIdentifier = getColumnSafe(rs, "skin_identifier");
                     if (skinIdentifier == null) {
                        skinIdentifier = getColumnSafe(rs, "skin");
                     }

                     this.module.getPlugin().getLogger().info("[SM_Clans] SR players: found by name '" + playerName + "', skin_identifier=" + skinIdentifier);
                  }
               } catch (Throwable var23) {
                  if (rs != null) {
                     try {
                        rs.close();
                     } catch (Throwable var17) {
                        var23.addSuppressed(var17);
                     }
                  }

                  throw var23;
               }

               if (rs != null) {
                  rs.close();
               }
            } catch (Throwable var24) {
               if (ps != null) {
                  try {
                     ps.close();
                  } catch (Throwable var16) {
                     var24.addSuppressed(var16);
                  }
               }

               throw var24;
            }

            if (ps != null) {
               ps.close();
            }
         } catch (Exception e) {
            this.module.getPlugin().getLogger().warning("[SM_Clans] SR players name query error: " + e.getMessage());
         }
      }

      if (skinIdentifier != null && !skinIdentifier.isEmpty()) {
         if ("dynamic".equalsIgnoreCase(skinIdentifier)) {
            String cacheTable = skinsTable.replace("player_skins", "cache");
            String[] cacheResult = this.querySrCacheByName(conn, cacheTable, playerName);
            return cacheResult != null ? cacheResult : this.querySrSkinDirect(conn, skinsTable, uuidStr, uuidNoDashes, playerName);
         } else {
            String sql3 = "SELECT * FROM " + skinsTable + " WHERE uuid = ? OR LOWER(uuid) = LOWER(?) LIMIT 1";

            try {
               label209: {
                  PreparedStatement ps = conn.prepareStatement(sql3);

                  String[] var12;
                  label149: {
                     try {
                        ps.setString(1, skinIdentifier);
                        ps.setString(2, skinIdentifier);
                        ResultSet rs = ps.executeQuery();

                        label211: {
                           try {
                              if (!rs.next()) {
                                 break label211;
                              }

                              var12 = this.extractSkinFromRow(rs);
                           } catch (Throwable var20) {
                              if (rs != null) {
                                 try {
                                    rs.close();
                                 } catch (Throwable var15) {
                                    var20.addSuppressed(var15);
                                 }
                              }

                              throw var20;
                           }

                           if (rs != null) {
                              rs.close();
                           }
                           break label149;
                        }

                        if (rs != null) {
                           rs.close();
                        }
                     } catch (Throwable var21) {
                        if (ps != null) {
                           try {
                              ps.close();
                           } catch (Throwable var14) {
                              var21.addSuppressed(var14);
                           }
                        }

                        throw var21;
                     }

                     if (ps != null) {
                        ps.close();
                     }
                     break label209;
                  }

                  if (ps != null) {
                     ps.close();
                  }

                  return var12;
               }
            } catch (Exception e) {
               this.module.getPlugin().getLogger().warning("[SM_Clans] SR skins query error for identifier '" + skinIdentifier + "': " + e.getMessage());
            }

            this.module.getPlugin().getLogger().warning("[SM_Clans] SR DB: skin_identifier '" + skinIdentifier + "' not found in " + skinsTable);
            return null;
         }
      } else {
         return null;
      }
   }

   private String[] querySrSkinDirect(Connection conn, String tableName, String uuidStr, String uuidNoDashes, String playerName) {
      StringBuilder where = new StringBuilder("uuid = ? OR uuid = ?");
      int paramCount = 2;
      if (playerName != null && !playerName.isEmpty()) {
         where.append(" OR uuid = ? OR LOWER(uuid) = LOWER(?)");
         paramCount = 4;
      }

      String sql = "SELECT * FROM " + tableName + " WHERE " + String.valueOf(where) + " LIMIT 1";

      try {
         PreparedStatement ps = conn.prepareStatement(sql);

         String[] var11;
         label97: {
            try {
               ps.setString(1, uuidStr);
               ps.setString(2, uuidNoDashes);
               if (paramCount == 4) {
                  ps.setString(3, playerName);
                  ps.setString(4, playerName);
               }

               ResultSet rs = ps.executeQuery();

               label90: {
                  try {
                     if (!rs.next()) {
                        break label90;
                     }

                     var11 = this.extractSkinFromRow(rs);
                  } catch (Throwable var15) {
                     if (rs != null) {
                        try {
                           rs.close();
                        } catch (Throwable var14) {
                           var15.addSuppressed(var14);
                        }
                     }

                     throw var15;
                  }

                  if (rs != null) {
                     rs.close();
                  }
                  break label97;
               }

               if (rs != null) {
                  rs.close();
               }
            } catch (Throwable var16) {
               if (ps != null) {
                  try {
                     ps.close();
                  } catch (Throwable var13) {
                     var16.addSuppressed(var13);
                  }
               }

               throw var16;
            }

            if (ps != null) {
               ps.close();
            }

            return null;
         }

         if (ps != null) {
            ps.close();
         }

         return var11;
      } catch (Exception e) {
         this.module.getPlugin().getLogger().warning("[SM_Clans] SR direct skin query error: " + e.getMessage());
         return null;
      }
   }

   private String[] querySrCacheByName(Connection conn, String cacheTable, String playerName) {
      if (playerName != null && !playerName.isEmpty()) {
         String[] columns = new String[]{"Nick", "Name", "Player"};

         for(String col : columns) {
            try {
               String sql = "SELECT * FROM " + cacheTable + " WHERE " + col + " = ? OR LOWER(" + col + ") = LOWER(?) LIMIT 1";
               PreparedStatement ps = conn.prepareStatement(sql);

               label98: {
                  String[] var12;
                  try {
                     ps.setString(1, playerName);
                     ps.setString(2, playerName);
                     ResultSet rs = ps.executeQuery();

                     label100: {
                        try {
                           if (rs.next()) {
                              this.logColumns(rs, cacheTable);
                              var12 = this.extractSkinFromRow(rs);
                              break label100;
                           }
                        } catch (Throwable var16) {
                           if (rs != null) {
                              try {
                                 rs.close();
                              } catch (Throwable var15) {
                                 var16.addSuppressed(var15);
                              }
                           }

                           throw var16;
                        }

                        if (rs != null) {
                           rs.close();
                        }
                        break label98;
                     }

                     if (rs != null) {
                        rs.close();
                     }
                  } catch (Throwable var17) {
                     if (ps != null) {
                        try {
                           ps.close();
                        } catch (Throwable var14) {
                           var17.addSuppressed(var14);
                        }
                     }

                     throw var17;
                  }

                  if (ps != null) {
                     ps.close();
                  }

                  return var12;
               }

               if (ps != null) {
                  ps.close();
               }
               break;
            } catch (Exception e) {
               if (col.equals(columns[columns.length - 1])) {
                  this.module.getPlugin().getLogger().info("[SM_Clans] SR cache lookup failed in " + cacheTable + " (tried cols: " + String.join(",", columns) + "): " + e.getMessage());
               }
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private void logColumns(ResultSet rs, String tableName) {
      if (this.loggedTables.add(tableName)) {
         try {
            ResultSetMetaData rsMeta = rs.getMetaData();
            StringBuilder cols = new StringBuilder();

            for(int i = 1; i <= rsMeta.getColumnCount(); ++i) {
               if (i > 1) {
                  cols.append(", ");
               }

               cols.append(rsMeta.getColumnName(i));
            }

            this.module.getPlugin().getLogger().info("[SM_Clans] " + tableName + " columns: " + String.valueOf(cols));
         } catch (Exception var6) {
         }

      }
   }

   private String getPrivacyName(ClanPrivacy privacy) {
      String var10000;
      switch (privacy) {
         case PUBLIC -> var10000 = this.getGuiMessage("settings.privacy-public");
         case INVITE_ONLY -> var10000 = this.getGuiMessage("settings.privacy-invite");
         case PRIVATE -> var10000 = this.getGuiMessage("settings.privacy-private");
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   private Component deserialize(String text) {
      try {
         Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(text.replace("§", "&"));
         return component.decoration(TextDecoration.ITALIC, false);
      } catch (Exception var3) {
         return Component.text(text).decoration(TextDecoration.ITALIC, false);
      }
   }

   private static FileConfiguration loadDefaultResource(String resourceName) {
      try {
         InputStream is = ClanMenuManager.class.getClassLoader().getResourceAsStream(resourceName);

         Object var7;
         label48: {
            YamlConfiguration var3;
            try {
               if (is == null) {
                  var7 = null;
                  break label48;
               }

               InputStreamReader reader = new InputStreamReader(is);
               var3 = YamlConfiguration.loadConfiguration(reader);
            } catch (Throwable var5) {
               if (is != null) {
                  try {
                     is.close();
                  } catch (Throwable var4) {
                     var5.addSuppressed(var4);
                  }
               }

               throw var5;
            }

            if (is != null) {
               is.close();
            }

            return var3;
         }

         if (is != null) {
            is.close();
         }

         return (FileConfiguration)var7;
      } catch (Exception var6) {
         return null;
      }
   }

   public String getGuiMessage(String key) {
      FileConfiguration config = this.module.getGui();
      if (config != null) {
         String raw = config.getString(key, (String)null);
         if (raw != null) {
            return this.module.getPlugin().applyColors(raw);
         }
      }

      if (defaultGuiConfig == null) {
         defaultGuiConfig = loadDefaultResource("gui.yml");
      }

      if (defaultGuiConfig != null) {
         String raw = defaultGuiConfig.getString(key, "§c" + key);
         return this.module.getPlugin().applyColors(raw);
      } else {
         return this.module.getPlugin().applyColors("§c" + key);
      }
   }

   public String getMessage(String key) {
      FileConfiguration config = this.module.getMessages();
      if (config == null) {
         if (defaultMessagesConfig == null) {
            defaultMessagesConfig = loadDefaultResource("messages.yml");
         }

         if (defaultMessagesConfig != null) {
            String msg = defaultMessagesConfig.getString(key, (String)null);
            if (msg != null) {
               String prefix = defaultMessagesConfig.getString("prefix", "");
               FileConfiguration mainConfig = this.module.getConfig();
               if (mainConfig != null) {
                  boolean prefixEnabled = mainConfig.getBoolean("prefix.enabled", true);
                  if (!prefixEnabled) {
                     prefix = "";
                  }
               }

               String combined = msg.replace("<prefix>", prefix);
               return this.module.getPlugin().applyColors(combined);
            }
         }

         return this.module.getPlugin().applyColors("§cMessage not found: " + key);
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

   public void sendMessage(Player player, String message) {
      if (message != null && !message.isEmpty()) {
         try {
            player.sendMessage(LegacyComponentSerializer.legacySection().deserialize(message));
         } catch (Exception var4) {
            player.sendMessage(message);
         }

      }
   }

   private Material getBannerMaterial(String color) {
      if (color != null && !color.isEmpty()) {
         try {
            return Material.valueOf(color.toUpperCase() + "_BANNER");
         } catch (IllegalArgumentException var3) {
            return Material.WHITE_BANNER;
         }
      } else {
         return Material.WHITE_BANNER;
      }
   }

   private ItemStack createClanBanner(Clan clan) {
      String flagData = clan.getFlagData();
      ItemStack banner;
      if (flagData != null && !flagData.isEmpty()) {
         banner = this.deserializeBannerData(flagData);
      } else {
         Material bannerMaterial = this.getBannerMaterial(clan.getBannerColor());
         banner = new ItemStack(bannerMaterial);
      }

      ItemMeta meta = banner.getItemMeta();
      if (meta != null) {
         try {
            meta.addItemFlags(new ItemFlag[]{ItemFlag.valueOf("HIDE_ADDITIONAL_TOOLTIP")});
         } catch (IllegalArgumentException var8) {
            try {
               meta.addItemFlags(new ItemFlag[]{ItemFlag.valueOf("HIDE_POTION_EFFECTS")});
            } catch (IllegalArgumentException var7) {
            }
         }

         banner.setItemMeta(meta);
      }

      return banner;
   }

   private ItemStack deserializeBannerData(String flagData) {
      if (flagData != null && !flagData.isEmpty()) {
         try {
            String[] parts = flagData.split(";");
            String baseColor = parts[0];
            Material bannerMaterial = this.getBannerMaterial(baseColor);
            ItemStack banner = new ItemStack(bannerMaterial);
            if (parts.length > 1 && !parts[1].isEmpty()) {
               BannerMeta meta = (BannerMeta)banner.getItemMeta();
               String[] patterns = parts[1].split(",");

               for(String patternData : patterns) {
                  String[] patternParts = patternData.split(":");
                  if (patternParts.length == 2) {
                     try {
                        NamespacedKey patternKey = NamespacedKey.minecraft(patternParts[0].toLowerCase());
                        PatternType patternType = (PatternType)RegistryAccess.registryAccess().getRegistry(RegistryKey.BANNER_PATTERN).get(patternKey);
                        DyeColor dyeColor = DyeColor.valueOf(patternParts[1].toUpperCase());
                        if (patternType != null) {
                           meta.addPattern(new Pattern(dyeColor, patternType));
                        }
                     } catch (Exception var16) {
                     }
                  }
               }

               banner.setItemMeta(meta);
            }

            return banner;
         } catch (Exception var17) {
            return new ItemStack(Material.WHITE_BANNER);
         }
      } else {
         return new ItemStack(Material.WHITE_BANNER);
      }
   }

   private void addDescriptionLines(List<Component> lore, String template, String description) {
      if (description != null && !description.isEmpty()) {
         String[] lines = description.split("\n");

         for(int i = 0; i < lines.length; ++i) {
            String line = this.legacyToMiniMessage(lines[i]);
            if (i == 0) {
               lore.add(this.deserialize(template.replace("{description}", line)));
            } else {
               lore.add(this.deserialize("&f" + line));
            }
         }

      }
   }

   private void addPriceLore(List<Component> lore, Clan clan, long cost) {
      ClanEconomyManager econ = this.module.getClanEconomyManager();
      if (econ != null && econ.isEnabled() && cost > 0L) {
         lore.add(this.deserialize(this.getGuiMessage("settings.price").replace("{cost}", String.valueOf(cost))));
         this.addAffordabilityLore(lore, clan, cost);
      }
   }

   private void addAffordabilityLore(List<Component> lore, Clan clan, long cost) {
      ClanEconomyManager econ = this.module.getClanEconomyManager();
      if (econ != null && econ.isEnabled() && cost > 0L) {
         long balance = clan.getBalance();
         if (balance >= cost) {
            lore.add(this.deserialize(this.getGuiMessage("settings.price-affordable")));
         } else {
            lore.add(this.deserialize(this.getGuiMessage("settings.price-unaffordable").replace("{balance}", String.valueOf(balance))));
         }

      }
   }
}
