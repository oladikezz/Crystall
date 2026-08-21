package site.deforce.SMPS.modules.vanish.commands;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import site.deforce.SMPS.modules.vanish.SM_Vanish;

public class VanishListCommand implements CommandExecutor, TabCompleter {
   private final SM_Vanish plugin;

   public VanishListCommand(SM_Vanish plugin) {
      super();
      this.plugin = plugin;
   }

   public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
      if (!sender.hasPermission("smvanish.vanishlist")) {
         sender.sendMessage(Component.text("You do not have permission to execute this command.", NamedTextColor.RED));
         return true;
      } else {
         List<String> vanishedNames = (List)this.plugin.getVanishedPlayers().stream().map(Bukkit::getPlayer).filter((p) -> p != null).map(Player::getName).collect(Collectors.toList());
         if (vanishedNames.isEmpty()) {
            String emptyMsg = this.plugin.getModuleConfig().getString("messages.vanishlist-empty", "&6There are no vanished players currently.");
            sender.sendMessage(this.plugin.formatMessage(emptyMsg));
         } else {
            String formatMsg = this.plugin.getModuleConfig().getString("messages.vanishlist-format", "&6Vanished players ({count}): &f{players}");
            String message = formatMsg.replace("{count}", String.valueOf(vanishedNames.size())).replace("{players}", String.join(", ", vanishedNames));
            sender.sendMessage(this.plugin.formatMessage(message));
         }

         return true;
      }
   }

   public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
      return Collections.emptyList();
   }
}
