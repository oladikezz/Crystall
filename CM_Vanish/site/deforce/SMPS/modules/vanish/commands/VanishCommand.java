package site.deforce.SMPS.modules.vanish.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import site.deforce.SMPS.modules.vanish.SM_Vanish;

public class VanishCommand implements CommandExecutor, TabCompleter {
   private final SM_Vanish module;

   public VanishCommand(SM_Vanish module) {
      super();
      this.module = module;
   }

   public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
      this.module.log("Vanish command from " + sender.getName());
      Player target;
      if (args.length == 0) {
         if (!(sender instanceof Player)) {
            sender.sendMessage(this.module.formatMessage("&cУкажите игрока!"));
            return true;
         }

         Player player = (Player)sender;
         if (!player.hasPermission("smvanish.use")) {
            sender.sendMessage(this.module.formatMessage("&cУ вас нет прав на использование vanish!"));
            return true;
         }

         target = player;
      } else {
         if (!sender.hasPermission("smvanish.others")) {
            sender.sendMessage(this.module.formatMessage("&cУ вас нет прав скрывать других игроков!"));
            return true;
         }

         target = Bukkit.getPlayer(args[0]);
         if (target == null) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(args[0]);
            UUID offlineUuid = offlinePlayer.getUniqueId();
            if (!offlinePlayer.hasPlayedBefore() && !offlinePlayer.isOnline()) {
               sender.sendMessage(this.module.formatMessage("&cИгрок &e" + args[0] + " &cникогда не заходил на сервер!"));
               return true;
            }

            boolean newState = this.module.toggleOfflineVanishState(offlineUuid, offlinePlayer.getName() != null ? offlinePlayer.getName() : args[0]);
            String playerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : args[0];
            if (newState) {
               sender.sendMessage(this.module.formatMessage("&7Vanish for &e" + playerName + " &7(offline) &aENABLED"));
               sender.sendMessage(this.module.formatMessage("&7Игрок зайдёт в ванише."));
            } else {
               sender.sendMessage(this.module.formatMessage("&7Vanish for &e" + playerName + " &7(offline) &cDISABLED"));
            }

            return true;
         }
      }

      this.module.toggleVanish(target);
      boolean isVanished = this.module.isVanished(target);
      if (!target.equals(sender)) {
         if (isVanished) {
            sender.sendMessage(this.module.formatMessage("&7Vanish for &e" + target.getName() + " &aENABLED"));
         } else {
            sender.sendMessage(this.module.formatMessage("&7Vanish for &e" + target.getName() + " &cDISABLED"));
         }
      }

      return true;
   }

   public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
      if (sender.hasPermission("smvanish.others") && args.length <= 1) {
         String input = args.length > 0 ? args[0].toLowerCase() : "";
         List<String> suggestions = new ArrayList();

         for(Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase().startsWith(input)) {
               suggestions.add(player.getName());
            }
         }

         return suggestions;
      } else {
         return new ArrayList();
      }
   }
}
