package ru.lor.watcher.model;

public class AutoEvent {

    private final String id;
    private int intervalMinutes;
    private WatcherSpawnSettings settings;

    public AutoEvent(String id, int intervalMinutes, WatcherSpawnSettings settings) {
        this.id = id;
        this.intervalMinutes = intervalMinutes;
        this.settings = settings;
    }

    public String getId() {
        return id;
    }

    public int getIntervalMinutes() {
        return intervalMinutes;
    }

    public void setIntervalMinutes(int intervalMinutes) {
        this.intervalMinutes = intervalMinutes;
    }

    public WatcherSpawnSettings getSettings() {
        return settings;
    }

    public void setSettings(WatcherSpawnSettings settings) {
        this.settings = settings;
    }
}
