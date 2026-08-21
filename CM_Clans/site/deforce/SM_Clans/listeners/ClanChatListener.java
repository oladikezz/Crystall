package site.deforce.SM_Clans.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.listener.BaseListener;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import site.deforce.SM_Clans.SM_Clans;
import site.deforce.SM_Clans.models.Clan;
import site.deforce.SM_Clans.models.ClanMember;

public class ClanChatListener extends BaseListener {
   private final SM_Clans module;

   public ClanChatListener(DoAPI plugin, SM_Clans module) {
      super(plugin);
      this.module = module;
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onChat(AsyncChatEvent event) {
      Player player = event.getPlayer();
      String plainMessage = PlainTextComponentSerializer.plainText().serialize(event.message());
      if (!plainMessage.isEmpty()) {
         if (this.module.handleConfirmChat(player, plainMessage)) {
            event.setCancelled(true);
         } else {
            String prefix = this.getChatPrefix();
            if (plainMessage.startsWith(prefix)) {
               event.setCancelled(true);
               this.plugin.getSchedulerManager().runEntityTaskLater(player, "clan-chat-handle", () -> {
                  if (player.isOnline()) {
                     Clan clan = this.module.getClanManager().getPlayerClan(player.getUniqueId());
                     if (clan == null) {
                        this.sendMessage(player, this.getMessage("not-in-clan"));
                     } else if (!clan.isChatEnabled()) {
                        this.sendMessage(player, this.getMessage("chat.disabled"));
                     } else {
                        String chatMessage = plainMessage.substring(prefix.length()).trim();
                        if (!chatMessage.isEmpty()) {
                           String var10000 = this.getMessage("chat.format");
                           String var10002 = this.normalizeHexColors(clan.getTag());
                           String format = var10000.replace("{tag}", var10002 + "&r").replace("{player}", player.getName()).replace("{message}", chatMessage);
                           String coloredFormat = this.module.getPlugin().applyColors(format);

                           try {
                              Component component = LegacyComponentSerializer.legacySection().deserialize(coloredFormat);

                              for(ClanMember member : clan.getMembers().values()) {
                                 Player memberPlayer = Bukkit.getPlayer(member.getPlayerId());
                                 if (memberPlayer != null) {
                                    this.plugin.getSchedulerManager().runEntityTask(memberPlayer, "clan-chat-deliver", () -> {
                                       if (memberPlayer.isOnline()) {
                                          memberPlayer.sendMessage(component);
                                       }

                                    });
                                 }
                              }

                              String spyFormatMessage = this.getMessage("chat.spy-format");
                              if (spyFormatMessage.startsWith("§cMessage not found")) {
                                 spyFormatMessage = "&c[SPY] " + this.getMessage("chat.format");
                              }

                              var10002 = this.normalizeHexColors(clan.getTag());
                              String spyFormat = spyFormatMessage.replace("{tag}", var10002 + "&r").replace("{player}", player.getName()).replace("{message}", chatMessage);
                              Component spyComponent = LegacyComponentSerializer.legacySection().deserialize(this.module.getPlugin().applyColors(spyFormat));

                              for(Player p : Bukkit.getOnlinePlayers()) {
                                 if (this.module.isSpying(p.getUniqueId()) && !clan.hasMember(p.getUniqueId())) {
                                    p.sendMessage(spyComponent);
                                 }
                              }
                           } catch (Exception var14) {
                              for(ClanMember member : clan.getMembers().values()) {
                                 Player memberPlayer = Bukkit.getPlayer(member.getPlayerId());
                                 if (memberPlayer != null) {
                                    String clanTag = clan.getTag() == null ? "" : this.normalizeHexColors(clan.getTag()).replace("&", "§");
                                    String fallback = this.module.getPlugin().applyColors("§8[" + clanTag + "§r§7] §7" + player.getName() + "§f: " + chatMessage);
                                    this.plugin.getSchedulerManager().runEntityTask(memberPlayer, "clan-chat-fallback", () -> {
                                       if (memberPlayer.isOnline()) {
                                          memberPlayer.sendMessage(fallback);
                                       }

                                    });
                                 }
                              }
                           }

                        }
                     }
                  }
               }, 1L);
            }
         }
      }
   }

   private String getChatPrefix() {
      FileConfiguration config = this.module.getConfig();
      return config != null ? config.getString("chat.prefix", "*") : "*";
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
         FileConfiguration mainConfig = this.module.getConfig();
         if (mainConfig != null) {
            boolean prefixEnabled = mainConfig.getBoolean("prefix.enabled", true);
            if (!prefixEnabled) {
               prefix = "";
            }
         }

         return message.replace("<prefix>", prefix);
      }
   }

   private void sendMessage(Player player, String message) {
      this.plugin.getSchedulerManager().runEntityTask(player, "clan-chat-message", () -> {
         try {
            String colored = this.module.getPlugin().applyColors(message);
            player.sendMessage(LegacyComponentSerializer.legacySection().deserialize(colored));
         } catch (Exception var4) {
            player.sendMessage(message);
         }

      });
   }
}
