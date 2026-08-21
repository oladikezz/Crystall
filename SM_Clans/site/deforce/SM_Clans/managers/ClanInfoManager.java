package site.deforce.SM_Clans.managers;

import java.text.SimpleDateFormat;
import java.util.Date;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.schalker.DoAPI.DoAPI;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import site.deforce.SM_Clans.SM_Clans;
import site.deforce.SM_Clans.models.Clan;
import site.deforce.SM_Clans.models.ClanPrivacy;
import site.deforce.SM_Clans.models.ClanRole;

public class ClanInfoManager {
   private final DoAPI plugin;
   private final SM_Clans module;
   private final ClanManager clanManager;
   private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");

   public ClanInfoManager(DoAPI plugin, SM_Clans module, ClanManager clanManager) {
      super();
      this.plugin = plugin;
      this.module = module;
      this.clanManager = clanManager;
   }

   public void showOwnClanInfo(Player player) {
      Clan clan = this.clanManager.getPlayerClan(player.getUniqueId());
      if (clan == null) {
         this.sendMessage(player, this.getMessage("not-in-clan"));
      } else {
         this.showClanInfoInternal(player, clan, true);
      }

   }

   public void showClanInfo(Player player, String tag) {
      Clan clan = this.clanManager.getClanByTag(tag);
      if (clan == null) {
         this.sendMessage(player, this.getMessage("clan-not-found"));
      } else if (clan.getPrivacy() == ClanPrivacy.PRIVATE && !clan.hasMember(player.getUniqueId())) {
         this.sendMessage(player, this.getMessage("clan-is-private"));
      } else {
         this.showClanInfoInternal(player, clan, false);
      }

   }

   private void showClanInfoInternal(Player player, Clan clan, boolean detailed) {
      DoAPI var10000 = this.plugin;
      String var10001 = this.normalizeHexColors(clan.getTag());
      String formattedTag = var10000.applyColors(var10001 + "&r");
      this.sendMessage(player, "");
      this.sendMessage(player, this.getMessage("info-header").replace("{tag}", formattedTag).replace("{name}", clan.getName()));
      this.sendMessage(player, "");
      this.sendMessage(player, this.getMessage("info-created").replace("{date}", this.dateFormat.format(new Date(clan.getCreatedAt()))));
      this.sendMessage(player, this.getMessage("info-members").replace("{current}", String.valueOf(clan.getMemberCount())).replace("{max}", String.valueOf(clan.getMaxMembers())));
      this.sendMessage(player, this.getMessage("info-privacy").replace("{privacy}", this.getPrivacyName(clan.getPrivacy())));
      this.sendTagActivationInfo(player, clan);
      if (detailed || clan.getPrivacy() != ClanPrivacy.PRIVATE) {
         this.sendMessage(player, "");
         this.sendMessage(player, this.getMessage("info-members-list"));
         clan.getMembers().values().stream().sorted((m1, m2) -> {
            ClanRole role1 = clan.getRole(m1.getRoleId());
            ClanRole role2 = clan.getRole(m2.getRoleId());
            return Integer.compare(role2.getPriority(), role1.getPriority());
         }).forEach((member) -> {
            ClanRole role = clan.getRole(member.getRoleId());
            String playerName = this.plugin.getServer().getOfflinePlayer(member.getPlayerId()).getName();
            this.sendMessage(player, this.getMessage("info-member-entry").replace("{player}", playerName != null ? playerName : "Unknown").replace("{role}", role.getDisplayName()));
         });
      }

      this.sendMessage(player, "");
   }

   private void sendTagActivationInfo(Player player, Clan clan) {
      FileConfiguration config = this.module.getConfig();
      if (config != null && config.getBoolean("clans.tag-activation.enabled", false)) {
         int required = Math.max(1, config.getInt("clans.tag-activation.min-members", 3));
         int current = clan.getMemberCount();
         if (current < required) {
            int needed = required - current;
            this.sendMessage(player, this.getMessage("info-tag-locked").replace("{current}", String.valueOf(current)).replace("{required}", String.valueOf(required)).replace("{needed}", String.valueOf(needed)));
         } else {
            this.sendMessage(player, this.getMessage("info-tag-unlocked").replace("{current}", String.valueOf(current)).replace("{required}", String.valueOf(required)));
         }

      }
   }

   private String getPrivacyName(ClanPrivacy privacy) {
      String var10000;
      switch (privacy) {
         case PUBLIC -> var10000 = this.getMessage("privacy-public");
         case PRIVATE -> var10000 = this.getMessage("privacy-private");
         case INVITE_ONLY -> var10000 = this.getMessage("privacy-invite-only");
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   private String normalizeHexColors(String text) {
      return text == null ? "" : text.replaceAll("(?<![&§])(#[0-9a-fA-F]{6})", "&$1");
   }

   private String getMessage(String key) {
      FileConfiguration config = this.module.getMessages();
      if (config == null) {
         return "§cMessage not found: " + key;
      } else {
         String message = config.getString(key, "§cMessage not found: " + key);
         String prefix = config.getString("prefix", "");
         String combined = message.replace("<prefix>", prefix);
         return this.module.getPlugin().applyColors(combined);
      }
   }

   private void sendMessage(Player player, String message) {
      if (message != null && !message.isEmpty()) {
         player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message.replace("§", "&")));
      }

   }
}
