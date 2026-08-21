package be.isach.ultracosmetics;

import be.isach.ultracosmetics.commands.CosmeticCommand;
import be.isach.ultracosmetics.config.AutoCommentConfiguration;
import be.isach.ultracosmetics.config.CustomConfiguration;
import be.isach.ultracosmetics.config.MessageManager;
import be.isach.ultracosmetics.util.ItemFactory;
import be.isach.ultracosmetics.util.UCScheduler;
import be.isach.ultracosmetics.config.SettingsManager;
import be.isach.ultracosmetics.cosmetics.Category;
import be.isach.ultracosmetics.cosmetics.type.CosmeticType;
import be.isach.ultracosmetics.listeners.EntityDismountListener;
import be.isach.ultracosmetics.listeners.MainListener;
import be.isach.ultracosmetics.listeners.PlayerListener;
import be.isach.ultracosmetics.listeners.PriorityListener;
import be.isach.ultracosmetics.listeners.UnmovableItemListener;
import be.isach.ultracosmetics.menu.CosmeticsInventoryHolder;
import be.isach.ultracosmetics.menu.Menus;
import be.isach.ultracosmetics.permissions.PermissionManager;
import be.isach.ultracosmetics.player.UltraPlayer;
import be.isach.ultracosmetics.player.UltraPlayerManager;
import be.isach.ultracosmetics.run.FallDamageManager;
import be.isach.ultracosmetics.run.InvalidWorldChecker;
import be.isach.ultracosmetics.run.VanishChecker;
import be.isach.ultracosmetics.util.EntityMountManager;
import be.isach.ultracosmetics.util.EntitySpawningManager;
import be.isach.ultracosmetics.version.ServerVersion;
import be.isach.ultracosmetics.version.VersionManager;
import be.isach.ultracosmetics.worldguard.WorldGuardManager;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.module.BaseModule;
import net.schalker.DoAPI.core.module.ModuleInfo;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * SM_UCosmetics — SMPS module port of UCosmeticsModule.
 * All cosmetics are available from the start (no treasure chests, keys, economy).
 * Hot-loadable via SMPS module system.
 */
public class UCosmeticsModule extends BaseModule {

    private static UCosmeticsModule instance;

    private CustomConfiguration config;
    private File configFile;
    private UltraPlayerManager playerManager;
    private Menus menus;
    private PermissionManager permissionManager;
    private VersionManager versionManager;
    private UnmovableItemListener unmovableItemListener;
    private EntityDismountListener entityDismountListener;
    private final WorldGuardManager worldGuardManager = new WorldGuardManager(this);
    private UCScheduler scheduler;

    // Config fields (previously in UltraCosmeticsData singleton)
    private String language = "en";
    private boolean closeAfterSelect;
    private boolean cooldownInBar;
    private boolean cosmeticsProfilesEnabled;

    public UCosmeticsModule(DoAPI plugin) {
        super(plugin, loadModuleInfo());
    }

    private static ModuleInfo loadModuleInfo() {
        try (InputStream stream = UCosmeticsModule.class.getClassLoader().getResourceAsStream("module.yml")) {
            if (stream != null) {
                YamlConfiguration yml = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
                return new ModuleInfo(
                        yml.getString("name", "SM_UCosmetics"),
                        yml.getString("version", "1.0.0"),
                        yml.getString("author", "Unknown"),
                        yml.getString("description", "")
                );
            }
        } catch (Exception ignored) {
        }
        return new ModuleInfo("SM_UCosmetics", "1.0.0", "Unknown", "UCosmeticsModule as SMPS module");
    }

    @Override
    public void onEnable() {
        super.onEnable();
        instance = this;

        // Initialize static factory references early (must happen before any class that uses ItemFactory)
        ItemFactory.init(this);

        // Load config
        if (!setUpConfig()) {
            plugin.getDebugSystem().logError("SM_UCosmetics", "Failed to load config.yml", null);
            return;
        }

        // Initialize config fields
        initConfigFields();

        // Initialize scheduler adapter
        this.scheduler = new UCScheduler(plugin);

        // Initialize NMS module (1.21 only)
        if (!initModule()) {
            plugin.getDebugSystem().logError("SM_UCosmetics", "Failed to initialize NMS module", null);
            return;
        }

        // Initialize messages
        if (!MessageManager.load(this)) {
            plugin.getDebugSystem().logError("SM_UCosmetics", "Failed to load messages", null);
            return;
        }

        // Player manager
        this.playerManager = new UltraPlayerManager(this);

        // Register cosmetic types (all available from start)
        CosmeticType.registerAll(this);

        // Permission manager
        permissionManager = new PermissionManager(this);

        // Register listeners via SMPS
        registerListeners();

        // Register commands
        registerCommands();

        // WorldGuard integration
        worldGuardManager.registerPhase2();

        // Start periodic tasks
        startTasks();

        // Menus
        this.menus = new Menus(this);

        // Initialize online players
        playerManager.initPlayers();

        plugin.getDebugSystem().log("SM_UCosmetics", "Module enabled successfully!");
    }

    @Override
    public void onDisable() {
        super.onDisable();

        if (playerManager != null) {
            // Close open cosmetic inventories — use direct API (1.21+ InventoryView is an interface)
            // Wrapped in try-catch for hot-swap safety: the old JAR may be replaced on disk,
            // causing NoClassDefFoundError for any class not yet loaded by this classloader.
            try {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    try {
                        if (player.getOpenInventory().getTopInventory().getHolder() instanceof CosmeticsInventoryHolder) {
                            player.closeInventory();
                        }
                    } catch (Exception | NoClassDefFoundError ignored) {
                        try { player.closeInventory(); } catch (Exception ignored2) {}
                    }
                }
            } catch (Exception | NoClassDefFoundError e) {
                // Last resort: just try closing all inventories
                try {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        try { player.closeInventory(); } catch (Exception ignored) {}
                    }
                } catch (Exception ignored) {}
            }
            try {
                playerManager.dispose();
            } catch (Exception | NoClassDefFoundError ignored) {}
        }

        try {
            if (versionManager != null && versionManager.getModule() != null) {
                versionManager.getModule().disable();
            }
        } catch (Exception | NoClassDefFoundError ignored) {}

        try {
            CosmeticType.removeAllTypes();
        } catch (Exception | NoClassDefFoundError ignored) {}

        try {
            MessageManager.destroy();
        } catch (Exception | NoClassDefFoundError ignored) {}

        instance = null;
        plugin.getDebugSystem().log("SM_UCosmetics", "Module disabled.");
    }

    @Override
    public void reload() {
        super.reload();
        onDisable();
        onEnable();
        plugin.getDebugSystem().log("SM_UCosmetics", "Module reloaded.");
    }

    // ─── Static access (used by legacy code) ─────────────────────────

    public static UCosmeticsModule get() {
        return instance;
    }

    // ─── Config Setup ────────────────────────────────────────────────

    private boolean setUpConfig() {
        configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.getModuleManager().saveModuleDefaultConfig("SM_UCosmetics");
        }
        config = new AutoCommentConfiguration();
        try {
            config.load(configFile);
        } catch (Exception e) {
            plugin.getDebugSystem().logError("SM_UCosmetics", "Failed to load config.yml", e);
            return false;
        }

        // Load defaults from jar
        try (Reader reader = getFileReader("config.yml")) {
            if (reader != null) {
                AutoCommentConfiguration defaults = new AutoCommentConfiguration();
                defaults.load(reader);
                for (String key : defaults.getKeys(true)) {
                    config.addDefault(key, defaults.get(key));
                }
            }
        } catch (Exception e) {
            plugin.getDebugSystem().logWarning("SM_UCosmetics", "Failed to load default config");
        }

        try {
            config.save(configFile);
        } catch (Exception e) {
            plugin.getDebugSystem().logError("SM_UCosmetics", "Failed to save config.yml", e);
            return false;
        }
        return true;
    }

    private void initConfigFields() {
        language = config.getString("Language", "en");
        closeAfterSelect = config.getBoolean("Close-GUI-After-Select", false);
        cooldownInBar = config.getBoolean("Gadget-Cooldown-In-ActionBar", true);
        cosmeticsProfilesEnabled = config.getBoolean("Auto-Equip-Cosmetics", true);
    }

    private boolean initModule() {
        try {
            // Hardcoded for 1.21 - single target version
            ServerVersion version = ServerVersion.v1_21;
            versionManager = new VersionManager(version, true);
            if (!versionManager.getModule().enable()) {
                plugin.getDebugSystem().log("SM_UCosmetics", "NMS module failed to enable, using NMS-less mode");
                versionManager = new VersionManager(version, false);
            }
            return true;
        } catch (Exception e) {
            plugin.getDebugSystem().log("SM_UCosmetics", "NMS module not bundled, using NMS-less mode (this is normal)");
            try {
                versionManager = new VersionManager(ServerVersion.v1_21, false);
            } catch (Exception ex) {
                plugin.getDebugSystem().logError("SM_UCosmetics", "Failed to init version manager", ex);
                return false;
            }
            return true;
        }
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new PriorityListener(this), plugin);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), plugin);
        Bukkit.getPluginManager().registerEvents(new MainListener(), plugin);
        Bukkit.getPluginManager().registerEvents(new EntitySpawningManager(), plugin);
        Bukkit.getPluginManager().registerEvents(new EntityMountManager(), plugin);
        unmovableItemListener = new UnmovableItemListener(this);
        Bukkit.getPluginManager().registerEvents(unmovableItemListener, plugin);
        entityDismountListener = new EntityDismountListener(this);
    }

    private void registerCommands() {
        try {
            plugin.getCommandManager().registerModuleCommand(new CosmeticCommand(plugin));
            plugin.getDebugSystem().log("SM_UCosmetics", "Commands registered (/cosmetic, /cosm, /cosmetics)");
        } catch (Exception e) {
            plugin.getDebugSystem().logError("SM_UCosmetics", "Failed to register commands", e);
        }
    }

    private void startTasks() {
        // Fall damage manager
        scheduler.runTimerAsync(new FallDamageManager()::run, 1L, 1L);

        // Invalid world checker
        if (!config.getStringList("Enabled-Worlds").contains("*")) {
            scheduler.runTimer(new InvalidWorldChecker(this)::run, 100L, 100L);
        }

        // Vanish checker
        if (config.getBoolean("Prevent-Cosmetics-In-Vanish")) {
            scheduler.runTimer(new VanishChecker(this)::run, 20L, 20L);
        }
    }

    // ─── Accessors (replacing UltraCosmetics JavaPlugin methods) ─────

    public DoAPI getSMPS() {
        return plugin;
    }

    /**
     * Scheduler adapter — provides same API as FoliaLib for Folia-native scheduling.
     */
    public UCScheduler getScheduler() {
        return scheduler;
    }

    public org.bukkit.Server getServer() {
        return Bukkit.getServer();
    }

    public File getDataFolder() {
        return new File(plugin.getModuleManager().getModuleDataFolder("SM_UCosmetics").getAbsolutePath());
    }

    public CustomConfiguration getConfig() {
        return config;
    }

    public File getConfigFile() {
        return configFile;
    }

    public UltraPlayerManager getPlayerManager() {
        return playerManager;
    }

    public Menus getMenus() {
        return menus;
    }

    public PermissionManager getPermissionManager() {
        return permissionManager;
    }

    public VersionManager getVersionManager() {
        return versionManager;
    }

    public WorldGuardManager getWorldGuardManager() {
        return worldGuardManager;
    }

    public UnmovableItemListener getUnmovableItemListener() {
        return unmovableItemListener;
    }

    public EntityDismountListener getEntityDismountListener() {
        return entityDismountListener;
    }

    /**
     * Get a reader for a resource embedded in the module JAR.
     */
    public Reader getFileReader(String path) {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(path);
        if (stream == null) return null;
        return new InputStreamReader(stream, StandardCharsets.UTF_8);
    }

    // ─── Config field accessors (replacing UCosmeticsModule) ───────

    public String getLanguage() {
        return language;
    }

    public ServerVersion getServerVersion() {
        return ServerVersion.v1_21;
    }

    public boolean isCloseAfterSelect() {
        return closeAfterSelect;
    }

    public boolean shouldCloseAfterSelect() {
        return closeAfterSelect;
    }

    public boolean isCooldownInBar() {
        return cooldownInBar;
    }

    public boolean areCosmeticsProfilesEnabled() {
        return cosmeticsProfilesEnabled;
    }

    public boolean arePlaceholdersColored() {
        return config.getBoolean("Placeholders-Colored", true);
    }

    public boolean isCosmeticsAffectEntities() {
        return config.getBoolean("Cosmetics-Affect-Entities", true);
    }

    /**
     * Ammo is disabled in the SMPS module version.
     */
    public boolean isAmmoEnabled() {
        return false;
    }

    /**
     * Treasure chests are disabled in the SMPS module version.
     */
    public boolean areTreasureChestsEnabled() {
        return false;
    }

    /**
     * All cosmetics are free in the SMPS module version.
     */
    public boolean usingFileStorage() {
        return true;
    }

    /**
     * Check if MobChip is available for pet pathfinding.
     */
    public boolean isMobChipAvailable() {
        try {
            Class.forName("me.gamercoder215.mobchip.abstraction.ChipUtil");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Returns true if the module is enabled (i.e. the singleton is set).
     */
    public boolean isEnabled() {
        return instance != null;
    }

    /**
     * Placeholder hook is not used in the SMPS module version.
     * Returns null to indicate no PlaceholderAPI hook.
     */
    public Object getPlaceholderHook() {
        return null;
    }

    /**
     * Problem tracking stub — SMPS module doesn't use the Problem enum.
     */
    public void addProblem(Object problem) {
        // no-op in SMPS port
    }
}




