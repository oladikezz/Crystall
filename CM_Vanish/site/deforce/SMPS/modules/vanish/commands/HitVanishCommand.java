package site.deforce.SMPS.modules.vanish.commands;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import site.deforce.SMPS.modules.vanish.SM_Vanish;

public class HitVanishCommand implements CommandExecutor, TabCompleter {
   private final SM_Vanish module;

   public HitVanishCommand(SM_Vanish module) {
      super();
      this.module = module;
   }

   public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
      int paramOffset = 0;
      Player target;
      if (args.length > 0 && Bukkit.getPlayer(args[0]) != null && !this.isNumeric(args[0])) {
         if (!sender.hasPermission("smvanish.hitvanish.others")) {
            sender.sendMessage(this.module.formatMessage("&cНет разрешения на изменение режима другим игрокам!"));
            return true;
         }

         target = Bukkit.getPlayer(args[0]);
         paramOffset = 1;
      } else {
         if (!(sender instanceof Player)) {
            sender.sendMessage(this.module.formatMessage("&cУкажите игрока!"));
            return true;
         }

         Player player = (Player)sender;
         if (!player.hasPermission("smvanish.hitvanish")) {
            sender.sendMessage(this.module.formatMessage("&cYou do not have permission!"));
            return true;
         }

         target = (Player)sender;
      }

      double damageThreshold = 0.0;
      String particle = "";
      String sound = "";
      if (args.length > paramOffset) {
         try {
            damageThreshold = Double.parseDouble(args[paramOffset]);
         } catch (NumberFormatException var13) {
            sender.sendMessage(this.module.formatMessage("&cInvalid damage amount. Please provide a number."));
            return true;
         }
      }

      if (args.length > paramOffset + 1) {
         particle = args[paramOffset + 1];
      }

      if (args.length > paramOffset + 2) {
         sound = args[paramOffset + 2];
      }

      boolean isEnabled = this.module.toggleHitVanish(target, damageThreshold, particle, sound);
      if (isEnabled) {
         String msg = "&aHitVanish enabled";
         if (damageThreshold > 0.0) {
            msg = msg + " &ewith threshold &6" + damageThreshold;
         }

         if (!particle.isEmpty()) {
            msg = msg + " &eparticle &6" + particle;
         }

         if (!sound.isEmpty()) {
            msg = msg + " &esound &6" + sound;
         }

         if (!target.equals(sender)) {
            msg = msg + " &efor &6" + target.getName();
         }

         sender.sendMessage(this.module.formatMessage(msg + "&a!"));
      } else {
         String msg = "&cHitVanish disabled";
         if (!target.equals(sender)) {
            msg = msg + " &efor &6" + target.getName();
         }

         sender.sendMessage(this.module.formatMessage(msg + "!"));
      }

      return true;
   }

   private boolean isNumeric(String str) {
      try {
         Double.parseDouble(str);
         return true;
      } catch (NumberFormatException var3) {
         return false;
      }
   }

   public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
      if (!sender.hasPermission("smvanish.hitvanish")) {
         return new ArrayList();
      } else {
         List<String> suggestions = new ArrayList();
         if (args.length == 1) {
            if (sender.hasPermission("smvanish.hitvanish.others")) {
               String input = args[0].toLowerCase();

               for(Player player : Bukkit.getOnlinePlayers()) {
                  if (player.getName().toLowerCase().startsWith(input)) {
                     suggestions.add(player.getName());
                  }
               }
            }

            suggestions.add("1.0");
            suggestions.add("5.0");
            suggestions.add("10.0");
         } else {
            int paramOffset = Bukkit.getPlayer(args[0]) != null && !this.isNumeric(args[0]) ? 1 : 0;
            int paramIndex = args.length - 1 - paramOffset;
            if (paramIndex == 0) {
               suggestions.add("1.0");
               suggestions.add("5.0");
               suggestions.add("10.0");
            } else if (paramIndex == 1) {
               String input = args[args.length - 1].toLowerCase();

               for(Particle p : Particle.values()) {
                  if (p.name().toLowerCase().startsWith(input)) {
                     suggestions.add(p.name());
                  }
               }
            } else if (paramIndex == 2) {
               String input = args[args.length - 1].toLowerCase();

               for(Sound s : Sound.values()) {
                  if (s.name().toLowerCase().startsWith(input)) {
                     suggestions.add(s.name());
                  }
               }
            }
         }

         return suggestions;
      }
   }
}
