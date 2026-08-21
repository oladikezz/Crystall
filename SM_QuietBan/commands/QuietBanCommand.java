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
import net.schalker.SMPS.modules.quietban.QuietBanLevel;
import net.schalker.SMPS.modules.quietban.QuietBanManager;
import net.schalker.SMPS.modules.quietban.QuietBanModule;
import net.schalker.SMPS.modules.quietban.QuietBanTime;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class QuietBanCommand extends ModuleCommand {

   private static final List<String> TIME_HINTS = List.of("30m", "1h", "6h", "1d", "7d", "perm");

   private final QuietBanModule module;

   public QuietBanCommand(DoAPI plugin, QuietBanModule module) {
      super(plugin);
      this.module = module;
   }

   @Override
   public String getName() {
      return "quietban";
   }

   @Override
   public String getPermission() {
      return QuietBanModule.PERMISSION_MANAGE;
   }

   @Override
   public String getDescription() {
      return "Теневой бан игрока";
   }

   @Override
   public String getUsage() {
      return "/quietban <ник> <quiet|medium|aggressive> <yes|no> [время] [причина]";
   }

   @Override
   public void execute(CommandSourceStack stack, String[] args) {
      CommandSender sender = stack.getSender();
      QuietBanManager manager = this.module.getManager();
      if (manager == null) {
         this.module.send(sender, "not-ready", "&[SECONDARY]Модуль ещё не готов, попробуйте позже.");
         return;
      }

      if (args.length < 3) {
         sender.sendMessage(this.module.getMessage("usage-ban",
            "&[SECONDARY]Использование: &[MAIN]/quietban <ник> <quiet|medium|aggressive> <yes|no> [время] [причина]"));
         return;
      }

      QuietBanLevel level = QuietBanLevel.parse(args[1]);
      if (level == null) {
         this.module.send(sender, "invalid-level",
            "&[SECONDARY]Неизвестный тип. Доступно: &[MAIN]quiet&[SECONDARY], &[MAIN]medium&[SECONDARY], &[MAIN]aggressive&[SECONDARY].");
         return;
      }

      Boolean ipLock = parseFlag(args[2]);
      if (ipLock == null) {
         this.module.send(sender, "invalid-iplock",
            "&[SECONDARY]Третий аргумент должен быть &[MAIN]yes &[SECONDARY]или &[MAIN]no&[SECONDARY].");
         return;
      }

      long duration = 0L;
      int reasonStart = 3;
      if (args.length > 3 && QuietBanTime.looksLikeDuration(args[3])) {
         duration = QuietBanTime.parseMillis(args[3]);
         if (duration < 0L) {
            this.module.send(sender, "invalid-time",
               "&[SECONDARY]Неверный формат времени. Примеры: &[MAIN]30s&[SECONDARY], &[MAIN]2h&[SECONDARY], &[MAIN]7d&[SECONDARY].");
            return;
         }
         reasonStart = 4;
      }

      String reason = args.length > reasonStart
         ? String.join(" ", Arrays.copyOfRange(args, reasonStart, args.length))
         : "не указана";

      QuietBanManager.Target target = manager.resolveTarget(args[0]);
      if (target.online() != null && this.module.isImmune(target.online())) {
         this.module.send(sender, "target-immune",
            "&[SECONDARY]На этого игрока нельзя выдать теневой бан.");
         return;
      }

      String issuedBy = sender.getName();
      long finalDuration = duration;
      boolean finalIpLock = ipLock;

      this.module.runAsync("quietban-issue-" + target.name(), () -> {
         QuietBanEntry entry = manager.issue(target, level, finalIpLock, finalDuration, reason, issuedBy);
         this.module.logAction("Quiet ban issued: " + entry.playerName() + " level=" + entry.level().getKey()
            + " ipLock=" + entry.ipLock() + " by=" + issuedBy);

         String applied = target.online() != null && target.online().isOnline()
            ? this.module.getMessage("applied-now", "&[SECONDARY]Эффект применён сразу.")
            : this.module.getMessage("applied-later", "&[SECONDARY]Эффект применится при следующем входе.");

         sender.sendMessage(this.module.getMessage("ban-done",
               "&[SECONDARY]Теневой бан выдан игроку &[MAIN]{player}&[SECONDARY]. Тип: &[MAIN]{level}&[SECONDARY], IP-привязка: &[MAIN]{ip}&[SECONDARY], срок: &[MAIN]{time}&[SECONDARY].")
            .replace("{player}", entry.playerName())
            .replace("{level}", entry.level().getDisplayName())
            .replace("{ip}", entry.ipLock() ? "да" : "нет")
            .replace("{time}", QuietBanTime.format(finalDuration)));
         sender.sendMessage(applied);
      });
   }

   @Override
   public Collection<String> suggest(CommandSourceStack stack, String[] args) {
      CommandSender sender = stack.getSender();
      if (!sender.hasPermission(QuietBanModule.PERMISSION_MANAGE)) {
         return List.of();
      }

      List<String> suggestions = new ArrayList<>();
      if (args.length == 1) {
         String input = args[0].toLowerCase(Locale.ROOT);
         for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(input)) {
               suggestions.add(player.getName());
            }
         }
         return suggestions;
      }

      if (args.length == 2) {
         String input = args[1].toLowerCase(Locale.ROOT);
         for (QuietBanLevel level : QuietBanLevel.values()) {
            if (level.getKey().startsWith(input)) {
               suggestions.add(level.getKey());
            }
         }
         return suggestions;
      }

      if (args.length == 3) {
         String input = args[2].toLowerCase(Locale.ROOT);
         for (String option : List.of("yes", "no")) {
            if (option.startsWith(input)) {
               suggestions.add(option);
            }
         }
         return suggestions;
      }

      if (args.length == 4) {
         String input = args[3].toLowerCase(Locale.ROOT);
         for (String hint : TIME_HINTS) {
            if (hint.startsWith(input)) {
               suggestions.add(hint);
            }
         }
         return suggestions;
      }

      return List.of();
   }

   private Boolean parseFlag(String raw) {
      if (raw == null) {
         return null;
      }
      return switch (raw.trim().toLowerCase(Locale.ROOT)) {
         case "yes", "y", "true", "1", "да" -> Boolean.TRUE;
         case "no", "n", "false", "0", "нет" -> Boolean.FALSE;
         default -> null;
      };
   }
}
