package ru.lor.watcher.config;

import org.bukkit.configuration.file.FileConfiguration;
import ru.lor.watcher.WatcherPlugin;
import ru.lor.watcher.model.WatcherBehaviorType;
import ru.lor.watcher.model.WatcherPositionType;

import java.util.List;

public class ConfigManager {

    private final WatcherPlugin plugin;
    private YmlFile configFile;
    private YmlFile messagesFile;
    private YmlFile settingsFile;
    private YmlFile eventsFile;

    public ConfigManager(WatcherPlugin plugin) {
        this.plugin = plugin;
        loadConfigs();
    }

    public void loadConfigs() {
        configFile = new YmlFile(plugin, "config.yml");
        messagesFile = new YmlFile(plugin, "messages.yml");
        settingsFile = new YmlFile(plugin, "settings.yml");
        eventsFile = new YmlFile(plugin, "events.yml");
    }

    public void reloadAll() {
        configFile.reloadConfig();
        messagesFile.reloadConfig();
        settingsFile.reloadConfig();
        eventsFile.reloadConfig();
    }

    public FileConfiguration getConfig() {
        return configFile.getConfig();
    }

    public FileConfiguration getMessages() {
        return messagesFile.getConfig();
    }

    public FileConfiguration getSettings() {
        return settingsFile.getConfig();
    }

    public FileConfiguration getEvents() {
        return eventsFile.getConfig();
    }

    public void saveConfig() {
        configFile.saveConfig();
    }

    public void saveEvents() {
        eventsFile.saveConfig();
    }

    public String getMessage(String path) {
        String msg = getMessages().getString(path, "<red>Missing message: " + path + "</red>");
        String prefix = getMessages().getString("prefix", "");
        return msg.replace("{prefix}", prefix);
    }

    // Config defaults getters
    public String getBroadcastFormat() {
        return getMessages().getString("watcher-broadcast-format",
                "<dark_gray>[<purple>Смотрящий</purple>]</dark_gray> <white>{message}</white>");
    }

    public String getHeadTexture() {
        return getConfig().getString("watcher.head-texture", "");
    }

    public double getDefaultSpawnDistance() {
        return getConfig().getDouble("defaults.spawn-distance", 5.0);
    }

    public int getDefaultDurationSeconds() {
        return getConfig().getInt("defaults.duration-seconds", 30);
    }

    public double getDefaultDespawnDistance() {
        return getConfig().getDouble("defaults.despawn-distance", 20.0);
    }

    public WatcherPositionType getDefaultPosition() {
        String pos = getConfig().getString("defaults.position", "BEHIND");
        try {
            return WatcherPositionType.valueOf(pos.toUpperCase());
        } catch (IllegalArgumentException e) {
            return WatcherPositionType.BEHIND;
        }
    }

    public WatcherBehaviorType getDefaultBehavior() {
        String beh = getConfig().getString("defaults.behavior", "STATIC");
        try {
            return WatcherBehaviorType.valueOf(beh.toUpperCase());
        } catch (IllegalArgumentException e) {
            return WatcherBehaviorType.STATIC;
        }
    }

    // Settings presets getters
    public List<Double> getSpawnDistancePresets() {
        return getSettings().getDoubleList("presets.spawn-distances");
    }

    public List<Integer> getDurationPresets() {
        return getSettings().getIntegerList("presets.durations");
    }

    public List<Double> getDespawnDistancePresets() {
        return getSettings().getDoubleList("presets.despawn-distances");
    }

    public List<String> getSoundPresets() {
        return getSettings().getStringList("sounds");
    }
}
