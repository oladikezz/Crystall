package net.schalker.SMPS.modules.quietban.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.SMPS.modules.quietban.QuietBanEntry;
import net.schalker.SMPS.modules.quietban.QuietBanManager;
import net.schalker.SMPS.modules.quietban.QuietBanModule;
import net.schalker.SMPS.modules.quietban.QuietBanTime;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CheckQuietBanCommand extends ModuleCommand {

   private static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

   private final QuietBanModule module;

   public CheckQuietBanCommand(DoAPI plugin, QuietBanModule module) {
      super(plugin);
      this.module = module;
   }

   @Override
   public String getName() {
      return "checkquietban";
   }

   @Override
   public String getPermission() {
      return QuietBanModule.PERMISSION_MANAGE;
   }

   @Override
   public String getDescription() {
      return "Проверка теневого бана";
   }

   @Override
   public String getUsage() {
      return "/checkquietban <ник>";
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
         sender.sendMessage(this.module.getMessage("usage-check",
            "&[SECONDARY]Использование: &[MAIN]/checkquietban <ник>"));
         return;
      }

      QuietBanManager.Target target = manager.resolveTarget(args[0]);
      QuietBanEntry entry = manager.findByPlayer(target.uuid(), target.name());
      if (entry == null) {
         entry = manager.findByName(args[0]);
      }

      if (entry == null) {
         sender.sendMessage(this.module.getMessage("no-ban",
               "&[SECONDARY]У игрока &[MAIN]{player} &[SECONDARY]нет активного теневого бана.")
            .replace("{player}", target.name()));
         return;
      }

      long now = System.currentTimeMillis();
      String ipValue = entry.ip() == null || entry.ip().isEmpty() ? "неизвестен" : entry.ip();
      String origin = "выдан вручную";
      if (entry.isIpLinked()) {
         QuietBanEntry root = manager.findById(entry.source());
         origin = "авто по IP" + (root == null ? "" : " (от " + root.playerName() + ")");
      }

      sender.sendMessage(this.module.getMessage("check-header",
            "&[MAIN]Теневой бан: &[SECONDARY]{player}").replace("{player}", entry.playerName()));
      sender.sendMessage(this.module.getMessage("check-level",
            "&[SECONDARY]Тип: &[MAIN]{level}").replace("{level}", entry.level().getDisplayName()));
      sender.sendMessage(this.module.getMessage("check-ip",
            "&[SECONDARY]IP-привязка: &[MAIN]{lock} &[SECONDARY]({ip})")
         .replace("{lock}", entry.ipLock() ? "да" : "нет")
         .replace("{ip}", ipValue));
      sender.sendMessage(this.module.getMessage("check-time",
            "&[SECONDARY]Осталось: &[MAIN]{time}")
         .replace("{time}", QuietBanTime.formatRemaining(entry.expiresAt(), now)));
      sender.sendMessage(this.module.getMessage("check-issued",
            "&[SECONDARY]Выдал: &[MAIN]{issuer} &[SECONDARY]({date})")
         .replace("{issuer}", entry.issuedBy() == null ? "неизвестно" : entry.issuedBy())
         .replace("{date}", DATE_FORMAT.format(Instant.ofEpochMilli(entry.issuedAt()))));
      sender.sendMessage(this.module.getMessage("check-source",
            "&[SECONDARY]Источник: &[MAIN]{source}").replace("{source}", origin));
      sender.sendMessage(this.module.getMessage("check-reason",
            "&[SECONDARY]Причина: &[MAIN]{reason}")
         .replace("{reason}", entry.reason() == null || entry.reason().isEmpty() ? "не указана" : entry.reason()));
   }

   @Override
   public Collection<String> suggest(CommandSourceStack stack, String[] args) {
      CommandSender sender = stack.getSender();
      if (!sender.hasPermission(QuietBanModule.PERMISSION_MANAGE) || args.length != 1) {
         return List.of();
      }

      List<String> suggestions = new ArrayList<>();
      String input = args[0].toLowerCase(Locale.ROOT);
      QuietBanManager manager = this.module.getManager();
      if (manager != null) {
         for (QuietBanEntry entry : manager.snapshot()) {
            if (entry.playerNameLower().startsWith(input)) {
               suggestions.add(entry.playerName());
            }
         }
      }
      for (Player player : this.plugin.getServer().getOnlinePlayers()) {
         if (player.getName().toLowerCase(Locale.ROOT).startsWith(input)
            && !suggestions.contains(player.getName())) {
            suggestions.add(player.getName());
         }
      }
      return suggestions;
   }
}
