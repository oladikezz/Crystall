package net.schalker.SMPS.modules.essentials.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.DoAPI.core.debug.DebugSystem;
import net.schalker.SMPS.modules.essentials.BackTracker;
import net.schalker.SMPS.modules.essentials.EssentialsModule;

public class TpHereCommand extends ModuleCommand {
   private static final String ADMIN_PERMISSION = "smess.admin";
   private final EssentialsModule module;

   public TpHereCommand(DoAPI plugin, EssentialsModule module) {
      super(plugin);
      this.module = module;
   }

   public String getName() {
      return "tphere";
   }

   public String getPermission() {
      return "smess.tphere";
   }

   public String getDescription() {
      return "Teleport a player to you";
   }

   public String getUsage() {
      return "/tphere <player|@a>";
   }

   @Override
   public Collection<String> getAliases() {
      return java.util.List.of("s");
   }

   @Override
   public void execute(CommandSourceStack stack, String[] args) {
      CommandSender sender = stack.getSender();
      if (!(sender instanceof Player player)) {
         this.sendMessage(sender, this.module.getMessage("tphere.only-player", "&cOnly players can use this command."));
         return;
      }

      if (args.length == 0) {
         this.sendMessage(sender, this.module.getMessage("tphere.usage", "&cUsage: /tphere <player|@a>"));
         return;
      }

      if (args.length == 1 && args[0].equalsIgnoreCase("@a")) {
         if (!sender.hasPermission("smess.tphere.all")) {
            this.sendMessage(sender, this.module.getMessage("tphere.no-permission-all", "&cYou don't have permission for this command."));
            return;
         }
         this.teleportAllToPlayer(player, sender, args);
         return;
      }

      Player target = this.plugin.getServer().getPlayer(args[0]);
      if (target == null) {
         this.sendMessage(sender, this.module.getMessage("tphere.player-not-found", "&cPlayer {player} not found!").replace("{player}", args[0]));
         return;
      }
      if (target.equals(player)) {
         this.sendMessage(sender, this.module.getMessage("tphere.self", "&cYou can't teleport yourself to yourself!"));
         return;
      }

      this.plugin.getSchedulerManager().runEntityTask(player, "tphere-get-location", () -> {
         Location location = player.getLocation().clone();
         this.plugin.getSchedulerManager().runEntityTask(target, "tphere-teleport", () -> {
            BackTracker.record(this.plugin, target);
            target.teleportAsync(location);
            String targetMsg = this.module.getMessage("tphere.target-message", "&aYou were teleported to &e{player}&a.")
               .replace("{player}", player.getName());
            this.sendMessage(target, targetMsg);
         });
         String senderMsg = this.module.getMessage("tphere.sender-message", "&aPlayer &e{player}&a was teleported to you.")
            .replace("{player}", target.getName());
         this.sendMessage(sender, senderMsg);
         DebugSystem debugSystem = this.plugin.getDebugSystem();
         debugSystem.log("TpHereCommand", player.getName() + " teleported " + target.getName() + " to self");
      });

      this.highlightForAdmins(sender, args);
   }

   private void teleportAllToPlayer(Player player, CommandSender sender, String[] args) {
      this.plugin.getSchedulerManager().runEntityTask(player, "tphere-get-location", () -> {
         Location location = player.getLocation().clone();
         int count = 0;
         for (Player target : this.plugin.getServer().getOnlinePlayers()) {
            if (target.equals(player)) {
               continue;
            }
            count++;
            this.plugin.getSchedulerManager().runEntityTask(target, "tphere-teleport-all", () -> {
               BackTracker.record(this.plugin, target);
               target.teleportAsync(location);
               String targetMsg = this.module.getMessage("tphere.all-target-message", "&aYou were teleported to &e{player}&a.")
                  .replace("{player}", player.getName());
               this.sendMessage(target, targetMsg);
            });
         }

         if (count == 0) {
            this.sendMessage(sender, this.module.getMessage("tphere.all-none", "&cNo other players online!"));
         } else {
            String msg = this.module.getMessage("tphere.all-success", "&aTeleported &e{count}&a players to you.")
               .replace("{count}", Integer.toString(count));
            this.sendMessage(sender, msg);
         }

         DebugSystem debugSystem = this.plugin.getDebugSystem();
         debugSystem.log("TpHereCommand", player.getName() + " teleported all (" + count + ") to self");
      });

      this.highlightForAdmins(sender, args);
   }

   @Override
   public Collection<String> suggest(CommandSourceStack stack, String[] args) {
      if (args.length <= 1) {
         String input = args.length > 0 ? args[0].toLowerCase() : "";
         Collection<String> results = this.plugin.getServer().getOnlinePlayers().stream()
            .filter((player) -> !player.equals(stack.getSender()))
            .map(Player::getName)
            .filter((name) -> name.toLowerCase().startsWith(input))
            .collect(Collectors.toList());
         if ("@a".startsWith(input)) {
            results.add("@a");
         }
         return results;
      }
      return java.util.Collections.emptyList();
   }

   private void highlightForAdmins(CommandSender sender, String[] args) {
      if (!this.module.isAdminHighlightEnabled()) {
         return;
      }
      String commandLine = "/" + getName();
      if (args.length > 0) {
         commandLine += " " + String.join(" ", args);
      }
      String message = this.module.formatAdminLog(sender.getName(), commandLine);
      for (Player player : this.plugin.getServer().getOnlinePlayers()) {
         if (player.isOp() || player.hasPermission(ADMIN_PERMISSION)) {
            if (!this.module.isAdminLogEnabled(player.getUniqueId())) {
               continue;
            }
            String taskName = "cmd-highlight-" + player.getUniqueId() + "-" + UUID.randomUUID();
            this.plugin.getSchedulerManager().runEntityTask(player, taskName, () -> {
               if (player.isOnline()) {
                  player.sendMessage(message);
               }
            });
         }
      }

      if (!(sender instanceof Player)) {
         sender.sendMessage(message);
      }
   }

   private void sendMessage(CommandSender sender, String message) {
      if (message == null || message.isEmpty()) {
         return;
      }
      if (sender instanceof Player player) {
         this.plugin.getSchedulerManager().runEntityTask(player, "tphere-command-message", () -> {
            if (player.isOnline()) {
               player.sendMessage(message);
            }
         });
      } else {
         sender.sendMessage(message);
      }
   }
}

