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
import site.deforce.SM_Accounts.database.TwinksDatabase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TwinkCommand implements CommandExecutor, TabCompleter {

    private final SM_Accounts module;
    private final DoAPI plugin;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public TwinkCommand(SM_Accounts module) {
        this.module = module;
        this.plugin = module.getPlugin();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!module.isEnabled()) return true;

        String cmdName = command.getName().toLowerCase();

        if (cmdName.equals("twinks")) {
            handleTwinks(sender);
            return true;
        }

        if (cmdName.equals("twink")) {
            handleTwink(sender, args);
            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("twink")) {
            return Collections.emptyList();
        }

        if (!sender.hasPermission("smaccs.twink.add") &&
            !sender.hasPermission("smaccs.twink.remove") &&
            !sender.hasPermission("smaccs.twink.list") &&
            !sender.hasPermission("smaccs.twink.info") &&
            !sender.isOp()) {
            return Collections.emptyList();
        }

        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            List<String> commands = new ArrayList<>(Arrays.asList("add", "remove", "list", "info"));
            StringUtil.copyPartialMatches(args[0], commands, suggestions);
            Collections.sort(suggestions);
            return suggestions;
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("add") || sub.equals("info") || sub.equals("list")) {
                // Suggest all players for main/info/list
                return null; // Return null to let Bukkit suggest online players
            }
            if (sub.equals("remove")) {
                // Ideally suggest only twinks, but querying DB might be slow for tab complete
                // So we'll suggest online players or nothing
                return null;
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("add")) {
             // Second arg for add is twink name, suggest players
             return null;
        }

        return suggestions;
    }

    /**
     * Handle /twinks command - shows all twinks for the sender's account.
     * Permission: smaccs.twinks
     */
    private void handleTwinks(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.applyColors(getMessage("twink-messages.player-only")));
            return;
        }

        if (!player.hasPermission("smaccs.twinks")) {
            sender.sendMessage(plugin.applyColors(getMessage("twink-messages.no-permission")));
            return;
        }

        plugin.getSchedulerManager().runAsync("twinks-list", () -> {
            TwinksDatabase db = module.getTwinksDatabase();
            if (db == null) {
                sender.sendMessage(plugin.applyColors(getMessage("twink-messages.db-error")));
                return;
            }

            String playerName = player.getName();

            // Check if player is a twink
            if (db.isTwink(playerName)) {
                String mainName = db.getMainUsername(playerName);
                List<String> messages = module.getConfig().getStringList("twink-messages.you-are-twink");
                if (messages.isEmpty()) {
                    // Fallback if list is empty or missing
                    sender.sendMessage(plugin.applyColors("&[MAIN]You are a twink account of &[SECONDARY]" + mainName + "&[MAIN]."));
                    sender.sendMessage(plugin.applyColors("&[MAIN]Ask an administrator to view your twinks."));
                } else {
                    for (String msg : messages) {
                        sender.sendMessage(plugin.applyColors(msg.replace("{main}", mainName)));
                    }
                }
                return;
            }

            // Check if player is a main
            List<String> twinks = db.getTwinkUsernames(playerName);
            if (twinks.isEmpty()) {
                String you = module.getConfig().getString("twink-messages.you", "You");
                sender.sendMessage(plugin.applyColors(getMessage("twink-messages.no-twinks").replace("{player}", you)));
                return;
            }

            sender.sendMessage(plugin.applyColors(getMessage("twink-messages.header-your-twinks")));
            for (String twink : twinks) {
                sender.sendMessage(plugin.applyColors("&7- &[SECONDARY]" + twink));
            }
            sender.sendMessage(plugin.applyColors(getMessage("twink-messages.total-twinks").replace("{count}",String.valueOf(twinks.size()))));
        });
    }

    /**
     * Handle /twink command - admin command for managing twinks.
     * Permission: smaccs.twink.*
     */
    private void handleTwink(CommandSender sender, String[] args) {
        // Check base permission for any subcommand
        boolean hasAnyPermission = sender.hasPermission("smaccs.twink.add") ||
                sender.hasPermission("smaccs.twink.remove") ||
                sender.hasPermission("smaccs.twink.list") ||
                sender.hasPermission("smaccs.twink.info") ||
                sender.isOp() ||
                !(sender instanceof Player); // Console has all permissions

        if (!hasAnyPermission) {
            sender.sendMessage(plugin.applyColors(getMessage("twink-messages.no-permission")));
            return;
        }

        if (args.length < 1) {
            sendUsage(sender);
            return;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "add" -> handleTwinkAdd(sender, args);
            case "remove" -> handleTwinkRemove(sender, args);
            case "list" -> handleTwinkList(sender, args);
            case "info" -> handleTwinkInfo(sender, args);
            default -> sendUsage(sender);
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(plugin.applyColors(getMessage("twink-messages.usage-header")));
        sender.sendMessage(plugin.applyColors(getMessage("twink-messages.usage-add")));
        sender.sendMessage(plugin.applyColors(getMessage("twink-messages.usage-remove")));
        sender.sendMessage(plugin.applyColors(getMessage("twink-messages.usage-list")));
        sender.sendMessage(plugin.applyColors(getMessage("twink-messages.usage-info")));
    }

    /**
     * Handle /twink add <main> <twink>
     */
    private void handleTwinkAdd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("smaccs.twink.add") && !sender.isOp() && sender instanceof Player) {
            sender.sendMessage(plugin.applyColors(getMessage("twink-messages.no-permission")));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(plugin.applyColors(getMessage("twink-messages.invalid-usage-add")));
            return;
        }

        String mainName = args[1];
        String twinkName = args[2];

        if (mainName.equalsIgnoreCase(twinkName)) {
            sender.sendMessage(plugin.applyColors(getMessage("twink-messages.same-player")));
            return;
        }

        plugin.getSchedulerManager().runAsync("twink-add", () -> {
            TwinksDatabase db = module.getTwinksDatabase();
            if (db == null) {
                sender.sendMessage(plugin.applyColors(getMessage("twink-messages.db-error")));
                return;
            }

            // Validate main account
            if (db.isTwink(mainName)) {
                sender.sendMessage(plugin.applyColors(getMessage("twink-messages.not-a-main").replace("{player}",mainName)));
                return;
            }

            // Validate twink account isn't already a twink
            if (db.isTwink(twinkName)) {
                String existingMain = db.getMainUsername(twinkName);
                sender.sendMessage(plugin.applyColors(getMessage("twink-messages.already-a-twink").replace("{player}", twinkName).replace("{main}", existingMain)));
                return;
            }

            // Validate twink account isn't a main with twinks
            if (db.isMain(twinkName)) {
                sender.sendMessage(plugin.applyColors(getMessage("twink-messages.already-a-main").replace("{player}", twinkName)));
                return;
            }

            // Add the twink relationship
            if (!db.addTwink(mainName, twinkName)) {
                sender.sendMessage(plugin.applyColors(getMessage("twink-messages.failed-add")));
                return;
            }

            sender.sendMessage(plugin.applyColors(getMessage("twink-messages.twink-added").replace("{twink}", twinkName).replace("{main}", mainName)));

            // Execute additional commands on main thread using Folia-compatible scheduler
            final String finalTwinkName = twinkName;

            // Add to whitelist permanently
            plugin.getSchedulerManager().runTaskLater("twink-whitelist-" + finalTwinkName.toLowerCase(), () -> {
                String wlCmd = "twl add " + finalTwinkName + " permanent";
                plugin.getDebugSystem().log("SM_Accounts", "Executing: " + wlCmd);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), wlCmd);
            }, 1L);

            // Set LuckPerms parent to twink
            plugin.getSchedulerManager().runTaskLater("twink-luckperms-" + finalTwinkName.toLowerCase(), () -> {
                String lpCmd = "lp user " + finalTwinkName + " parent set twink";
                plugin.getDebugSystem().log("SM_Accounts", "Executing: " + lpCmd);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), lpCmd);
                sender.sendMessage(plugin.applyColors(getMessage("twink-messages.executed-whitelist-lp")));
            }, 2L);
        });
    }

    /**
     * Handle /twink remove <twink>
     */
    private void handleTwinkRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("smaccs.twink.remove") && !sender.isOp() && sender instanceof Player) {
            sender.sendMessage(plugin.applyColors(getMessage("twink-messages.no-permission")));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.applyColors(getMessage("twink-messages.invalid-usage-remove")));
            return;
        }

        String twinkName = args[1];

        plugin.getSchedulerManager().runAsync("twink-remove", () -> {
            TwinksDatabase db = module.getTwinksDatabase();
            if (db == null) {
                sender.sendMessage(plugin.applyColors(getMessage("twink-messages.db-error")));
                return;
            }

            // Check if this is actually a twink
            if (!db.isTwink(twinkName)) {
                // Check if it's a main
                if (db.isMain(twinkName)) {
                    sender.sendMessage(plugin.applyColors(getMessage("twink-messages.not-a-twink").replace("{player}", twinkName)));
                } else {
                    sender.sendMessage(plugin.applyColors(getMessage("twink-messages.not-a-twink").replace("{player}", twinkName)));
                }
                return;
            }

            String mainName = db.getMainUsername(twinkName);

            // Remove the twink relationship
            if (!db.removeTwink(twinkName)) {
                sender.sendMessage(plugin.applyColors(getMessage("twink-messages.failed-remove")));
                return;
            }

            sender.sendMessage(plugin.applyColors(getMessage("twink-messages.twink-removed").replace("{twink}", twinkName).replace("{main}", mainName)));

            // Execute additional commands (reverse of add) using Folia-compatible scheduler
            final String finalTwinkName = twinkName;

            // Remove from whitelist
            plugin.getSchedulerManager().runTaskLater("twink-whitelist-remove-" + finalTwinkName.toLowerCase(), () -> {
                String wlCmd = "twl remove " + finalTwinkName;
                plugin.getDebugSystem().log("SM_Accounts", "Executing: " + wlCmd);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), wlCmd);
            }, 1L);

            // Clear LuckPerms parent (set to default)
            plugin.getSchedulerManager().runTaskLater("twink-luckperms-remove-" + finalTwinkName.toLowerCase(), () -> {
                String lpCmd = "lp user " + finalTwinkName + " parent set default";
                plugin.getDebugSystem().log("SM_Accounts", "Executing: " + lpCmd);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), lpCmd);
                sender.sendMessage(plugin.applyColors(getMessage("twink-messages.executed-whitelist-lp-remove")));
            }, 2L);
        });
    }

    /**
     * Handle /twink list <main>
     */
    private void handleTwinkList(CommandSender sender, String[] args) {
        if (!sender.hasPermission("smaccs.twink.list") && !sender.isOp() && sender instanceof Player) {
            sender.sendMessage(plugin.applyColors(getMessage("twink-messages.no-permission")));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.applyColors(getMessage("twink-messages.invalid-usage-list")));
            return;
        }

        final String inputName = args[1];

        plugin.getSchedulerManager().runAsync("twink-list", () -> {
            TwinksDatabase db = module.getTwinksDatabase();
            if (db == null) {
                sender.sendMessage(plugin.applyColors(getMessage("twink-messages.db-error")));
                return;
            }

            // Check if this is a twink (show main's twinks instead)
            String targetMain = inputName;
            if (db.isTwink(inputName)) {
                String actualMain = db.getMainUsername(inputName);
                sender.sendMessage(plugin.applyColors(getMessage("twink-messages.is-twink-of").replace("{twink}", inputName).replace("{main}", actualMain)));
                targetMain = actualMain;
            }

            // Get twinks for main
            List<String> twinks = db.getTwinkUsernames(targetMain);
            if (twinks.isEmpty()) {
                sender.sendMessage(plugin.applyColors(getMessage("twink-messages.no-twinks").replace("{player}", targetMain)));
                return;
            }

            sender.sendMessage(plugin.applyColors(getMessage("twink-messages.header-twinks-for").replace("{player}", targetMain)));
            for (String twink : twinks) {
                TwinksDatabase.TwinkInfo info = db.getTwinkInfo(twink);
                if (info != null) {
                    sender.sendMessage(plugin.applyColors("&7- &[SECONDARY]" + twink + " &7(linked: " + DATE_FORMAT.format(info.linkedAt()) + ")"));
                } else {
                    sender.sendMessage(plugin.applyColors("&7- &[SECONDARY]" + twink));
                }
            }
            sender.sendMessage(plugin.applyColors(getMessage("twink-messages.total-twinks").replace("{count}", String.valueOf(twinks.size()))));
        });
    }

    /**
     * Handle /twink info <player>
     */
    private void handleTwinkInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("smaccs.twink.info") && !sender.isOp() && sender instanceof Player) {
            sender.sendMessage(plugin.applyColors(getMessage("twink-messages.no-permission")));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.applyColors(getMessage("twink-messages.invalid-usage-info")));
            return;
        }

        String playerName = args[1];

        plugin.getSchedulerManager().runAsync("twink-info", () -> {
            TwinksDatabase db = module.getTwinksDatabase();
            if (db == null) {
                sender.sendMessage(plugin.applyColors(getMessage("twink-messages.db-error")));
                return;
            }

            sender.sendMessage(plugin.applyColors(getMessage("twink-messages.header-twink-info").replace("{player}", playerName)));

            // Check if this is a twink
            if (db.isTwink(playerName)) {
                TwinksDatabase.TwinkInfo info = db.getTwinkInfo(playerName);
                if (info != null) {
                    sender.sendMessage(plugin.applyColors(getMessage("twink-messages.status-twink")));
                    sender.sendMessage(plugin.applyColors(getMessage("twink-messages.info-main-account").replace("{main}", info.mainUsername())));
                    sender.sendMessage(plugin.applyColors(getMessage("twink-messages.info-linked-at").replace("{date}", DATE_FORMAT.format(info.linkedAt()))));
                }
                return;
            }

            // Check if this is a main
            if (db.isMain(playerName)) {
                List<String> twinks = db.getTwinkUsernames(playerName);
                sender.sendMessage(plugin.applyColors(getMessage("twink-messages.status-main")));
                sender.sendMessage(plugin.applyColors(getMessage("twink-messages.info-twink-count").replace("{count}", String.valueOf(twinks.size()))));
                sender.sendMessage(plugin.applyColors(getMessage("twink-messages.info-twinks-list").replace("{list}", String.join(", ", twinks))));
                return;
            }

            // Neither twink nor main
            sender.sendMessage(plugin.applyColors(getMessage("twink-messages.status-regular")));
            sender.sendMessage(plugin.applyColors(getMessage("twink-messages.no-relationship").replace("{player}", "This player")));
        });
    }

    private String getMessage(String path) {
        return module.getConfig().getString(path, "&cMessage not found: " + path);
    }
}

