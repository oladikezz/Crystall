package net.schalker.SMPS.modules.stats.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.SMPS.modules.stats.StatsMetric;
import net.schalker.SMPS.modules.stats.StatsModule;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StatsCommand extends ModuleCommand {
   private final StatsModule module;

   public StatsCommand(DoAPI plugin, StatsModule module) {
      super(plugin);
      this.module = module;
   }

   @Override
   public String getName() {
      return "stats";
   }

   @Override
   public String getPermission() {
      return "smstats.use";
   }

   @Override
   public String getDescription() {
      return "Статистика игроков";
   }

   @Override
   public String getUsage() {
      return "/stats [ник|top|reset]";
   }

   @Override
   public void execute(CommandSourceStack stack, String[] args) {
      CommandSender sender = stack.getSender();
      if (!(sender instanceof Player player)) {
         sender.sendMessage(this.module.getMessage("only-player", "&[SECONDARY]Эта команда доступна только в игре."));
         return;
      }

      if (args.length == 0) {
         this.module.openStats(player, player.getName());
         return;
      }

      String sub = args[0].toLowerCase();
      if (sub.equals("reload")) {
         if (!sender.hasPermission("smstats.reload")) {
            sender.sendMessage(this.module.getMessage("no-permission", "&[SECONDARY]У вас нет прав."));
            return;
         }
         this.module.reload();
         sender.sendMessage(this.module.getMessage("reload.done", "&[SECONDARY]Конфигурация перезагружена."));
         return;
      }

      if (sub.equals("top")) {
         if (!sender.hasPermission("smstats.top")) {
            sender.sendMessage(this.module.getMessage("no-permission", "&[SECONDARY]У вас нет прав."));
            return;
         }
         if (args.length == 1) {
            this.module.openTopSelect(player);
            return;
         }
         StatsMetric metric = StatsMetric.fromKey(args[1]);
         if (metric == null) {
            sender.sendMessage(this.module.getMessage("top.invalid", "&[SECONDARY]Неизвестная метрика."));
            return;
         }
         int page = 1;
         if (args.length >= 3) {
            try {
               page = Integer.parseInt(args[2]);
            } catch (NumberFormatException ignored) {
               page = 1;
            }
         }
         this.module.openTop(player, metric, page);
         return;
      }

      if (sub.equals("reset")) {
         if (!sender.hasPermission("smstats.reset")) {
            sender.sendMessage(this.module.getMessage("no-permission", "&[SECONDARY]У вас нет прав."));
            return;
         }
         if (args.length < 2) {
            sender.sendMessage(this.module.getMessage("reset.usage", "&[SECONDARY]Использование: &[MAIN]/stats reset <ник|all confirm>"));
            return;
         }
         if (args[1].equalsIgnoreCase("all")) {
            if (!sender.hasPermission("smstats.reset.all")) {
               sender.sendMessage(this.module.getMessage("no-permission", "&[SECONDARY]У вас нет прав."));
               return;
            }
            if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
               sender.sendMessage(this.module.getMessage("reset.confirm", "&[SECONDARY]Подтвердите: &[MAIN]/stats reset all confirm"));
               return;
            }
            this.module.resetAll(player);
            return;
         }
         this.module.resetPlayer(player, args[1]);
         return;
      }

      if (!sender.hasPermission("smstats.others")) {
         sender.sendMessage(this.module.getMessage("no-permission", "&[SECONDARY]У вас нет прав."));
         return;
      }
      this.module.openStats(player, args[0]);
   }

   @Override
   public Collection<String> suggest(CommandSourceStack stack, String[] args) {
      List<String> suggestions = new ArrayList<>();
      if (args.length == 1) {
         suggestions.add("top");
         suggestions.add("reset");
         if (stack.getSender().hasPermission("smstats.reload")) {
            suggestions.add("reload");
         }
         if (stack.getSender().hasPermission("smstats.others")) {
            String input = args[0].toLowerCase();
            this.plugin.getServer().getOnlinePlayers().forEach(player -> {
               if (player.getName().toLowerCase().startsWith(input)) {
                  suggestions.add(player.getName());
               }
            });
         }
         return suggestions;
      }
      if (args.length == 2 && args[0].equalsIgnoreCase("top")) {
         String input = args[1].toLowerCase();
         for (StatsMetric metric : StatsMetric.values()) {
            if (metric.getKey().startsWith(input)) {
               suggestions.add(metric.getKey());
            }
         }
         return suggestions;
      }
      if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
         String input = args[1].toLowerCase();
         if ("all".startsWith(input)) {
            suggestions.add("all");
         }
         this.plugin.getServer().getOnlinePlayers().forEach(player -> {
            if (player.getName().toLowerCase().startsWith(input)) {
               suggestions.add(player.getName());
            }
         });
         return suggestions;
      }
      if (args.length == 3 && args[0].equalsIgnoreCase("reset") && args[1].equalsIgnoreCase("all")) {
         String input = args[2].toLowerCase();
         if ("confirm".startsWith(input)) {
            suggestions.add("confirm");
         }
         return suggestions;
      }
      return suggestions;
   }
}