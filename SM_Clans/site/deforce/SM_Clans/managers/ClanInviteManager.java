package site.deforce.SM_Clans.managers;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.schalker.DoAPI.DoAPI;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import site.deforce.SM_Clans.SM_Clans;
import site.deforce.SM_Clans.logging.ClanAuditLogger;
import site.deforce.SM_Clans.models.Clan;
import site.deforce.SM_Clans.models.ClanMember;
import site.deforce.SM_Clans.models.ClanPermission;
import site.deforce.SM_Clans.models.ClanPrivacy;
import site.deforce.SM_Clans.models.ClanRole;
import site.deforce.SM_Clans.models.DefaultClanRole;

public class ClanInviteManager {
   private final DoAPI plugin;
   private final SM_Clans module;
   private final ClanManager clanManager;
   private final RoleManager roleManager;
   private final Map<UUID, Map<UUID, String>> invites;

   public ClanInviteManager(DoAPI plugin, SM_Clans module, ClanManager clanManager, RoleManager roleManager) {
      super();
      this.plugin = plugin;
      this.module = module;
      this.clanManager = clanManager;
      this.roleManager = roleManager;
      this.invites = new ConcurrentHashMap();
   }

   public void invitePlayer(Player inviter, String targetName) {
      Clan clan = this.clanManager.getPlayerClan(inviter.getUniqueId());
      if (clan == null) {
         this.sendMessage(inviter, this.getMessage("not-in-clan"));
      } else if (!this.roleManager.hasPermission(inviter.getUniqueId(), ClanPermission.INVITE_MEMBERS)) {
         this.sendMessage(inviter, this.getMessage("no-permission-invite"));
      } else {
         Player target = Bukkit.getPlayer(targetName);
         if (target != null && target.isOnline()) {
            if (this.clanManager.hasPlayerClan(target.getUniqueId())) {
               this.sendMessage(inviter, this.getMessage("player-already-in-clan"));
            } else if (clan.isFull()) {
               this.sendMessage(inviter, this.getMessage("clan-full"));
            } else {
               ((Map)this.invites.computeIfAbsent(target.getUniqueId(), (k) -> new ConcurrentHashMap())).put(inviter.getUniqueId(), clan.getClanId());
               String msg = this.getMessage("invite-sent");
               if (msg.contains("Message not found")) {
                  msg = this.module.getPlugin().applyColors("<prefix>&aПриглашение отправлено игроку &f{player}&a.");
               }

               this.sendMessage(inviter, msg.replace("{player}", target.getName()));
               this.sendClickableInvite(target, inviter.getName(), clan);
               this.plugin.getSchedulerManager().runTaskLater("remove-invite-" + String.valueOf(target.getUniqueId()), () -> this.removeInvite(target.getUniqueId(), inviter.getUniqueId()), 2400L);
            }
         } else {
            this.sendMessage(inviter, this.getMessage("player-not-found"));
         }
      }

   }

   public void acceptInvite(Player player, String inviterName) {
      Player inviter = Bukkit.getPlayer(inviterName);
      if (inviter == null) {
         this.sendMessage(player, this.getMessage("player-not-found"));
      } else {
         Map<UUID, String> playerInvites = (Map)this.invites.get(player.getUniqueId());
         if (playerInvites != null && playerInvites.containsKey(inviter.getUniqueId())) {
            String clanId = (String)playerInvites.get(inviter.getUniqueId());
            Clan clan = this.clanManager.getClan(clanId);
            if (clan == null) {
               this.sendMessage(player, this.getMessage("clan-not-found"));
               this.removeInvite(player.getUniqueId(), inviter.getUniqueId());
            } else if (clan.isFull()) {
               this.sendMessage(player, this.getMessage("clan-full"));
               this.removeInvite(player.getUniqueId(), inviter.getUniqueId());
            } else {
               String safeTag = this.escapeLegacyCodes(clan.getTag());

               try {
                  this.clanManager.addMember(clanId, player.getUniqueId(), DefaultClanRole.MEMBER.getId());
                  if (this.auditLogger() != null) {
                     this.auditLogger().logJoin(player, clan);
                  }

                  this.sendMessage(player, this.getMessage("joined-clan").replace("{name}", this.escapeLegacyCodes(clan.getName())).replace("{tag}", safeTag));
                  this.notifyClanMembers(clan, this.getMessage("member-joined").replace("{player}", player.getName()));
                  if (this.shouldBroadcast("player-joined")) {
                     this.broadcastClanEvent("broadcast.player-joined", "{player}", player.getName(), "{tag}", safeTag);
                  }

                  this.removeInvite(player.getUniqueId(), inviter.getUniqueId());
               } catch (Exception exception) {
                  this.sendMessage(player, this.getMessage("join-error"));
                  this.plugin.getDebugSystem().logError("Failed to add member to clan", exception);
               }
            }
         } else {
            this.sendMessage(player, this.getMessage("no-invite-from-player"));
         }
      }

   }

   public void denyInvite(Player player, String inviterName) {
      Player inviter = Bukkit.getPlayer(inviterName);
      if (inviter == null) {
         this.sendMessage(player, this.getMessage("player-not-found"));
      } else if (this.removeInvite(player.getUniqueId(), inviter.getUniqueId())) {
         this.sendMessage(player, this.getMessage("invite-denied"));
         this.sendMessage(inviter, this.getMessage("invite-was-denied").replace("{player}", player.getName()));
      } else {
         this.sendMessage(player, this.getMessage("no-invite-from-player"));
      }

   }

   public void kickPlayer(Player kicker, String targetName) {
      Clan clan = this.clanManager.getPlayerClan(kicker.getUniqueId());
      if (clan == null) {
         this.sendMessage(kicker, this.getMessage("not-in-clan"));
      } else if (!this.roleManager.hasPermission(kicker.getUniqueId(), ClanPermission.KICK_MEMBERS)) {
         this.sendMessage(kicker, this.getMessage("no-permission-kick"));
      } else {
         Player target = Bukkit.getPlayer(targetName);
         UUID targetId = target != null ? target.getUniqueId() : Bukkit.getOfflinePlayer(targetName).getUniqueId();
         if (!clan.hasMember(targetId)) {
            this.sendMessage(kicker, this.getMessage("player-not-in-clan"));
         } else if (clan.getLeaderId().equals(targetId)) {
            this.sendMessage(kicker, this.getMessage("cannot-kick-leader"));
         } else {
            ClanMember kickerMember = clan.getMember(kicker.getUniqueId());
            ClanMember targetMember = clan.getMember(targetId);
            ClanRole kickerRole = clan.getRole(kickerMember.getRoleId());
            ClanRole targetRole = clan.getRole(targetMember.getRoleId());
            if (kickerRole.getPriority() <= targetRole.getPriority()) {
               this.sendMessage(kicker, this.getMessage("cannot-kick-higher-rank"));
            } else {
               String safeTag = this.escapeLegacyCodes(clan.getTag());

               try {
                  this.clanManager.removeMember(targetId);
                  if (this.auditLogger() != null) {
                     this.auditLogger().logKick(kicker, clan, targetName);
                  }

                  this.sendMessage(kicker, this.getMessage("player-kicked").replace("{player}", targetName));
                  if (target != null && target.isOnline()) {
                     this.sendMessage(target, this.getMessage("you-were-kicked").replace("{clan}", clan.getName()));
                  }

                  this.notifyClanMembers(clan, this.getMessage("member-was-kicked").replace("{player}", targetName).replace("{kicker}", kicker.getName()));
                  if (this.shouldBroadcast("player-kicked")) {
                     this.broadcastClanEvent("broadcast.player-kicked", "{player}", targetName, "{tag}", safeTag);
                  }
               } catch (Exception exception) {
                  this.sendMessage(kicker, this.getMessage("kick-error"));
                  this.plugin.getDebugSystem().logError("Failed to kick player", exception);
               }
            }
         }
      }

   }

   public void adminKickPlayer(Player admin, Clan clan, String targetName) {
      if (clan != null) {
         Player target = Bukkit.getPlayer(targetName);
         UUID targetId = target != null ? target.getUniqueId() : Bukkit.getOfflinePlayer(targetName).getUniqueId();
         if (!clan.hasMember(targetId)) {
            this.sendMessage(admin, this.getMessage("player-not-in-clan"));
         } else if (clan.getLeaderId().equals(targetId)) {
            this.sendMessage(admin, this.getMessage("cannot-kick-leader"));
         } else {
            try {
               this.clanManager.removeMember(targetId);
               if (this.auditLogger() != null) {
                  this.auditLogger().logKick(admin, clan, targetName);
               }

               this.sendMessage(admin, this.getMessage("player-kicked").replace("{player}", targetName));
               if (target != null && target.isOnline()) {
                  this.sendMessage(target, this.getMessage("you-were-kicked").replace("{clan}", clan.getName()));
               }

               this.notifyClanMembers(clan, this.getMessage("member-was-kicked").replace("{player}", targetName).replace("{kicker}", admin.getName()));
            } catch (Exception exception) {
               this.sendMessage(admin, this.getMessage("kick-error"));
               this.plugin.getDebugSystem().logError("Admin failed to kick player", exception);
            }

         }
      }
   }

   public void leaveClan(Player player) {
      Clan clan = this.clanManager.getPlayerClan(player.getUniqueId());
      if (clan == null) {
         this.sendMessage(player, this.getMessage("not-in-clan"));
      } else if (clan.getLeaderId().equals(player.getUniqueId())) {
         this.sendMessage(player, this.getMessage("leader-cannot-leave"));
      } else {
         String safeTag = this.escapeLegacyCodes(clan.getTag());

         try {
            this.clanManager.removeMember(player.getUniqueId());
            if (this.auditLogger() != null) {
               this.auditLogger().logLeave(player, clan);
            }

            this.sendMessage(player, this.getMessage("you-left-clan").replace("{player}", player.getName()).replace("{clan}", clan.getName()));
            this.notifyClanMembers(clan, this.getMessage("member-left").replace("{player}", player.getName()));
            if (this.shouldBroadcast("player-left")) {
               this.broadcastClanEvent("broadcast.player-left", "{player}", player.getName(), "{tag}", safeTag);
            }
         } catch (Exception exception) {
            this.sendMessage(player, this.getMessage("leave-error"));
            this.plugin.getDebugSystem().logError("Failed to leave clan", exception);
         }
      }

   }

   public void disbandClan(Player player) {
      Clan clan = this.clanManager.getPlayerClan(player.getUniqueId());
      if (clan == null) {
         this.sendMessage(player, this.getMessage("not-in-clan"));
      } else if (!clan.getLeaderId().equals(player.getUniqueId())) {
         this.sendMessage(player, this.getMessage("only-leader-can-disband"));
      } else {
         String safeTag = this.escapeLegacyCodes(clan.getTag());
         String safeName = this.escapeLegacyCodes(clan.getName());

         for(UUID memberId : clan.getMembers().keySet()) {
            if (!memberId.equals(player.getUniqueId())) {
               Player member = Bukkit.getPlayer(memberId);
               if (member != null && member.isOnline()) {
                  this.sendMessage(member, this.getMessage("clan-disbanded").replace("{clan}", clan.getName()));
               }
            }
         }

         if (this.auditLogger() != null) {
            this.auditLogger().logPlayerDisband(player, clan);
         }

         this.clanManager.disbandClan(clan.getClanId());
         this.sendMessage(player, this.getMessage("clan-disbanded-success").replace("{clan}", clan.getName()));
         if (this.shouldBroadcast("clan-disbanded")) {
            this.broadcastClanEvent("broadcast.clan-disbanded", "{tag}", safeTag, "{name}", safeName);
         }
      }

   }

   public void joinPublicClan(Player player, String clanTag) {
      if (this.clanManager.hasPlayerClan(player.getUniqueId())) {
         this.sendMessage(player, this.getMessage("already-in-clan"));
      } else {
         Clan clan = this.clanManager.getClanByTag(clanTag.trim());
         if (clan == null) {
            this.sendMessage(player, this.getMessage("clan-not-found"));
         } else if (clan.getPrivacy() != ClanPrivacy.PUBLIC) {
            this.sendMessage(player, this.getMessage("clan-is-private"));
         } else if (clan.isFull()) {
            this.sendMessage(player, this.getMessage("clan-full"));
         } else {
            String safeTag = this.escapeLegacyCodes(clan.getTag());

            try {
               this.clanManager.addMember(clan.getClanId(), player.getUniqueId(), DefaultClanRole.MEMBER.getId());
               if (this.auditLogger() != null) {
                  this.auditLogger().logJoin(player, clan);
               }

               this.sendMessage(player, this.getMessage("joined-clan").replace("{name}", this.escapeLegacyCodes(clan.getName())).replace("{tag}", safeTag));
               this.notifyClanMembers(clan, this.getMessage("member-joined").replace("{player}", player.getName()));
               if (this.shouldBroadcast("player-joined")) {
                  this.broadcastClanEvent("broadcast.player-joined", "{player}", player.getName(), "{tag}", safeTag);
               }
            } catch (Exception exception) {
               this.sendMessage(player, this.getMessage("join-error"));
               this.plugin.getDebugSystem().logError("Failed to add member to clan", exception);
            }
         }
      }

   }

   private ClanAuditLogger auditLogger() {
      return this.module.getAuditLogger();
   }

   private boolean removeInvite(UUID playerId, UUID inviterId) {
      Map<UUID, String> playerInvites = (Map)this.invites.get(playerId);
      if (playerInvites != null) {
         boolean removed = playerInvites.remove(inviterId) != null;
         if (playerInvites.isEmpty()) {
            this.invites.remove(playerId);
         }

         return removed;
      } else {
         return false;
      }
   }

   public void clearPlayerInvites(UUID playerId) {
      this.invites.remove(playerId);
   }

   private void notifyClanMembers(Clan clan, String message) {
      for(UUID memberId : clan.getMembers().keySet()) {
         Player member = Bukkit.getPlayer(memberId);
         if (member != null && member.isOnline()) {
            this.sendMessage(member, message);
         }
      }

   }

   private String getMessage(String key) {
      FileConfiguration config = this.module.getMessages();
      if (config == null) {
         return "§cMessage not found: " + key;
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
         return this.plugin.applyColors(combined);
      }
   }

   private String getPrefix() {
      FileConfiguration config = this.module.getMessages();
      String prefix = config != null ? config.getString("prefix", "") : "";
      FileConfiguration mainConfig = this.module.getConfig();
      if (mainConfig != null) {
         boolean prefixEnabled = mainConfig.getBoolean("prefix.enabled", true);
         if (!prefixEnabled) {
            return "";
         }
      }

      return this.plugin.applyColors(prefix);
   }

   private Component deserialize(String text) {
      return LegacyComponentSerializer.legacyAmpersand().deserialize(text.replace("§", "&"));
   }

   private void sendClickableInvite(Player target, String inviterName, Clan clan) {
      String prefix = this.getPrefix();
      String mainTemplate = this.getMessage("invite-received-click");
      Component mainMessage = this.deserialize(mainTemplate.replace("<prefix>", prefix).replace("{player}", inviterName).replace("{tag}", this.escapeLegacyCodes(clan.getTag())).replace("{name}", this.escapeLegacyCodes(clan.getName())));
      Component acceptBtn = this.deserialize(this.getMessage("invite-accept-button")).clickEvent(ClickEvent.runCommand("/clan accept " + inviterName)).hoverEvent(HoverEvent.showText(this.deserialize(this.getMessage("invite-accept-hover"))));
      Component denyBtn = this.deserialize(this.getMessage("invite-deny-button")).clickEvent(ClickEvent.runCommand("/clan deny " + inviterName)).hoverEvent(HoverEvent.showText(this.deserialize(this.getMessage("invite-deny-hover"))));
      this.plugin.getSchedulerManager().runEntityTask(target, "clan-invite-message", () -> {
         if (target.isOnline()) {
            target.sendMessage(mainMessage);
            target.sendMessage(((TextComponent)((TextComponent)Component.text("").append(acceptBtn)).append(Component.text("  "))).append(denyBtn));
         }

      });
   }

   private String escapeLegacyCodes(String text) {
      if (text == null) {
         return "";
      } else {
         String normalized = text.replaceAll("(?<![&§])(#[0-9a-fA-F]{6})", "&$1");
         return normalized.replace("§", "&") + "&r";
      }
   }

   private void sendMessage(Player player, String message) {
      if (message != null && !message.isEmpty()) {
         this.plugin.getSchedulerManager().runEntityTask(player, "clan-invite-send", () -> {
            if (player.isOnline()) {
               try {
                  String colored = this.module.getPlugin().applyColors(message);
                  player.sendMessage(LegacyComponentSerializer.legacySection().deserialize(colored));
               } catch (Exception var4) {
                  player.sendMessage(message);
               }
            }

         });
      }

   }

   public void broadcastClanEvent(String messageKey, String... replacements) {
      String message = this.getMessage(messageKey);

      for(int i = 0; i < replacements.length - 1; i += 2) {
         message = message.replace(replacements[i], replacements[i + 1]);
      }

      Component component = this.deserialize(message);

      for(Player player : Bukkit.getOnlinePlayers()) {
         this.plugin.getSchedulerManager().runEntityTask(player, "clan-broadcast", () -> {
            if (player.isOnline()) {
               player.sendMessage(component);
            }

         });
      }

   }

   private boolean shouldBroadcast(String eventType) {
      FileConfiguration config = this.module.getConfig();
      return config == null || config.getBoolean("broadcast." + eventType, true);
   }
}
