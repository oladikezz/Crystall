package net.schalker.SMPS.modules.help.listeners;

import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.listener.BaseListener;
import net.schalker.SMPS.modules.help.HelpModule;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

public class HelpListener extends BaseListener {
   private final HelpModule module;

   public HelpListener(DoAPI plugin, HelpModule module) {
      super(plugin);
      this.module = module;
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
      if (!this.module.isHelpEnabled()) {
         return;
      }

      String label = extractCommandLabel(event.getMessage(), true);
      if (!isHelpCommand(label)) {
         return;
      }

      event.setCancelled(true);
      this.module.sendHelp(event.getPlayer());
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onConsoleCommand(ServerCommandEvent event) {
      if (!this.module.isHelpEnabled()) {
         return;
      }

      String label = extractCommandLabel(event.getCommand(), false);
      if (!isHelpCommand(label)) {
         return;
      }

      event.setCancelled(true);
      this.module.sendHelp(event.getSender());
   }

   private String extractCommandLabel(String raw, boolean startsWithSlash) {
      if (raw == null) {
         return "";
      }

      String trimmed = raw.trim();
      if (trimmed.isEmpty()) {
         return "";
      }

      if (startsWithSlash && trimmed.startsWith("/")) {
         trimmed = trimmed.substring(1);
      }

      int space = trimmed.indexOf(' ');
      return space < 0 ? trimmed.toLowerCase() : trimmed.substring(0, space).toLowerCase();
   }

   private boolean isHelpCommand(String label) {
      if (label == null || label.isBlank()) {
         return false;
      }
      return "help".equals(label)
         || "?".equals(label)
         || label.endsWith(":help")
         || label.endsWith(":?");
   }
}

