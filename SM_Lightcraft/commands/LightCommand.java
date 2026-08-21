package net.schalker.SMPS.modules.lightcraft.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.SMPS.modules.lightcraft.LightCraftModule;

public class LightCommand extends ModuleCommand {
   public LightCommand(DoAPI plugin) {
      super(plugin);
   }

   public String getName() {
      return "light";
   }

   public String getPermission() {
      return "SMPS.light";
   }

   public String getDescription() {
      return "Получить световой блок";
   }

   public String getUsage() {
      return "/light [уровень 0-15] [игрок]";
   }

   private Component colorize(String text) {
      return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
   }

   @Override
   public void execute(CommandSourceStack stack, String[] args) {
      var sender = stack.getSender();
      LightCraftModule module = LightCraftModule.getInstance();

      int level = 15;
      if (args.length >= 1) {
         try {
            level = Integer.parseInt(args[0]);
            if (level < 0 || level > 15) {
               String msg = module.getMessage("commands.light-invalid-level", "&cУровень света должен быть от 0 до 15!");
               sender.sendMessage(colorize(msg));
               return;
            }
         } catch (NumberFormatException exception) {
            String msg = module.getMessage("commands.light-invalid-format", "&cНеверный уровень света! Используйте число от 0 до 15");
            sender.sendMessage(colorize(msg));
            return;
         }
      }

      Player target;
      if (args.length >= 2) {
         if (!sender.hasPermission("SMPS.light.others")) {
            String msg = module.getMessage("commands.light-no-permission-others", "&cУ вас нет прав для выдачи света другим игрокам!");
            sender.sendMessage(colorize(msg));
            return;
         }

         target = this.plugin.getServer().getPlayer(args[1]);
         if (target == null) {
            String msg = module.getMessage("commands.light-player-not-found", "&cИгрок не найден!");
            sender.sendMessage(colorize(msg));
            return;
         }
      } else {
         if (!(sender instanceof Player player)) {
            String msg = module.getMessage("commands.light-specify-player", "&cУкажите имя игрока!");
            sender.sendMessage(colorize(msg));
            return;
         }
         target = player;
      }

      int lightLevel = level;
      ItemStack lightBlock = module.createLightBlock(lightLevel);
      this.plugin.getSchedulerManager().runEntityTask(target, "light-command", () -> {
         target.getInventory().addItem(new ItemStack[]{lightBlock});
         String msg = module.getMessage("commands.light-received", "&aВы получили световой блок &6(Уровень: {level})")
            .replace("{level}", String.valueOf(lightLevel));
         target.sendMessage(colorize(msg));
      });

      if (!target.equals(sender)) {
         String msg = module.getMessage("commands.light-given", "&aСветовой блок выдан игроку &e{player} &6(Уровень: {level})")
            .replace("{player}", target.getName())
            .replace("{level}", String.valueOf(lightLevel));
         Component response = colorize(msg);
         if (sender instanceof Player senderPlayer) {
            this.plugin.getSchedulerManager().runEntityTask(senderPlayer, "light-command-message", () -> {
               if (senderPlayer.isOnline()) {
                  senderPlayer.sendMessage(response);
               }
            });
         } else {
            sender.sendMessage(response);
         }
      }

      this.plugin.getDebugSystem().log("LightCommand", sender.getName() + " выдал световой блок уровня " + lightLevel + " игроку " + target.getName());
   }

   @Override
   public Collection<String> suggest(CommandSourceStack stack, String[] args) {
      var sender = stack.getSender();
      if (args.length <= 1) {
         String input = args.length > 0 ? args[0].toLowerCase() : "";
         List<String> levels = new ArrayList<>();
         for (int i = 0; i <= 15; i++) {
            String value = String.valueOf(i);
            if (value.startsWith(input)) {
               levels.add(value);
            }
         }
         return levels;
      }

      if (args.length == 2 && sender.hasPermission("SMPS.light.others")) {
         String input = args[1].toLowerCase();
         List<String> names = new ArrayList<>();
         for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            if (player.getName().toLowerCase().startsWith(input)) {
                names.add(player.getName());
            }
         }
         return names;
      }

      return List.of();
   }
}
