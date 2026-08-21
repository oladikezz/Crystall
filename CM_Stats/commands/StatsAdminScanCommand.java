package net.schalker.SMPS.modules.stats.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.SMPS.modules.stats.StatsModule;
import org.bukkit.command.CommandSender;

public class StatsAdminScanCommand extends ModuleCommand {
   private final StatsModule module;

   public StatsAdminScanCommand(DoAPI plugin, StatsModule module) {
      super(plugin);
      this.module = module;
   }

   @Override
   public String getName() {
      return "statsadmin";
   }

   @Override
   public String getPermission() {
      return "smstats.admin.scan";
   }

   @Override
   public String getDescription() {
      return "Запустить/остановить импорт статистики из playerdata";
   }

   @Override
   public String getUsage() {
      return "/statsadmin scan";
   }

   @Override
   public void execute(CommandSourceStack stack, String[] args) {
      CommandSender sender = stack.getSender();

      if (!sender.hasPermission(this.getPermission())) {
         sender.sendMessage(this.module.getMessage("no-permission", "&[SECONDARY]У вас нет прав."));
         return;
      }

      if (args.length == 0 || !args[0].equalsIgnoreCase("scan")) {
         sender.sendMessage(this.module.getMessage("scan.usage", "&[SECONDARY]Использование: &[MAIN]/statsadmin scan"));
         return;
      }

      this.module.togglePlayerdataScan(sender);
   }

   @Override
   public Collection<String> suggest(CommandSourceStack stack, String[] args) {
      if (args.length == 1) {
         String input = args[0].toLowerCase();
         List<String> suggestions = new ArrayList<>();
         if ("scan".startsWith(input)) {
            suggestions.add("scan");
         }
         return suggestions;
      }
      return List.of();
   }
}
