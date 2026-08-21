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

public class ItemLoreCommand extends ModuleCommand {
   private static final String PERMISSION_ADD = "smitemmeta.lore.add";
   private static final String PERMISSION_SET = "smitemmeta.lore.set";
   private static final String PERMISSION_REMOVE = "smitemmeta.lore.remove";
   private static final String PERMISSION_CLEAR = "smitemmeta.lore.clear";
   private final ItemMetaModule module;

   public ItemLoreCommand(DoAPI plugin, ItemMetaModule module) {
      super(plugin);
      this.module = module;
   }

   @Override
   public String getName() {
      return "itemlore";
   }

   @Override
   public String getPermission() {
      return "smitemmeta.lore";
   }

   @Override
   public String getDescription() {
      return "Изменить лор предмета в руке";
   }

   @Override
   public String getUsage() {
      return "/itemlore <add|set|remove|clear> ...";
   }

   @Override
   public void execute(CommandSourceStack stack, String[] args) {
      CommandSender sender = stack.getSender();
      if (!(sender instanceof Player player)) {
         this.sendMessage(sender, this.module.getMessage("itemlore.only-player", "&[SECONDARY]Эту команду может использовать только игрок!"));
         return;
      }

      if (args.length == 0) {
         this.sendMessage(sender, this.module.getMessage("itemlore.usage", "&[SECONDARY]Использование: &[MAIN]/itemlore <add|set|remove|clear> ..."));
         return;
      }

      ItemStack item = player.getInventory().getItemInMainHand();
      if (item == null || item.getType() == Material.AIR) {
         this.sendMessage(sender, this.module.getMessage("itemlore.no-item", "&[SECONDARY]Возьмите предмет в руку!"));
         return;
      }

      String sub = args[0].toLowerCase();
      switch (sub) {
         case "add" -> this.handleAdd(sender, player, item, args);
         case "set" -> this.handleSet(sender, player, item, args);
         case "remove" -> this.handleRemove(sender, player, item, args);
         case "clear" -> this.handleClear(sender, player, item);
         default -> this.sendMessage(sender, this.module.getMessage("itemlore.usage", "&[SECONDARY]Использование: &[MAIN]/itemlore <add|set|remove|clear> ..."));
      }
   }

   private void handleAdd(CommandSender sender, Player player, ItemStack item, String[] args) {
      if (!sender.hasPermission(PERMISSION_ADD)) {
         this.sendMessage(sender, this.module.getMessage("itemlore.no-permission-add", "&[SECONDARY]У вас нет прав добавлять лор!"));
         return;
      }
      if (args.length < 2) {
         this.sendMessage(sender, this.module.getMessage("itemlore.add-usage", "&[SECONDARY]Использование: &[MAIN]/itemlore add <текст>"));
         return;
      }

      String line = String.join(" ", List.of(args).subList(1, args.length));
      this.plugin.getSchedulerManager().runEntityTask(player, "itemlore-add-" + player.getUniqueId(), () -> {
         ItemMeta meta = item.getItemMeta();
         if (meta == null) {
            this.sendMessage(sender, this.module.getMessage("itemlore.not-editable", "&[SECONDARY]Этот предмет нельзя изменить."));
            return;
         }

         List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
         lore.add(this.module.formatMetaText(line));
         meta.setLore(lore);
         item.setItemMeta(meta);
         this.sendMessage(sender, this.module.getMessage("itemlore.added", "&[SECONDARY]Строка лора добавлена."));
      });
   }

   private void handleSet(CommandSender sender, Player player, ItemStack item, String[] args) {
      if (!sender.hasPermission(PERMISSION_SET)) {
         this.sendMessage(sender, this.module.getMessage("itemlore.no-permission-set", "&[SECONDARY]У вас нет прав изменять лор!"));
         return;
      }
      if (args.length < 3) {
         this.sendMessage(sender, this.module.getMessage("itemlore.set-usage", "&[SECONDARY]Использование: &[MAIN]/itemlore set <номер> <текст>"));
         return;
      }

      int index;
      try {
         index = Integer.parseInt(args[1]) - 1;
      } catch (NumberFormatException e) {
         this.sendMessage(sender, this.module.getMessage("itemlore.invalid-number", "&[SECONDARY]Номер строки должен быть числом."));
         return;
      }

      String line = String.join(" ", List.of(args).subList(2, args.length));
      this.plugin.getSchedulerManager().runEntityTask(player, "itemlore-set-" + player.getUniqueId(), () -> {
         ItemMeta meta = item.getItemMeta();
         if (meta == null) {
            this.sendMessage(sender, this.module.getMessage("itemlore.not-editable", "&[SECONDARY]Этот предмет нельзя изменить."));
            return;
         }

         List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
         if (index < 0 || index >= lore.size()) {
            this.sendMessage(sender, this.module.getMessage("itemlore.out-of-range", "&[SECONDARY]Строка с таким номером не найдена."));
            return;
         }

         lore.set(index, this.module.formatMetaText(line));
         meta.setLore(lore);
         item.setItemMeta(meta);
         this.sendMessage(sender, this.module.getMessage("itemlore.updated", "&[SECONDARY]Строка лора изменена."));
      });
   }

   private void handleRemove(CommandSender sender, Player player, ItemStack item, String[] args) {
      if (!sender.hasPermission(PERMISSION_REMOVE)) {
         this.sendMessage(sender, this.module.getMessage("itemlore.no-permission-remove", "&[SECONDARY]У вас нет прав удалять строки лора!"));
         return;
      }
      if (args.length < 2) {
         this.sendMessage(sender, this.module.getMessage("itemlore.remove-usage", "&[SECONDARY]Использование: &[MAIN]/itemlore remove <номер>"));
         return;
      }

      int index;
      try {
         index = Integer.parseInt(args[1]) - 1;
      } catch (NumberFormatException e) {
         this.sendMessage(sender, this.module.getMessage("itemlore.invalid-number", "&[SECONDARY]Номер строки должен быть числом."));
         return;
      }

      this.plugin.getSchedulerManager().runEntityTask(player, "itemlore-remove-" + player.getUniqueId(), () -> {
         ItemMeta meta = item.getItemMeta();
         if (meta == null) {
            this.sendMessage(sender, this.module.getMessage("itemlore.not-editable", "&[SECONDARY]Этот предмет нельзя изменить."));
            return;
         }

         List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
         if (index < 0 || index >= lore.size()) {
            this.sendMessage(sender, this.module.getMessage("itemlore.out-of-range", "&[SECONDARY]Строка с таким номером не найдена."));
            return;
         }

         lore.remove(index);
         meta.setLore(lore.isEmpty() ? null : lore);
         item.setItemMeta(meta);
         this.sendMessage(sender, this.module.getMessage("itemlore.removed", "&[SECONDARY]Строка лора удалена."));
      });
   }

   private void handleClear(CommandSender sender, Player player, ItemStack item) {
      if (!sender.hasPermission(PERMISSION_CLEAR)) {
         this.sendMessage(sender, this.module.getMessage("itemlore.no-permission-clear", "&[SECONDARY]У вас нет прав очищать лор!"));
         return;
      }

      this.plugin.getSchedulerManager().runEntityTask(player, "itemlore-clear-" + player.getUniqueId(), () -> {
         ItemMeta meta = item.getItemMeta();
         if (meta == null) {
            this.sendMessage(sender, this.module.getMessage("itemlore.not-editable", "&[SECONDARY]Этот предмет нельзя изменить."));
            return;
         }

         meta.setLore(null);
         item.setItemMeta(meta);
         this.sendMessage(sender, this.module.getMessage("itemlore.cleared", "&[SECONDARY]Лор очищен."));
      });
   }

   @Override
   public Collection<String> suggest(CommandSourceStack stack, String[] args) {
      if (args.length == 1) {
         String input = args[0].toLowerCase();
         return List.of("add", "set", "remove", "clear").stream()
            .filter(s -> s.startsWith(input))
            .toList();
      }

      if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
         return List.of("1", "2", "3");
      }

      if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
         return List.of("1", "2", "3");
      }

      if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
         return List.of("<#ff0000:#00ff00>Текст</>", "<gradient:#ff0000:#00ff00>Текст</gradient>", "#55ffffТекст", "&1Текст");
      }

      return List.of();
   }

   private void sendMessage(CommandSender sender, String message) {
      if (message == null || message.isEmpty()) {
         return;
      }
      if (sender instanceof Player player) {
         this.plugin.getSchedulerManager().runEntityTask(player, "itemlore-message-" + player.getUniqueId(), () -> {
            if (player.isOnline()) {
               player.sendMessage(message);
            }
         });
      } else {
         sender.sendMessage(message);
      }
   }
}
