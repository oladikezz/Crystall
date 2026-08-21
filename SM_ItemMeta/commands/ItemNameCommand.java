package net.schalker.SMPS.modules.itemmeta.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.SMPS.modules.itemmeta.ItemMetaModule;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class ItemNameCommand extends ModuleCommand {
   private static final String PERMISSION_USE = "smitemmeta.name";
   private static final String PERMISSION_CLEAR = "smitemmeta.name.clear";
   private final ItemMetaModule module;

   public ItemNameCommand(DoAPI plugin, ItemMetaModule module) {
      super(plugin);
      this.module = module;
   }

   @Override
   public String getName() {
      return "itemname";
   }

   @Override
   public String getPermission() {
      return PERMISSION_USE;
   }

   @Override
   public String getDescription() {
      return "Изменить или убрать название предмета в руке";
   }

   @Override
   public String getUsage() {
      return "/itemname <текст|clear>";
   }

   @Override
   public void execute(CommandSourceStack stack, String[] args) {
      CommandSender sender = stack.getSender();
      if (!(sender instanceof Player player)) {
         this.sendMessage(sender, this.module.getMessage("itemname.only-player", "&[SECONDARY]Эту команду может использовать только игрок!"));
         return;
      }

      if (args.length == 0) {
         this.sendMessage(sender, this.module.getMessage("itemname.usage", "&[SECONDARY]Использование: &[MAIN]/itemname <текст|clear>"));
         return;
      }

      ItemStack item = player.getInventory().getItemInMainHand();
      if (item == null || item.getType() == Material.AIR) {
         this.sendMessage(sender, this.module.getMessage("itemname.no-item", "&[SECONDARY]Возьмите предмет в руку!"));
         return;
      }

      if (args.length == 1 && args[0].equalsIgnoreCase("clear")) {
         if (!sender.hasPermission(PERMISSION_CLEAR)) {
            this.sendMessage(sender, this.module.getMessage("itemname.no-permission-clear", "&[SECONDARY]У вас нет прав для очистки названия!"));
            return;
         }

         this.plugin.getSchedulerManager().runEntityTask(player, "itemname-clear-" + player.getUniqueId(), () -> {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
               this.sendMessage(sender, this.module.getMessage("itemname.not-editable", "&[SECONDARY]Этот предмет нельзя изменить."));
               return;
            }
            meta.setDisplayName(null);
            item.setItemMeta(meta);
            this.sendMessage(sender, this.module.getMessage("itemname.cleared", "&[SECONDARY]Название предмета очищено."));
         });
         return;
      }

      if (!sender.hasPermission(PERMISSION_USE)) {
         this.sendMessage(sender, this.module.getMessage("itemname.no-permission", "&[SECONDARY]У вас нет прав на эту команду!"));
         return;
      }

      String text = String.join(" ", args);
      this.plugin.getSchedulerManager().runEntityTask(player, "itemname-set-" + player.getUniqueId(), () -> {
         ItemMeta meta = item.getItemMeta();
         if (meta == null) {
            this.sendMessage(sender, this.module.getMessage("itemname.not-editable", "&[SECONDARY]Этот предмет нельзя изменить."));
            return;
         }

         meta.setDisplayName(this.module.formatMetaText(text));
         item.setItemMeta(meta);
         this.sendMessage(sender, this.module.getMessage("itemname.updated", "&[SECONDARY]Название предмета изменено."));
      });
   }

   @Override
   public Collection<String> suggest(CommandSourceStack stack, String[] args) {
      if (args.length == 1) {
         String input = args[0].toLowerCase();
         List<String> variants = new ArrayList<>();
         variants.add("clear");
         variants.add("<#ff0000:#00ff00>Текст</>");
         variants.add("<gradient:#ff0000:#00ff00>Текст</gradient>");
         return variants.stream().filter(v -> v.toLowerCase().startsWith(input)).toList();
      }
      return List.of();
   }

   private void sendMessage(CommandSender sender, String message) {
      if (message == null || message.isEmpty()) {
         return;
      }
      if (sender instanceof Player player) {
         this.plugin.getSchedulerManager().runEntityTask(player, "itemname-message-" + player.getUniqueId(), () -> {
            if (player.isOnline()) {
               player.sendMessage(message);
            }
         });
      } else {
         sender.sendMessage(message);
      }
   }
}
