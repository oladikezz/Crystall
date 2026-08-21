package site.deforce.SMPS.modules.vanish.commands;

import java.util.Collections;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import site.deforce.SMPS.modules.vanish.SM_Vanish;

public class ToggleTabVisibilityCommand implements CommandExecutor, TabCompleter {
   private final SM_Vanish module;

   public ToggleTabVisibilityCommand(SM_Vanish module) {
      super();
      this.module = module;
   }

   public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("smvanish.use")) {
            player.sendMessage(this.module.formatMessage("&cУ вас нет прав на использование этой команды!"));
            return true;
         } else {
            boolean lockedNow = this.module.toggleTabVisibilityLock(player);
            if (lockedNow) {
               if (this.module.isTabVisible(player)) {
                  player.sendMessage(this.module.formatMessage("&aTab visibility locked: you will stay visible in tab/playercount."));
               } else {
                  player.sendMessage(this.module.formatMessage("&eTab visibility locked: you will stay hidden in tab/playercount."));
               }
            } else {
               player.sendMessage(this.module.formatMessage("&7Tab visibility unlocked: now follows vanish state."));
            }

            return true;
         }
      } else {
         sender.sendMessage(this.module.formatMessage("&cThis command can only be used by players."));
         return true;
      }
   }

   public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
      return Collections.emptyList();
   }
}
