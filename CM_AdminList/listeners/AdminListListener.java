package net.schalker.SMPS.modules.adminlist.listeners;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.listener.BaseListener;
import net.schalker.SMPS.modules.adminlist.AdminListModule;
import net.schalker.SMPS.modules.adminlist.AdminListWebhook;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class AdminListListener extends BaseListener {
   private final AdminListModule module;
   private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("#([0-9a-fA-F]{6})");
   private static final Pattern LEGACY_COLOR_PATTERN = Pattern.compile("§([0-9a-fk-or])");

   public AdminListListener(DoAPI plugin, AdminListModule module) {
      super(plugin);
      this.module = module;
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      if (!player.hasPermission(this.module.getPermissionNode())) {
         return;
      }
      this.sendOnlineListAsync(this.module.getJoinActionText(), player.getName(), null, true);
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onQuit(PlayerQuitEvent event) {
      Player player = event.getPlayer();
      if (!player.hasPermission(this.module.getPermissionNode())) {
         return;
      }
      this.sendOnlineListAsync(this.module.getQuitActionText(), player.getName(), player.getUniqueId(), false);
   }

   private void sendOnlineListAsync(String action, String triggerPlayer, UUID excludePlayerId, boolean isJoin) {
      List<AdminListWebhook.OnlineEntry> onlineEntries = new ArrayList<>();

      for (Player online : Bukkit.getOnlinePlayers()) {
         if (excludePlayerId != null && excludePlayerId.equals(online.getUniqueId())) {
            continue;
         }
         boolean isAdmin = online.hasPermission(this.module.getPermissionNode());
         Color prefixColor = isAdmin ? this.getLuckPermsGroupColor(online) : null;
         onlineEntries.add(new AdminListWebhook.OnlineEntry(
            online.getName(),
            online.getUniqueId().toString(),
            Math.max(0, online.getPing()),
            isAdmin,
            prefixColor
         ));
      }

      onlineEntries.sort((a, b) -> {
         if (a.isAdmin() != b.isAdmin()) {
            return a.isAdmin() ? -1 : 1;
         }
         return String.CASE_INSENSITIVE_ORDER.compare(a.name(), b.name());
      });

      this.plugin.getSchedulerManager().runAsync("adminlist-webhook-send", () -> {
         this.module.getWebhook().sendAdminListEmbed(
            this.module,
            action,
            triggerPlayer,
            onlineEntries,
            Bukkit.getMaxPlayers(),
            isJoin
         );
      });
   }

   private Color getLuckPermsGroupColor(Player player) {
      try {
         LuckPerms luckPerms = LuckPermsProvider.get();
         User user = luckPerms.getUserManager().getUser(player.getUniqueId());
         if (user == null) {
            return null;
         }

         String primaryGroupName = user.getPrimaryGroup();
         Group group = luckPerms.getGroupManager().getGroup(primaryGroupName);
         if (group == null) {
            return null;
         }

         String prefix = group.getCachedData().getMetaData().getPrefix();
         if (prefix == null || prefix.isEmpty()) {
            return null;
         }

         return this.parseColorFromPrefix(prefix);
      } catch (Exception e) {
         return null;
      }
   }

   private Color parseColorFromPrefix(String prefix) {
      Matcher hexMatcher = HEX_COLOR_PATTERN.matcher(prefix);
      if (hexMatcher.find()) {
         try {
            return Color.decode("#" + hexMatcher.group(1));
         } catch (NumberFormatException ignored) {
         }
      }

      Matcher legacyMatcher = LEGACY_COLOR_PATTERN.matcher(prefix);
      String lastColorCode = null;
      while (legacyMatcher.find()) {
         String code = legacyMatcher.group(1).toLowerCase();
         if (isColorCode(code)) {
            lastColorCode = code;
         }
      }

      if (lastColorCode != null) {
         return legacyColorToAwt(lastColorCode);
      }

      return null;
   }

   private static boolean isColorCode(String code) {
      return "0123456789abcdef".contains(code);
   }

   private static Color legacyColorToAwt(String code) {
      return switch (code) {
         case "0" -> new Color(0, 0, 0);
         case "1" -> new Color(0, 0, 170);
         case "2" -> new Color(0, 170, 0);
         case "3" -> new Color(0, 170, 170);
         case "4" -> new Color(170, 0, 0);
         case "5" -> new Color(170, 0, 170);
         case "6" -> new Color(255, 170, 0);
         case "7" -> new Color(170, 170, 170);
         case "8" -> new Color(85, 85, 85);
         case "9" -> new Color(85, 85, 255);
         case "a" -> new Color(85, 255, 85);
         case "b" -> new Color(85, 255, 255);
         case "c" -> new Color(255, 85, 85);
         case "d" -> new Color(255, 85, 255);
         case "e" -> new Color(255, 255, 85);
         case "f" -> new Color(255, 255, 255);
         default -> null;
      };
   }
}
