package net.schalker.SMPS.modules.marry;

import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.module.BaseModule;
import net.schalker.DoAPI.core.module.ModuleInfo;
import net.schalker.SMPS.modules.marry.database.MarryDatabase;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * SM_Marry Module - Marriage system for Minecraft server.
 *
 * Features:
 * - Priests can marry players with /marry <nick1> <nick2>
 * - Both players must confirm with /marry confirm within timeout
 * - Priests can divorce players with /marry divorce <nick1> <nick2>
 * - Both players must confirm divorce with /marry confirm
 * - Players can deny with /marry deny
 * - Check marriage info with /marry info [nick]
 * - List all marriages with /marry list
 *
 * Required permission for priest: smmarry.priest
 */
public class MarryModule extends BaseModule {

    private FileConfiguration config;
    private FileConfiguration messages;
    private MarryDatabase marryDatabase;
    private net.schalker.SMPS.modules.marry.managers.MarryManager marryManager;
    private net.schalker.SMPS.modules.marry.listeners.MarryListener marryListener;
    private final Set<String> registeredCommandNames = new HashSet<>();

    public MarryModule(DoAPI plugin) {
        super(plugin, loadModuleInfo());
    }

    /**
     * Load module info from module.yml resource.
     */
    private static ModuleInfo loadModuleInfo() {
        try (InputStream stream = MarryModule.class.getClassLoader().getResourceAsStream("module.yml")) {
            if (stream != null) {
                YamlConfiguration yml = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
                return new ModuleInfo(
                        yml.getString("name", "SM_Marry"),
                        yml.getString("version", "1.0.0"),
                        yml.getString("author", "deforce_"),
                        yml.getString("description", "Система браков на сервере")
                );
            }
        } catch (Exception e) {
            // Ignored - use defaults
        }
        return new ModuleInfo("SM_Marry", "1.0.0", "deforce_", "Система браков на сервере");
    }

    @Override
    public void onEnable() {
        super.onEnable();

        // Load configs
        this.loadConfigs();

        // Initialize database
        if (!plugin.isDatabaseConnected()) {
            plugin.getDebugSystem().logWarning("SM_Marry", "Database not connected! Marriage data will not be saved.");
        } else {
            this.marryDatabase = new MarryDatabase(plugin);
            this.marryDatabase.createTables();
        }

        // Initialize manager
        this.marryManager = new net.schalker.SMPS.modules.marry.managers.MarryManager(plugin, this);

        // Register listener
        this.marryListener = new net.schalker.SMPS.modules.marry.listeners.MarryListener(plugin, this, marryManager);
        plugin.getListenerManager().registerListener(marryListener);

        // Register commands
        if (isCommandEnabled("marry")) {
            this.registerCommandSafely(new net.schalker.SMPS.modules.marry.commands.MarryCommand(plugin, this));
        }
        if (isCommandEnabled("divorce")) {
            this.registerCommandSafely(new net.schalker.SMPS.modules.marry.commands.DivorceCommand(plugin, this));
        }

        plugin.getDebugSystem().log("SM_Marry", "Модуль SM_Marry включен");
    }

    @Override
    public void onDisable() {
        super.onDisable();

        // Unregister listener
        if (marryListener != null) {
            plugin.getListenerManager().unregisterListener(marryListener);
        }

        // Clear all pending requests
        if (marryManager != null) {
            marryManager.clearAll();
        }

        // Unregister commands
        this.unregisterAllCommands();

        plugin.getDebugSystem().log("SM_Marry", "Модуль SM_Marry выключен");
    }

    @Override
    public void reload() {
        super.reload();

        // Reload configs
        this.loadConfigs();

        plugin.getDebugSystem().log("SM_Marry", "Конфигурация SM_Marry перезагружена");
    }

    /**
     * Load configuration files.
     */
    private void loadConfigs() {
        this.config = plugin.getModuleManager().loadModuleConfig("SM_Marry");
        this.messages = plugin.getModuleManager().loadModuleConfig("SM_Marry", "messages.yml");

        if (this.config == null) {
            this.config = new YamlConfiguration();
        }
        if (this.messages == null) {
            this.messages = new YamlConfiguration();
        }
    }

    /**
     * Get module configuration.
     */
    public FileConfiguration getConfig() {
        return this.config;
    }

    /**
     * Get messages configuration.
     */
    public FileConfiguration getMessages() {
        return this.messages;
    }

    /**
     * Get MarryDatabase instance.
     */
    public MarryDatabase getMarryDatabase() {
        return this.marryDatabase;
    }

    /**
     * Get MarryManager instance.
     */
    public net.schalker.SMPS.modules.marry.managers.MarryManager getMarryManager() {
        return this.marryManager;
    }

    /**
     * Get a formatted message from messages.yml with color codes applied.
     */
    public String getMessage(String key) {
        FileConfiguration msgs = this.getMessages();
        if (msgs == null) {
            return "&cMessage not found: " + key;
        }

        String message = msgs.getString(key, "&cMessage not found: " + key);
        return plugin.applyColors(message);
    }

    /**
     * Check if a command is enabled in config.
     */
    private boolean isCommandEnabled(String commandKey) {
        FileConfiguration cfg = this.getConfig();
        if (cfg == null) {
            return true;
        }
        return cfg.getBoolean("commands." + commandKey + ".enabled", true);
    }

    /**
     * Register a command safely with proper lifecycle handling.
     */
    private void registerCommandSafely(net.schalker.DoAPI.core.command.ModuleCommand command) {
        boolean hackEnabled = false;
        try {
            hackEnabled = plugin.getPluginReloader().setLifecycleContext();

            // Unregister existing command with same name/aliases
            this.unregisterCommandName(command.getName());
            this.unregisterAliases(command.getAliases());

            // Register the command
            plugin.getCommandManager().registerModuleCommand(command);

            // Track registered command names
            this.trackCommand(command.getName(), command.getAliases());

        } catch (Exception e) {
            plugin.getDebugSystem().logError("SM_Marry", "Failed to register command: " + command.getName(), e);
        } finally {
            if (hackEnabled) {
                plugin.getPluginReloader().clearLifecycleContext();
            }
        }
    }

    /**
     * Track command names for later cleanup.
     */
    private void trackCommand(String name, Collection<String> aliases) {
        if (name != null) {
            this.registeredCommandNames.add(name.toLowerCase());
        }
        if (aliases != null) {
            for (String alias : aliases) {
                if (alias != null) {
                    this.registeredCommandNames.add(alias.toLowerCase());
                }
            }
        }
    }

    /**
     * Unregister command aliases.
     */
    private void unregisterAliases(Collection<String> aliases) {
        if (aliases == null) {
            return;
        }
        for (String alias : aliases) {
            this.unregisterCommandName(alias);
        }
    }

    /**
     * Unregister all commands registered by this module.
     */
    private void unregisterAllCommands() {
        for (String name : this.registeredCommandNames) {
            this.unregisterCommandName(name);
        }
        this.registeredCommandNames.clear();
    }

    /**
     * Unregister a single command by name using reflection.
     */
    private void unregisterCommandName(String name) {
        if (name == null) {
            return;
        }
        String key = name.toLowerCase();

        try {
            var commandManager = plugin.getCommandManager();

            // Remove from internal sets/maps
            this.removeFromRegisteredCommands(commandManager, key);
            this.removeFromModuleCommands(commandManager, key);

            // Try to unregister from Paper's command registrar
            Object registrar = this.getCommandsRegistrar(commandManager);
            this.tryUnregisterFromRegistrar(registrar, key);

        } catch (Exception e) {
            plugin.getDebugSystem().logError("SM_Marry", "Failed to unregister command: " + name, e);
        }
    }

    /**
     * Remove a value from the 'registeredCommands' Set field using reflection.
     */
    private void removeFromRegisteredCommands(Object obj, Object value) {
        try {
            Field field = obj.getClass().getDeclaredField("registeredCommands");
            field.setAccessible(true);
            Object fieldValue = field.get(obj);
            if (fieldValue instanceof Set<?> set) {
                set.remove(value);
            }
        } catch (Exception ignored) {
            // Field might not exist or be different type - that's ok
        }
    }

    /**
     * Remove a key from the 'moduleCommands' Map field using reflection.
     */
    private void removeFromModuleCommands(Object obj, Object key) {
        try {
            Field field = obj.getClass().getDeclaredField("moduleCommands");
            field.setAccessible(true);
            Object fieldValue = field.get(obj);
            if (fieldValue instanceof Map<?, ?> map) {
                map.remove(key);
            }
        } catch (Exception ignored) {
            // Field might not exist or be different type - that's ok
        }
    }

    /**
     * Get the 'commandsRegistrar' field value using reflection.
     */
    private Object getCommandsRegistrar(Object obj) {
        try {
            Field field = obj.getClass().getDeclaredField("commandsRegistrar");
            field.setAccessible(true);
            return field.get(obj);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Try to unregister a command from Paper's command registrar using reflection.
     */
    private void tryUnregisterFromRegistrar(Object registrar, String commandName) {
        if (registrar == null) {
            return;
        }

        try {
            // Try to find and invoke unregister method
            for (Method method : registrar.getClass().getDeclaredMethods()) {
                if (method.getName().contains("unregister") && method.getParameterCount() == 1) {
                    method.setAccessible(true);
                    method.invoke(registrar, commandName);
                    break;
                }
            }
        } catch (Exception ignored) {
            // Unregister might not be supported - that's ok
        }
    }
}
