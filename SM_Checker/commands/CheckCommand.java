package net.schalker.SMPS.modules.checker.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.SMPS.modules.checker.CheckerModule;
import net.schalker.SMPS.modules.checker.managers.CheckManager;

public class CheckCommand extends ModuleCommand {
   private static final String ADMIN_PERMISSION = "smchecker.command";
   private final CheckManager checkManager;
   private final CheckerModule module;

   public CheckCommand(DoAPI plugin, CheckManager checkManager, CheckerModule module) {
      super(plugin);
      this.checkManager = checkManager;
      this.module = module;
   }

   public String getName() {
      return "check";
   }

   public String getPermission() {
      return "smchecker.command";
   }

   public String getDescription() {
      return "Checker module made by MeXaNoBoP and updated by @deforce_ for checking users for malicious files";
   }

   public String getUsage() {
      return "/check <nick> [pass|failed|denied|rejected] [reason...]";
   }

   public List<String> getAliases() {
      return List.of("checker");
   }

   @Override
   public void execute(CommandSourceStack stack, String[] args) {
      var sender = stack.getSender();
      if (args.length == 0) {
         sender.sendMessage(this.module.getMessage("usage"));
         return;
      }

      // Syntax: /check <nick> [pass/failed/denied] [reason...]
      String targetName = args[0];

      // Prevent accidental legacy usage like /check confirm
      if (targetName.equalsIgnoreCase("confirm") || targetName.equalsIgnoreCase("deny")
         || targetName.equalsIgnoreCase("pass") || targetName.equalsIgnoreCase("failed")
         || targetName.equalsIgnoreCase("denied") || targetName.equalsIgnoreCase("rejected")) {
         sender.sendMessage(this.module.getMessage("usage"));
         return;
      }
      
      Player target = plugin.getServer().getPlayer(targetName);
      UUID targetId = null;
      if (target != null) {
          targetId = target.getUniqueId();
      } else {
          // Check if it's a known session by name
          var session = this.checkManager.getSessionByName(targetName);
          if (session != null) targetId = session.getTargetId();
      }

      if (args.length == 1) {
          if (!sender.hasPermission(this.getPermission())) {
             sender.sendMessage(this.module.getMessage("no-permission"));
             return;
          }
          if (target == null) {
             sender.sendMessage(this.module.getMessage("player-not-found").replace("{player}", targetName));
             return;
          }
          if (sender instanceof Player staff && staff.getUniqueId().equals(target.getUniqueId())) {
               sender.sendMessage(this.module.getMessage("self-check"));
               return;
          }
          if (target.hasPermission("smchecker.bypass")) {
             sender.sendMessage(this.module.getMessage("bypass").replace("{player}", target.getName()));
             return;
          }
          this.checkManager.startCheck(sender, target);
          this.highlightForAdmins(sender, args);
          return;
      }

      // /check <nick> <pass/failed/denied> [reason...]
      if (args.length >= 2) {
         if (!sender.hasPermission(this.getPermission())) {
            sender.sendMessage(this.module.getMessage("no-permission"));
            return;
         }

         String action = args[1].toLowerCase();
          String reason = args.length >= 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : null;
         if (targetId == null) {
             sender.sendMessage(this.module.getMessage("player-not-found").replace("{player}", targetName));
             return;
         }

          if (action.equals("pass")) {
            this.checkManager.confirmCheck(sender, targetId, reason);
            this.highlightForAdmins(sender, args);
         } else if (action.equals("failed")) {
            this.checkManager.failCheck(sender, targetId, reason);
            this.highlightForAdmins(sender, args);
         } else if (action.equals("denied") || action.equals("rejected")) {
            this.checkManager.denyCheck(sender, targetId, reason);
            this.highlightForAdmins(sender, args);
         } else {
             sender.sendMessage(this.module.getMessage("usage"));
         }
      }
   }

   @Override
   public Collection<String> suggest(CommandSourceStack stack, String[] args) {
      if (args.length <= 1) {
         String input = args.length > 0 ? args[0].toLowerCase() : "";
         List<String> names = new ArrayList<>();
         // Р В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљРЎвЂќР В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљРІР‚СњР В Р’В Р В Р вЂ№Р В Р вЂ Р В РІР‚С™Р РЋРІвЂћСћР В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљР’ВР В Р’В Р вЂ™Р’В Р В Р Р‹Р вЂ™Р’ВР В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљР’ВР В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В·Р В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В°Р В Р’В Р В Р вЂ№Р В Р вЂ Р В РІР‚С™Р вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљР’ВР В Р’В Р В Р вЂ№Р В Р’В Р В Р РЏ: Р В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљРІР‚СњР В Р’В Р В Р вЂ№Р В Р’В Р Р†Р вЂљРЎв„ўР В Р’В Р В Р вЂ№Р В Р’В Р В Р РЏР В Р’В Р вЂ™Р’В Р В Р Р‹Р вЂ™Р’ВР В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљРЎС›Р В Р’В Р вЂ™Р’В Р В Р вЂ Р Р†Р вЂљРЎвЂєР Р†Р вЂљРІР‚Сљ Р В Р’В Р вЂ™Р’В Р В РЎС›Р Р†Р вЂљР’ВР В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљРЎС›Р В Р’В Р В Р вЂ№Р В Р’В Р РЋРІР‚СљР В Р’В Р В Р вЂ№Р В Р вЂ Р В РІР‚С™Р РЋРІвЂћСћР В Р’В Р В Р вЂ№Р В Р Р‹Р Р†Р вЂљРЎС™Р В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљРІР‚Сњ Р В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В±Р В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’ВµР В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В· Р В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В±Р В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В»Р В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљРЎС›Р В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљРЎСљР В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљР’ВР В Р’В Р В Р вЂ№Р В Р’В Р Р†Р вЂљРЎв„ўР В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљРЎС›Р В Р’В Р вЂ™Р’В Р В Р’В Р Р†Р вЂљР’В Р В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљРЎСљР В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљР’В Р В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљРІР‚СњР В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљРЎС›Р В Р’В Р В Р вЂ№Р В Р вЂ Р В РІР‚С™Р РЋРІвЂћСћР В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљРЎС›Р В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљРЎСљР В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В° callGlobalSync,
         // Р В Р’В Р В Р вЂ№Р В Р вЂ Р В РІР‚С™Р РЋРІвЂћСћР В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В°Р В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљРЎСљ Р В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљРЎСљР В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В°Р В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљРЎСљ getOnlinePlayers() Р В Р’В Р вЂ™Р’В Р В Р’В Р Р†Р вЂљР’В  Paper/Folia Р В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљРІР‚СњР В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљРЎС›Р В Р’В Р В Р вЂ№Р В Р вЂ Р В РІР‚С™Р РЋРІвЂћСћР В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљРЎС›Р В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљРЎСљР В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљРЎС›Р В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В±Р В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’ВµР В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В·Р В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљРЎС›Р В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљРІР‚СњР В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В°Р В Р’В Р В Р вЂ№Р В Р’В Р РЋРІР‚СљР В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’ВµР В Р’В Р вЂ™Р’В Р В Р’В Р Р†Р вЂљР’В¦ Р В Р’В Р вЂ™Р’В Р В РЎС›Р Р†Р вЂљР’ВР В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В»Р В Р’В Р В Р вЂ№Р В Р’В Р В Р РЏ Р В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљР’ВР В Р’В Р В Р вЂ№Р В Р вЂ Р В РІР‚С™Р РЋРІвЂћСћР В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’ВµР В Р’В Р В Р вЂ№Р В Р’В Р Р†Р вЂљРЎв„ўР В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В°Р В Р’В Р В Р вЂ№Р В Р вЂ Р В РІР‚С™Р вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљР’ВР В Р’В Р вЂ™Р’В Р В Р Р‹Р Р†Р вЂљР’В
         for (Player player : this.plugin.getServer().getOnlinePlayers()) {
             if (player.getName().toLowerCase().startsWith(input)) {
                 names.add(player.getName());
             }
         }
         return names;
      }
      
      if (args.length == 2) {
         String input = args[1].toLowerCase();
         return List.of("pass", "failed", "denied", "rejected").stream()
            .filter(s -> s.startsWith(input))
            .collect(Collectors.toList());
      }

      return List.of();
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
}




