package site.deforce.SM_Accounts.listeners;

import net.schalker.DoAPI.DoAPI;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import site.deforce.SM_Accounts.SM_Accounts;
import site.deforce.SM_Accounts.database.AccountsDatabase;
import site.deforce.SM_Accounts.utils.DiscordSRVHelper; // Import utility

import java.util.UUID;

/**
 * Fallback listener for commands when Brigadier registration fails.
 */
public class CommandListener implements Listener {

    private final SM_Accounts module;
    private final DoAPI plugin;

    public CommandListener(SM_Accounts module) {
        this.module = module;
        this.plugin = module.getPlugin();
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!module.isEnabled()) return;
        // No commands handled here anymore
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onServerCommand(ServerCommandEvent event) {
        if (!module.isEnabled()) return;
        // No commands handled here anymore
    }

}
