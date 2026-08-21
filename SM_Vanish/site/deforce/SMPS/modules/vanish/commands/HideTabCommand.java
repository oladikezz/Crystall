package site.deforce.SMPS.modules.vanish.commands;

import java.util.Arrays;
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

public class HideTabCommand implements CommandExecutor, TabCompleter {
   private final SM_Vanish module;

   public HideTabCommand(SM_Vanish module) {
      super();
      this.module = module;
   }

   public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
      if (!(sender instanceof Player player)) {
         sender.sendMessage(this.module.formatMessage("&cThis command can only be used by players."));
         return true;
      } else if (!player.hasPermission("smvanish.use")) {
         player.sendMessage(this.module.formatMessage("&cУ вас нет прав на использование этой команды!"));
         return true;
      } else {
         String mode = args.length > 0 ? args[0].toLowerCase() : "toggle";
         String usedLabel = label.toLowerCase();
         if (usedLabel.equals("hidetab")) {
            this.toggleTab(player);
            return true;
         } else if (usedLabel.equals("hidenametag")) {
            this.toggleNameTag(player);
            return true;
         } else {
            switch (mode) {
               case "tab":
                  this.toggleTab(player);
                  break;
               case "nametag":
                  this.toggleNameTag(player);
                  break;
               case "both":
               case "toggle":
                  boolean tabHidden = this.module.hasTabVisibilityOverride(player.getUniqueId()) && !this.module.isTabVisible(player);
                  boolean nameTagHidden = this.module.isDebugNameTagHidden(player);
                  boolean enable = !tabHidden || !nameTagHidden;
                  this.module.setTabVisibilityOverride(player, enable ? Boolean.FALSE : null);
                  this.module.setDebugNameTagHidden(player, enable);
                  if (enable) {
                     player.sendMessage(this.module.formatMessage("&eDebug hide enabled: tab + nametag are forced hidden."));
                  } else {
                     player.sendMessage(this.module.formatMessage("&7Debug hide disabled: tab and nametag now follow normal state."));
                  }
                  break;
               case "off":
                  this.module.setTabVisibilityOverride(player, (Boolean)null);
                  this.module.setDebugNameTagHidden(player, false);
                  player.sendMessage(this.module.formatMessage("&7Debug hide disabled: tab and nametag now follow normal state."));
                  break;
               case "status":
                  boolean statusTabHidden = this.module.hasTabVisibilityOverride(player.getUniqueId()) && !this.module.isTabVisible(player);
                  boolean statusNameTagHidden = this.module.isDebugNameTagHidden(player);
                  player.sendMessage(this.module.formatMessage("&fDebug status: &eTab=" + (statusTabHidden ? "hidden" : "normal") + "&f, &eNameTag=" + (statusNameTagHidden ? "hidden" : "normal")));
                  break;
               default:
                  player.sendMessage(this.module.formatMessage("&cUsage: /hidedebug [tab|nametag|both|off|status]"));
            }

            return true;
         }
      }
   }

   private void toggleTab(Player player) {
      if (this.module.hasTabVisibilityOverride(player.getUniqueId()) && !this.module.isTabVisible(player)) {
         this.module.setTabVisibilityOverride(player, (Boolean)null);
         player.sendMessage(this.module.formatMessage("&7Debug tab hide disabled: tab visibility now follows vanish/incognito."));
      } else {
         this.module.setTabVisibilityOverride(player, false);
         player.sendMessage(this.module.formatMessage("&eDebug tab hide enabled: you are forced hidden in tab."));
      }

   }

   private void toggleNameTag(Player player) {
      boolean hiddenNow = this.module.toggleDebugNameTagHidden(player);
      if (hiddenNow) {
         player.sendMessage(this.module.formatMessage("&eDebug nametag hide enabled: your nametag is forced hidden."));
      } else {
         player.sendMessage(this.module.formatMessage("&7Debug nametag hide disabled: nametag now follows normal state."));
      }

   }

   public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
      if (!alias.equalsIgnoreCase("hidedebug")) {
         return Collections.emptyList();
      } else {
         return args.length == 1 ? Arrays.asList("tab", "nametag", "both", "off", "status") : Collections.emptyList();
      }
   }
}
