package net.schalker.SMPS.modules.crowns.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.api.IModule;
import net.schalker.DoAPI.core.command.ModuleCommand;
import org.bukkit.command.CommandSender;

public class CrownsCommand extends ModuleCommand {

   public CrownsCommand(DoAPI plugin) {
      super(plugin);
   }

   @Override
   public String getName() {
      return "crowns";
   }

   @Override
   public String getPermission() {
      return "smcrowns.admin";
   }

   @Override
   public String getDescription() {
      return "Управление модулем корон";
   }

   @Override
   public String getUsage() {
      return "/crowns [reload]";
   }

   /**
    * Resolve current module instance dynamically.
    * Critical for surviving classloader changes after module unload/reload.
    */
   private IModule getCurrentModule() {
      return (IModule) plugin.getModuleManager().getModule("SM_Crowns");
   }

   private String invokeGetMessage(Object module, String key) {
      try {
         Method m = module.getClass().getMethod("getMessage", String.class);
         return (String) m.invoke(module, key);
      } catch (Exception e) {
         return "§cError: " + key;
      }
   }

   @Override
   public void execute(CommandSourceStack stack, String[] args) {
      CommandSender sender = stack.getSender();

      IModule currentModule = getCurrentModule();
      if (currentModule == null || !currentModule.isEnabled()) {
         sender.sendMessage("§cМодуль SM_Crowns не загружен.");
         return;
      }

      if (!sender.hasPermission("smcrowns.admin")) {
         sender.sendMessage(invokeGetMessage(currentModule, "no-permission"));
         return;
      }

      if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
         currentModule.reload();
         sender.sendMessage(invokeGetMessage(currentModule, "reload-done"));
         return;
      }

      sender.sendMessage(invokeGetMessage(currentModule, "usage"));
   }

   @Override
   public Collection<String> suggest(CommandSourceStack stack, String[] args) {
      if (args.length == 1 && stack.getSender().hasPermission("smcrowns.admin")) {
         return List.of("reload");
      }
      return List.of();
   }
}
