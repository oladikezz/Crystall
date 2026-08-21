package ru.lor.watcher.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.lor.watcher.WatcherPlugin;

import java.io.File;
import java.io.IOException;

public class YmlFile {

    private final WatcherPlugin plugin;
    private final String fileName;
    private File file;
    private FileConfiguration config;

    public YmlFile(WatcherPlugin plugin, String fileName) {
        this.plugin = plugin;
        this.fileName = fileName;
        saveDefaultConfig();
    }

    public void saveDefaultConfig() {
        if (file == null) {
            file = new File(plugin.getDataFolder(), fileName);
        }
        if (!file.exists()) {
            if (!plugin.saveResource(fileName)) {
                try {
                    File parent = file.getParentFile();
                    if (parent != null) {
                        parent.mkdirs();
                    }
                    file.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().severe("Could not create " + fileName + ": " + e.getMessage());
                }
            }
        }
        reloadConfig();
    }

    public void reloadConfig() {
        if (file == null) {
            file = new File(plugin.getDataFolder(), fileName);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public FileConfiguration getConfig() {
        if (config == null) {
            reloadConfig();
        }
        return config;
    }

    public void saveConfig() {
        if (config == null || file == null) {
            return;
        }
        try {
            config.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save config to " + file + ": " + ex.getMessage());
        }
    }
}
