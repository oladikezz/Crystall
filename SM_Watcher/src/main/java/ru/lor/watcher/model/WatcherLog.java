package ru.lor.watcher.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class WatcherLog {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final long timestamp;
    private final String targetPlayerName;
    private final String executorName;
    private final int durationSeconds;
    private final String position;

    public WatcherLog(long timestamp, String targetPlayerName, String executorName, int durationSeconds, String position) {
        this.timestamp = timestamp;
        this.targetPlayerName = targetPlayerName;
        this.executorName = executorName;
        this.durationSeconds = durationSeconds;
        this.position = position;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getFormattedTime() {
        return FORMATTER.format(Instant.ofEpochMilli(timestamp));
    }

    public String getTargetPlayerName() {
        return targetPlayerName;
    }

    public String getExecutorName() {
        return executorName;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public String getPosition() {
        return position;
    }
}
