package net.schalker.SMPS.modules.trafficoptimizer.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.SMPS.modules.trafficoptimizer.OptimizationLevel;
import net.schalker.SMPS.modules.trafficoptimizer.PlayerNetworkState;
import net.schalker.SMPS.modules.trafficoptimizer.TrafficOptimizerModule;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class NetOptCommand extends ModuleCommand {

   private final TrafficOptimizerModule module;

   public NetOptCommand(DoAPI plugin, TrafficOptimizerModule module) {
      super(plugin);
      this.module = module;
   }

   @Override
   public String getName() {
      return "netopt";
   }

   @Override
   public String getPermission() {
      return TrafficOptimizerModule.PERMISSION_ADMIN;
   }

   @Override
   public String getDescription() {
      return "Adaptive traffic optimizer control";
   }

   @Override
   public String getUsage() {
      return "/netopt <status|list|reset|on|off>";
   }

   @Override
   public void execute(CommandSourceStack stack, String[] args) {
      CommandSender sender = stack.getSender();
      if (args.length == 0) {
         this.sendUsage(sender);
         return;
      }

      switch (args[0].toLowerCase()) {
         case "status" -> this.handleStatus(sender, args);
         case "list" -> this.handleList(sender);
         case "reset" -> this.handleReset(sender, args);
         case "on" -> this.handleToggle(sender, true);
         case "off" -> this.handleToggle(sender, false);
         default -> this.sendUsage(sender);
      }
   }

   private void handleStatus(CommandSender sender, String[] args) {
      Player target;
      if (args.length >= 2) {
         target = this.plugin.getServer().getPlayerExact(args[1]);
         if (target == null) {
            this.send(sender, this.module.getMessage("player-not-found",
                  "&[SECONDARY]Игрок &[MAIN]{player} &[SECONDARY]не в сети.")
               .replace("{player}", args[1]));
            return;
         }
      } else if (sender instanceof Player self) {
         target = self;
      } else {
         this.send(sender, this.module.getMessage("console-needs-target",
            "&[SECONDARY]Консоль должна указать ник."));
         return;
      }

      PlayerNetworkState state = this.module.getState(target.getUniqueId());
      if (state == null) {
         this.send(sender, this.module.getMessage("no-data",
               "&[SECONDARY]По игроку &[MAIN]{player} &[SECONDARY]данных пока нет.")
            .replace("{player}", target.getName()));
         return;
      }

      String skip = state.isSkipped() ? state.getSkipReason() : "-";
      long particles = this.module.getParticleFilter() == null
         ? 0L
         : this.module.getParticleFilter().getDropped(target.getUniqueId());

      this.send(sender, this.module.getMessage("status-line",
            "&[MAIN]{player}&[SECONDARY]: пинг &[MAIN]{ping}&[SECONDARY]мс, уровень &[MAIN]{level}&[SECONDARY], "
               + "прорисовка &[MAIN]{view}&[SECONDARY]/&[MAIN]{base}&[SECONDARY], частиц срезано "
               + "&[MAIN]{particles}&[SECONDARY], пропуск: &[MAIN]{skip}")
         .replace("{player}", target.getName())
         .replace("{ping}", formatPing(state))
         .replace("{level}", state.getLevel().name())
         .replace("{view}", String.valueOf(target.getViewDistance()))
         .replace("{base}", String.valueOf(state.getBaselineView()))
         .replace("{particles}", String.valueOf(particles))
         .replace("{skip}", skip));
   }

   private void handleList(CommandSender sender) {
      List<PlayerNetworkState> degraded = this.module.getDegradedStates();
      this.send(sender, this.module.getMessage("list-header",
            "&[SECONDARY]Активен: &[MAIN]{active}&[SECONDARY], под оптимизацией: &[MAIN]{count}")
         .replace("{active}", this.module.isActive() ? "да" : "нет")
         .replace("{count}", String.valueOf(degraded.size())));

      if (this.module.isGuardActive()) {
         this.send(sender, this.module.getMessage("guard-active",
            "&[SECONDARY]Пауза: пинг поднялся у большинства — это похоже на проблему сервера, "
               + "а не каналов игроков."));
      }

      for (PlayerNetworkState state : degraded) {
         Player player = this.plugin.getServer().getPlayer(state.getPlayerId());
         String name = player != null ? player.getName() : state.getPlayerId().toString();
         this.send(sender, this.module.getMessage("list-entry",
               "&[MAIN]{player} &[SECONDARY]- &[MAIN]{level}&[SECONDARY], пинг &[MAIN]{ping}&[SECONDARY]мс")
            .replace("{player}", name)
            .replace("{level}", state.getLevel().name())
            .replace("{ping}", formatPing(state)));
      }
   }

   private void handleReset(CommandSender sender, String[] args) {
      if (args.length < 2) {
         this.send(sender, this.module.getMessage("reset-usage",
            "&[SECONDARY]Использование: &[MAIN]/netopt reset <ник>"));
         return;
      }

      Player target = this.plugin.getServer().getPlayerExact(args[1]);
      if (target == null) {
         this.send(sender, this.module.getMessage("player-not-found",
               "&[SECONDARY]Игрок &[MAIN]{player} &[SECONDARY]не в сети.")
            .replace("{player}", args[1]));
         return;
      }

      this.module.resetPlayer(target);
      this.send(sender, this.module.getMessage("reset-done",
            "&[SECONDARY]Настройки игрока &[MAIN]{player} &[SECONDARY]возвращены к исходным.")
         .replace("{player}", target.getName()));
   }

   private void handleToggle(CommandSender sender, boolean enable) {
      this.module.setActive(enable);
      this.send(sender, this.module.getMessage(enable ? "toggled-on" : "toggled-off",
         enable
            ? "&[SECONDARY]Оптимизатор включен."
            : "&[SECONDARY]Оптимизатор выключен, всем возвращены исходные настройки."));
   }

   private static String formatPing(PlayerNetworkState state) {
      double ping = state.getSmoothedPing();
      return ping < 0.0D ? "?" : String.valueOf(Math.round(ping));
   }

   private void sendUsage(CommandSender sender) {
      this.send(sender, this.module.getMessage("usage",
         "&[SECONDARY]Использование: &[MAIN]/netopt <status|list|reset|on|off>"));
   }

   @Override
   public Collection<String> suggest(CommandSourceStack stack, String[] args) {
      if (args.length <= 1) {
         String input = args.length == 0 ? "" : args[0].toLowerCase();
         return List.of("status", "list", "reset", "on", "off").stream()
            .filter(value -> value.startsWith(input))
            .collect(Collectors.toList());
      }
      if (args.length == 2 && ("status".equalsIgnoreCase(args[0]) || "reset".equalsIgnoreCase(args[0]))) {
         String input = args[1].toLowerCase();
         return this.plugin.getServer().getOnlinePlayers().stream()
            .map(Player::getName)
            .filter(name -> name.toLowerCase().startsWith(input))
            .collect(Collectors.toList());
      }
      return List.of();
   }

   private void send(CommandSender sender, String message) {
      if (message == null || message.isEmpty()) {
         return;
      }
      if (sender instanceof Player player) {
         this.plugin.getSchedulerManager().runEntityTask(player, "netopt-msg-" + UUID.randomUUID(), () -> {
            if (player.isOnline()) {
               player.sendMessage(message);
            }
         });
         return;
      }
      sender.sendMessage(message);
   }
}
