package net.schalker.SMPS.modules.alert.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.Collections;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.SMPS.modules.alert.AlertModule;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AToggleCommand extends ModuleCommand {
   private final AlertModule module;

   public AToggleCommand(DoAPI plugin, AlertModule module) {
      super(plugin);
      this.module = module;
   }

   @Override
   public String getName() {
      return "atoggle";
   }

   @Override
   public String getPermission() {
      return "smalert.atoggle";
   }

   @Override
   public String getDescription() {
      return "Открыть меню логов админских команд";
   }

   @Override
   public String getUsage() {
      return "/atoggle";
   }

   @Override
   public void execute(CommandSourceStack stack, String[] args) {
      CommandSender sender = stack.getSender();
      if (!(sender instanceof Player player)) {
         this.sendMessage(sender, this.module.getMessage("atoggle.only-player", "&cЭту команду можно использовать только в игре."));
         return;
      }

      this.module.openMenu(player);
   }

   @Override
   public Collection<String> suggest(CommandSourceStack stack, String[] args) {
      return Collections.emptyList();
   }

   private void sendMessage(CommandSender sender, String message) {
      if (message == null || message.isEmpty()) {
         return;
      }
      if (sender instanceof Player player) {
         this.plugin.getSchedulerManager().runEntityTask(player, "alert-command-message", () -> {
            if (player.isOnline()) {
               player.sendMessage(message);
            }
         });
      } else {
         sender.sendMessage(message);
      }
   }
}