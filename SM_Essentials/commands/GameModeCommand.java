package net.schalker.SMPS.modules.essentials.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.DoAPI.core.debug.DebugSystem;
import net.schalker.SMPS.modules.essentials.EssentialsModule;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GameModeCommand extends ModuleCommand {
   private static final String ADMIN_PERMISSION = "smess.admin";
   private final EssentialsModule module;

   public GameModeCommand(DoAPI plugin, EssentialsModule module) {
      super(plugin);
      this.module = module;
   }

   public String getName() {
      return "gm";
   }

   public String getPermission() {
      return "smess.gm";
   }

   public String getDescription() {
      return "Change game mode";
   }

   public String getUsage() {
      return "/gm <0|1|2|3|survival|creative|adventure|spectator> [player]";
   }

   public Collection<String> getAliases() {
      return Arrays.asList("gamemode");
   }

   @Override
   public void execute(CommandSourceStack stack, String[] args) {
      var sender = stack.getSender();
      if (args.length == 0) {
         this.sendMessage(sender, this.module.getMessage("gm.usage", "&cUsage: /gm <mode> [player]"));
         this.sendMessage(sender, this.module.getMessage("gm.modes", "&7Modes: 0/survival, 1/creative, 2/adventure, 3/spectator"));
         return;
      }

      GameMode gameMode = this.parseGameMode(args[0]);
      if (gameMode == null) {
         this.sendMessage(sender, this.module.getMessage("gm.invalid-mode", "&cInvalid mode! Use 0-3 or mode name"));
         return;
      }

      if (sender instanceof Player && !this.hasModePermission(sender, gameMode)) {
         this.sendMessage(sender, this.module.getMessage("gm.no-permission-mode", "&[SECONDARY]У вас нет прав для этого режима игры!"));
         return;
      }

      Player target;
      if (args.length >= 2) {
         if (!sender.hasPermission("smess.gm.others")) {
            this.sendMessage(sender, this.module.getMessage("gm.no-permission-others", "&cYou don't have permission to change other players' modes!"));
            return;
         }

         target = this.plugin.getServer().getPlayer(args[1]);
         if (target == null) {
            this.sendMessage(sender, this.module.getMessage("gm.player-not-found", "&cPlayer {player} not found!").replace("{player}", args[1]));
            return;
         }
      } else {
         if (!(sender instanceof Player player)) {
            this.sendMessage(sender, this.module.getMessage("gm.only-player", "&cOnly players can use this command!"));
            return;
         }
         target = player;
      }

      String modeName = this.getGameModeName(gameMode);
      this.plugin.getSchedulerManager().runEntityTask(target, "gm-command", () -> {
         target.setGameMode(gameMode);
         String message = this.module.getMessage("gm.target-message", "&aGame mode changed to &6{mode}")
            .replace("{mode}", modeName);
         if (!message.isEmpty()) {
            target.sendMessage(message);
         }
      });

      if (!target.equals(sender)) {
         String response = this.module.getMessage("gm.sender-message", "&aPlayer &e{player}&a mode changed to &6{mode}")
            .replace("{player}", target.getName())
            .replace("{mode}", modeName);
         this.sendMessage(sender, response);
      }

      this.highlightForAdmins(sender, args);

      DebugSystem debugSystem = this.plugin.getDebugSystem();
      String senderName = sender.getName();
      debugSystem.log("GameModeCommand", senderName + " changed mode of " + target.getName() + " to " + modeName);
   }

   @Override
   public Collection<String> suggest(CommandSourceStack stack, String[] args) {
      var sender = stack.getSender();
      if (args.length <= 1) {
         String input = args.length > 0 ? args[0].toLowerCase() : "";
         List<String> modes = Arrays.asList("0", "1", "2", "3", "survival", "creative", "adventure", "spectator", "s", "c", "a", "sp");
         return modes.stream().filter(m -> m.startsWith(input)).collect(Collectors.toList());
      }

      if (args.length == 2 && sender.hasPermission("smess.gm.others")) {
         String input = args[1].toLowerCase();
         return this.plugin.getServer().getOnlinePlayers().stream()
            .map(Player::getName)
            .filter(name -> name.toLowerCase().startsWith(input))
            .collect(Collectors.toList());
      }

      return new ArrayList<>();
   }

   private GameMode parseGameMode(String arg) {
      switch (arg.toLowerCase()) {
         case "0":
         case "survival":
         case "s":
            return GameMode.SURVIVAL;
         case "1":
         case "creative":
         case "c":
            return GameMode.CREATIVE;
         case "2":
         case "adventure":
         case "a":
            return GameMode.ADVENTURE;
         case "3":
         case "spectator":
         case "sp":
            return GameMode.SPECTATOR;
         default:
            return null;
      }
   }

   private String getGameModeName(GameMode mode) {
      return switch (mode) {
         case SURVIVAL -> this.module.getMessage("gm.mode.survival", "Survival");
         case CREATIVE -> this.module.getMessage("gm.mode.creative", "Creative");
         case ADVENTURE -> this.module.getMessage("gm.mode.adventure", "Adventure");
         case SPECTATOR -> this.module.getMessage("gm.mode.spectator", "Spectator");
         default -> mode.name();
      };
   }

   private boolean hasModePermission(CommandSender sender, GameMode mode) {
      String permission = switch (mode) {
         case SURVIVAL -> "smess.gm.survival";
         case CREATIVE -> "smess.gm.creative";
         case ADVENTURE -> "smess.gm.adventure";
         case SPECTATOR -> "smess.gm.spectator";
         default -> null;
      };
      return permission == null || sender.hasPermission(permission);
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
         this.plugin.getSchedulerManager().runEntityTask(player, "gm-command-message", () -> {
            if (player.isOnline()) {
               player.sendMessage(message);
            }
         });
      } else {
         sender.sendMessage(message);
      }
   }
}

