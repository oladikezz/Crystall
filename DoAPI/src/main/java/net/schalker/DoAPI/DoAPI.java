package net.schalker.DoAPI;

import net.schalker.DoAPI.core.command.CommandManager;
import net.schalker.DoAPI.core.config.ConfigManager;
import net.schalker.DoAPI.core.database.DatabaseManager;
import net.schalker.DoAPI.core.debug.DebugSystem;
import net.schalker.DoAPI.core.listener.ListenerManager;
import net.schalker.DoAPI.core.module.ModuleManager;
import net.schalker.DoAPI.core.reload.PluginReloader;
import net.schalker.DoAPI.core.scheduler.SchedulerManager;
import net.schalker.DoAPI.core.util.TextFormatter;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class DoAPI extends JavaPlugin {

    public ModuleManager moduleManager;
    public ConfigManager configManager;
    public CommandManager commandManager;
    public ListenerManager listenerManager;
    public SchedulerManager schedulerManager;
    public DebugSystem debugSystem;
    public PluginReloader pluginReloader;
    public DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();

        if (!isFoliaServer()) {
            getLogger().warning("Folia not detected, region schedulers fall back to Paper behaviour");
        }
        warnAboutLegacyDataFolder();

        try {
            initializeCore();
        } catch (Throwable throwable) {
            getLogger().severe("Core initialization failed, disabling DoAPI");
            throwable.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("DoAPI v" + getPluginMeta().getVersion() + " enabled in "
                + (System.currentTimeMillis() - start) + " ms ("
                + moduleManager.getEnabledModuleCount() + "/" + moduleManager.getModuleCount()
                + " modules)");
    }

    @Override
    public void onDisable() {
        if (moduleManager != null) {
            moduleManager.disableAllModules();
            moduleManager.unloadAllModules();
        }
        if (schedulerManager != null) {
            schedulerManager.cancelAllTasks();
        }
        if (listenerManager != null) {
            listenerManager.unregisterAllListeners();
        }
        if (databaseManager != null) {
            databaseManager.shutdown();
        }

        getLogger().info("DoAPI disabled");
    }

    public void initializeCore() {
        this.configManager = new ConfigManager(this);
        this.configManager.initialize();

        this.debugSystem = new DebugSystem(this);
        this.debugSystem.initialize();

        this.schedulerManager = new SchedulerManager(this);
        this.listenerManager = new ListenerManager(this);

        this.databaseManager = new DatabaseManager(this);
        this.databaseManager.initialize();

        this.moduleManager = new ModuleManager(this);
        this.pluginReloader = new PluginReloader(this);

        this.commandManager = new CommandManager(this);
        this.commandManager.initialize(pluginReloader);

        if (moduleManager.shouldAutoLoadModules()) {
            moduleManager.discoverAndLoadModules();
            moduleManager.enableAllModules();
        } else {
            getLogger().info("Auto-loading disabled, use /doapi module load <file>");
        }
    }

    private boolean isFoliaServer() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private void warnAboutLegacyDataFolder() {
        File parent = getDataFolder().getParentFile();
        if (parent == null) {
            return;
        }

        File legacy = new File(parent, "SMPS");
        if (legacy.isDirectory() && !getDataFolder().isDirectory()) {
            getLogger().warning("Found legacy plugins/SMPS folder. Rename it to plugins/"
                    + getDataFolder().getName() + " to keep module configs and databases.");
        }
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public ListenerManager getListenerManager() {
        return listenerManager;
    }

    public SchedulerManager getSchedulerManager() {
        return schedulerManager;
    }

    public DebugSystem getDebugSystem() {
        return debugSystem;
    }

    public PluginReloader getPluginReloader() {
        return pluginReloader;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public boolean isDatabaseConnected() {
        return databaseManager != null && databaseManager.isConnected();
    }

    public String getMainColor() {
        return configManager.getConfig().getString("main-color", "&#f44d89");
    }

    public String getSecondaryColor() {
        return configManager.getConfig().getString("secondary-color", "&#FFA1C4");
    }

    public String applyColors(String message) {
        if (message == null) {
            return "";
        }
        return TextFormatter.colorize(message
                .replace("&[MAIN]", getMainColor())
                .replace("&[SECONDARY]", getSecondaryColor()));
    }

    public String applyTinyCaps(String message) {
        return TextFormatter.toTinyCaps(message);
    }

    public boolean shouldLogModuleEvents() {
        return configManager.getConfig().getBoolean("log-module-events", true);
    }

    public boolean shouldLogCommands() {
        return configManager.getConfig().getBoolean("log-commands", true);
    }

    public boolean shouldLogListeners() {
        return configManager.getConfig().getBoolean("log-listeners", true);
    }

    public boolean shouldLogScheduler() {
        return configManager.getConfig().getBoolean("log-scheduler", true);
    }
}
