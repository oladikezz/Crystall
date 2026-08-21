package net.schalker.SMPS.modules.essentials.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.SMPS.modules.essentials.EssentialsModule;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class FreezeCommand extends ModuleCommand {
   private final EssentialsModule module;

   public FreezeCommand(DoAPI plugin, EssentialsModule module) {
      super(plugin);
      this.module = module;
   }

   @Override
   public String getName() {
      return "freeze";
   }

   @Override
   public String getPermission() {
      return "smess.freeze";
   }

   @Override
   public String getDescription() {
      return "Заморозить или разморозить игрока";
   }

   @Override
   public String getUsage() {
      return "/freeze <ник>";
   }

   @Override
   public void execute(CommandSourceStack stack, String[] args) {
      CommandSender sender = stack.getSender();

      if (args.length == 0) {
         sendMessage(sender, this.module.getMessage("freeze.usage", "&[SECONDARY]Использование: &[MAIN]/freeze <ник>"));
         return;
      }

      Player target = this.plugin.getServer().getPlayer(args[0]);
      if (target == null) {
         sendMessage(sender, this.module.getMessage("freeze.player-not-found", "&[SECONDARY]Игрок &[MAIN]{player} &[SECONDARY]не найден!")
            .replace("{player}", args[0]));
         return;
      }

      boolean frozen = this.module.toggleFreeze(target.getUniqueId());
      if (frozen) {
         sendMessage(target, this.module.getMessage("freeze.target-enabled", "&[SECONDARY]Вы были заморожены."));
         sendMessage(sender, this.module.getMessage("freeze.sender-enabled", "&[SECONDARY]Игрок &[MAIN]{player} &[SECONDARY]заморожен.")
            .replace("{player}", target.getName()));
      } else {
         sendMessage(target, this.module.getMessage("freeze.target-disabled", "&[SECONDARY]Вы были разморожены."));
         sendMessage(sender, this.module.getMessage("freeze.sender-disabled", "&[SECONDARY]Игрок &[MAIN]{player} &[SECONDARY]разморожен.")
            .replace("{player}", target.getName()));
      }
   }

   @Override
   public Collection<String> suggest(CommandSourceStack stack, String[] args) {
      if (args.length <= 1) {
         String input = args.length > 0 ? args[0].toLowerCase() : "";
         return this.plugin.getServer().getOnlinePlayers().stream()
            .map(Player::getName)
            .filter(name -> name.toLowerCase().startsWith(input))
            .collect(Collectors.toList());
      }
      return new ArrayList<>();
   }

   private void sendMessage(CommandSender sender, String message) {
      if (message == null || message.isEmpty()) {
         return;
      }
      if (sender instanceof Player player) {
         this.plugin.getSchedulerManager().runEntityTask(player, "freeze-command-message", () -> {
            if (player.isOnline()) {
               player.sendMessage(message);
            }
         });
      } else {
         sender.sendMessage(message);
      }
   }
}
