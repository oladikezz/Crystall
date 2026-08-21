package ru.lor.watcher.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import ru.lor.watcher.model.WatcherSpawnSettings;
import ru.lor.watcher.watcher.WatcherEntity;

public class WatcherSpawnEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player target;
    private final WatcherEntity watcherEntity;
    private final WatcherSpawnSettings settings;
    private final String executorName;
    private boolean cancelled = false;

    public WatcherSpawnEvent(Player target, WatcherEntity watcherEntity, WatcherSpawnSettings settings, String executorName) {
        this.target = target;
        this.watcherEntity = watcherEntity;
        this.settings = settings;
        this.executorName = executorName;
    }

    public Player getTarget() {
        return target;
    }

    public WatcherEntity getWatcherEntity() {
        return watcherEntity;
    }

    public WatcherSpawnSettings getSettings() {
        return settings;
    }

    public String getExecutorName() {
        return executorName;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
