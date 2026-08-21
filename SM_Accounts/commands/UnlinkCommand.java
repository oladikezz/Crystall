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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Full account unlink command. Removes the player's link from:
 * <ol>
 *   <li>sm_accounts (SMPS primary database)</li>
 *   <li>s3_SchalkerSiteDB.users — looked up by discord_id (SMPS "database3")</li>
 *   <li>s3_SchalkerSiteDB.player_profiles — looked up by user_id (SMPS "database3")</li>
 *   <li>DiscordSRV accounts.aof</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>
 *   /unlink &lt;nickname&gt;            — preview what will be deleted
 *   /unlink &lt;nickname&gt; --confirm  — actually delete (must preview first)
 * </pre>
 * <p>Permission: <code>smaccs.unlink</code>
 */
public class UnlinkCommand implements CommandExecutor, TabCompleter {

    private final SM_Accounts module;
    private final DoAPI plugin;

    /** SMPS database name for s3_SchalkerSiteDB */
    private static final String SITE_DB = "database3";

    /** Pending confirmations: sender key → pending unlink (expires after 60 s) */
    private final Map<String, PendingUnlink> pendingConfirmations = new ConcurrentHashMap<>();

    public UnlinkCommand(SM_Accounts module) {
        this.module = module;
        this.plugin = module.getPlugin();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        plugin.getDebugSystem().log("SM_Accounts", "UnlinkCommand.onCommand called by " + sender.getName()
                + " | args=" + java.util.Arrays.toString(args) + " | module.isEnabled=" + module.isEnabled());

        if (!module.isEnabled()) {
            plugin.getDebugSystem().log("SM_Accounts", "Unlink: module is disabled, ignoring");
            return true;
        }

        if (!sender.hasPermission("smaccs.unlink") && !sender.isOp() && sender instanceof Player) {
            sender.sendMessage(plugin.applyColors(getMessage("unlink-messages.no-permission")));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(plugin.applyColors(getMessage("unlink-messages.usage")));
            return true;
        }

        String targetName = args[0];
        boolean confirm = args.length >= 2 && args[1].equalsIgnoreCase("--confirm");

        AccountsDatabase database = module.getDatabase();
        if (database == null) {
            sender.sendMessage(plugin.applyColors(getMessage("unlink-messages.db-error")));
            return true;
        }

        if (confirm) {
            PendingUnlink pending = pendingConfirmations.remove(senderKey(sender));
            if (pending == null || !pending.targetName().equalsIgnoreCase(targetName)
                    || System.currentTimeMillis() - pending.timestamp() > 60_000L) {
                sender.sendMessage(plugin.applyColors(getMessage("unlink-messages.no-pending")
                        .replace("{player}", targetName)));
                return true;
            }
            executeUnlink(sender, targetName);
        } else {
            previewUnlink(sender, targetName);
        }

        return true;
    }

    // ─── Preview ────────────────────────────────────────────────────────

    private void previewUnlink(CommandSender sender, String targetName) {
        plugin.getSchedulerManager().runAsync("unlink-preview-" + targetName.toLowerCase(), () -> {
            AccountsDatabase database = module.getDatabase();

            if (!database.existsByUsername(targetName)) {
                sender.sendMessage(plugin.applyColors(getMessage("unlink-messages.not-found")
                        .replace("{player}", targetName)));
                return;
            }

            String discordId = database.getDiscordIdByUsername(targetName);
            boolean hasDiscord = discordId != null && !discordId.isBlank();

            boolean hasSiteUser = false;
            boolean hasSiteProfile = false;
            String siteUserId = null;
            boolean siteDbAvailable = isSiteDbAvailable();

            if (hasDiscord && siteDbAvailable) {
                try (Connection conn = plugin.getDatabaseManager().getConnection(SITE_DB)) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT id FROM users WHERE discord_id = ?")) {
                        ps.setString(1, discordId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                hasSiteUser = true;
                                siteUserId = rs.getString("id");
                            }
                        }
                    }

                    if (siteUserId != null) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "SELECT id FROM player_profiles WHERE user_id = ?")) {
                            ps.setString(1, siteUserId);
                            try (ResultSet rs = ps.executeQuery()) {
                                hasSiteProfile = rs.next();
                            }
                        }
                    }
                } catch (Exception e) {
                    plugin.getDebugSystem().logError("Failed to preview site DB for unlink", e);
                    sender.sendMessage(plugin.applyColors(getMessage("unlink-messages.site-db-error")));
                }
            }

            // Show preview
            sender.sendMessage(plugin.applyColors(getMessage("unlink-messages.preview-header")
                    .replace("{player}", targetName)));
            sender.sendMessage(plugin.applyColors("&7├ sm_accounts: &f" + targetName
                    + (hasDiscord ? " &7(Discord: &f" + discordId + "&7)" : " &7(не привязан)")));

            if (siteDbAvailable) {
                if (hasSiteUser) {
                    sender.sendMessage(plugin.applyColors("&7├ s3_SchalkerSiteDB.users: &fid=" + siteUserId));
                }
                if (hasSiteProfile) {
                    sender.sendMessage(plugin.applyColors("&7├ s3_SchalkerSiteDB.player_profiles: &fuser_id=" + siteUserId));
                }
                if (!hasSiteUser) {
                    sender.sendMessage(plugin.applyColors("&7├ s3_SchalkerSiteDB: &7нет записей"));
                }
            } else {
                sender.sendMessage(plugin.applyColors("&7├ s3_SchalkerSiteDB: &eнедоступна (database3 не подключена)"));
            }

            sender.sendMessage(plugin.applyColors(getMessage("unlink-messages.preview-confirm")
                    .replace("{player}", targetName)));

            pendingConfirmations.put(senderKey(sender),
                    new PendingUnlink(targetName, System.currentTimeMillis()));
        });
    }

    // ─── Execute ────────────────────────────────────────────────────────

    private void executeUnlink(CommandSender sender, String targetName) {
        plugin.getSchedulerManager().runAsync("unlink-exec-" + targetName.toLowerCase(), () -> {
            AccountsDatabase database = module.getDatabase();

            String discordId = database.getDiscordIdByUsername(targetName);
            boolean hasDiscord = discordId != null && !discordId.isBlank();

            // 1. Clean up site DB via SMPS database3
            if (hasDiscord && isSiteDbAvailable()) {
                try (Connection conn = plugin.getDatabaseManager().getConnection(SITE_DB)) {
                    String siteUserId = null;

                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT id FROM users WHERE discord_id = ?")) {
                        ps.setString(1, discordId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                siteUserId = rs.getString("id");
                            }
                        }
                    }

                    if (siteUserId != null) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "DELETE FROM player_profiles WHERE user_id = ?")) {
                            ps.setString(1, siteUserId);
                            int deleted = ps.executeUpdate();
                            plugin.getDebugSystem().log("SM_Accounts",
                                    "Deleted " + deleted + " player_profile(s) for user_id=" + siteUserId);
                        }

                        try (PreparedStatement ps = conn.prepareStatement(
                                "DELETE FROM users WHERE id = ?")) {
                            ps.setString(1, siteUserId);
                            int deleted = ps.executeUpdate();
                            plugin.getDebugSystem().log("SM_Accounts",
                                    "Deleted " + deleted + " user(s) with id=" + siteUserId);
                        }

                        sender.sendMessage(plugin.applyColors("&7├ &aУдалено из s3_SchalkerSiteDB"));
                    } else {
                        sender.sendMessage(plugin.applyColors("&7├ &7s3_SchalkerSiteDB: нет записей (пропуск)"));
                    }
                } catch (Exception e) {
                    plugin.getDebugSystem().logError("Failed to clean site DB during unlink for " + targetName, e);
                    sender.sendMessage(plugin.applyColors("&7├ &cs3_SchalkerSiteDB: ошибка — " + e.getMessage()));
                }
            }

            // 2. Remove from DiscordSRV accounts.aof
            if (hasDiscord) {
                UUID targetUuid = database.getUUID(targetName);
                if (targetUuid != null) {
                    new site.deforce.SM_Accounts.utils.DiscordSRVHelper(plugin)
                            .removeEntry(discordId, targetUuid);
                    sender.sendMessage(plugin.applyColors("&7├ &aУдалено из DiscordSRV accounts.aof"));
                }
            }

            // 3. Delete from sm_accounts
            boolean deleted = database.deleteByUsername(targetName);
            if (deleted) {
                sender.sendMessage(plugin.applyColors("&7├ &aУдалено из sm_accounts"));
            } else {
                sender.sendMessage(plugin.applyColors("&7├ &csm_accounts: запись не найдена"));
            }

            // 4. Kick the player if online (Folia-safe: use entity task)
            Player target = Bukkit.getPlayerExact(targetName);
            if (target != null && target.isOnline()) {
                String kickMsg = module.getConfig().getString("unlink-kick-message",
                        "&cYou have been unlinked by an administrator.");
                final String finalKickMsg = plugin.applyColors(kickMsg);
                plugin.getDebugSystem().log("SM_Accounts", "Kicking " + targetName + " (online) after unlink");
                plugin.getSchedulerManager().runEntityTask(target, "unlink-kick-" + targetName, () ->
                        target.kick(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                                .legacySection().deserialize(finalKickMsg)));
                sender.sendMessage(plugin.applyColors("&7├ &aИгрок кикнут с сервера"));
            } else {
                plugin.getDebugSystem().log("SM_Accounts", targetName + " is not online, skip kick");
            }

            sender.sendMessage(plugin.applyColors(getMessage("unlink-messages.success")
                    .replace("{player}", targetName)));

            plugin.getDebugSystem().log("SM_Accounts",
                    "Full unlink completed for " + targetName + " by " + sender.getName());
        });
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    /**
     * Check if SMPS database3 (s3_SchalkerSiteDB) is connected.
     */
    private boolean isSiteDbAvailable() {
        try {
            return plugin.getDatabaseManager().isConnected(SITE_DB);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            List<String> playerNames = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                playerNames.add(p.getName());
            }
            StringUtil.copyPartialMatches(args[0], playerNames, suggestions);
            Collections.sort(suggestions);
            return suggestions;
        }
        if (args.length == 2) {
            return List.of("--confirm");
        }
        return Collections.emptyList();
    }

    private String senderKey(CommandSender sender) {
        if (sender instanceof Player p) {
            return p.getUniqueId().toString();
        }
        return "CONSOLE";
    }

    private String getMessage(String path) {
        return module.getConfig().getString(path, "&c[Missing message: " + path + "]");
    }

    private record PendingUnlink(String targetName, long timestamp) {}
}

