package net.schalker.SMPS.modules.flags.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.api.IModule;
import net.schalker.DoAPI.core.command.ModuleCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Slim Brigadier command delegator for /flags.
 * <p>
 * All actual command logic lives in FlagsModule.handleCommand() / handleSuggest().
 * This class only resolves the current module instance via reflection and delegates.
 * <p>
 * This design is critical for hot-swap: Brigadier commands cannot be unregistered,
 * so this old handler will persist across module reloads. By delegating everything
 * to the module (which IS replaced on reload), new subcommands and logic are picked
 * up immediately without a server restart.
 */
public class FlagsCommand extends ModuleCommand {

   public FlagsCommand(DoAPI plugin) {
      super(plugin);
   }

   @Override
   public String getName() {
      return "flags";
   }

   @Override
   public String getPermission() {
      return "smflags.menu";
   }

   @Override
   public String getDescription() {
      return "Управление системой флагов";
   }

   @Override
   public String getUsage() {
      return "/flags [help|reload|history|mute|unmute|clearplayer|clearhistory]";
   }

   private IModule getCurrentModule() {
      return (IModule) plugin.getModuleManager().getModule("SM_Flags");
   }

   @Override
   public void execute(CommandSourceStack stack, String[] args) {
      CommandSender sender = stack.getSender();

      if (!(sender instanceof Player player)) {
         sender.sendMessage("§cТолько игроки могут использовать эту команду.");
         return;
      }

      IModule currentModule = getCurrentModule();
      if (currentModule == null || !currentModule.isEnabled()) {
         sender.sendMessage("§cМодуль SM_Flags не загружен.");
         return;
      }

      try {
         currentModule.getClass()
            .getMethod("handleCommand", Player.class, String[].class)
            .invoke(currentModule, player, args);
      } catch (Exception e) {
         sender.sendMessage("§cОшибка выполнения команды.");
         e.printStackTrace();
      }
   }

   @Override
   @SuppressWarnings("unchecked")
   public Collection<String> suggest(CommandSourceStack stack, String[] args) {
      IModule currentModule = getCurrentModule();
      if (currentModule == null || !currentModule.isEnabled()) {
         return List.of();
      }
      try {
         Object result = currentModule.getClass()
            .getMethod("handleSuggest", CommandSender.class, String[].class)
            .invoke(currentModule, stack.getSender(), args);
         return (Collection<String>) result;
      } catch (Exception e) {
         return List.of();
      }
   }
}
