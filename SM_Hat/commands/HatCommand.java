package net.schalker.SMPS.modules.hat.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.SMPS.modules.hat.HatModule;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class HatCommand extends ModuleCommand {
   private final HatModule module;

   public HatCommand(DoAPI plugin, HatModule module) {
      super(plugin);
      this.module = module;
   }

   @Override
   public String getName() {
      return "hat";
   }

   @Override
   public String getPermission() {
      return "smhat.use";
   }

   @Override
   public String getDescription() {
      return "Надеть предмет из руки на голову";
   }

   @Override
   public String getUsage() {
      return "/hat";
   }

   @Override
   public void execute(CommandSourceStack stack, String[] args) {
      CommandSender sender = stack.getSender();
      if (!(sender instanceof Player player)) {
         sender.sendMessage(this.module.getMessage("hat.only-player",
            "&cЭту команду может использовать только игрок!"));
         return;
      }

      this.plugin.getSchedulerManager().runEntityTask(player, "hat-command", () -> {
         if (!player.isOnline()) return;

         PlayerInventory inventory = player.getInventory();
         ItemStack hand = inventory.getItemInMainHand();

         if (hand.getType() == Material.AIR) {
            player.sendMessage(this.module.getMessage("hat.empty-hand",
               "&cВы должны держать предмет в руке!"));
            return;
         }

         ItemStack currentHelmet = inventory.getHelmet();

         // Put the hand item on head
         inventory.setHelmet(hand.clone());

         // If there was already a helmet, put it in hand; otherwise clear hand
         if (currentHelmet != null && currentHelmet.getType() != Material.AIR) {
            inventory.setItemInMainHand(currentHelmet);
            player.sendMessage(this.module.getMessage("hat.swapped",
               "&aПредметы поменялись местами!"));
         } else {
            inventory.setItemInMainHand(null);
            player.sendMessage(this.module.getMessage("hat.success",
               "&aПредмет надет на голову!"));
         }
      });
   }
}

