package site.deforce.SM_Accounts.tasks;

import net.schalker.DoAPI.DoAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import site.deforce.SM_Accounts.SM_Accounts;
import site.deforce.SM_Accounts.database.AccountsDatabase;
import site.deforce.SM_Accounts.hooks.DiscordBotManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Repeating task that synchronizes Minecraft player names to Discord nicknames.
 * Only processes players who have the required permission(s).
 */
public class NicknameSyncTask implements Runnable {

    private final SM_Accounts module;
    private final DoAPI plugin;

    public NicknameSyncTask(SM_Accounts module) {
        this.module = module;
        this.plugin = module.getPlugin();
    }

    @Override
    public void run() {
        DiscordBotManager botManager = module.getDiscordBotManager();
        if (botManager == null || !botManager.isReady()) {
            plugin.getDebugSystem().log("SM_Accounts", "Nickname sync skipped - bot not ready");
            return;
        }

        AccountsDatabase database = module.getDatabase();
        if (database == null) {
            plugin.getDebugSystem().log("SM_Accounts", "Nickname sync skipped - database not available");
            return;
        }

        // Get permission list from config
        List<String> permissions = module.getConfig().getStringList("nickname-sync.permissions");
        if (permissions.isEmpty()) {
            // Fallback defaults
            permissions = List.of("smaccs.nicknamesync", "discordsrv.nicknamesync");
        }

        // Collect eligible players from the main thread context
        // Since this task runs async, we need to safely read online players
        // Bukkit.getOnlinePlayers() is generally thread-safe for reading
        List<PlayerSyncEntry> toSync = new ArrayList<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                // If smaccs.nicknamesync is explicitly set to false, always skip
                if (player.isPermissionSet("smaccs.nicknamesync")
                        && !player.hasPermission("smaccs.nicknamesync")) {
                    continue;
                }

                boolean hasPermission = false;
                for (String perm : permissions) {
                    if (player.hasPermission(perm)) {
                        hasPermission = true;
                        break;
                    }
                }

                if (!hasPermission) {
                    continue;
                }

                UUID uuid = player.getUniqueId();
                String mcName = player.getName();
                toSync.add(new PlayerSyncEntry(uuid, mcName));
            } catch (Exception e) {
                plugin.getDebugSystem().logError("Error collecting player for nickname sync: " + player.getName(), e);
            }
        }

        if (toSync.isEmpty()) {
            plugin.getDebugSystem().log("SM_Accounts", "Nickname sync: no eligible players online");
            return;
        }

        plugin.getDebugSystem().log("SM_Accounts",
                "Nickname sync: processing " + toSync.size() + " eligible player(s)");

        int success = 0;
        int skipped = 0;
        int failed = 0;

        for (PlayerSyncEntry entry : toSync) {
            try {
                String discordId = database.getDiscordId(entry.uuid());
                if (discordId == null || discordId.isBlank()) {
                    skipped++;
                    continue; // Player not linked
                }

                boolean result = botManager.setNickname(discordId, entry.mcName());
                if (result) {
                    success++;
                } else {
                    failed++;
                }

                // Small delay between API calls to respect rate limits
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                plugin.getDebugSystem().log("SM_Accounts", "Nickname sync interrupted");
                return;
            } catch (Exception e) {
                failed++;
                plugin.getDebugSystem().logError("Error syncing nickname for " + entry.mcName(), e);
            }
        }

        plugin.getDebugSystem().log("SM_Accounts",
                "Nickname sync complete: " + success + " synced, " + skipped + " skipped (not linked), " + failed + " failed");
    }

    /**
     * Data record for a player pending nickname sync.
     */
    private record PlayerSyncEntry(UUID uuid, String mcName) {}
}

