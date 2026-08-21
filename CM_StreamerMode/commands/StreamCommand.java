package net.schalker.SMPS.modules.streamermode.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import net.schalker.SMPS.SMPS;
import net.schalker.SMPS.core.command.ModuleCommand;
import net.schalker.SMPS.modules.streamermode.StreamerModeModule;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StreamCommand extends ModuleCommand {
   private final StreamerModeModule module;

   public StreamCommand(SMPS plugin, StreamerModeModule module) {
      super(plugin);
      this.module = module;
   }

   @Override
   public String getName() {
      return "stream";
   }

   @Override
   public String getPermission() {
      return "smstream.use";
   }

   @Override
   public String getDescription() {
      return "Manage streamer mode";
   }

   @Override
   public String getUsage() {
      return "/stream [on|off|link|unlink|reload banned]";
   }

   @Override
   public Collection<String> getAliases() {
      return List.of("streame");
   }

   @Override
   public void execute(CommandSourceStack stack, String[] args) {
      CommandSender sender = stack.getSender();

      if (args.length == 0) {
         if (!(sender instanceof Player player)) {
            send(sender, this.module.getMessage("stream.usage", "&[SECONDARY]Usage: &[MAIN]/stream <on|off|link|unlink|reload banned>"));
            return;
         }
         this.module.openMenu(player);
         return;
      }

      String sub = args[0].toLowerCase();
      switch (sub) {
         case "on" -> handleSwitch(sender, args, true);
         case "off" -> handleSwitch(sender, args, false);
         case "link" -> handleLink(sender, args);
         case "unlink" -> handleUnlink(sender, args);
         case "reload" -> handleReload(sender, args);
         default -> send(sender, this.module.getMessage("stream.usage", "&[SECONDARY]Usage: &[MAIN]/stream <on|off|link|unlink|reload banned>"));
      }
   }

   private void handleReload(CommandSender sender, String[] args) {
      if (args.length != 2 || !"banned".equalsIgnoreCase(args[1])) {
         send(sender, this.module.getMessage(
            "stream.reload-banned-usage",
            "&[SECONDARY]Usage: &[MAIN]/stream reload banned"
         ));
         return;
      }

      if (!sender.hasPermission("smstream.admin") && !sender.hasPermission("smstream.reload.banned")) {
         send(sender, this.module.getMessage("stream.no-admin", "&[SECONDARY]No permission."));
         return;
      }

      StreamerModeModule.BannedWordsReloadResult result = this.module.reloadBannedWords();
      if (result.success()) {
         send(sender, this.module.getMessage(
            "stream.reload-banned-success",
            "&[SECONDARY]Reloaded &[MAIN]{count} &[SECONDARY]banned word(s) from &[MAIN]{file}&[SECONDARY]."
         ).replace("{count}", Integer.toString(result.count())).replace("{file}", result.fileName()));
      } else {
         send(sender, this.module.getMessage(
            "stream.reload-banned-failed",
            "&[SECONDARY]Could not reload banned words. The previous list is still active; check the server log."
         ));
      }
   }

   private void handleSwitch(CommandSender sender, String[] args, boolean enable) {
      Target target = resolveTarget(sender, args, 1);
      if (target == null) {
         return;
      }

      if (enable && this.module.getChannelLink(target.uuid()) == null) {
         if (target.self()) {
            send(sender, this.module.getMessage("stream.link-required", "&[SECONDARY]First link your channel: &[MAIN]/stream link <url>"));
         } else {
            send(sender, this.module.getMessage("stream.target-link-missing", "&[SECONDARY]Target has no linked channel."));
         }
         return;
      }

      this.module.setStreamEnabled(target.uuid(), target.name(), enable);

      if (target.onlinePlayer() != null && target.onlinePlayer().isOnline()) {
         String tKey = enable ? "stream.enabled-target" : "stream.disabled-target";
         send(target.onlinePlayer(), this.module.getMessage(tKey,
            enable ? "&[SECONDARY]Streamer mode enabled." : "&[SECONDARY]Streamer mode disabled."));
      }

      String sKey = enable ? "stream.enabled-sender" : "stream.disabled-sender";
      send(sender, this.module.getMessage(sKey,
         enable
            ? "&[SECONDARY]Streamer mode enabled for &[MAIN]{player}&[SECONDARY]."
            : "&[SECONDARY]Streamer mode disabled for &[MAIN]{player}&[SECONDARY].")
         .replace("{player}", target.name()));

      if (enable && target.onlinePlayer() != null) {
         this.module.broadcastStreamStart(target.onlinePlayer());
      }
   }

   private void handleLink(CommandSender sender, String[] args) {
      if (!(sender instanceof Player player)) {
         send(sender, this.module.getMessage("stream.link-only-player", "&[SECONDARY]Only player can link channel."));
         return;
      }

      if (args.length < 2) {
         send(sender, this.module.getMessage("stream.link-usage", "&[SECONDARY]Usage: &[MAIN]/stream link <url>"));
         return;
      }

      String url = args[1].trim();
      if (!(url.startsWith("http://") || url.startsWith("https://"))) {
         url = "https://" + url;
      }

      this.module.setChannelLink(player.getUniqueId(), url);
      send(sender, this.module.getMessage("stream.link-saved", "&[SECONDARY]Channel linked: &[MAIN]{link}").replace("{link}", url));
   }

   private void handleUnlink(CommandSender sender, String[] args) {
      if (args.length >= 2) {
         if (!sender.hasPermission("smstream.admin")) {
            send(sender, this.module.getMessage("stream.no-admin", "&[SECONDARY]No permission."));
            return;
         }

         Target target = resolveTargetByName(args[1]);
         if (target == null) {
            send(sender, this.module.getMessage("stream.player-not-found", "&[SECONDARY]Player &[MAIN]{player} &[SECONDARY]not found.").replace("{player}", args[1]));
            return;
         }

         this.module.unlinkChannel(target.uuid());
         this.module.setStreamEnabled(target.uuid(), target.name(), false);
         send(sender, this.module.getMessage("stream.unlinked-sender", "&[SECONDARY]Unlinked channel for &[MAIN]{player}&[SECONDARY].").replace("{player}", target.name()));
         if (target.onlinePlayer() != null) {
            send(target.onlinePlayer(), this.module.getMessage("stream.unlinked-target", "&[SECONDARY]Your channel link has been removed by admin."));
         }
         return;
      }

      if (!(sender instanceof Player player)) {
         send(sender, this.module.getMessage("stream.unlink-only-player", "&[SECONDARY]Only player can unlink self."));
         return;
      }

      this.module.unlinkChannel(player.getUniqueId());
      this.module.setStreamEnabled(player.getUniqueId(), player.getName(), false);
      send(sender, this.module.getMessage("stream.unlinked-self", "&[SECONDARY]Channel unlinked and streamer mode disabled."));
   }

   private Target resolveTarget(CommandSender sender, String[] args, int index) {
      if (args.length <= index) {
         if (!(sender instanceof Player player)) {
            send(sender, this.module.getMessage("stream.console-requires-target", "&[SECONDARY]Console must specify a player."));
            return null;
         }
         return new Target(player.getUniqueId(), player.getName(), player, true);
      }

      if (!sender.hasPermission("smstream.admin")) {
         send(sender, this.module.getMessage("stream.no-admin", "&[SECONDARY]No permission."));
         return null;
      }

      return resolveTargetByName(args[index]);
   }

   private Target resolveTargetByName(String name) {
      Player online = Bukkit.getPlayerExact(name);
      OfflinePlayer offline = online != null ? online : Bukkit.getOfflinePlayer(name);
      if (offline.getUniqueId() == null || (!offline.isOnline() && !offline.hasPlayedBefore())) {
         return null;
      }

      String targetName = offline.getName() != null ? offline.getName() : name;
      return new Target(offline.getUniqueId(), targetName, online, false);
   }

   @Override
   public Collection<String> suggest(CommandSourceStack stack, String[] args) {
      if (args.length == 1) {
         String input = args[0].toLowerCase();
         List<String> base = new ArrayList<>(List.of("on", "off", "link", "unlink"));
         if (stack.getSender().hasPermission("smstream.admin")
            || stack.getSender().hasPermission("smstream.reload.banned")) {
            base.add("reload");
         }
         return base.stream().filter(v -> v.startsWith(input)).collect(Collectors.toList());
      }

      if (args.length == 2 && "reload".equalsIgnoreCase(args[0])) {
         if (!stack.getSender().hasPermission("smstream.admin")
            && !stack.getSender().hasPermission("smstream.reload.banned")) {
            return List.of();
         }
         String input = args[1].toLowerCase();
         return "banned".startsWith(input) ? List.of("banned") : List.of();
      }

      if (args.length == 2 && ("on".equalsIgnoreCase(args[0]) || "off".equalsIgnoreCase(args[0]) || "unlink".equalsIgnoreCase(args[0]))) {
         if (!stack.getSender().hasPermission("smstream.admin")) {
            return List.of();
         }
         String input = args[1].toLowerCase();
         return this.plugin.getServer().getOnlinePlayers().stream()
            .map(Player::getName)
            .filter(v -> v.toLowerCase().startsWith(input))
            .collect(Collectors.toList());
      }

      return List.of();
   }

   private void send(CommandSender sender, String message) {
      if (message == null || message.isEmpty()) {
         return;
      }
      sender.sendMessage(message);
   }

   private record Target(UUID uuid, String name, Player onlinePlayer, boolean self) {}
}
