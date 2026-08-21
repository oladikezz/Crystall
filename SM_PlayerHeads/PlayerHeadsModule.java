package net.schalker.SMPS.modules.playerheads;

import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.module.BaseModule;
import net.schalker.DoAPI.core.module.ModuleInfo;
import net.schalker.SMPS.modules.playerheads.listeners.PlayerDeathListener;
import net.schalker.SMPS.modules.playerheads.managers.PlayerHeadsDatabaseManager;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class PlayerHeadsModule extends BaseModule {
    private static final String MODULE_NAME = "SM_PlayerHead";
    private static final String MODULE_FOLDER_NAME = "SM_PlayerHead";

    private PlayerDeathListener deathListener;
    private PlayerHeadsDatabaseManager databaseManager;

    public PlayerHeadsModule(DoAPI plugin) {
        super(plugin, loadModuleInfo());
    }

    private static ModuleInfo loadModuleInfo() {
        try (InputStream stream = PlayerHeadsModule.class.getClassLoader().getResourceAsStream("module.yml")) {
            if (stream != null) {
                YamlConfiguration yml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
                return new ModuleInfo(
                    yml.getString("name", MODULE_NAME),
                    yml.getString("version", "2.0.0"),
                    yml.getString("author", "MeXaNoBoP"),
                    yml.getString("description", "Drops player heads with death metadata")
                );
            }
        } catch (Exception ignored) {}
        return new ModuleInfo(MODULE_NAME, "2.0.0", "MeXaNoBoP", "Drops player heads with death metadata");
    }

    @Override
    public void onEnable() {
        super.onEnable();
        syncConfigFileToModuleFolder();
        syncMessagesFileToModuleFolder();

        YamlConfiguration config = loadModuleConfig(plugin, MODULE_FOLDER_NAME);
        if (config == null) {
            plugin.getLogger().severe("Failed to load PlayerHeads config");
            enabled = false;
            return;
        }

        try {
            databaseManager = new PlayerHeadsDatabaseManager(plugin);
            databaseManager.initialize();

            // Configure SkinsRestorer integration from config
            if (config.getBoolean("skinsrestorer.enabled", true)) {
                String srDb = config.getString("skinsrestorer.database", "s3_SkinsRestorer");
                String srPlayerSkins = config.getString("skinsrestorer.tables.player-skins", "sr_player_skins");
                String srUrlSkins = config.getString("skinsrestorer.tables.url-skins", "sr_url_skins");
                String srPlayers = config.getString("skinsrestorer.tables.players", "sr_players");
                databaseManager.configureSkinsRestorer(true, srDb, srPlayerSkins, srUrlSkins, srPlayers);
                plugin.getDebugSystem().log(MODULE_NAME, "SkinsRestorer integration enabled, db: " + srDb);
            } else {
                databaseManager.configureSkinsRestorer(false, null, null, null, null);
            }

            plugin.getDebugSystem().log(MODULE_NAME, "Database initialized");
        } catch (Exception exception) {
            plugin.getDebugSystem().logError("Failed to initialize PlayerHeads database", exception);
            plugin.getLogger().severe("PlayerHeads module failed to initialize database");
            enabled = false;
            return;
        }

        deathListener = new PlayerDeathListener(plugin, databaseManager, MODULE_NAME);
        plugin.getListenerManager().registerListener(deathListener);

        plugin.getDebugSystem().log(MODULE_NAME, "PlayerHeads enabled");
        plugin.getLogger().info("PlayerHeads module enabled");
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (deathListener != null) {
            plugin.getListenerManager().unregisterListener(deathListener);
        }

        plugin.getDebugSystem().log(MODULE_NAME, "PlayerHeads disabled");
        plugin.getLogger().info("PlayerHeads module disabled");
    }

    @Override
    public void reload() {
        super.reload();
        syncConfigFileToModuleFolder();
        syncMessagesFileToModuleFolder();
        loadModuleConfig(plugin, MODULE_FOLDER_NAME);
        loadModuleConfig(plugin, MODULE_FOLDER_NAME, "messages.yml");

        plugin.getDebugSystem().log(MODULE_NAME, "PlayerHeads reloaded");
        plugin.getLogger().info("PlayerHeads module reloaded");
    }

    private void syncMessagesFileToModuleFolder() {
        File moduleFolder = getModuleDataFolder(plugin, MODULE_FOLDER_NAME);
        if (!moduleFolder.exists() && !moduleFolder.mkdirs()) {
            plugin.getLogger().warning("Failed to create module folder for PlayerHeads");
            return;
        }

        File messagesFile = new File(moduleFolder, "messages.yml");
        if (messagesFile.exists()) {
            return;
        }
        InputStream primary = getClass().getClassLoader().getResourceAsStream("messages.yml");
        InputStream fallback = primary == null
            ? getClass().getClassLoader().getResourceAsStream("modules/playerheads/messages.yml")
            : null;
        try (InputStream source = primary != null ? primary : fallback) {
            if (source == null) {
                plugin.getDebugSystem().log(MODULE_NAME, "Bundled messages.yml not found");
                return;
            }
            Files.copy(source, messagesFile.toPath());
            plugin.getDebugSystem().log(MODULE_NAME, "messages.yml created in module folder");
        } catch (Exception exception) {
            plugin.getDebugSystem().logError("Failed to sync messages.yml", exception);
        }
    }

    private void syncConfigFileToModuleFolder() {
        syncModuleFileToModuleFolder("config.yml");
    }

    private void syncModuleFileToModuleFolder(String fileName) {
        File moduleFolder = getModuleDataFolder(plugin, MODULE_FOLDER_NAME);
        if (!moduleFolder.exists() && !moduleFolder.mkdirs()) {
            plugin.getLogger().warning("Failed to create module folder for PlayerHeads");
            return;
        }

        File targetFile = new File(moduleFolder, fileName);
        if (targetFile.exists()) {
            return;
        }
        InputStream primary = getClass().getClassLoader().getResourceAsStream(fileName);
        InputStream fallback = primary == null
            ? getClass().getClassLoader().getResourceAsStream("modules/playerheads/" + fileName)
            : null;

        try (InputStream source = primary != null ? primary : fallback) {
            if (source == null) {
                plugin.getDebugSystem().log(MODULE_NAME, "Bundled " + fileName + " not found");
                return;
            }
            Files.copy(source, targetFile.toPath());
            plugin.getDebugSystem().log(MODULE_NAME, fileName + " created in module folder");
        } catch (Exception exception) {
            plugin.getDebugSystem().logError("Failed to sync " + fileName, exception);
        }
    }

    public static File getModuleDataFolder(DoAPI plugin, String moduleFolderName) {
        return new File(new File(plugin.getDataFolder(), "modules"), moduleFolderName);
    }

    public static YamlConfiguration loadModuleConfig(DoAPI plugin, String moduleFolderName) {
        return loadModuleConfig(plugin, moduleFolderName, "config.yml");
    }

    public static YamlConfiguration loadModuleConfig(DoAPI plugin, String moduleFolderName, String fileName) {
        File moduleFolder = getModuleDataFolder(plugin, moduleFolderName);
        if (!moduleFolder.exists() && !moduleFolder.mkdirs()) {
            return null;
        }

        File configFile = new File(moduleFolder, fileName);
        if (!configFile.exists()) {
            return null;
        }

        return YamlConfiguration.loadConfiguration(configFile);
    }
}
