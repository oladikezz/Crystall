package net.schalker.SMPS.modules.keepinventory.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.SMPS.modules.keepinventory.KeepInventoryModule;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class KeepInventoryCommand extends ModuleCommand {
   private final KeepInventoryModule module;

   public KeepInventoryCommand(DoAPI plugin, KeepInventoryModule module) {
      super(plugin);
      this.module = module;
   }

   @Override
   public String getName() {
      return "keepinv";
   }

   @Override
   public String getPermission() {
      return "smkeepinv.use";
   }

   @Override
   public String getDescription() {
      return "Переключает сохранение инвентаря при смерти";
   }

   @Override
   public String getUsage() {
      return "/keepinv [ник]";
   }

   @Override
   public Collection<String> getAliases() {
      return List.of("ki");
   }

   @Override
   public void execute(CommandSourceStack stack, String[] args) {
      CommandSender sender = stack.getSender();

      if (args.length == 0) {
         if (!(sender instanceof Player player)) {
            sendMessage(sender, this.module.getMessage("keepinv.only-player", "&[SECONDARY]Из консоли укажите ник: &[MAIN]/keepinv <ник>"));
            return;
         }

         boolean enabled = this.module.toggleKeepInventory(player.getUniqueId());
         String key = enabled ? "keepinv.enabled-self" : "keepinv.disabled-self";
         sendMessage(player, this.module.getMessage(key, enabled ? "&[SECONDARY]Режим keep inventory &[MAIN]включен&[SECONDARY]." : "&[SECONDARY]Режим keep inventory &[MAIN]выключен&[SECONDARY]."));
         return;
      }

      if (!sender.hasPermission("smkeepinv.use.others")) {
         sendMessage(sender, this.module.getMessage("keepinv.no-permission-others", "&[SECONDARY]У вас нет прав для изменения режима другим игрокам."));
         return;
      }

      String targetName = args[0];
      Player onlineTarget = Bukkit.getPlayerExact(targetName);
      OfflinePlayer target = onlineTarget != null ? onlineTarget : Bukkit.getOfflinePlayer(targetName);

      if (target.getUniqueId() == null || (!target.isOnline() && !target.hasPlayedBefore())) {
         sendMessage(sender, this.module.getMessage("keepinv.player-not-found", "&[SECONDARY]Игрок &[MAIN]{player} &[SECONDARY]не найден.").replace("{player}", targetName));
         return;
      }

      boolean enabled = this.module.toggleKeepInventory(target.getUniqueId());
      String senderKey = enabled ? "keepinv.enabled-other-sender" : "keepinv.disabled-other-sender";
      sendMessage(sender, this.module.getMessage(senderKey,
         enabled
            ? "&[SECONDARY]Для игрока &[MAIN]{player} &[SECONDARY]режим keep inventory &[MAIN]включен&[SECONDARY]."
            : "&[SECONDARY]Для игрока &[MAIN]{player} &[SECONDARY]режим keep inventory &[MAIN]выключен&[SECONDARY].")
         .replace("{player}", target.getName() != null ? target.getName() : targetName));

      if (onlineTarget != null && onlineTarget.isOnline()) {
         String targetKey = enabled ? "keepinv.enabled-other-target" : "keepinv.disabled-other-target";
         sendMessage(onlineTarget, this.module.getMessage(targetKey,
            enabled
               ? "&[SECONDARY]Вам включили режим keep inventory."
               : "&[SECONDARY]Вам выключили режим keep inventory."));
      }
   }

   @Override
   public Collection<String> suggest(CommandSourceStack stack, String[] args) {
      if (args.length == 1 && stack.getSender().hasPermission("smkeepinv.use.others")) {
         String input = args[0].toLowerCase();
         return this.plugin.getServer().getOnlinePlayers().stream()
            .map(Player::getName)
            .filter(name -> name.toLowerCase().startsWith(input))
            .collect(Collectors.toCollection(ArrayList::new));
      }
      return List.of();
   }

   private void sendMessage(CommandSender sender, String message) {
      if (message == null || message.isEmpty()) {
         return;
      }
      sender.sendMessage(message);
   }
}
