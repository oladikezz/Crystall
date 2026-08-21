package net.schalker.DoAPI.core.config;

import net.schalker.DoAPI.DoAPI;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

public class ConfigManager {

    private final DoAPI plugin;
    private FileConfiguration config;
    private final Map<String, Object> defaultConfigValues = new LinkedHashMap<>();

    public ConfigManager(DoAPI plugin) {
        this.plugin = plugin;

        defaultConfigValues.put("prefix", "&6[&eDoAPI&6]&r");
        defaultConfigValues.put("main-color", "&#f44d89");
        defaultConfigValues.put("secondary-color", "&#FFA1C4");
        defaultConfigValues.put("debug", true);
        defaultConfigValues.put("file-logging", true);
        defaultConfigValues.put("detail-level", "DETAILED");
        defaultConfigValues.put("log-module-events", true);
        defaultConfigValues.put("log-commands", true);
        defaultConfigValues.put("log-listeners", true);
        defaultConfigValues.put("log-scheduler", true);
        defaultConfigValues.put("webhook.url", "");
        defaultConfigValues.put("webhook.enabled", true);
        defaultConfigValues.put("webhook.send-warnings", true);
        defaultConfigValues.put("auto-load-modules", true);
        defaultConfigValues.put("database.type", "mysql");
        defaultConfigValues.put("database.host", "localhost");
        defaultConfigValues.put("database.port", 3306);
        defaultConfigValues.put("database.database", "DoAPI");
        defaultConfigValues.put("database.username", "root");
        defaultConfigValues.put("database.password", "");
        defaultConfigValues.put("database.pool.size", 10);
        defaultConfigValues.put("database.pool.connection-timeout", 30000);
        defaultConfigValues.put("database.pool.idle-timeout", 600000);
        defaultConfigValues.put("database.pool.max-lifetime", 1800000);
        defaultConfigValues.put("database.table-prefix", "sm_");
        defaultConfigValues.put("database.file", "database");
    }

    public void initialize() {
        plugin.saveDefaultConfig();
        this.config = plugin.getConfig();
        verifyDefaultValues();
    }

    public FileConfiguration getConfig() {
        if (config == null) {
            config = plugin.getConfig();
        }
        return config;
    }

    public void reloadConfig() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        verifyDefaultValues();
    }

    private void verifyDefaultValues() {
        boolean changed = false;
        for (Map.Entry<String, Object> entry : defaultConfigValues.entrySet()) {
            if (!config.contains(entry.getKey())) {
                config.set(entry.getKey(), entry.getValue());
                changed = true;
            }
        }
        if (changed) {
            plugin.saveConfig();
        }
    }
}
