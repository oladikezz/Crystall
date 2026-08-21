package site.deforce.SM_Accounts.hooks;

import net.schalker.DoAPI.DoAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import site.deforce.SM_Accounts.SM_Accounts;
import site.deforce.SM_Accounts.database.AccountsDatabase;
import site.deforce.SM_Accounts.database.TwinksDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hook for LiteBans to sync punishments across main and twink accounts.
 */
public class LiteBansHook implements Listener {

    private final SM_Accounts module;
    private final DoAPI plugin;

    /**
     * Commands dispatched by this hook. Used to prevent infinite loops —
     * when we see a command we dispatched ourselves, we skip it.
     */
    private final Set<String> ownCommands = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Patterns for recognizing ban/mute/warn commands.
    // They now handle optional -s flag anywhere before the player name.
    private static final Pattern BAN_PATTERN = Pattern.compile(
            "^(?:litebans:)?(?:temp)?ban\\s+(?:-s\\s+)?(\\S+)(?:\\s+(.+))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern MUTE_PATTERN = Pattern.compile(
            "^(?:litebans:)?(?:temp)?mute\\s+(?:-s\\s+)?(\\S+)(?:\\s+(.+))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern WARN_PATTERN = Pattern.compile(
            "^(?:litebans:)?warn\\s+(?:-s\\s+)?(\\S+)(?:\\s+(.+))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNBAN_PATTERN = Pattern.compile(
            "^(?:litebans:)?(?:unban|pardon)\\s+(?:-s\\s+)?(\\S+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNMUTE_PATTERN = Pattern.compile(
            "^(?:litebans:)?unmute\\s+(?:-s\\s+)?(\\S+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNWARN_PATTERN = Pattern.compile(
            "^(?:litebans:)?unwarn\\s+(?:-s\\s+)?(\\S+)", Pattern.CASE_INSENSITIVE);

    public LiteBansHook(SM_Accounts module) {
        this.module = module;
        this.plugin = module.getPlugin();

        plugin.getListenerManager().registerListener(this);
        plugin.getDebugSystem().log("SM_Accounts", "LiteBans command sync listener enabled");
    }

    /**
     * Process commands from players — only if they have the relevant permission.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!module.isEnabled()) return;
        if (!module.getConfig().getBoolean("sync-punishments", true)) return;

        Player sender = event.getPlayer();
        String command = event.getMessage().substring(1); // Remove '/'

        // Ignore our own dispatched commands
        if (ownCommands.remove(command)) return;

        String baseCmd = command.split("\\s+")[0].toLowerCase().replace("litebans:", "");
        boolean hasPermission = switch (baseCmd) {
            case "ban", "tempban" -> sender.hasPermission("litebans.ban");
            case "mute", "tempmute" -> sender.hasPermission("litebans.mute");
            case "warn" -> sender.hasPermission("litebans.warn");
            case "unban", "pardon" -> sender.hasPermission("litebans.unban");
            case "unmute" -> sender.hasPermission("litebans.unmute");
            case "unwarn" -> sender.hasPermission("litebans.unwarn");
            default -> false;
        };

        if (!hasPermission) return;

        processCommand(command);
    }

    /**
     * Process commands from console
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        if (!module.isEnabled()) return;
        if (!module.getConfig().getBoolean("sync-punishments", true)) return;

        String command = event.getCommand();

        // Ignore our own dispatched commands
        if (ownCommands.remove(command)) return;

        processCommand(command);
    }

    /**
     * Process a command
     */
    private void processCommand(String command) {
        Matcher banMatcher = BAN_PATTERN.matcher(command);
        if (banMatcher.find()) {
            String playerName = banMatcher.group(1);
            if (playerName.equals("-s")) return; // edge case: -s parsed as name
            String args = banMatcher.group(2);
            plugin.getSchedulerManager().runTaskLater("litebans-ban-sync-" + playerName,
                    () -> handleBan(playerName, args), 20L);
            return;
        }

        Matcher muteMatcher = MUTE_PATTERN.matcher(command);
        if (muteMatcher.find()) {
            String playerName = muteMatcher.group(1);
            if (playerName.equals("-s")) return;
            String args = muteMatcher.group(2);
            plugin.getSchedulerManager().runTaskLater("litebans-mute-sync-" + playerName,
                    () -> handleMute(playerName, args), 20L);
            return;
        }

        Matcher warnMatcher = WARN_PATTERN.matcher(command);
        if (warnMatcher.find()) {
            String playerName = warnMatcher.group(1);
            if (playerName.equals("-s")) return;
            String args = warnMatcher.group(2);
            plugin.getSchedulerManager().runTaskLater("litebans-warn-sync-" + playerName,
                    () -> handleWarn(playerName, args), 20L);
            return;
        }

        Matcher unbanMatcher = UNBAN_PATTERN.matcher(command);
        if (unbanMatcher.find()) {
            String playerName = unbanMatcher.group(1);
            if (playerName.equals("-s")) return;
            plugin.getSchedulerManager().runTaskLater("litebans-unban-sync-" + playerName,
                    () -> handleUnban(playerName), 20L);
            return;
        }

        Matcher unmuteMatcher = UNMUTE_PATTERN.matcher(command);
        if (unmuteMatcher.find()) {
            String playerName = unmuteMatcher.group(1);
            if (playerName.equals("-s")) return;
            plugin.getSchedulerManager().runTaskLater("litebans-unmute-sync-" + playerName,
                    () -> handleUnmute(playerName), 20L);
            return;
        }

        Matcher unwarnMatcher = UNWARN_PATTERN.matcher(command);
        if (unwarnMatcher.find()) {
            String playerName = unwarnMatcher.group(1);
            if (playerName.equals("-s")) return;
            plugin.getSchedulerManager().runTaskLater("litebans-unwarn-sync-" + playerName,
                    () -> handleUnwarn(playerName), 20L);
        }
    }

    // ─── Handlers ────────────────────────────────────────────────────────

    private void handleBan(String playerName, String args) {
        List<String> toSync = getSyncTargets(playerName);
        if (toSync.isEmpty()) return;

        long duration = parseDuration(args);
        String originalReason = parseReason(args);
        String reason = getReason("ban", playerName, originalReason);

        for (String target : toSync) {
            String cmd = buildCommand("ban", target, duration, reason);
            executeCommand(cmd);
        }

        plugin.getDebugSystem().log("SM_Accounts",
                "Synced ban of " + playerName + " to " + toSync.size() + " accounts: " + toSync);
    }

    private void handleMute(String playerName, String args) {
        List<String> toSync = getSyncTargets(playerName);
        if (toSync.isEmpty()) return;

        long duration = parseDuration(args);
        String originalReason = parseReason(args);
        String reason = getReason("mute", playerName, originalReason);

        for (String target : toSync) {
            String cmd = buildCommand("mute", target, duration, reason);
            executeCommand(cmd);
        }

        plugin.getDebugSystem().log("SM_Accounts",
                "Synced mute of " + playerName + " to " + toSync.size() + " accounts: " + toSync);
    }

    private void handleWarn(String playerName, String args) {
        List<String> toSync = getSyncTargets(playerName);
        if (toSync.isEmpty()) return;

        String originalReason = parseReason(args);
        String reason = getReason("warn", playerName, originalReason);

        for (String target : toSync) {
            String cmd = buildCommand("warn", target, 0, reason);
            executeCommand(cmd);
        }

        plugin.getDebugSystem().log("SM_Accounts",
                "Synced warn of " + playerName + " to " + toSync.size() + " accounts: " + toSync);
    }

    private void handleUnban(String playerName) {
        List<String> toSync = getSyncTargets(playerName);
        if (toSync.isEmpty()) return;

        String reason = getReason("unban", playerName, null);

        for (String target : toSync) {
            String cmd = buildCommand("unban", target, 0, reason);
            executeCommand(cmd);
        }

        plugin.getDebugSystem().log("SM_Accounts",
                "Synced unban of " + playerName + " to " + toSync.size() + " accounts: " + toSync);
    }

    private void handleUnmute(String playerName) {
        List<String> toSync = getSyncTargets(playerName);
        if (toSync.isEmpty()) return;

        String reason = getReason("unmute", playerName, null);

        for (String target : toSync) {
            String cmd = buildCommand("unmute", target, 0, reason);
            executeCommand(cmd);
        }

        plugin.getDebugSystem().log("SM_Accounts",
                "Synced unmute of " + playerName + " to " + toSync.size() + " accounts: " + toSync);
    }

    private void handleUnwarn(String playerName) {
        List<String> toSync = getSyncTargets(playerName);
        if (toSync.isEmpty()) return;

        String reason = getReason("unwarn", playerName, null);

        for (String target : toSync) {
            String cmd = buildCommand("unwarn", target, 0, reason);
            executeCommand(cmd);
        }

        plugin.getDebugSystem().log("SM_Accounts",
                "Synced unwarn of " + playerName + " to " + toSync.size() + " accounts: " + toSync);
    }

    // ─── Sync targets ────────────────────────────────────────────────────

    private List<String> getSyncTargets(String playerName) {
        TwinksDatabase twinksDb = module.getTwinksDatabase();
        if (twinksDb == null) return List.of();

        // If the ORIGINAL target is exempt — do not sync at all
        if (isExempt(playerName)) {
            plugin.getDebugSystem().log("SM_Accounts",
                    "Skipping punishment sync — original target " + playerName + " is exempt");
            return List.of();
        }

        // Get ALL related accounts (main + all twinks), then remove the original target
        List<String> allRelated = twinksDb.getAllRelatedUsernames(playerName);
        List<String> targets = new ArrayList<>(allRelated);
        targets.removeIf(name -> name.equalsIgnoreCase(playerName));

        // Filter out exempt players (admin main accounts etc.)
        targets.removeIf(this::isExempt);

        plugin.getDebugSystem().log("SM_Accounts",
                "Sync targets for " + playerName + ": " + targets);

        return targets;
    }

    private boolean isExempt(String playerName) {
        Player player = Bukkit.getPlayerExact(playerName);
        if (player != null && player.hasPermission("smaccs.sync.exempt")) {
            plugin.getDebugSystem().log("SM_Accounts",
                    "Exempt (permission): " + playerName);
            return true;
        }

        List<String> exemptList = module.getConfig().getStringList("sync-exempt-players");
        for (String exempt : exemptList) {
            if (exempt.equalsIgnoreCase(playerName)) {
                plugin.getDebugSystem().log("SM_Accounts",
                        "Exempt (config list): " + playerName);
                return true;
            }
        }

        return false;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private String getReason(String type, String origin, String originalReason) {
        String template = module.getConfig().getString("sync-reasons." + type,
                "Synced " + type + " from {origin}");
        template = template.replace("{origin}", origin);
        if (originalReason != null) {
            template = template.replace("{original_reason}", originalReason);
        } else {
            template = template.replace("{original_reason}", "");
        }
        return template;
    }

    private String parseReason(String args) {
        if (args == null || args.isEmpty()) return null;

        // Remove -s flag if present
        args = args.replaceAll("(?i)^-s\\s+", "").trim();

        Pattern durationPattern = Pattern.compile("^(\\d+[smhd])\\s+(.*)");
        Matcher matcher = durationPattern.matcher(args);
        if (matcher.find()) {
            return matcher.group(2);
        }

        if (args.matches("^\\d+[smhd]$")) {
            return null; // Just duration
        }

        return args;
    }

    /**
     * Build a LiteBans command. Resolves the target's UUID and uses the
     * {@code #UUID} syntax so LiteBans bans by UUID only — no IP ban.
     * Falls back to the player name if UUID cannot be resolved.
     */
    private String buildCommand(String type, String target, long duration, String reason) {
        // Resolve UUID to avoid IP-based banning
        String resolvedTarget = resolveTarget(target);

        StringBuilder sb = new StringBuilder();
        sb.append(type).append(" -s ").append(resolvedTarget);
        if (duration > 0) {
            sb.append(" ").append(formatDuration(duration));
        }
        if (reason != null && !reason.isBlank()) {
            sb.append(" ").append(reason);
        }
        return sb.toString();
    }

    /**
     * Resolve a player name to {@code #UUID} for LiteBans.
     * Using {@code #UUID} tells LiteBans to ban by UUID only, bypassing IP ban.
     * Falls back to name if UUID is not found.
     */
    private String resolveTarget(String playerName) {
        // Try online player first
        Player onlinePlayer = Bukkit.getPlayerExact(playerName);
        if (onlinePlayer != null) {
            String uuidTarget = "#" + onlinePlayer.getUniqueId();
            plugin.getDebugSystem().log("SM_Accounts",
                    "Resolved " + playerName + " → " + uuidTarget + " (online)");
            return uuidTarget;
        }

        // Try AccountsDatabase
        AccountsDatabase db = module.getDatabase();
        if (db != null) {
            UUID uuid = db.getUUID(playerName);
            if (uuid != null) {
                String uuidTarget = "#" + uuid;
                plugin.getDebugSystem().log("SM_Accounts",
                        "Resolved " + playerName + " → " + uuidTarget + " (database)");
                return uuidTarget;
            }
        }

        // Fallback to name (will use LiteBans default behavior including potential IP ban)
        plugin.getDebugSystem().log("SM_Accounts",
                "Could not resolve UUID for " + playerName + ", using name (IP ban may apply)");
        return playerName;
    }

    /**
     * Execute a command as console. Registers the command string in {@link #ownCommands}
     * so that the listener ignores it when it fires back through ServerCommandEvent.
     */
    private void executeCommand(String command) {
        ownCommands.add(command);
        plugin.getSchedulerManager().runTaskLater("litebans-sync-cmd", () -> {
            plugin.getDebugSystem().log("SM_Accounts", "Executing sync command: " + command);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            // Clean up after a delay in case the event already fired
            plugin.getSchedulerManager().runTaskLater("litebans-sync-cleanup", () -> {
                ownCommands.remove(command);
            }, 5L);
        }, 1L);
    }

    private long parseDuration(String args) {
        if (args == null || args.isEmpty()) return 0;

        // Remove -s flag if present
        args = args.replaceAll("(?i)^-s\\s+", "").trim();

        Pattern durationPattern = Pattern.compile("(\\d+)([smhd])");
        Matcher matcher = durationPattern.matcher(args);

        if (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2);
            return switch (unit) {
                case "s" -> value * 1000L;
                case "m" -> value * 60 * 1000L;
                case "h" -> value * 60 * 60 * 1000L;
                case "d" -> value * 24 * 60 * 60 * 1000L;
                default -> 0;
            };
        }

        return 0;
    }

    private String formatDuration(long durationMs) {
        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) return days + "d";
        if (hours > 0) return hours + "h";
        if (minutes > 0) return minutes + "m";
        return seconds + "s";
    }
}
