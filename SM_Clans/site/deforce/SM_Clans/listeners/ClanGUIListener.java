package site.deforce.SM_Clans.listeners;

import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.listener.BaseListener;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import site.deforce.SM_Clans.SM_Clans;
import site.deforce.SM_Clans.gui.ClanDialogManager;
import site.deforce.SM_Clans.gui.ClanInventoryHolder;
import site.deforce.SM_Clans.managers.ClanAdminManager;
import site.deforce.SM_Clans.managers.ClanEconomyManager;
import site.deforce.SM_Clans.models.Clan;
import site.deforce.SM_Clans.models.ClanPrivacy;
import site.deforce.SM_Clans.models.PendingPurchase;

public class ClanGUIListener extends BaseListener {
   private final SM_Clans module;

   public ClanGUIListener(DoAPI plugin, SM_Clans module) {
      super(plugin);
      this.module = module;
   }

   @EventHandler
   public void onInventoryClick(InventoryClickEvent event) {
      if (this.module.isEnabled()) {
         HumanEntity whoClicked = event.getWhoClicked();
         if (whoClicked instanceof Player) {
            Player player = (Player)whoClicked;
            Inventory inv = event.getInventory();
            InventoryHolder holder = inv.getHolder();
            if (holder instanceof ClanInventoryHolder) {
               ClanInventoryHolder clanHolder = (ClanInventoryHolder)holder;
               event.setCancelled(true);
               if (!event.getClick().isShiftClick() && !event.getClick().isKeyboardClick()) {
                  if (event.getClickedInventory() != event.getView().getBottomInventory()) {
                     ItemStack clicked = event.getCurrentItem();
                     if (clicked != null && clicked.getType() != Material.AIR) {
                        if (clicked.hasItemMeta() && clicked.getItemMeta().displayName() != null) {
                           String itemName = PlainTextComponentSerializer.plainText().serialize(clicked.getItemMeta().displayName());
                           switch (clanHolder.getMenuType()) {
                              case MAIN -> this.handleMainMenuClick(player, itemName, clicked);
                              case NO_CLAN -> this.handleNoClanMenuClick(player, itemName);
                              case CLAN_LIST -> this.handleClanListClick(player, itemName, clicked, event);
                              case MEMBERS -> this.handleMembersListClick(player, itemName, clicked, event);
                              case SETTINGS -> this.handleSettingsMenuClick(player, itemName, clicked, event);
                              case PROFILE -> this.handleProfileMenuClick(player, itemName, clicked, clanHolder.getExtraData());
                              case ROLE_ASSIGN -> this.handleMemberRoleClick(player, itemName, clicked);
                              case BANNER_COLORS -> this.handleBannerColorClick(player, clicked);
                              case PLAYER_MANAGE -> this.handlePlayerManageClick(player, itemName, clicked, clanHolder.getExtraData());
                              case ADMIN_CLAN -> this.handleAdminClanMenuClick(player, itemName, clicked, event, clanHolder);
                              case ADMIN_CLAN_MANAGE -> this.handleAdminClanManageClick(player, itemName, clicked, clanHolder.getExtraData());
                              case ADMIN_MEMBERS -> this.handleAdminMembersListClick(player, itemName, clicked, event, clanHolder);
                              case ADMIN_BANK -> this.handleAdminBankClick(player, clicked, event, clanHolder.getExtraData());
                              case BANK -> this.handleBankMenuClick(player, itemName, clicked, event);
                              case CONFIRM_PURCHASE -> this.handleConfirmPurchaseClick(player, clicked);
                           }

                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void handleConfirmPurchaseClick(Player player, ItemStack clicked) {
      if (clicked.getType() == Material.LIME_WOOL) {
         this.module.getMenuManager().playSuccessSound(player);
         player.closeInventory();
         this.module.confirmPurchase(player);
         this.module.getMenuManager().openSettingsMenu(player);
      } else if (clicked.getType() == Material.RED_WOOL) {
         this.module.getMenuManager().playClickSound(player);
         this.module.cancelPurchase(player.getUniqueId());
         player.closeInventory();
         this.module.getMenuManager().openSettingsMenu(player);
      }

   }

   @EventHandler
   public void onInventoryDrag(InventoryDragEvent event) {
      if (this.module.isEnabled()) {
         if (event.getWhoClicked() instanceof Player) {
            Inventory inv = event.getInventory();
            InventoryHolder holder = inv.getHolder();
            if (holder instanceof ClanInventoryHolder) {
               boolean involvesTopInventory = event.getRawSlots().stream().anyMatch((slot) -> slot < event.getView().getTopInventory().getSize());
               if (involvesTopInventory) {
                  event.setCancelled(true);
               }

            }
         }
      }
   }

   public boolean isClanGUI(String title) {
      String stripped = this.stripColorCodes(title);
      return stripped.contains("Клан") || stripped.contains("Настройки") || stripped.contains("Участники") || stripped.contains("Профиль") || stripped.contains("Список кланов") || stripped.contains("Нет клана") || stripped.contains("Цвета знамен") || stripped.contains("Назначение роли") || stripped.contains("Казна") || stripped.contains("Банк") || stripped.contains("Подтверждение") || stripped.contains("Clan") || stripped.contains("Settings") || stripped.contains("Members") || stripped.contains("Profile") || stripped.contains("Bank") || stripped.contains("Treasury");
   }

   private String stripColorCodes(String text) {
      if (text == null) {
         return "";
      } else {
         String noLegacy = text.replaceAll("(?i)[&§][0-9a-fk-orx]", "");
         return noLegacy.replaceAll("(?i)(?:&#|#)[0-9a-f]{6}", "");
      }
   }

   private void handleBannerColorClick(Player player, ItemStack clicked) {
      if (clicked.getType() == Material.DARK_OAK_DOOR) {
         this.module.getMenuManager().playClickSound(player);
         player.closeInventory();
         this.module.getMenuManager().openSettingsMenu(player);
      } else if (clicked.getType().name().endsWith("_BANNER")) {
         this.module.getMenuManager().playSuccessSound(player);
         String type = clicked.getType().name();
         String color = type.substring(0, type.indexOf("_BANNER"));
         player.closeInventory();
         Clan clan = this.module.getClanManager().getPlayerClan(player.getUniqueId());
         ClanEconomyManager econ = this.module.getClanEconomyManager();
         if (clan != null && color.equalsIgnoreCase(clan.getBannerColor())) {
            this.module.getMenuManager().openSettingsMenu(player);
            return;
         }

         long cost = econ == null ? 0L : (long)econ.getBannerColorCost();
         this.module.requestPurchase(player, new PendingPurchase(PendingPurchase.Type.CHANGE_BANNER_COLOR, color, cost, "Цвет знамени → " + color));
      }

   }

   private void handleBankMenuClick(Player player, String itemName, ItemStack clicked, InventoryClickEvent event) {
      if (clicked.getType() == Material.DARK_OAK_DOOR) {
         this.module.getMenuManager().playClickSound(player);
         player.closeInventory();
         this.module.getMenuManager().openMainMenu(player);
      } else {
         String action = null;
         String amountStr = null;
         if (clicked.hasItemMeta() && clicked.getItemMeta().hasLore()) {
            List<Component> lore = clicked.getItemMeta().lore();
            if (lore != null) {
               for(Component line : lore) {
                  String plain = PlainTextComponentSerializer.plainText().serialize(line);
                  if (plain.startsWith("Action: ")) {
                     action = plain.substring(8).trim();
                  } else if (plain.startsWith("Amount: ")) {
                     amountStr = plain.substring(8).trim();
                  }
               }
            }
         }

         if (action != null && amountStr != null) {
            ClanEconomyManager economyManager = this.module.getClanEconomyManager();
            if (economyManager != null) {
               boolean deposit = action.equals("DEPOSIT");
               if (amountStr.equalsIgnoreCase("custom")) {
                  this.module.getMenuManager().playClickSound(player);
                  player.closeInventory();
                  this.module.getDialogManager().openTextEdit(player, deposit ? "Внести ары" : "Снять ары", (String)null, "amount", "Количество", "", 12, (value) -> {
                     int n;
                     try {
                        n = Integer.parseInt(value.trim());
                     } catch (NumberFormatException var7) {
                        this.module.getMenuManager().openBankMenu(player);
                        return;
                     }

                     if (n > 0) {
                        if (deposit) {
                           economyManager.deposit(player, n);
                        } else {
                           economyManager.withdraw(player, n);
                        }
                     }

                     this.module.getMenuManager().openBankMenu(player);
                  });
               } else {
                  int amount;
                  if (amountStr.equalsIgnoreCase("all")) {
                     amount = -1;
                  } else {
                     try {
                        amount = Integer.parseInt(amountStr);
                     } catch (NumberFormatException var11) {
                        return;
                     }
                  }

                  this.module.getMenuManager().playSuccessSound(player);
                  if (deposit) {
                     economyManager.deposit(player, amount);
                  } else {
                     economyManager.withdraw(player, amount);
                  }

                  this.module.getMenuManager().refreshBankInfo(event.getInventory(), player);
               }
            }
         }
      }
   }

   private void handleMainMenuClick(Player player, String itemName, ItemStack clicked) {
      Clan clan = this.module.getClanManager().getPlayerClan(player.getUniqueId());
      if (clan != null) {
         Material type = clicked.getType();
         if (type == Material.PLAYER_HEAD && !itemName.isEmpty() && !itemName.equals(" ")) {
            if (this.checkItemName(itemName, this.module.getMenuManager().getGuiMessage("main.members-list"))) {
               this.module.getMenuManager().playClickSound(player);
               player.closeInventory();
               this.module.getMenuManager().openMembersList(player, clan);
            }
         } else if (type == Material.COMPASS) {
            this.module.getMenuManager().playClickSound(player);
            player.closeInventory();
            this.module.getMenuManager().openClanList(player);
         } else if (type == Material.BOOK) {
            this.module.getMenuManager().playClickSound(player);
            player.closeInventory();
            this.module.getMenuManager().openOwnClanProfile(player);
         } else if (type == Material.COMPARATOR) {
            this.module.getMenuManager().playClickSound(player);
            player.closeInventory();
            this.module.getMenuManager().openSettingsMenu(player);
         } else if (type == Material.RED_WOOL) {
            this.module.getMenuManager().playClickSound(player);
            player.closeInventory();
            this.module.getClanInviteManager().leaveClan(player);
         } else if (type == Material.BARRIER) {
            this.module.getMenuManager().playClickSound(player);
            player.closeInventory();
            this.module.requestDisbandConfirm(player.getUniqueId());
            this.module.getMenuManager().sendMessage(player, this.module.getMenuManager().getMessage("disband.confirm-required"));
            this.module.getMenuManager().sendMessage(player, this.module.getMenuManager().getMessage("disband.confirm-hint"));
         } else if (type == Material.GOLD_BLOCK) {
            this.module.getMenuManager().playClickSound(player);
            player.closeInventory();
            this.module.getMenuManager().openBankMenu(player);
         }

      }
   }

   private void handleNoClanMenuClick(Player player, String itemName) {
      String createText = this.module.getMenuManager().getGuiMessage("noclan.create");
      String browseText = this.module.getMenuManager().getGuiMessage("noclan.browse");
      if (this.checkItemName(itemName, createText)) {
         this.module.getMenuManager().playClickSound(player);
         player.closeInventory();
         this.module.getCreationListener().startClanCreation(player);
      } else if (this.checkItemName(itemName, browseText)) {
         this.module.getMenuManager().playClickSound(player);
         player.closeInventory();
         this.module.getMenuManager().openClanList(player);
      }

   }

   private void handleClanListClick(Player player, String itemName, ItemStack clicked, InventoryClickEvent event) {
      ClanInventoryHolder holder = null;
      InventoryHolder var7 = player.getOpenInventory().getTopInventory().getHolder();
      if (var7 instanceof ClanInventoryHolder h) {
         holder = h;
      }

      int currentPage = holder != null ? holder.getPage() : 0;
      if (clicked.getType() == Material.DARK_OAK_DOOR) {
         this.module.getMenuManager().playClickSound(player);
         player.closeInventory();
         this.module.getMenuManager().openMainMenu(player);
      } else if (clicked.getType() == Material.ARROW && event.getSlot() == 48) {
         this.module.getMenuManager().playClickSound(player);
         player.closeInventory();
         this.module.getMenuManager().openClanList(player, currentPage - 1);
      } else if (clicked.getType() == Material.ARROW && event.getSlot() == 50) {
         this.module.getMenuManager().playClickSound(player);
         player.closeInventory();
         this.module.getMenuManager().openClanList(player, currentPage + 1);
      } else {
         if (clicked.getType().name().endsWith("_BANNER")) {
            String tag = this.extractTagFromBrackets(itemName);
            if (tag == null || tag.isEmpty()) {
               tag = this.findClanTagByName(itemName);
            }

            if (tag != null && !tag.isEmpty()) {
               this.module.getMenuManager().playClickSound(player);
               player.closeInventory();
               this.module.getMenuManager().openClanProfileByTag(player, tag);
            }
         }

      }
   }

   private String findClanTagByName(String text) {
      String strippedText = this.stripColorCodes(text).trim();

      for(Clan clan : this.module.getClanManager().getAllClans()) {
         String clanName = this.stripColorCodes(clan.getName()).trim();
         String clanTag = this.stripColorCodes(clan.getTag()).trim();
         if (!clanName.isEmpty() && strippedText.contains(clanName)) {
            return clan.getTag();
         }

         if (!clanTag.isEmpty() && strippedText.contains(clanTag)) {
            return clan.getTag();
         }
      }

      return null;
   }

   private void handleMembersListClick(Player player, String itemName, ItemStack clicked, InventoryClickEvent event) {
      Clan clan = this.module.getClanManager().getPlayerClan(player.getUniqueId());
      if (clan != null) {
         ClanInventoryHolder holder = (ClanInventoryHolder)event.getInventory().getHolder();
         int currentPage = holder != null ? holder.getPage() : 0;
         Material type = clicked.getType();
         if (type == Material.DARK_OAK_DOOR) {
            this.module.getMenuManager().playClickSound(player);
            player.closeInventory();
            this.module.getMenuManager().openMainMenu(player);
         } else if (type == Material.ARROW && event.getSlot() == 48) {
            this.module.getMenuManager().playClickSound(player);
            player.closeInventory();
            this.module.getMenuManager().openMembersList(player, clan, currentPage - 1);
         } else if (type == Material.ARROW && event.getSlot() == 50) {
            this.module.getMenuManager().playClickSound(player);
            player.closeInventory();
            this.module.getMenuManager().openMembersList(player, clan, currentPage + 1);
         } else {
            String targetUuid = null;
            String targetName = null;
            String roleId = null;
            if (clicked.hasItemMeta() && clicked.getItemMeta().hasLore()) {
               List<Component> lore = clicked.getItemMeta().lore();
               if (lore != null) {
                  for(Component line : lore) {
                     String plainLine = PlainTextComponentSerializer.plainText().serialize(line);
                     if (plainLine.startsWith("UUID: ")) {
                        targetUuid = plainLine.substring(6);
                     } else if (plainLine.startsWith("Name: ")) {
                        targetName = plainLine.substring(6);
                     } else if (plainLine.startsWith("RoleID: ")) {
                        roleId = plainLine.substring(8);
                     }
                  }
               }
            }

            if (type == Material.COMPARATOR && targetUuid != null && roleId != null) {
               try {
                  UUID uuid = UUID.fromString(targetUuid);
                  boolean isLeftClick = event.getClick().isLeftClick();
                  boolean isRightClick = event.getClick().isRightClick();
                  if (isLeftClick) {
                     this.module.getMenuManager().playSuccessSound(player);
                     this.module.getClanSettingsManager().changeRoleByDelta(player, uuid, 1);
                  } else if (isRightClick) {
                     this.module.getMenuManager().playSuccessSound(player);
                     this.module.getClanSettingsManager().changeRoleByDelta(player, uuid, -1);
                  }

                  player.closeInventory();
                  this.module.getMenuManager().openMembersList(player, clan, currentPage);
               } catch (IllegalArgumentException var16) {
                  this.module.getMenuManager().playErrorSound(player);
               }

            } else if (type == Material.BARRIER && targetName != null) {
               this.module.getMenuManager().playClickSound(player);
               player.closeInventory();
               this.module.getClanInviteManager().kickPlayer(player, targetName);
            } else if (type == Material.GOLDEN_HELMET && targetName != null) {
               this.module.getMenuManager().playClickSound(player);
               player.closeInventory();
               this.module.requestPromoteConfirm(player.getUniqueId(), targetName);
               this.module.getMenuManager().sendMessage(player, this.module.getMenuManager().getMessage("promote.confirm-required").replace("{player}", targetName));
               this.module.getMenuManager().sendMessage(player, this.module.getMenuManager().getMessage("promote.confirm-hint"));
            }
         }
      }
   }

   private void handleMemberRoleClick(Player player, String itemName, ItemStack clicked) {
      if (clicked.getType() == Material.DARK_OAK_DOOR) {
         this.module.getMenuManager().playClickSound(player);
         Clan clan = this.module.getClanManager().getPlayerClan(player.getUniqueId());
         if (clan != null) {
            player.closeInventory();
            this.module.getMenuManager().openMembersList(player, clan);
         }
      } else if ((clicked.getType() == Material.WHITE_WOOL || clicked.getType() == Material.LIME_WOOL) && clicked.hasItemMeta() && clicked.getItemMeta().hasLore()) {
         List<Component> lore = clicked.getItemMeta().lore();
         String roleId = null;
         String targetUuid = null;

         for(Component line : lore) {
            String plainLine = PlainTextComponentSerializer.plainText().serialize(line);
            if (plainLine.startsWith("RoleID: ")) {
               roleId = plainLine.substring(8);
            } else if (plainLine.startsWith("TargetUUID: ")) {
               targetUuid = plainLine.substring(12);
            }
         }

         if (roleId != null && targetUuid != null) {
            try {
               UUID target = UUID.fromString(targetUuid);
               this.module.getMenuManager().playSuccessSound(player);
               player.closeInventory();
               this.module.getClanSettingsManager().assignRoleById(player, target, roleId);
               Clan clan = this.module.getClanManager().getPlayerClan(player.getUniqueId());
               if (clan != null) {
                  this.module.getMenuManager().openMembersList(player, clan);
               }
            } catch (Exception var10) {
               this.module.getMenuManager().playErrorSound(player);
               this.module.getMenuManager().sendMessage(player, this.module.getMenuManager().getMessage("role-assign-error"));
            }
         }
      }

   }

   private void handleSettingsMenuClick(Player player, String itemName, ItemStack clicked, InventoryClickEvent event) {
      if (clicked.getType() == Material.DARK_OAK_DOOR) {
         this.module.getMenuManager().playClickSound(player);
         player.closeInventory();
         this.module.getMenuManager().openMainMenu(player);
      } else if (clicked.getType() == Material.ANVIL && this.checkItemName(itemName, this.module.getMenuManager().getGuiMessage("settings.buy-slots"))) {
         ClanEconomyManager econ = this.module.getClanEconomyManager();
         long cost = econ == null ? 0L : (long)econ.getSlotCostPerSlot() * (long)econ.getSlotsPerPurchase();
         int slots = econ == null ? 0 : econ.getSlotsPerPurchase();
         if (!this.ensureAffordable(player, cost)) {
            return;
         }

         this.module.getMenuManager().playClickSound(player);
         player.closeInventory();
         this.module.requestPurchase(player, new PendingPurchase(PendingPurchase.Type.BUY_SLOTS, (String)null, cost, "+" + slots + " слот(ов)"));
      } else if (clicked.getType() == Material.GOLD_BLOCK && this.checkItemName(itemName, this.module.getMenuManager().getGuiMessage("settings.bank"))) {
         this.module.getMenuManager().playClickSound(player);
         player.closeInventory();
         this.module.getMenuManager().openBankMenu(player);
      } else if (clicked.getType().name().endsWith("_BANNER") && this.checkItemName(itemName, this.module.getMenuManager().getGuiMessage("settings.set-flag"))) {
         if (event.isRightClick()) {
            this.module.getMenuManager().playClickSound(player);
            player.closeInventory();
            this.module.getClanSettingsManager().clearClanFlag(player);
         } else {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            boolean holdingBanner = mainHand != null && mainHand.getType().name().endsWith("_BANNER");
            ClanEconomyManager flagEcon = this.module.getClanEconomyManager();
            if (holdingBanner && flagEcon != null) {
               long cost = (long)flagEcon.getCustomBannerCost();
               if (!this.ensureAffordable(player, cost)) {
                  return;
               }

               this.module.getMenuManager().playClickSound(player);
               player.closeInventory();
               this.module.requestPurchase(player, new PendingPurchase(PendingPurchase.Type.SET_FLAG, (String)null, cost, "Кастомное знамя гильдии"));
            } else {
               this.module.getMenuManager().playClickSound(player);
               player.closeInventory();
               this.module.getClanSettingsManager().setClanFlag(player);
            }
         }
      } else if (clicked.getType().name().endsWith("_BANNER") && this.checkItemName(itemName, this.module.getMenuManager().getGuiMessage("settings.change-banner"))) {
         ClanEconomyManager econ = this.module.getClanEconomyManager();
         if (!this.ensureAffordable(player, econ == null ? 0L : (long)econ.getBannerColorCost())) {
            return;
         }

         this.module.getMenuManager().playClickSound(player);
         player.closeInventory();
         this.module.getMenuManager().openBannerColorMenu(player);
      } else if (clicked.getType() == Material.BOOK && this.checkItemName(itemName, this.module.getMenuManager().getGuiMessage("settings.change-description"))) {
         ClanEconomyManager econ = this.module.getClanEconomyManager();
         long cost = econ == null ? 0L : (long)econ.getDescriptionCost();
         if (!this.ensureAffordable(player, cost)) {
            return;
         }

         Clan clan = this.module.getClanManager().getPlayerClan(player.getUniqueId());
         if (clan == null) {
            return;
         }

         this.module.getMenuManager().playClickSound(player);
         player.closeInventory();
         this.module.getDialogManager().openTextEdit(player, "Описание гильдии", this.costLine(cost), "description", "Новое описание", clan.getDescription(), this.cfgInt("clans.max-description-length", 128), (value) -> this.module.getClanSettingsManager().changeClanDescription(player, value));
      } else if (clicked.getType() == Material.WRITABLE_BOOK) {
         ClanEconomyManager econ = this.module.getClanEconomyManager();
         long cost = econ == null ? 0L : (long)econ.getNameCost();
         if (!this.ensureAffordable(player, cost)) {
            return;
         }

         Clan clan = this.module.getClanManager().getPlayerClan(player.getUniqueId());
         if (clan == null) {
            return;
         }

         this.module.getMenuManager().playClickSound(player);
         player.closeInventory();
         this.module.getDialogManager().openTextEdit(player, "Название гильдии", this.costLine(cost), "name", "Новое название", clan.getName(), this.cfgInt("clans.max-name-length", 24), (value) -> this.module.getClanSettingsManager().changeClanName(player, value));
      } else if (clicked.getType() == Material.NAME_TAG) {
         ClanEconomyManager econ = this.module.getClanEconomyManager();
         long cost = econ == null ? 0L : (long)econ.getTagCost();
         if (!this.ensureAffordable(player, cost)) {
            return;
         }

         Clan clan = this.module.getClanManager().getPlayerClan(player.getUniqueId());
         if (clan == null) {
            return;
         }

         this.module.getMenuManager().playClickSound(player);
         player.closeInventory();
         this.module.getDialogManager().openTextEdit(player, "Тег гильдии", this.costLine(cost), "tag", "Новый тег", clan.getTag(), this.cfgInt("clans.max-tag-length", 10), (value) -> this.module.getClanSettingsManager().changeClanTag(player, value));
      } else if (clicked.getType() == Material.PLAYER_HEAD && this.checkItemName(itemName, this.module.getMenuManager().getGuiMessage("settings.manage-roles"))) {
         Clan clan = this.module.getClanManager().getPlayerClan(player.getUniqueId());
         if (clan != null) {
            this.module.getMenuManager().playClickSound(player);
            player.closeInventory();
            this.module.getMenuManager().openMembersList(player, clan);
         }
      } else if (clicked.getType() == Material.BARRIER && this.checkItemName(itemName, this.module.getMenuManager().getGuiMessage("settings.settings-tag-toggle"))) {
         this.module.getMenuManager().playErrorSound(player);
      } else if ((clicked.getType() == Material.LIME_DYE || clicked.getType() == Material.GRAY_DYE) && this.checkItemName(itemName, this.module.getMenuManager().getGuiMessage("settings.settings-tag-toggle"))) {
         this.module.getMenuManager().playSuccessSound(player);
         this.module.getClanSettingsManager().toggleTag(player);
         player.closeInventory();
         this.module.getMenuManager().openSettingsMenu(player);
      } else if ((clicked.getType() == Material.RED_DYE || clicked.getType() == Material.GRAY_DYE) && this.checkItemName(itemName, this.module.getMenuManager().getGuiMessage("settings.friendly-fire"))) {
         this.module.getMenuManager().playSuccessSound(player);
         this.module.getClanSettingsManager().toggleFriendlyFire(player);
         player.closeInventory();
         this.module.getMenuManager().openSettingsMenu(player);
      } else if (clicked.getType() == Material.LIME_WOOL || clicked.getType() == Material.WHITE_WOOL) {
         if (this.checkItemName(itemName, this.module.getMenuManager().getGuiMessage("settings.privacy-public"))) {
            this.module.getMenuManager().playSuccessSound(player);
            this.module.getClanSettingsManager().changePrivacy(player, "public");
            player.closeInventory();
            this.module.getMenuManager().openSettingsMenu(player);
         } else if (this.checkItemName(itemName, this.module.getMenuManager().getGuiMessage("settings.privacy-private"))) {
            this.module.getMenuManager().playSuccessSound(player);
            this.module.getClanSettingsManager().changePrivacy(player, "private");
            player.closeInventory();
            this.module.getMenuManager().openSettingsMenu(player);
         } else if (this.checkItemName(itemName, this.module.getMenuManager().getGuiMessage("settings.privacy-invite"))) {
            this.module.getMenuManager().playSuccessSound(player);
            this.module.getClanSettingsManager().changePrivacy(player, "invite_only");
            player.closeInventory();
            this.module.getMenuManager().openSettingsMenu(player);
         }
      }

   }

   private void handleProfileMenuClick(Player player, String itemName, ItemStack clicked, String clanTag) {
      if (clicked.getType() == Material.DARK_OAK_DOOR) {
         this.module.getMenuManager().playClickSound(player);
         player.closeInventory();
         Clan playerClan = this.module.getClanManager().getPlayerClan(player.getUniqueId());
         if (playerClan != null && clanTag != null && playerClan.getTag().equalsIgnoreCase(clanTag)) {
            this.module.getMenuManager().openMainMenu(player);
         } else {
            this.module.getMenuManager().openClanList(player);
         }
      } else if (clicked.getType() == Material.EMERALD && this.checkItemName(itemName, this.module.getMenuManager().getGuiMessage("profile.join"))) {
         if (clanTag != null && !clanTag.isEmpty()) {
            this.module.getMenuManager().playSuccessSound(player);
            player.closeInventory();
            this.module.getClanInviteManager().joinPublicClan(player, clanTag);
         } else {
            this.module.getMenuManager().playErrorSound(player);
            this.module.getMenuManager().sendMessage(player, this.module.getMenuManager().getMessage("clan-not-found"));
         }
      }

   }

   private void handlePlayerManageClick(Player player, String itemName, ItemStack clicked, String targetUuidString) {
      if (clicked.getType() == Material.DARK_OAK_DOOR) {
         this.module.getMenuManager().playClickSound(player);
         Clan clan = this.module.getClanManager().getPlayerClan(player.getUniqueId());
         if (clan != null) {
            player.closeInventory();
            this.module.getMenuManager().openMembersList(player, clan);
         }

      } else if (targetUuidString != null && !targetUuidString.isEmpty()) {
         UUID targetUuid;
         try {
            targetUuid = UUID.fromString(targetUuidString);
         } catch (IllegalArgumentException var7) {
            return;
         }

         String targetName = Bukkit.getOfflinePlayer(targetUuid).getName();
         if (targetName == null) {
            targetName = "Unknown";
         }

         if (clicked.getType() == Material.NAME_TAG && this.checkItemName(itemName, this.module.getMenuManager().getGuiMessage("player-manage.change-role"))) {
            this.module.getMenuManager().playClickSound(player);
            player.closeInventory();
            this.module.getMenuManager().openMemberRoleMenu(player, targetUuid);
         } else if (clicked.getType() == Material.BARRIER && this.checkItemName(itemName, this.module.getMenuManager().getGuiMessage("player-manage.kick"))) {
            this.module.getMenuManager().playClickSound(player);
            player.closeInventory();
            this.module.getClanInviteManager().kickPlayer(player, targetName);
         } else if (clicked.getType() == Material.GOLDEN_HELMET && this.checkItemName(itemName, this.module.getMenuManager().getGuiMessage("player-manage.transfer-leadership"))) {
            this.module.getMenuManager().playClickSound(player);
            player.closeInventory();
            this.module.requestPromoteConfirm(player.getUniqueId(), targetName);
            this.module.getMenuManager().sendMessage(player, this.module.getMenuManager().getMessage("promote.confirm-required").replace("{player}", targetName));
            this.module.getMenuManager().sendMessage(player, this.module.getMenuManager().getMessage("promote.confirm-hint"));
         }
      }
   }

   private void handleAdminMembersListClick(Player admin, String itemName, ItemStack clicked, InventoryClickEvent event, ClanInventoryHolder holder) {
      if (admin.hasPermission("smclans.clan.admin")) {
         String clanTag = holder.getExtraData();
         Clan clan = this.resolveClanByTag(clanTag);
         if (clan != null) {
            int currentPage = holder.getPage();
            Material type = clicked.getType();
            if (type == Material.DARK_OAK_DOOR) {
               this.module.getMenuManager().playClickSound(admin);
               admin.closeInventory();
               this.module.getMenuManager().openAdminClanManageMenu(admin, clan);
            } else if (type == Material.ARROW && event.getSlot() == 48) {
               this.module.getMenuManager().playClickSound(admin);
               admin.closeInventory();
               this.module.getMenuManager().openAdminMembersList(admin, clan, currentPage - 1);
            } else if (type == Material.ARROW && event.getSlot() == 50) {
               this.module.getMenuManager().playClickSound(admin);
               admin.closeInventory();
               this.module.getMenuManager().openAdminMembersList(admin, clan, currentPage + 1);
            } else {
               String targetUuid = null;
               String targetName = null;
               String roleId = null;
               if (clicked.hasItemMeta() && clicked.getItemMeta().hasLore()) {
                  List<Component> lore = clicked.getItemMeta().lore();
                  if (lore != null) {
                     for(Component line : lore) {
                        String plainLine = PlainTextComponentSerializer.plainText().serialize(line);
                        if (plainLine.startsWith("UUID: ")) {
                           targetUuid = plainLine.substring(6);
                        } else if (plainLine.startsWith("Name: ")) {
                           targetName = plainLine.substring(6);
                        } else if (plainLine.startsWith("RoleID: ")) {
                           roleId = plainLine.substring(8);
                        }
                     }
                  }
               }

               if (type == Material.COMPARATOR && targetUuid != null && roleId != null) {
                  try {
                     UUID uuid = UUID.fromString(targetUuid);
                     boolean isLeftClick = event.getClick().isLeftClick();
                     boolean isRightClick = event.getClick().isRightClick();
                     if (isLeftClick) {
                        this.module.getMenuManager().playSuccessSound(admin);
                        this.module.getClanSettingsManager().adminChangeRoleByDelta(admin, clan, uuid, 1);
                     } else if (isRightClick) {
                        this.module.getMenuManager().playSuccessSound(admin);
                        this.module.getClanSettingsManager().adminChangeRoleByDelta(admin, clan, uuid, -1);
                     }

                     admin.closeInventory();
                     this.module.getMenuManager().openAdminMembersList(admin, clan, currentPage);
                  } catch (IllegalArgumentException var17) {
                     this.module.getMenuManager().playErrorSound(admin);
                  }

               } else if (type == Material.BARRIER && targetName != null) {
                  this.module.getMenuManager().playClickSound(admin);
                  admin.closeInventory();
                  this.module.getClanInviteManager().adminKickPlayer(admin, clan, targetName);
                  this.module.getMenuManager().openAdminMembersList(admin, clan, currentPage);
               }
            }
         }
      }
   }

   private Clan resolveClanByTag(String clanTag) {
      if (clanTag != null && !clanTag.isEmpty()) {
         Clan clan = this.module.getClanManager().getClanByTag(clanTag);
         if (clan == null) {
            for(Clan c : this.module.getClanManager().getAllClans()) {
               if (this.stripColorCodes(c.getTag()).trim().equalsIgnoreCase(clanTag)) {
                  return c;
               }
            }
         }

         return clan;
      } else {
         return null;
      }
   }

   private boolean ensureAffordable(Player player, long cost) {
      ClanEconomyManager econ = this.module.getClanEconomyManager();
      if (econ != null && econ.isEnabled() && cost > 0L) {
         Clan clan = this.module.getClanManager().getPlayerClan(player.getUniqueId());
         if (clan == null) {
            return true;
         } else if (!econ.canAfford(clan, cost)) {
            this.module.getMenuManager().playErrorSound(player);
            econ.notifyNotEnoughTreasury(player, clan, cost);
            return false;
         } else {
            return true;
         }
      } else {
         return true;
      }
   }

   private String costLine(long cost) {
      return cost > 0L ? "Стоимость: " + cost + " ар (из казны)" : "";
   }

   private int cfgInt(String path, int def) {
      FileConfiguration config = this.module.getConfig();
      return config != null ? config.getInt(path, def) : def;
   }

   private boolean checkItemName(String plainItemName, String configMessage) {
      if (configMessage == null) {
         return false;
      } else {
         String strippedConfig = this.stripColorCodes(configMessage).trim();
         return !strippedConfig.isEmpty() && plainItemName.contains(strippedConfig);
      }
   }

   private String extractTagFromBrackets(String text) {
      int startIdx = text.indexOf("[");
      int endIdx = text.indexOf("]");
      return startIdx != -1 && endIdx != -1 && startIdx < endIdx ? text.substring(startIdx + 1, endIdx).trim() : null;
   }

   private void handleAdminClanMenuClick(Player admin, String itemName, ItemStack clicked, InventoryClickEvent event, ClanInventoryHolder holder) {
      if (admin.hasPermission("smclans.clan.admin")) {
         int currentPage = holder.getPage();
         if (clicked.getType() == Material.DARK_OAK_DOOR) {
            this.module.getMenuManager().playClickSound(admin);
            admin.closeInventory();
         } else if (clicked.getType() == Material.ARROW && event.getSlot() == 48) {
            this.module.getMenuManager().playClickSound(admin);
            admin.closeInventory();
            this.module.getMenuManager().openAdminClanMenu(admin, currentPage - 1);
         } else if (clicked.getType() == Material.ARROW && event.getSlot() == 50) {
            this.module.getMenuManager().playClickSound(admin);
            admin.closeInventory();
            this.module.getMenuManager().openAdminClanMenu(admin, currentPage + 1);
         } else {
            if (clicked.hasItemMeta() && clicked.getItemMeta().hasLore()) {
               List<Component> lore = clicked.getItemMeta().lore();
               String clanTag = null;
               if (lore != null) {
                  for(Component line : lore) {
                     String plain = PlainTextComponentSerializer.plainText().serialize(line);
                     if (plain.startsWith("Tag: ")) {
                        clanTag = plain.substring(5).trim();
                        break;
                     }
                  }
               }

               if (clanTag != null) {
                  Clan clan = this.module.getClanManager().getClanByTag(clanTag);
                  if (clan == null) {
                     for(Clan c : this.module.getClanManager().getAllClans()) {
                        if (this.stripColorCodes(c.getTag()).trim().equalsIgnoreCase(clanTag)) {
                           clan = c;
                           break;
                        }
                     }
                  }

                  if (clan != null) {
                     this.module.getMenuManager().playClickSound(admin);
                     admin.closeInventory();
                     this.module.getMenuManager().openAdminClanManageMenu(admin, clan);
                  }
               }
            }

         }
      }
   }

   private void handleAdminClanManageClick(Player admin, String itemName, ItemStack clicked, String extraData) {
      if (admin.hasPermission("smclans.clan.admin")) {
         Clan clan = null;
         if (extraData != null && !extraData.isEmpty()) {
            clan = this.module.getClanManager().getClanByTag(extraData);
            if (clan == null) {
               for(Clan c : this.module.getClanManager().getAllClans()) {
                  if (this.stripColorCodes(c.getTag()).trim().equalsIgnoreCase(extraData)) {
                     clan = c;
                     break;
                  }
               }
            }
         }

         if (clicked.getType() == Material.DARK_OAK_DOOR) {
            this.module.getMenuManager().playClickSound(admin);
            admin.closeInventory();
            this.module.getMenuManager().openAdminClanMenu(admin);
         } else if (clan != null) {
            Clan finalClan = clan;
            String toggleType = null;
            String clanTagFromLore = null;
            if (clicked.hasItemMeta() && clicked.getItemMeta().hasLore()) {
               List<Component> lore = clicked.getItemMeta().lore();
               if (lore != null) {
                  for(Component line : lore) {
                     String plain = PlainTextComponentSerializer.plainText().serialize(line);
                     if (plain.startsWith("Toggle: ")) {
                        toggleType = plain.substring(8).trim();
                     }

                     if (plain.startsWith("Tag: ")) {
                        clanTagFromLore = plain.substring(5).trim();
                     }
                  }
               }
            }

            if (toggleType != null) {
               switch (toggleType) {
                  case "TAGENABLED":
                     finalClan.setTagEnabled(!finalClan.isTagEnabled());
                     this.module.getClanManager().saveClan(finalClan);
                     this.module.getMenuManager().playSuccessSound(admin);
                     admin.closeInventory();
                     this.module.getMenuManager().openAdminClanManageMenu(admin, finalClan);
                     break;
                  case "FRIENDLYFIRE":
                     finalClan.setFriendlyFire(!finalClan.isFriendlyFire());
                     this.module.getClanManager().saveClan(finalClan);
                     this.module.getMenuManager().playSuccessSound(admin);
                     admin.closeInventory();
                     this.module.getMenuManager().openAdminClanManageMenu(admin, finalClan);
                     break;
                  case "CHATENABLED":
                     finalClan.setChatEnabled(!finalClan.isChatEnabled());
                     this.module.getClanManager().saveClan(finalClan);
                     this.module.getMenuManager().playSuccessSound(admin);
                     admin.closeInventory();
                     this.module.getMenuManager().openAdminClanManageMenu(admin, finalClan);
                     break;
                  case "PRIVACY":
                     ClanPrivacy current = finalClan.getPrivacy();
                     ClanPrivacy var10000;
                     switch (current) {
                        case PUBLIC -> var10000 = ClanPrivacy.INVITE_ONLY;
                        case INVITE_ONLY -> var10000 = ClanPrivacy.PRIVATE;
                        case PRIVATE -> var10000 = ClanPrivacy.PUBLIC;
                        default -> throw new MatchException((String)null, (Throwable)null);
                     }

                     ClanPrivacy next = var10000;
                     finalClan.setPrivacy(next);
                     this.module.getClanManager().saveClan(finalClan);
                     this.module.getMenuManager().playSuccessSound(admin);
                     admin.closeInventory();
                     this.module.getMenuManager().openAdminClanManageMenu(admin, finalClan);
               }

            } else {
               String btnDisband = this.module.getMenuManager().getGuiMessage("admin.btn.disband");
               String btnMembers = this.module.getMenuManager().getGuiMessage("admin.btn.members");
               String btnSetname = this.module.getMenuManager().getGuiMessage("admin.btn.setname");
               String btnSettag = this.module.getMenuManager().getGuiMessage("admin.btn.settag");
               String btnSetdesc = this.module.getMenuManager().getGuiMessage("admin.btn.setdescription");
               String btnSetmax = this.module.getMenuManager().getGuiMessage("admin.btn.setmaxmembers");
               String btnBanner = this.module.getMenuManager().getGuiMessage("admin.btn.setbanner");
               String btnForceadd = this.module.getMenuManager().getGuiMessage("admin.btn.forceadd");
               String btnLeader = this.module.getMenuManager().getGuiMessage("admin.btn.setleader");
               String btnKick = this.module.getMenuManager().getGuiMessage("admin.btn.kick");
               if (clicked.getType() == Material.GOLD_BLOCK) {
                  this.module.getMenuManager().playClickSound(admin);
                  admin.closeInventory();
                  this.module.getMenuManager().openAdminBankMenu(admin, finalClan);
               } else if (clicked.getType() == Material.TNT && this.checkItemName(itemName, btnDisband)) {
                  this.module.getMenuManager().playClickSound(admin);
                  admin.closeInventory();
                  this.module.getClanAdminManager().disband(admin, finalClan);
               } else if (clicked.getType() == Material.PLAYER_HEAD && this.checkItemName(itemName, btnMembers)) {
                  this.module.getMenuManager().playClickSound(admin);
                  admin.closeInventory();
                  this.module.getMenuManager().openAdminMembersList(admin, finalClan);
               } else if (clicked.getType().name().endsWith("_BANNER") && this.checkItemName(itemName, btnBanner)) {
                  this.module.getMenuManager().playClickSound(admin);
                  admin.closeInventory();
                  this.module.getMenuManager().openBannerColorMenu(admin);
               } else {
                  ClanDialogManager dialogs = this.module.getDialogManager();
                  ClanAdminManager adminMgr = this.module.getClanAdminManager();
                  if (clicked.getType() == Material.WRITABLE_BOOK && this.checkItemName(itemName, btnSetname)) {
                     this.module.getMenuManager().playClickSound(admin);
                     admin.closeInventory();
                     dialogs.openTextEdit(admin, "Название гильдии", (String)null, "name", "Новое название", finalClan.getName(), this.cfgInt("clans.max-name-length", 24), (value) -> adminMgr.setName(admin, finalClan, value));
                  } else if (clicked.getType() == Material.NAME_TAG && this.checkItemName(itemName, btnSettag)) {
                     this.module.getMenuManager().playClickSound(admin);
                     admin.closeInventory();
                     dialogs.openTextEdit(admin, "Тег гильдии", (String)null, "tag", "Новый тег", finalClan.getTag(), this.cfgInt("clans.max-tag-length", 10), (value) -> adminMgr.setTag(admin, finalClan, value));
                  } else if (clicked.getType() == Material.BOOK && this.checkItemName(itemName, btnSetdesc)) {
                     this.module.getMenuManager().playClickSound(admin);
                     admin.closeInventory();
                     dialogs.openTextEdit(admin, "Описание гильдии", (String)null, "description", "Новое описание", finalClan.getDescription(), this.cfgInt("clans.max-description-length", 128), (value) -> adminMgr.setDescription(admin, finalClan, value));
                  } else if (clicked.getType() == Material.ANVIL && this.checkItemName(itemName, btnSetmax)) {
                     this.module.getMenuManager().playClickSound(admin);
                     admin.closeInventory();
                     dialogs.openTextEdit(admin, "Макс. участников", (String)null, "max", "Число", String.valueOf(finalClan.getMaxMembers()), 4, (value) -> adminMgr.setMaxMembers(admin, finalClan, value));
                  } else if (clicked.getType() == Material.EMERALD && this.checkItemName(itemName, btnForceadd)) {
                     this.module.getMenuManager().playClickSound(admin);
                     admin.closeInventory();
                     dialogs.openTextEdit(admin, "Добавить игрока", (String)null, "player", "Имя игрока", "", 16, (value) -> adminMgr.forceAdd(admin, finalClan, value));
                  } else if (clicked.getType() == Material.GOLDEN_HELMET && this.checkItemName(itemName, btnLeader)) {
                     this.module.getMenuManager().playClickSound(admin);
                     admin.closeInventory();
                     dialogs.openTextEdit(admin, "Новый лидер", (String)null, "player", "Имя игрока", "", 16, (value) -> adminMgr.setLeader(admin, finalClan, value));
                  } else if (clicked.getType() == Material.BARRIER && this.checkItemName(itemName, btnKick)) {
                     this.module.getMenuManager().playClickSound(admin);
                     admin.closeInventory();
                     dialogs.openTextEdit(admin, "Исключить игрока", (String)null, "player", "Имя игрока", "", 16, (value) -> adminMgr.kick(admin, finalClan, value));
                  }
               }
            }
         }
      }
   }

   private void handleAdminBankClick(Player admin, ItemStack clicked, InventoryClickEvent event, String extraData) {
      if (admin.hasPermission("smclans.clan.admin")) {
         Clan clan = this.module.getClanManager().getClanByTag(extraData);
         if (clan == null && extraData != null) {
            for(Clan c : this.module.getClanManager().getAllClans()) {
               if (this.stripColorCodes(c.getTag()).trim().equalsIgnoreCase(extraData)) {
                  clan = c;
                  break;
               }
            }
         }

         if (clicked.getType() == Material.DARK_OAK_DOOR) {
            this.module.getMenuManager().playClickSound(admin);
            admin.closeInventory();
            if (clan != null) {
               this.module.getMenuManager().openAdminClanManageMenu(admin, clan);
            }

         } else if (clan != null) {
            Clan finalClan = clan;
            String action = null;
            String amountStr = null;
            if (clicked.hasItemMeta() && clicked.getItemMeta().hasLore()) {
               List<Component> lore = clicked.getItemMeta().lore();
               if (lore != null) {
                  for(Component line : lore) {
                     String plain = PlainTextComponentSerializer.plainText().serialize(line);
                     if (plain.startsWith("Action: ")) {
                        action = plain.substring(8).trim();
                     } else if (plain.startsWith("Amount: ")) {
                        amountStr = plain.substring(8).trim();
                     }
                  }
               }
            }

            if (action != null) {
               ClanEconomyManager economy = this.module.getClanEconomyManager();
               if (economy != null) {
                  if (!action.equals("ADDCUSTOM") && !action.equals("REMOVECUSTOM")) {
                     long amount;
                     if ("all".equalsIgnoreCase(amountStr)) {
                        amount = finalClan.getBalance();
                     } else {
                        try {
                           amount = Long.parseLong(amountStr);
                        } catch (NumberFormatException var13) {
                           return;
                        }
                     }

                     this.module.getMenuManager().playSuccessSound(admin);
                     if (action.equals("ADD")) {
                        economy.adminAddTreasury(admin, finalClan, amount);
                     } else if (action.equals("REMOVE") || action.equals("REMOVEALL")) {
                        economy.adminRemoveTreasury(admin, finalClan, amount);
                     }

                     this.module.getMenuManager().refreshAdminBankInfo(event.getInventory(), finalClan);
                  } else {
                     boolean add = action.equals("ADDCUSTOM");
                     this.module.getMenuManager().playClickSound(admin);
                     admin.closeInventory();
                     this.module.getDialogManager().openTextEdit(admin, add ? "Добавить ары" : "Забрать ары", (String)null, "amount", "Количество", "", 12, (value) -> {
                        int n;
                        try {
                           n = Integer.parseInt(value.trim());
                        } catch (NumberFormatException var8) {
                           this.module.getMenuManager().openAdminBankMenu(admin, finalClan);
                           return;
                        }

                        if (n > 0) {
                           if (add) {
                              economy.adminAddTreasury(admin, finalClan, (long)n);
                           } else {
                              economy.adminRemoveTreasury(admin, finalClan, (long)n);
                           }
                        }

                        this.module.getMenuManager().openAdminBankMenu(admin, finalClan);
                     });
                  }
               }
            }
         }
      }
   }
}
