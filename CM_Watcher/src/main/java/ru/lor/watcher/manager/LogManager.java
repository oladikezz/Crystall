package ru.lor.watcher.manager;

import org.bukkit.configuration.file.FileConfiguration;
import ru.lor.watcher.WatcherPlugin;
import ru.lor.watcher.config.YmlFile;
import ru.lor.watcher.model.WatcherLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LogManager {

    private final WatcherPlugin plugin;
    private final YmlFile logsFile;
    private final List<WatcherLog> logs = new ArrayList<>();

    public LogManager(WatcherPlugin plugin) {
        this.plugin = plugin;
        this.logsFile = new YmlFile(plugin, "logs.yml");
        loadLogs();
    }

    private void loadLogs() {
        logs.clear();
        FileConfiguration config = logsFile.getConfig();
        if (config.contains("logs")) {
            for (String key : config.getConfigurationSection("logs").getKeys(false)) {
                long timestamp = config.getLong("logs." + key + ".timestamp");
                String target = config.getString("logs." + key + ".target");
                String executor = config.getString("logs." + key + ".executor");
                int duration = config.getInt("logs." + key + ".duration");
                String position = config.getString("logs." + key + ".position");

                logs.add(new WatcherLog(timestamp, target, executor, duration, position));
            }
        }
    }

    public void addLog(WatcherLog log) {
        logs.add(log);
        String key = String.valueOf(log.getTimestamp());
        FileConfiguration config = logsFile.getConfig();
        config.set("logs." + key + ".timestamp", log.getTimestamp());
        config.set("logs." + key + ".target", log.getTargetPlayerName());
        config.set("logs." + key + ".executor", log.getExecutorName());
        config.set("logs." + key + ".duration", log.getDurationSeconds());
        config.set("logs." + key + ".position", log.getPosition());
        logsFile.saveConfig();
    }

    public List<WatcherLog> getLogs() {
        List<WatcherLog> copy = new ArrayList<>(logs);
        Collections.reverse(copy); // Latest first
        return copy;
    }
}
