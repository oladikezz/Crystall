package site.deforce.SM_Accounts.commands;

import net.schalker.DoAPI.DoAPI;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import site.deforce.SM_Accounts.SM_Accounts;
import site.deforce.SM_Accounts.database.AccountsDatabase;
import site.deforce.SM_Accounts.hooks.DiscordBotManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Debug command for manually triggering Discord nickname sync.
 * Usage: /nicksync         - Sync all online players
 *        /nicksync <player> - Sync a specific player
 * Permission: smaccs.nicksync
 */
public class NickSyncCommand implements CommandExecutor, TabCompleter {

    private final SM_Accounts module;
    private final DoAPI plugin;

    public NickSyncCommand(SM_Accounts module) {
        this.module = module;
        this.plugin = module.getPlugin();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!module.isEnabled()) return true;

        if (!sender.hasPermission("smaccs.nicksync") && !sender.isOp() && sender instanceof Player) {
            sender.sendMessage(plugin.applyColors(getMessage("nicksync-messages.no-permission")));
            return true;
        }

        DiscordBotManager botManager = module.getDiscordBotManager();
        if (botManager == null || !botManager.isReady()) {
            sender.sendMessage(plugin.applyColors(getMessage("nicksync-messages.bot-not-ready")));
            return true;
        }

        AccountsDatabase database = module.getDatabase();
        if (database == null) {
            sender.sendMessage(plugin.applyColors(getMessage("nicksync-messages.db-error")));
            return true;
        }

        if (args.length == 0) {
            // Sync all online players
            syncAll(sender, botManager, database);
        } else {
            // Sync specific player
            String targetName = args[0];
            syncPlayer(sender, botManager, database, targetName);
        }

        return true;
    }

    /**
     * Sync all online players' nicknames.
     */
    private void syncAll(CommandSender sender, DiscordBotManager botManager, AccountsDatabase database) {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) {
            sender.sendMessage(plugin.applyColors(getMessage("nicksync-messages.no-players")));
            return;
        }

        sender.sendMessage(plugin.applyColors(
                getMessage("nicksync-messages.sync-all-start")
                        .replace("{count}", String.valueOf(online.size()))));

        plugin.getSchedulerManager().runAsync("nicksync-manual-all", () -> {
            int success = 0;
            int skipped = 0;
            int failed = 0;

            for (Player player : online) {
                try {
                    String discordId = database.getDiscordId(player.getUniqueId());
                    if (discordId == null || discordId.isBlank()) {
                        skipped++;
                        sender.sendMessage(plugin.applyColors(
                                getMessage("nicksync-messages.player-not-linked")
                                        .replace("{player}", player.getName())));
                        continue;
                    }

                    boolean result = botManager.setNickname(discordId, player.getName());
                    if (result) {
                        success++;
                        sender.sendMessage(plugin.applyColors(
                                getMessage("nicksync-messages.player-synced")
                                        .replace("{player}", player.getName())));
                    } else {
                        failed++;
                        sender.sendMessage(plugin.applyColors(
                                getMessage("nicksync-messages.player-failed")
                                        .replace("{player}", player.getName())));
                    }

                    // Rate limit protection
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    sender.sendMessage(plugin.applyColors(getMessage("nicksync-messages.interrupted")));
                    return;
                } catch (Exception e) {
                    failed++;
                    plugin.getDebugSystem().logError("Error in manual nicksync for " + player.getName(), e);
                }
            }

            sender.sendMessage(plugin.applyColors(
                    getMessage("nicksync-messages.sync-all-complete")
                            .replace("{success}", String.valueOf(success))
                            .replace("{skipped}", String.valueOf(skipped))
                            .replace("{failed}", String.valueOf(failed))
                            .replace("{total}", String.valueOf(online.size()))));
        });
    }

    /**
     * Sync a specific player's nickname.
     */
    private void syncPlayer(CommandSender sender, DiscordBotManager botManager, AccountsDatabase database, String targetName) {
        // Try online player first
        Player target = Bukkit.getPlayerExact(targetName);
        final UUID uuid;
        final String mcName;

        if (target != null) {
            uuid = target.getUniqueId();
            mcName = target.getName();
        } else {
            // Try to look up UUID from database for offline players
            UUID lookedUp = database.getUUID(targetName);
            if (lookedUp == null) {
                sender.sendMessage(plugin.applyColors(
                        getMessage("nicksync-messages.player-not-found")
                                .replace("{player}", targetName)));
                return;
            }
            uuid = lookedUp;
            mcName = targetName;
        }

        sender.sendMessage(plugin.applyColors(
                getMessage("nicksync-messages.sync-player-start")
                        .replace("{player}", mcName)));

        plugin.getSchedulerManager().runAsync("nicksync-manual-" + mcName.toLowerCase(), () -> {
            String discordId = database.getDiscordId(uuid);
            if (discordId == null || discordId.isBlank()) {
                sender.sendMessage(plugin.applyColors(
                        getMessage("nicksync-messages.player-not-linked")
                                .replace("{player}", mcName)));
                return;
            }

            boolean result = botManager.setNickname(discordId, mcName);
            if (result) {
                sender.sendMessage(plugin.applyColors(
                        getMessage("nicksync-messages.player-synced")
                                .replace("{player}", mcName)));
            } else {
                sender.sendMessage(plugin.applyColors(
                        getMessage("nicksync-messages.player-failed")
                                .replace("{player}", mcName)));
            }
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            // Suggest online player names
            List<String> suggestions = new ArrayList<>();
            List<String> playerNames = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                playerNames.add(p.getName());
            }
            StringUtil.copyPartialMatches(args[0], playerNames, suggestions);
            Collections.sort(suggestions);
            return suggestions;
        }
        return Collections.emptyList();
    }

    private String getMessage(String path) {
        return module.getConfig().getString(path, "&cMessage not found: " + path);
    }
}

