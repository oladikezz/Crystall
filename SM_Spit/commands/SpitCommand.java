package net.schalker.SMPS.modules.spit.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.SMPS.modules.spit.SpitModule;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LlamaSpit;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

public class SpitCommand extends ModuleCommand {
   private final SpitModule module;

   public SpitCommand(DoAPI plugin, SpitModule module) {
      super(plugin);
      this.module = module;
   }

   @Override
   public String getName() {
      return "spit";
   }

   @Override
   public String getPermission() {
      return "smspit.use";
   }

   @Override
   public String getDescription() {
      return "Выпустить плевок ламы без урона";
   }

   @Override
   public String getUsage() {
      return "/spit";
   }

   @Override
   public void execute(CommandSourceStack stack, String[] args) {
      CommandSender sender = stack.getSender();
      if (!(sender instanceof Player player)) {
         this.sendMessage(sender, this.module.getMessage("spit.only-player", "&[SECONDARY]Эту команду может использовать только игрок!"));
         return;
      }

      this.plugin.getSchedulerManager().runEntityTask(player, "spit-command", () -> {
         Vector direction = player.getEyeLocation().getDirection().normalize();
         LlamaSpit spit = player.launchProjectile(LlamaSpit.class, direction.multiply(1.6D));
         spit.getPersistentDataContainer().set(this.module.getSpitKey(), PersistentDataType.BYTE, (byte) 1);

         String message = this.module.getMessage("spit.used", "&[SECONDARY]Плевок выпущен.");
         if (!message.isEmpty() && player.isOnline()) {
            player.sendMessage(message);
         }
      });
   }

   private void sendMessage(CommandSender sender, String message) {
      if (message == null || message.isEmpty()) {
         return;
      }
      sender.sendMessage(message);
   }
}
