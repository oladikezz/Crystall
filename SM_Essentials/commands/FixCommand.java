package net.schalker.SMPS.modules.essentials.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.SMPS.modules.essentials.EssentialsModule;

public class FixCommand extends ModuleCommand {
   private final EssentialsModule module;

   public FixCommand(DoAPI plugin, EssentialsModule module) {
      super(plugin);
      this.module = module;
   }

   public String getName() {
      return "fix";
   }

   public String getPermission() {
      return "smess.fix";
   }

   public String getDescription() {
      return "Чинит предмет в руке или весь инвентарь.";
   }

   public String getUsage() {
      return "/fix [all] [player]";
   }

   @Override
   public void execute(CommandSourceStack stack, String[] args) {
      var sender = stack.getSender();
      if (!sender.hasPermission(this.getPermission())) {
         this.sendMessage(sender, this.module.getMessage("fix.no-permission", "&cУ вас нет прав на эту команду!"));
         return;
      }

      boolean all = false;
      String targetName = null;
      if (args.length > 0) {
         if (args[0].equalsIgnoreCase("all")) {
            all = true;
            if (args.length > 1) {
               targetName = args[1];
            }
         } else {
            targetName = args[0];
         }
      }

      Player target;
      if (targetName == null) {
         if (!(sender instanceof Player player)) {
            this.sendMessage(sender, this.module.getMessage("fix.only-player", "&cЭту команду может использовать только игрок!"));
            return;
         }
         target = player;
      } else {
         if (!sender.hasPermission("smess.fix.others")) {
            this.sendMessage(sender, this.module.getMessage("fix.no-permission-others", "&cУ вас нет прав чинить предметы другим игрокам!"));
            return;
         }
         target = this.plugin.getServer().getPlayer(targetName);
         if (target == null) {
            this.sendMessage(sender, this.module.getMessage("fix.player-not-found", "&cИгрок {player} не найден!").replace("{player}", targetName));
            return;
         }
      }

      if (all) {
         this.fixAll(sender, target);
      } else {
         this.fixHand(sender, target);
      }
   }

   private void fixHand(CommandSender sender, Player target) {
      this.plugin.getSchedulerManager().runEntityTask(target, "fix-hand-" + target.getUniqueId(), () -> {
         ItemStack item = target.getInventory().getItemInMainHand();
         if (item == null || item.getType() == Material.AIR) {
            this.sendMessage(sender, this.module.getMessage("fix.no-item", "&cВ руке нет предмета для починки."));
            return;
         }
         if (!this.repairItem(item)) {
            this.sendMessage(sender, this.module.getMessage("fix.not-repairable", "&cЭтот предмет нельзя починить."));
            return;
         }
         target.getInventory().setItemInMainHand(item);
         if (!target.equals(sender)) {
            this.sendMessage(target, this.module.getMessage("fix.target-fixed", "&aВаш предмет починен."));
            this.sendMessage(sender, this.module.getMessage("fix.sender-fixed", "&aПредмет игрока &e{player}&a починен.").replace("{player}", target.getName()));
         } else {
            this.sendMessage(sender, this.module.getMessage("fix.fixed", "&aПредмет починен."));
         }
      });
   }

   private void fixAll(CommandSender sender, Player target) {
      this.plugin.getSchedulerManager().runEntityTask(target, "fix-all-" + target.getUniqueId(), () -> {
         PlayerInventory inventory = target.getInventory();
         int repaired = 0;

         repaired += this.repairItems(inventory.getStorageContents());
         repaired += this.repairItems(inventory.getArmorContents());
         repaired += this.repairItem(inventory.getItemInOffHand()) ? 1 : 0;
         ItemStack[] extra = inventory.getExtraContents();
         repaired += this.repairItems(extra);

         if (repaired <= 0) {
            this.sendMessage(sender, this.module.getMessage("fix.nothing", "&cНет предметов для починки."));
            return;
         }

         if (!target.equals(sender)) {
            this.sendMessage(target, this.module.getMessage("fix.target-fixed-all", "&aВам починили предметы: &e{count}&a.").replace("{count}", String.valueOf(repaired)));
            this.sendMessage(sender, this.module.getMessage("fix.sender-fixed-all", "&aПредметы игрока &e{player}&a починены: &e{count}&a.")
               .replace("{player}", target.getName())
               .replace("{count}", String.valueOf(repaired)));
         } else {
            this.sendMessage(sender, this.module.getMessage("fix.fixed-all", "&aПочинено предметов: &e{count}&a.")
               .replace("{count}", String.valueOf(repaired)));
         }
      });
   }

   private int repairItems(ItemStack[] items) {
      if (items == null) {
         return 0;
      }
      int repaired = 0;
      for (ItemStack item : items) {
         if (this.repairItem(item)) {
            repaired++;
         }
      }
      return repaired;
   }

   private boolean repairItem(ItemStack item) {
      if (item == null || item.getType() == Material.AIR) {
         return false;
      }
      var meta = item.getItemMeta();
      if (!(meta instanceof Damageable damageable)) {
         return false;
      }
      if (damageable.getDamage() <= 0) {
         return false;
      }
      damageable.setDamage(0);
      item.setItemMeta(meta);
      return true;
   }

   @Override
   public Collection<String> suggest(CommandSourceStack stack, String[] args) {
      var sender = stack.getSender();
      if (args.length == 1) {
         String input = args[0].toLowerCase();
         List<String> options = new ArrayList<>();
         options.add("all");
         if (sender.hasPermission("smess.fix.others")) {
            options.addAll(this.plugin.getServer().getOnlinePlayers().stream()
               .map(Player::getName)
               .collect(Collectors.toList()));
         }
         return options.stream().filter(o -> o.toLowerCase().startsWith(input)).collect(Collectors.toList());
      }
      if (args.length == 2 && args[0].equalsIgnoreCase("all") && sender.hasPermission("smess.fix.others")) {
         String input = args[1].toLowerCase();
         return this.plugin.getServer().getOnlinePlayers().stream()
            .map(Player::getName)
            .filter(n -> n.toLowerCase().startsWith(input))
            .collect(Collectors.toList());
      }
      return List.of();
   }

   private void sendMessage(CommandSender sender, String message) {
      if (message == null || message.isEmpty()) {
         return;
      }
      if (sender instanceof Player player) {
         this.plugin.getSchedulerManager().runEntityTask(player, "fix-message-" + player.getUniqueId(), () -> {
            if (player.isOnline()) {
               player.sendMessage(message);
            }
         });
      } else {
         sender.sendMessage(message);
      }
   }
}
