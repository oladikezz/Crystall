package site.deforce.SM_Clans.managers;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.UUID;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.schalker.DoAPI.DoAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.banner.Pattern;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.ItemMeta;
import site.deforce.SM_Clans.SM_Clans;
import site.deforce.SM_Clans.logging.ClanAuditLogger;
import site.deforce.SM_Clans.models.Clan;
import site.deforce.SM_Clans.models.ClanMember;
import site.deforce.SM_Clans.models.ClanPermission;
import site.deforce.SM_Clans.models.ClanPrivacy;
import site.deforce.SM_Clans.models.ClanRole;
import site.deforce.SM_Clans.models.DefaultClanRole;
import site.deforce.SM_Clans.util.StyleInput;

public class ClanSettingsManager {
   private final SM_Clans module;
   private final ClanManager clanManager;
   private final RoleManager roleManager;

   public ClanSettingsManager(SM_Clans module, ClanManager clanManager, RoleManager roleManager) {
      super();
      this.module = module;
      this.clanManager = clanManager;
      this.roleManager = roleManager;
   }

   public void changePrivacy(Player player, String privacyStr) {
      Clan clan = this.clanManager.getPlayerClan(player.getUniqueId());
      if (clan == null) {
         this.sendMessage(player, this.getMessage("not-in-clan"));
      } else if (!this.roleManager.isLeader(player.getUniqueId()) && !this.roleManager.hasPermission(player.getUniqueId(), ClanPermission.CHANGE_PRIVACY)) {
         this.sendMessage(player, this.getMessage("settings.privacy.no-permission"));
      } else {
         ClanPrivacy privacy;
         try {
            privacy = ClanPrivacy.valueOf(privacyStr.toUpperCase());
         } catch (IllegalArgumentException var6) {
            this.sendMessage(player, this.getMessage("settings.privacy.invalid"));
            return;
         }

         clan.setPrivacy(privacy);
         this.clanManager.saveClan(clan);
         this.sendMessage(player, this.getMessage("settings.privacy.changed").replace("{privacy}", this.getPrivacyName(privacy)));
      }

   }

   public void changeClanName(Player player, String newName) {
      Clan clan = this.clanManager.getPlayerClan(player.getUniqueId());
      if (clan == null) {
         this.sendMessage(player, this.getMessage("not-in-clan"));
      } else if (!this.roleManager.hasPermission(player.getUniqueId(), ClanPermission.CHANGE_NAME)) {
         this.sendMessage(player, this.getMessage("settings.name.no-permission"));
      } else {
         FileConfiguration config = this.module.getConfig();
         boolean allowColors = config != null && config.getBoolean("clans.allow-colored-names", false);
         int minLength = config != null ? config.getInt("clans.min-name-length", 1) : 1;
         int maxLength = config != null ? config.getInt("clans.max-name-length", 24) : 24;
         newName = StyleInput.miniToLegacy(newName);
         String plainName = PlainTextComponentSerializer.plainText().serialize(LegacyComponentSerializer.legacyAmpersand().deserialize(newName));
         String strippedName = this.stripColorCodes(plainName).trim();
         if (!allowColors) {
            newName = strippedName;
         }

         if (!strippedName.isEmpty() && strippedName.length() >= minLength && strippedName.length() <= maxLength) {
            if (this.clanManager.isClanNameTaken(newName, clan.getClanId())) {
               this.sendMessage(player, this.getMessage("settings.name.already-taken"));
               return;
            }

            ClanEconomyManager econ = this.module.getClanEconomyManager();
            boolean nameChanged = !newName.equals(clan.getName());
            if (econ != null && nameChanged && !econ.tryCharge(player, clan, (long)econ.getNameCost())) {
               return;
            }

            String oldName = clan.getName();
            clan.setName(newName);
            this.clanManager.saveClan(clan);
            this.sendMessage(player, this.getMessage("settings.name.changed").replace("{old}", oldName).replace("{new}", newName));
            if (econ != null && econ.isEnabled() && nameChanged) {
               this.logPurchase(player, clan, "CHANGE_NAME", "Название → " + newName, (long)econ.getNameCost());
            }
         } else {
            this.sendMessage(player, this.getMessage("settings.name.invalid").replace("{min}", String.valueOf(minLength)).replace("{max}", String.valueOf(maxLength)));
         }
      }

   }

   public void changeClanTag(Player player, String newTag) {
      Clan clan = this.clanManager.getPlayerClan(player.getUniqueId());
      if (clan == null) {
         this.sendMessage(player, this.getMessage("not-in-clan"));
      } else if (!this.roleManager.hasPermission(player.getUniqueId(), ClanPermission.CHANGE_TAG)) {
         this.sendMessage(player, this.getMessage("settings.tag.no-permission"));
      } else {
         FileConfiguration config = this.module.getConfig();
         int minLength = config != null ? config.getInt("clans.min-tag-length", 2) : 2;
         int maxLength = config != null ? config.getInt("clans.max-tag-length", 10) : 10;
         boolean allowColors = config != null && config.getBoolean("clans.allow-colored-tags", true);
         newTag = StyleInput.miniToLegacy(newTag);
         newTag = this.normalizeHexColors(newTag);
         if (!allowColors) {
            newTag = this.stripColorCodes(PlainTextComponentSerializer.plainText().serialize(LegacyComponentSerializer.legacyAmpersand().deserialize(newTag)));
         }

         String stripped = this.stripColorCodes(PlainTextComponentSerializer.plainText().serialize(LegacyComponentSerializer.legacyAmpersand().deserialize(newTag))).trim();
         int visibleLength = stripped.codePointCount(0, stripped.length());
         boolean lengthOk = visibleLength > 0 && (visibleLength == 1 || visibleLength >= minLength && visibleLength <= maxLength);
         if (lengthOk) {
            for(Clan existingClan : this.clanManager.getAllClans()) {
               if (!existingClan.getClanId().equals(clan.getClanId()) && this.stripColorCodes(PlainTextComponentSerializer.plainText().serialize(LegacyComponentSerializer.legacyAmpersand().deserialize(existingClan.getTag()))).trim().equalsIgnoreCase(stripped)) {
                  this.sendMessage(player, this.getMessage("settings.tag.already-taken"));
                  return;
               }
            }

            ClanEconomyManager tagEcon = this.module.getClanEconomyManager();
            boolean tagChanged = !newTag.equals(clan.getTag());
            if (tagEcon != null && tagChanged && !tagEcon.tryCharge(player, clan, (long)tagEcon.getTagCost())) {
               return;
            }

            String oldTag = clan.getTag();
            clan.setTag(newTag);
            this.clanManager.saveClan(clan);
            String var10002 = this.getMessage("settings.tag.changed");
            DoAPI var10004 = this.module.getPlugin();
            String var10005 = this.normalizeHexColors(oldTag);
            var10002 = var10002.replace("{old}", var10004.applyColors(var10005 + "&r"));
            var10004 = this.module.getPlugin();
            var10005 = this.normalizeHexColors(newTag);
            this.sendMessage(player, var10002.replace("{new}", var10004.applyColors(var10005 + "&r")));
            if (tagEcon != null && tagEcon.isEnabled() && tagChanged) {
               this.logPurchase(player, clan, "CHANGE_TAG", "Тег → " + this.stripColorCodes(newTag), (long)tagEcon.getTagCost());
            }
         } else {
            this.sendMessage(player, this.getMessage("settings.tag.invalid-length").replace("{min}", String.valueOf(minLength)).replace("{max}", String.valueOf(maxLength)));
         }
      }

   }

   public void changeClanDescription(Player player, String newDescription) {
      Clan clan = this.clanManager.getPlayerClan(player.getUniqueId());
      if (clan == null) {
         this.sendMessage(player, this.getMessage("not-in-clan"));
      } else if (!this.roleManager.hasPermission(player.getUniqueId(), ClanPermission.CHANGE_DESCRIPTION)) {
         this.sendMessage(player, this.getMessage("settings.description.no-permission"));
      } else {
         FileConfiguration config = this.module.getConfig();
         boolean allowColors = config != null && config.getBoolean("clans.allow-colored-descriptions", true);
         int minLength = config != null ? config.getInt("clans.min-description-length", 1) : 1;
         int maxLength = config != null ? config.getInt("clans.max-description-length", 128) : 128;
         int maxLines = config != null ? config.getInt("clans.max-description-lines", 20) : 20;
         newDescription = StyleInput.miniToLegacy(newDescription);
         newDescription = newDescription.replace("\\n", "\n");
         if (newDescription.split("\n", -1).length > maxLines) {
            this.sendMessage(player, this.getMessage("settings.description.too-many-lines").replace("{max}", String.valueOf(maxLines)));
            return;
         }

         String plainDesc = PlainTextComponentSerializer.plainText().serialize(LegacyComponentSerializer.legacyAmpersand().deserialize(newDescription));
         String stripped = this.stripColorCodes(plainDesc).trim();
         int visibleLength = stripped.length();
         if (!allowColors) {
            newDescription = plainDesc;
         }

         if (visibleLength >= minLength && visibleLength <= maxLength) {
            ClanEconomyManager econ = this.module.getClanEconomyManager();
            boolean descChanged = !newDescription.equals(clan.getDescription());
            if (econ != null && descChanged && !econ.tryCharge(player, clan, (long)econ.getDescriptionCost())) {
               return;
            }

            clan.setDescription(newDescription);
            this.clanManager.saveClan(clan);
            this.sendMessage(player, this.getMessage("settings.description.changed"));
            if (econ != null && econ.isEnabled() && descChanged) {
               this.logPurchase(player, clan, "CHANGE_DESCRIPTION", "Изменение описания", (long)econ.getDescriptionCost());
            }
         } else {
            this.sendMessage(player, this.getMessage("settings.description.invalid-length").replace("{min}", String.valueOf(minLength)).replace("{max}", String.valueOf(maxLength)));
         }
      }

   }

   public void toggleTag(Player player) {
      Clan clan = this.clanManager.getPlayerClan(player.getUniqueId());
      if (clan == null) {
         this.sendMessage(player, this.getMessage("not-in-clan"));
      } else if (!this.roleManager.hasPermission(player.getUniqueId(), ClanPermission.CHANGE_TAG)) {
         this.sendMessage(player, this.getMessage("settings.tag.no-permission"));
      } else {
         FileConfiguration config = this.module.getConfig();
         if (config != null && config.getBoolean("clans.tag-activation.enabled", false)) {
            int minMembers = config.getInt("clans.tag-activation.min-members", 3);
            if (clan.getMemberCount() < minMembers) {
               if (clan.isTagEnabled()) {
                  clan.setTagEnabled(false);
                  this.clanManager.saveClan(clan);
                  this.sendMessage(player, this.getMessage("settings.tag.disabled"));
               } else {
                  this.sendMessage(player, this.getMessage("settings.tag.not-enough-members").replace("{min}", String.valueOf(minMembers)).replace("{current}", String.valueOf(clan.getMemberCount())));
               }

               return;
            }
         }

         boolean newState = !clan.isTagEnabled();
         clan.setTagEnabled(newState);
         this.clanManager.saveClan(clan);
         this.sendMessage(player, this.getMessage(newState ? "settings.tag.enabled" : "settings.tag.disabled"));
      }
   }

   public void toggleProfilePublic(Player player) {
      Clan clan = this.clanManager.getPlayerClan(player.getUniqueId());
      if (clan == null) {
         this.sendMessage(player, this.getMessage("not-in-clan"));
      } else if (!this.roleManager.hasPermission(player.getUniqueId(), ClanPermission.CHANGE_PRIVACY)) {
         this.sendMessage(player, this.getMessage("settings.privacy.no-permission"));
      } else {
         boolean newState = !clan.isProfilePublic();
         clan.setProfilePublic(newState);
         this.clanManager.saveClan(clan);
         this.sendMessage(player, this.getMessage(newState ? "settings.profile.public" : "settings.profile.private"));
      }

   }

   public void toggleFriendlyFire(Player player) {
      Clan clan = this.clanManager.getPlayerClan(player.getUniqueId());
      if (clan == null) {
         this.sendMessage(player, this.getMessage("not-in-clan"));
      } else if (!this.roleManager.isLeader(player.getUniqueId())) {
         this.sendMessage(player, this.getMessage("no-permission"));
      } else {
         boolean newState = !clan.isFriendlyFire();
         clan.setFriendlyFire(newState);
         this.clanManager.saveClan(clan);
         this.sendMessage(player, this.getMessage(newState ? "settings.friendly-fire.enabled" : "settings.friendly-fire.disabled"));
      }

   }

   public void changeBannerColor(Player player, String color) {
      Clan clan = this.clanManager.getPlayerClan(player.getUniqueId());
      if (clan == null) {
         this.sendMessage(player, this.getMessage("not-in-clan"));
      } else if (!this.roleManager.hasPermission(player.getUniqueId(), ClanPermission.CHANGE_TAG)) {
         this.sendMessage(player, this.getMessage("no-permission"));
      } else if (color == null || !color.equalsIgnoreCase(clan.getBannerColor())) {
         ClanEconomyManager econ = this.module.getClanEconomyManager();
         if (econ == null || econ.tryCharge(player, clan, (long)econ.getBannerColorCost())) {
            clan.setBannerColor(color);
            this.clanManager.saveClan(clan);
            this.sendMessage(player, this.getMessage("settings.banner.changed").replace("{color}", color));
            if (econ != null && econ.isEnabled()) {
               this.logPurchase(player, clan, "CHANGE_BANNER", "Цвет знамени → " + color, (long)econ.getBannerColorCost());
            }

         }
      }
   }

   public void setClanFlag(Player player) {
      Clan clan = this.clanManager.getPlayerClan(player.getUniqueId());
      if (clan == null) {
         this.sendMessage(player, this.getMessage("not-in-clan"));
      } else {
         ClanMember member = clan.getMember(player.getUniqueId());
         if (member == null) {
            this.sendMessage(player, this.getMessage("not-in-clan"));
         } else {
            String roleId = member.getRoleId();
            if (!roleId.equals(DefaultClanRole.LEADER.getId()) && !roleId.equals(DefaultClanRole.CO_LEADER.getId())) {
               this.sendMessage(player, this.getMessage("settings.flag.no-permission"));
            } else {
               ItemStack mainHandItem = player.getInventory().getItemInMainHand();
               if (mainHandItem != null && mainHandItem.getType() != Material.AIR) {
                  if (!mainHandItem.getType().name().endsWith("_BANNER")) {
                     this.sendMessage(player, this.getMessage("settings.flag.not-banner"));
                  } else {
                     String flagData = this.serializeBannerData(mainHandItem);
                     if (flagData != null && !flagData.isEmpty()) {
                        ClanEconomyManager econ = this.module.getClanEconomyManager();
                        if (econ == null || econ.tryCharge(player, clan, (long)econ.getCustomBannerCost())) {
                           clan.setFlagData(flagData);
                           this.clanManager.saveClan(clan);
                           this.sendMessage(player, this.getMessage("settings.flag.success"));
                           if (econ != null && econ.isEnabled()) {
                              this.logPurchase(player, clan, "SET_FLAG", "Установка кастомного знамени", (long)econ.getCustomBannerCost());
                           }

                        }
                     } else {
                        this.sendMessage(player, this.getMessage("settings.flag.failed"));
                     }
                  }
               } else {
                  this.sendMessage(player, this.getMessage("settings.flag.no-item"));
               }
            }
         }
      }
   }

   public void clearClanFlag(Player player) {
      Clan clan = this.clanManager.getPlayerClan(player.getUniqueId());
      if (clan == null) {
         this.sendMessage(player, this.getMessage("not-in-clan"));
      } else {
         ClanMember member = clan.getMember(player.getUniqueId());
         if (member == null) {
            this.sendMessage(player, this.getMessage("not-in-clan"));
         } else {
            String roleId = member.getRoleId();
            if (!roleId.equals(DefaultClanRole.LEADER.getId()) && !roleId.equals(DefaultClanRole.CO_LEADER.getId())) {
               this.sendMessage(player, this.getMessage("settings.flag.no-permission"));
            } else if (clan.getFlagData() != null && !clan.getFlagData().isEmpty()) {
               clan.setFlagData((String)null);
               this.clanManager.saveClan(clan);
               this.sendMessage(player, this.getMessage("settings.flag.cleared"));
            } else {
               this.sendMessage(player, this.getMessage("settings.flag.no-custom"));
            }
         }
      }
   }

   private String serializeBannerData(ItemStack banner) {
      if (banner != null && banner.getType().name().endsWith("_BANNER")) {
         StringBuilder data = new StringBuilder();
         String materialName = banner.getType().name();
         String baseColor = materialName.substring(0, materialName.indexOf("_BANNER"));
         data.append(baseColor);
         if (banner.hasItemMeta()) {
            ItemMeta var6 = banner.getItemMeta();
            if (var6 instanceof BannerMeta) {
               BannerMeta bannerMeta = (BannerMeta)var6;
               if (!bannerMeta.getPatterns().isEmpty()) {
                  data.append(";");
                  boolean first = true;

                  for(Pattern pattern : bannerMeta.getPatterns()) {
                     NamespacedKey patternKey = RegistryAccess.registryAccess().getRegistry(RegistryKey.BANNER_PATTERN).getKey(pattern.getPattern());
                     if (patternKey != null) {
                        if (!first) {
                           data.append(",");
                        }

                        data.append(patternKey.getKey()).append(":").append(pattern.getColor().name());
                        first = false;
                     }
                  }
               }
            }
         }

         return data.toString();
      } else {
         return null;
      }
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
      return text == null ? "" : text.replaceAll("(?<!&)(#[0-9a-fA-F]{6})", "&$1");
   }

   public void assignRole(Player player, String targetName, String roleId) {
      Clan clan = this.clanManager.getPlayerClan(player.getUniqueId());
      if (clan == null) {
         this.sendMessage(player, this.getMessage("not-in-clan"));
      } else if (!this.roleManager.hasPermission(player.getUniqueId(), ClanPermission.ASSIGN_ROLES)) {
         this.sendMessage(player, this.getMessage("no-permission"));
      } else {
         UUID targetId = this.resolveTargetId(targetName);
         if (targetId == null) {
            this.sendMessage(player, this.getMessage("player-not-found"));
            return;
         }

         if (!clan.hasMember(targetId)) {
            this.sendMessage(player, this.getMessage("player-not-in-clan"));
         } else {
            ClanRole newRole = clan.getRole(roleId);
            if (newRole == null) {
               this.sendMessage(player, this.getMessage("role-not-found"));
            } else {
               ClanRole assignerRole = this.roleManager.getMemberRole(player.getUniqueId());
               ClanMember targetMember = clan.getMember(targetId);
               ClanRole targetCurrentRole = targetMember != null ? clan.getRole(targetMember.getRoleId()) : null;
               if (assignerRole.getPriority() <= newRole.getPriority() && !clan.getLeaderId().equals(player.getUniqueId())) {
                  this.sendMessage(player, this.getMessage("cannot-assign-higher-role"));
               } else if (clan.getLeaderId().equals(targetId)) {
                  this.sendMessage(player, this.getMessage("cannot-change-leader-role"));
               } else if (targetCurrentRole != null && assignerRole.getPriority() <= targetCurrentRole.getPriority() && !clan.getLeaderId().equals(player.getUniqueId())) {
                  this.sendMessage(player, this.getMessage("cannot-assign-higher-role"));
               } else {
                  this.clanManager.updateMemberRole(targetId, roleId);
                  String targetNameResolved = this.resolveTargetName(targetId, targetName);
                  this.logRoleChange(player, clan, targetNameResolved, this.stripColorCodes(newRole.getDisplayName()));
                  this.sendMessage(player, this.getMessage("settings.roles.assigned").replace("{player}", targetNameResolved).replace("{role}", newRole.getDisplayName()));
                  Player target = Bukkit.getPlayer(targetId);
                  if (target != null && target.isOnline()) {
                     this.sendMessage(target, this.getMessage("settings.roles.your-role-changed").replace("{role}", newRole.getDisplayName()));
                  }
               }
            }
         }
      }

   }

   public void assignRoleById(Player player, UUID targetId, String roleId) {
      Clan clan = this.clanManager.getPlayerClan(player.getUniqueId());
      if (clan == null) {
         this.sendMessage(player, this.getMessage("not-in-clan"));
      } else if (!this.roleManager.hasPermission(player.getUniqueId(), ClanPermission.ASSIGN_ROLES)) {
         this.sendMessage(player, this.getMessage("no-permission"));
      } else if (!clan.hasMember(targetId)) {
         this.sendMessage(player, this.getMessage("player-not-in-clan"));
      } else {
         ClanRole newRole = clan.getRole(roleId);
         if (newRole == null) {
            this.sendMessage(player, this.getMessage("role-not-found"));
            return;
         }

         ClanRole assignerRole = this.roleManager.getMemberRole(player.getUniqueId());
         ClanMember targetMember = clan.getMember(targetId);
         ClanRole targetCurrentRole = targetMember != null ? clan.getRole(targetMember.getRoleId()) : null;
         if (assignerRole.getPriority() <= newRole.getPriority() && !clan.getLeaderId().equals(player.getUniqueId())) {
            this.sendMessage(player, this.getMessage("cannot-assign-higher-role"));
         } else if (clan.getLeaderId().equals(targetId)) {
            this.sendMessage(player, this.getMessage("cannot-change-leader-role"));
         } else if (targetCurrentRole != null && assignerRole.getPriority() <= targetCurrentRole.getPriority() && !clan.getLeaderId().equals(player.getUniqueId())) {
            this.sendMessage(player, this.getMessage("cannot-assign-higher-role"));
         } else {
            this.clanManager.updateMemberRole(targetId, roleId);
            String targetNameResolved = this.resolveTargetName(targetId, (String)null);
            this.logRoleChange(player, clan, targetNameResolved, this.stripColorCodes(newRole.getDisplayName()));
            this.sendMessage(player, this.getMessage("settings.roles.assigned").replace("{player}", targetNameResolved).replace("{role}", newRole.getDisplayName()));
            Player target = Bukkit.getPlayer(targetId);
            if (target != null && target.isOnline()) {
               this.sendMessage(target, this.getMessage("settings.roles.your-role-changed").replace("{role}", newRole.getDisplayName()));
            }
         }
      }

   }

   public void changeRoleByDelta(Player player, UUID targetId, int delta) {
      Clan clan = this.clanManager.getPlayerClan(player.getUniqueId());
      if (clan == null) {
         this.sendMessage(player, this.getMessage("not-in-clan"));
      } else if (!this.roleManager.hasPermission(player.getUniqueId(), ClanPermission.ASSIGN_ROLES)) {
         this.sendMessage(player, this.getMessage("no-permission"));
      } else if (!clan.hasMember(targetId)) {
         this.sendMessage(player, this.getMessage("player-not-in-clan"));
      } else if (clan.getLeaderId().equals(targetId)) {
         this.sendMessage(player, this.getMessage("cannot-change-leader-role"));
      } else {
         ClanMember targetMember = clan.getMember(targetId);
         if (targetMember != null) {
            String currentRoleId = targetMember.getRoleId();
            String[] roleProgression = new String[]{DefaultClanRole.MEMBER.getId(), DefaultClanRole.MODERATOR.getId(), DefaultClanRole.CO_LEADER.getId()};
            int currentIndex = -1;

            for(int i = 0; i < roleProgression.length; ++i) {
               if (roleProgression[i].equalsIgnoreCase(currentRoleId)) {
                  currentIndex = i;
                  break;
               }
            }

            if (currentIndex != -1) {
               int newIndex = currentIndex + delta;
               if (newIndex >= 0 && newIndex < roleProgression.length) {
                  String newRoleId = roleProgression[newIndex];
                  ClanRole newRole = clan.getRole(newRoleId);
                  if (newRole != null) {
                     ClanRole assignerRole = this.roleManager.getMemberRole(player.getUniqueId());
                     ClanRole targetCurrentRole = clan.getRole(currentRoleId);
                     boolean isLeader = clan.getLeaderId().equals(player.getUniqueId());
                     if (!isLeader && assignerRole.getPriority() <= newRole.getPriority()) {
                        this.sendMessage(player, this.getMessage("cannot-assign-higher-role"));
                     } else if (!isLeader && targetCurrentRole != null && assignerRole.getPriority() <= targetCurrentRole.getPriority()) {
                        this.sendMessage(player, this.getMessage("cannot-assign-higher-role"));
                     } else {
                        this.clanManager.updateMemberRole(targetId, newRoleId);
                        String targetName = Bukkit.getOfflinePlayer(targetId).getName();
                        if (targetName == null) {
                           targetName = "Unknown";
                        }

                        this.logRoleChange(player, clan, targetName, this.stripColorCodes(newRole.getDisplayName()));
                        this.sendMessage(player, this.getMessage("settings.roles.assigned").replace("{player}", targetName).replace("{role}", newRole.getDisplayName()));
                        Player target = Bukkit.getPlayer(targetId);
                        if (target != null && target.isOnline()) {
                           this.sendMessage(target, this.getMessage("settings.roles.your-role-changed").replace("{role}", newRole.getDisplayName()));
                        }

                     }
                  }
               }
            }
         }
      }
   }

   public void adminChangeRoleByDelta(Player admin, Clan clan, UUID targetId, int delta) {
      if (clan != null && clan.hasMember(targetId)) {
         if (!clan.getLeaderId().equals(targetId)) {
            ClanMember targetMember = clan.getMember(targetId);
            if (targetMember != null) {
               String currentRoleId = targetMember.getRoleId();
               String[] roleProgression = new String[]{DefaultClanRole.MEMBER.getId(), DefaultClanRole.MODERATOR.getId(), DefaultClanRole.CO_LEADER.getId()};
               int currentIndex = -1;

               for(int i = 0; i < roleProgression.length; ++i) {
                  if (roleProgression[i].equalsIgnoreCase(currentRoleId)) {
                     currentIndex = i;
                     break;
                  }
               }

               if (currentIndex != -1) {
                  int newIndex = currentIndex + delta;
                  if (newIndex >= 0 && newIndex < roleProgression.length) {
                     String newRoleId = roleProgression[newIndex];
                     ClanRole newRole = clan.getRole(newRoleId);
                     if (newRole != null) {
                        this.clanManager.updateMemberRole(targetId, newRoleId);
                        String targetName = Bukkit.getOfflinePlayer(targetId).getName();
                        if (targetName == null) {
                           targetName = "Unknown";
                        }

                        this.logRoleChange(admin, clan, targetName, this.stripColorCodes(newRole.getDisplayName()));
                        this.sendMessage(admin, this.getMessage("settings.roles.assigned").replace("{player}", targetName).replace("{role}", newRole.getDisplayName()));
                        Player target = Bukkit.getPlayer(targetId);
                        if (target != null && target.isOnline()) {
                           this.sendMessage(target, this.getMessage("settings.roles.your-role-changed").replace("{role}", newRole.getDisplayName()));
                        }

                     }
                  }
               }
            }
         }
      }
   }

   public void transferOwnership(Player player, String targetName) {
      Clan clan = this.clanManager.getPlayerClan(player.getUniqueId());
      if (clan == null) {
         this.sendMessage(player, this.getMessage("not-in-clan"));
      } else if (!clan.getLeaderId().equals(player.getUniqueId())) {
         this.sendMessage(player, this.getMessage("no-permission"));
      } else {
         UUID targetId = Bukkit.getOfflinePlayer(targetName).getUniqueId();
         if (!clan.hasMember(targetId)) {
            this.sendMessage(player, this.getMessage("player-not-in-clan"));
         } else {
            this.clanManager.updateMemberRole(player.getUniqueId(), DefaultClanRole.CO_LEADER.getId());
            this.clanManager.updateMemberRole(targetId, DefaultClanRole.LEADER.getId());
            clan.setLeaderId(targetId);
            this.clanManager.saveClan(clan);
            this.logTransfer(player, clan, this.resolveTargetName(targetId, targetName));
            this.sendMessage(player, this.getMessage("promote.success").replace("{player}", targetName));
            Player target = Bukkit.getPlayer(targetId);
            if (target != null && target.isOnline()) {
               this.sendMessage(target, this.getMessage("promote.received"));
            }

         }
      }
   }

   private UUID resolveTargetId(String targetName) {
      if (targetName != null && !targetName.isEmpty()) {
         try {
            return UUID.fromString(targetName);
         } catch (IllegalArgumentException var3) {
            Player target = Bukkit.getPlayer(targetName);
            return target != null ? target.getUniqueId() : Bukkit.getOfflinePlayer(targetName).getUniqueId();
         }
      } else {
         return null;
      }
   }

   private String resolveTargetName(UUID targetId, String fallbackName) {
      if (fallbackName != null && !fallbackName.isEmpty() && !fallbackName.equalsIgnoreCase(targetId.toString())) {
         return fallbackName;
      } else {
         String name = Bukkit.getOfflinePlayer(targetId).getName();
         return name != null ? name : targetId.toString();
      }
   }

   private String getMessage(String key) {
      FileConfiguration messages = this.module.getMessages();
      if (messages == null) {
         return "§cMessage not found: " + key;
      } else {
         String message = messages.getString(key, "§cMessage not found: " + key);
         String prefix = messages.getString("prefix", "");
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

   private void sendMessage(Player player, String message) {
      if (message != null && !message.isEmpty()) {
         String colored = this.module.getPlugin().applyColors(message);
         player.sendMessage(LegacyComponentSerializer.legacySection().deserialize(colored));
      }

   }

   private void logPurchase(Player actor, Clan clan, String actionCode, String detail, long cost) {
      if (cost > 0L) {
         ClanAuditLogger logger = this.module.getAuditLogger();
         if (logger != null) {
            logger.logPurchase(actor, clan, actionCode, detail, cost, clan.getBalance());
         }

      }
   }

   private void logRoleChange(Player actor, Clan clan, String targetName, String newRole) {
      ClanAuditLogger logger = this.module.getAuditLogger();
      if (logger != null) {
         logger.logRoleChange(actor, clan, targetName, newRole);
      }

   }

   private void logTransfer(Player actor, Clan clan, String newLeaderName) {
      ClanAuditLogger logger = this.module.getAuditLogger();
      if (logger != null) {
         logger.logTransfer(actor, clan, newLeaderName);
      }

   }

   private String getPrivacyName(ClanPrivacy privacy) {
      String var10000;
      switch (privacy) {
         case PUBLIC -> var10000 = "settings.privacy.public";
         case PRIVATE -> var10000 = "settings.privacy.private";
         case INVITE_ONLY -> var10000 = "settings.privacy.invite-only";
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      String key = var10000;
      return this.getMessage(key);
   }
}
