package site.deforce.SM_Accounts;

import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.module.BaseModule;
import net.schalker.DoAPI.core.module.ModuleInfo;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import site.deforce.SM_Accounts.database.AccountsDatabase;
import site.deforce.SM_Accounts.database.TwinksDatabase;
import site.deforce.SM_Accounts.hooks.DiscordBotManager;
import site.deforce.SM_Accounts.hooks.LiteBansHook;
import site.deforce.SM_Accounts.listeners.JoinListener;
import site.deforce.SM_Accounts.commands.TwinkCommand;
import site.deforce.SM_Accounts.commands.NickSyncCommand;
import site.deforce.SM_Accounts.commands.UnlinkCommand;
import site.deforce.SM_Accounts.tasks.NicknameSyncTask;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/**
 * SM_Accounts - Discord account linking module.
 * Requires players to link their Discord account before joining.
 */
public class SM_Accounts extends BaseModule {

    private FileConfiguration config;
    private AccountsDatabase database;
    private TwinksDatabase twinksDatabase;
    private LiteBansHook liteBansHook;
    private DiscordBotManager discordBotManager;
    private final Set<String> registeredCommandNames = new HashSet<>();

    public SM_Accounts(DoAPI plugin) {
        super(plugin, loadModuleInfo());
    }

    private static ModuleInfo loadModuleInfo() {
        try (InputStream stream = SM_Accounts.class.getClassLoader().getResourceAsStream("module.yml")) {
            if (stream != null) {
                YamlConfiguration yml = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
                return new ModuleInfo(
                        yml.getString("name", "SM_Accounts"),
                        yml.getString("version", "1.0.0"),
                        yml.getString("author", "deforce_"),
                        yml.getString("description", "Discord account linking module")
                );
            }
        } catch (Exception ignored) {}
        return new ModuleInfo("SM_Accounts", "1.0.0", "deforce_", "Discord account linking module");
    }

    @Override
    public void onEnable() {
        super.onEnable();

        // Load config
        this.config = plugin.getModuleManager().loadModuleConfig("SM_Accounts");

        // Initialize database
        if (plugin.isDatabaseConnected()) {
            this.database = new AccountsDatabase(plugin);
            this.database.createTables();

            this.twinksDatabase = new TwinksDatabase(plugin);
            this.twinksDatabase.createTables();

            plugin.getDebugSystem().log("SM_Accounts", "Database initialized");
        } else {
            plugin.getDebugSystem().log("SM_Accounts", "WARNING: Database not connected!");
        }

        // Register listener
        plugin.getListenerManager().registerListener(new JoinListener(this));

        // Register twink commands
        TwinkCommand twinkCommand = new TwinkCommand(this);
        registerCommand("twink", twinkCommand, twinkCommand, "Manage twink accounts", "/twink <add|remove|list|info>", "smaccs.twink");
        registerCommand("twinks", twinkCommand, twinkCommand, "View your twink accounts", "/twinks", "smaccs.twinks");

        // Register nickname sync debug command
        NickSyncCommand nickSyncCommand = new NickSyncCommand(this);
        registerCommand("nicksync", nickSyncCommand, nickSyncCommand, "Manually sync Discord nicknames", "/nicksync [player]", "smaccs.nicksync");

        // Register full account unlink command
        UnlinkCommand unlinkCommand = new UnlinkCommand(this);
        registerCommand("unlink", unlinkCommand, unlinkCommand, "Fully unlink a player account", "/unlink <nickname> [--confirm]", "smaccs.unlink");

        syncCommands();

        // Initialize LiteBans hook for punishment sync
        this.liteBansHook = new LiteBansHook(this);

        // Initialize Discord bot for guild membership checks and nickname sync
        initDiscordBot();

        plugin.getDebugSystem().log("SM_Accounts", "Enabled!");
    }

    @SuppressWarnings("unchecked")
    private void registerCommand(String name, CommandExecutor executor, TabCompleter tabCompleter, String description, String usage, String permission) {
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            CommandMap commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());

            // Force-unregister any existing command with the same name (e.g. DiscordSRV /unlink)
            try {
                Field knownCommandsField = commandMap.getClass().getSuperclass().getDeclaredField("knownCommands");
                knownCommandsField.setAccessible(true);
                Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);
                knownCommands.remove(name.toLowerCase());
                plugin.getDebugSystem().log("SM_Accounts", "Cleared existing /" + name + " registration (if any)");
            } catch (Exception ignored) {
                // Not critical — if we can't clear, the register call may still work with prefix
            }

            Command command = new Command(name, description, usage, java.util.Collections.emptyList()) {
                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    plugin.getDebugSystem().log("SM_Accounts", "Command /" + name + " executed by " + sender.getName());
                    return executor.onCommand(sender, this, label, args);
                }

                @Override
                public java.util.List<String> tabComplete(CommandSender sender, String alias, String[] args) throws IllegalArgumentException {
                    if (tabCompleter != null) {
                        java.util.List<String> completions = tabCompleter.onTabComplete(sender, this, alias, args);
                        if (completions != null) {
                            return completions;
                        }
                    }
                    return super.tabComplete(sender, alias, args);
                }
            };
            // Do NOT call setPermission() — permission check is handled inside onCommand.
            // Setting it here causes Paper to silently reject the command for players without it.

            commandMap.register("sm_accounts", command);
            registeredCommandNames.add(name.toLowerCase());
            plugin.getDebugSystem().log("SM_Accounts", "Registered command: /" + name);
        } catch (Exception e) {
            plugin.getDebugSystem().logError("SM_Accounts", "Error registering command: " + name, e);
        }
    }

    private void syncCommands() {
        try {
            Bukkit.getServer().getClass().getMethod("syncCommands").invoke(Bukkit.getServer());
            plugin.getDebugSystem().log("SM_Accounts", "Commands synced successfully");
        } catch (Exception e) {
            plugin.getDebugSystem().log("SM_Accounts", "Warning: Could not sync commands (non-fatal)");
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        shutdownDiscordBot();
        registeredCommandNames.clear();
        plugin.getDebugSystem().log("SM_Accounts", "Disabled!");
    }

    @Override
    public void reload() {
        super.reload();
        this.config = plugin.getModuleManager().loadModuleConfig("SM_Accounts");

        // Re-initialize Discord bot with potentially new config
        shutdownDiscordBot();
        initDiscordBot();

        plugin.getDebugSystem().log("SM_Accounts", "Reloaded!");
    }

    public DoAPI getPlugin() {
        return plugin;
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public AccountsDatabase getDatabase() {
        return database;
    }

    public TwinksDatabase getTwinksDatabase() {
        return twinksDatabase;
    }

    public LiteBansHook getLiteBansHook() {
        return liteBansHook;
    }

    public DiscordBotManager getDiscordBotManager() {
        return discordBotManager;
    }

    /**
     * Initialize the Discord bot and nickname sync task.
     */
    private void initDiscordBot() {
        if (!config.getBoolean("discord-bot.enabled", false)) {
            plugin.getDebugSystem().log("SM_Accounts", "Discord bot is disabled in config");
            return;
        }

        String token = config.getString("discord-bot.token", "");
        String guildId = config.getString("discord-bot.guild-id", "");

        if (token.isEmpty() || token.equals("YOUR_BOT_TOKEN_HERE")) {
            plugin.getDebugSystem().log("SM_Accounts", "WARNING: Discord bot token is not configured!");
            return;
        }

        if (guildId.isEmpty() || guildId.equals("YOUR_GUILD_ID_HERE")) {
            plugin.getDebugSystem().log("SM_Accounts", "WARNING: Discord guild ID is not configured!");
            return;
        }

        plugin.getSchedulerManager().runAsync("smaccs-discord-bot-init", () -> {
            try {
                this.discordBotManager = new DiscordBotManager(plugin, token, guildId);
                plugin.getDebugSystem().log("SM_Accounts", "Discord bot initialized successfully");

                if (config.getBoolean("nickname-sync.enabled", false)) {
                    int intervalMinutes = config.getInt("nickname-sync.interval-minutes", 10);
                    long intervalTicks = intervalMinutes * 60L * 20L;

                    plugin.getSchedulerManager().runAsyncTimer(
                            "smaccs-nickname-sync",
                            new NicknameSyncTask(this),
                            intervalTicks,
                            intervalTicks
                    );

                    plugin.getDebugSystem().log("SM_Accounts",
                            "Nickname sync task started (interval: " + intervalMinutes + " minutes)");
                }
            } catch (Exception e) {
                plugin.getDebugSystem().logError("Failed to initialize Discord bot", e);
                this.discordBotManager = null;
            }
        });
    }

    private void shutdownDiscordBot() {
        if (plugin.getSchedulerManager().hasTask("smaccs-nickname-sync")) {
            plugin.getSchedulerManager().cancelTask("smaccs-nickname-sync");
        }

        if (discordBotManager != null) {
            discordBotManager.shutdown();
            discordBotManager = null;
        }
    }

    public String getKickMessage(String code, String playerName) {
        String message = config.getString("kick-message",
                "&cYou need to verify by going here: https://schalker.net/auth/link?code={code}");
        message = message.replace("{code}", code);
        message = message.replace("{player_name}", playerName);
        return plugin.applyColors(message);
    }

    public String getHoverText() {
        String text = config.getString("clickable-hover-text", "&bClick to verify your account!");
        return plugin.applyColors(text);
    }
}
