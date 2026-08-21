package ru.lor.watcher.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import ru.lor.watcher.watcher.WatcherEntity;

public class WatcherDespawnEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player target;
    private final WatcherEntity watcherEntity;
    private final DespawnReason reason;

    public enum DespawnReason {
        EXPIRED,
        DISTANCE_EXCEEDED,
        PLAYER_QUIT,
        MANUAL_DESPAWN,
        APPROACHED_OR_ATTACKED,
        PLUGIN_DISABLE
    }

    public WatcherDespawnEvent(Player target, WatcherEntity watcherEntity, DespawnReason reason) {
        this.target = target;
        this.watcherEntity = watcherEntity;
        this.reason = reason;
    }

    public Player getTarget() {
        return target;
    }

    public WatcherEntity getWatcherEntity() {
        return watcherEntity;
    }

    public DespawnReason getReason() {
        return reason;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
