package net.schalker.SMPS.modules.voodoo.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.SMPS.modules.voodoo.VoodooItem;
import net.schalker.SMPS.modules.voodoo.VoodooModule;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class VoodooCommand extends ModuleCommand {
   private final VoodooModule module;

   public VoodooCommand(DoAPI plugin, VoodooModule module) {
      super(plugin);
      this.module = module;
   }

   @Override
   public String getName() {
      return "voodoo";
   }

   @Override
   public String getPermission() {
      return "smvoodoo.admin";
   }

   @Override
   public String getDescription() {
      return "Управление вуду-куклами";
   }

   @Override
   public String getUsage() {
      return "/voodoo <ник_цели> add|remove <ник_владельца>";
   }

   @Override
   public void execute(CommandSourceStack stack, String[] args) {
      CommandSender sender = stack.getSender();

      // /voodoo <target> add <owner>
      // /voodoo <target> remove <owner>
      if (args.length < 3) {
         sender.sendMessage(this.module.getMessage("voodoo.usage",
            "&cИспользование: /voodoo <ник_цели> add|remove <ник_владельца>"));
         return;
      }

      String targetName = args[0];
      String subcommand = args[1].toLowerCase();
      String ownerName = args[2];

      switch (subcommand) {
         case "add" -> handleAdd(sender, targetName, ownerName);
         case "remove" -> handleRemove(sender, targetName, ownerName);
         default -> sender.sendMessage(this.module.getMessage("voodoo.unknown-subcommand",
            "&cНеизвестная подкоманда. Используйте: /voodoo <ник_цели> add|remove <ник_владельца>"));
      }
   }

   private void handleAdd(CommandSender sender, String targetName, String ownerName) {
      // Verify target exists (online or has played before)
      Player target = Bukkit.getPlayerExact(targetName);
      if (target == null) {
         @SuppressWarnings("deprecation")
         org.bukkit.OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
         if (!offlineTarget.hasPlayedBefore() && !offlineTarget.isOnline()) {
            sender.sendMessage(this.module.getMessage("voodoo.target-not-found",
               "&cИгрок {target} не найден!").replace("{target}", targetName));
            return;
         }
      }

      // Owner must be online to receive the item
      Player owner = Bukkit.getPlayerExact(ownerName);
      if (owner == null || !owner.isOnline()) {
         sender.sendMessage(this.module.getMessage("voodoo.owner-not-found",
            "&cИгрок {owner} не найден!").replace("{owner}", ownerName));
         return;
      }

      // Run on owner's entity scheduler (Folia-safe)
      this.plugin.getSchedulerManager().runEntityTask(owner, "voodoo-give-" + ownerName, () -> {
         if (!owner.isOnline()) return;

         // Pass the full module config and SMPS plugin for color processing
         ItemStack voodoo = VoodooItem.create(targetName, ownerName, this.module.getModuleConfig(), this.plugin);

         if (owner.getInventory().firstEmpty() == -1) {
            sender.sendMessage(this.module.getMessage("voodoo.inventory-full",
               "&cУ игрока {owner} нет места в инвентаре!").replace("{owner}", ownerName));
            return;
         }

         owner.getInventory().addItem(voodoo);
         sender.sendMessage(this.module.getMessage("voodoo.created",
            "&aВуду-кукла на &e{target} &aсоздана и выдана &e{owner}&a!")
            .replace("{target}", targetName)
            .replace("{owner}", ownerName));
      });
   }

   private void handleRemove(CommandSender sender, String targetName, String ownerName) {
      Player owner = Bukkit.getPlayerExact(ownerName);
      if (owner == null || !owner.isOnline()) {
         sender.sendMessage(this.module.getMessage("voodoo.owner-not-found",
            "&cИгрок {owner} не найден!").replace("{owner}", ownerName));
         return;
      }

      this.plugin.getSchedulerManager().runEntityTask(owner, "voodoo-remove-" + ownerName, () -> {
         if (!owner.isOnline()) return;

         ItemStack[] contents = owner.getInventory().getContents();
         boolean found = false;
         for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (VoodooItem.isVoodoo(item)) {
               String itemTarget = VoodooItem.getTarget(item);
               if (itemTarget != null && itemTarget.equalsIgnoreCase(targetName)) {
                  owner.getInventory().setItem(i, null);
                  found = true;
                  break;
               }
            }
         }

         if (found) {
            sender.sendMessage(this.module.getMessage("voodoo.removed",
               "&aВуду-кукла на &e{target} &aудалена из инвентаря &e{owner}&a.")
               .replace("{target}", targetName)
               .replace("{owner}", ownerName));
         } else {
            sender.sendMessage(this.module.getMessage("voodoo.not-found-in-inventory",
               "&cУ игрока {owner} нет вуду-куклы на {target}.")
               .replace("{target}", targetName)
               .replace("{owner}", ownerName));
         }
      });
   }
}
