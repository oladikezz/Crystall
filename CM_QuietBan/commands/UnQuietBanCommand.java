package net.schalker.SMPS.modules.quietban.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.SMPS.modules.quietban.QuietBanEntry;
import net.schalker.SMPS.modules.quietban.QuietBanManager;
import net.schalker.SMPS.modules.quietban.QuietBanModule;
import org.bukkit.command.CommandSender;

public class UnQuietBanCommand extends ModuleCommand {

   private final QuietBanModule module;

   public UnQuietBanCommand(DoAPI plugin, QuietBanModule module) {
      super(plugin);
      this.module = module;
   }

   @Override
   public String getName() {
      return "unquietban";
   }

   @Override
   public String getPermission() {
      return QuietBanModule.PERMISSION_MANAGE;
   }

   @Override
   public String getDescription() {
      return "Снятие теневого бана";
   }

   @Override
   public String getUsage() {
      return "/unquietban <ник> [причина]";
   }

   @Override
   public void execute(CommandSourceStack stack, String[] args) {
      CommandSender sender = stack.getSender();
      QuietBanManager manager = this.module.getManager();
      if (manager == null) {
         this.module.send(sender, "not-ready", "&[SECONDARY]Модуль ещё не готов, попробуйте позже.");
         return;
      }

      if (args.length < 1) {
         sender.sendMessage(this.module.getMessage("usage-unban",
            "&[SECONDARY]Использование: &[MAIN]/unquietban <ник> [причина]"));
         return;
      }

      String reason = args.length > 1
         ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
         : "не указана";

      QuietBanManager.Target target = manager.resolveTarget(args[0]);
      String issuedBy = sender.getName();

      this.module.runAsync("quietban-lift-" + target.name(), () -> {
         int lifted = manager.lift(target.uuid(), target.name(), issuedBy, reason,
            System.currentTimeMillis(), false);
         if (lifted == 0) {
            sender.sendMessage(this.module.getMessage("no-ban",
                  "&[SECONDARY]У игрока &[MAIN]{player} &[SECONDARY]нет активного теневого бана.")
               .replace("{player}", target.name()));
            return;
         }

         sender.sendMessage(this.module.getMessage("unban-done",
               "&[SECONDARY]Теневой бан снят с игрока &[MAIN]{player}&[SECONDARY]. Записей снято: &[MAIN]{count}&[SECONDARY].")
            .replace("{player}", target.name())
            .replace("{count}", String.valueOf(lifted)));
      });
   }

   @Override
   public Collection<String> suggest(CommandSourceStack stack, String[] args) {
      CommandSender sender = stack.getSender();
      if (!sender.hasPermission(QuietBanModule.PERMISSION_MANAGE) || args.length != 1) {
         return List.of();
      }

      QuietBanManager manager = this.module.getManager();
      if (manager == null) {
         return List.of();
      }

      List<String> suggestions = new ArrayList<>();
      String input = args[0].toLowerCase(Locale.ROOT);
      for (QuietBanEntry entry : manager.snapshot()) {
         if (entry.playerNameLower().startsWith(input)) {
            suggestions.add(entry.playerName());
         }
      }
      return suggestions;
   }
}
