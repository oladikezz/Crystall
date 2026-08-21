package net.schalker.SMPS.modules.essentials.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.SMPS.modules.essentials.EssentialsModule;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class ExtCommand extends ModuleCommand {
   private final EssentialsModule module;

   public ExtCommand(DoAPI plugin, EssentialsModule module) {
      super(plugin);
      this.module = module;
   }

   @Override
   public String getName() {
      return "ext";
   }

   @Override
   public String getPermission() {
      return "smess.ext";
   }

   @Override
   public String getDescription() {
      return "Потушить себя или другого игрока";
   }

   @Override
   public String getUsage() {
      return "/ext [ник|радиус]";
   }

   @Override
   public void execute(CommandSourceStack stack, String[] args) {
      CommandSender sender = stack.getSender();

      if (args.length == 0) {
         if (!(sender instanceof Player player)) {
            this.sendMessage(sender, this.module.getMessage("ext.only-player", "&[SECONDARY]Эту команду может использовать только игрок!"));
            return;
         }
         this.extinguishPlayer(player);
         return;
      }

      Integer parsedRadius = this.tryParseRadius(args[0]);
      if (parsedRadius != null) {
         if (!(sender instanceof Player player)) {
            this.sendMessage(sender, this.module.getMessage("ext.only-player", "&[SECONDARY]Эту команду может использовать только игрок!"));
            return;
         }

         int maxRadius = Math.max(1, this.module.getConfig().getInt("settings.ext.max-radius", 16));
         int radius = Math.min(Math.max(1, parsedRadius), maxRadius);

         if (parsedRadius > maxRadius) {
            this.sendMessage(sender, this.module.getMessage("ext.radius-limited", "&[SECONDARY]Радиус ограничен до &[MAIN]{max}&[SECONDARY].")
               .replace("{max}", Integer.toString(maxRadius)));
         }

         this.extinguishArea(player, radius);
         return;
      }

      if (!sender.hasPermission("smess.ext.others")) {
         this.sendMessage(sender, this.module.getMessage("ext.no-permission-others", "&[SECONDARY]У вас нет прав для тушения других игроков!"));
         return;
      }

      Player target = this.plugin.getServer().getPlayer(args[0]);
      if (target == null) {
         this.sendMessage(sender, this.module.getMessage("ext.player-not-found", "&[SECONDARY]Игрок &[MAIN]{player} &[SECONDARY]не найден!")
            .replace("{player}", args[0]));
         return;
      }

      this.extinguishPlayer(target);

      if (!target.equals(sender)) {
         String senderMessage = this.module.getMessage("ext.sender-message", "&[SECONDARY]Игрок &[MAIN]{player} &[SECONDARY]потушен.")
            .replace("{player}", target.getName());
         this.sendMessage(sender, senderMessage);
      }
   }

   private void extinguishPlayer(Player target) {
      this.plugin.getSchedulerManager().runEntityTask(target, "ext-command", () -> {
         target.setFireTicks(0);
         String targetMessage = this.module.getMessage("ext.target-message", "&[SECONDARY]Вы были потушены.");
         if (!targetMessage.isEmpty()) {
            target.sendMessage(targetMessage);
         }
      });
   }

   private void extinguishArea(Player source, int radius) {
      this.plugin.getSchedulerManager().runEntityTask(source, "ext-radius", () -> {
         if (!source.isOnline()) {
            return;
         }

         int radiusSquared = radius * radius;
         int centerX = source.getLocation().getBlockX();
         int centerY = source.getLocation().getBlockY();
         int centerZ = source.getLocation().getBlockZ();
         int extinguishedBlocks = 0;

         for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int y = centerY - radius; y <= centerY + radius; y++) {
               for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                  int dx = x - centerX;
                  int dy = y - centerY;
                  int dz = z - centerZ;
                  if ((dx * dx + dy * dy + dz * dz) > radiusSquared) {
                     continue;
                  }

                  Block block = source.getWorld().getBlockAt(x, y, z);
                  Material type = block.getType();
                  if (type == Material.FIRE || type == Material.SOUL_FIRE) {
                     block.setType(Material.AIR, false);
                     extinguishedBlocks++;
                  }
               }
            }
         }

         int extinguishedEntities = 0;
         for (Entity entity : source.getWorld().getNearbyEntities(source.getLocation(), radius, radius, radius)) {
            Vector delta = entity.getLocation().toVector().subtract(source.getLocation().toVector());
            if (delta.lengthSquared() > radiusSquared) {
               continue;
            }
            if (entity.getFireTicks() > 0) {
               entity.setFireTicks(0);
               extinguishedEntities++;
            }
         }

         if (source.getFireTicks() > 0) {
            source.setFireTicks(0);
         }

         String done = this.module.getMessage(
            "ext.radius-done",
            "&[SECONDARY]Потушено в радиусе &[MAIN]{radius}&[SECONDARY]: блоков &[MAIN]{blocks}&[SECONDARY], сущностей &[MAIN]{entities}&[SECONDARY]."
         )
            .replace("{radius}", Integer.toString(radius))
            .replace("{blocks}", Integer.toString(extinguishedBlocks))
            .replace("{entities}", Integer.toString(extinguishedEntities));
         this.sendMessage(source, done);
      });
   }

   private Integer tryParseRadius(String input) {
      try {
         return Integer.parseInt(input);
      } catch (NumberFormatException ignored) {
         return null;
      }
   }

   @Override
   public Collection<String> suggest(CommandSourceStack stack, String[] args) {
      if (args.length <= 1 && stack.getSender().hasPermission("smess.ext.others")) {
         String input = args.length > 0 ? args[0].toLowerCase() : "";
         return this.plugin.getServer().getOnlinePlayers().stream()
            .map(Player::getName)
            .filter(name -> name.toLowerCase().startsWith(input))
            .collect(Collectors.toList());
      }
      return new ArrayList<>();
   }

   private void sendMessage(CommandSender sender, String message) {
      if (message == null || message.isEmpty()) {
         return;
      }
      if (sender instanceof Player player) {
         this.plugin.getSchedulerManager().runEntityTask(player, "ext-command-message", () -> {
            if (player.isOnline()) {
               player.sendMessage(message);
            }
         });
      } else {
         sender.sendMessage(message);
      }
   }
}
