package net.schalker.SMPS.modules.invsee.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.SMPS.modules.invsee.InvseeModule;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class InvseeCommand extends ModuleCommand {
   private final InvseeModule module;

   public InvseeCommand(DoAPI plugin, InvseeModule module) {
      super(plugin);
      this.module = module;
   }

   @Override
   public String getName() {
      return "invsee";
   }

   @Override
   public String getPermission() {
      return InvseeModule.PERMISSION_COMMAND;
   }

   @Override
   public String getDescription() {
      return "Просмотр и редактирование инвентаря игрока";
   }

   @Override
   public String getUsage() {
      return "/invsee <ник>";
   }

   @Override
   public Collection<String> getAliases() {
      return List.of("inv", "invsee");
   }

   @Override
   public void execute(CommandSourceStack stack, String[] args) {
      var sender = stack.getSender();

      if (!(sender instanceof Player viewer)) {
         sender.sendMessage(this.module.getMessage("players-only", "&cЭту команду может использовать только игрок"));
         return;
      }
      if (!viewer.hasPermission(InvseeModule.PERMISSION_COMMAND)) {
         viewer.sendMessage(this.module.getMessage("no-permission", "&cУ вас нет прав на эту команду"));
         return;
      }
      if (args.length != 1) {
         viewer.sendMessage(this.module.getMessage("usage", "&cИспользование: &f/invsee <ник>"));
         return;
      }

      String targetName = args[0];
      InvseeModule.Target target = this.module.resolveTarget(targetName);
      if (target == null) {
         viewer.sendMessage(this.module.getMessage("playerdata-not-found",
            "&cИгрок &f{player} &cне найден или у него нет сохранённого playerdata")
            .replace("{player}", targetName));
         return;
      }
      if (target.targetId().equals(viewer.getUniqueId())) {
         viewer.sendMessage(this.module.getMessage("self", "&cНельзя открыть свой собственный инвентарь"));
         return;
      }

      this.module.openInvsee(viewer, target);
   }

   @Override
   public Collection<String> suggest(CommandSourceStack stack, String[] args) {
      if (args.length <= 1) {
         String input = args.length > 0 ? args[0].toLowerCase() : "";
         Set<String> names = new LinkedHashSet<>();
         for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            names.add(player.getName());
         }
         for (OfflinePlayer player : this.plugin.getServer().getOfflinePlayers()) {
            if (player.getName() != null) {
               names.add(player.getName());
            }
         }
         return names.stream()
            .filter(name -> name.toLowerCase().startsWith(input))
            .limit(80)
            .toList();
      }
      return List.of();
   }
}
