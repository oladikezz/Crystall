package net.schalker.DoAPI.core.listener;

import net.schalker.DoAPI.DoAPI;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ListenerManager {

    private final DoAPI plugin;
    private final List<Listener> registeredListeners = new CopyOnWriteArrayList<>();

    public ListenerManager(DoAPI plugin) {
        this.plugin = plugin;
    }

    public void registerListener(Listener listener) {
        if (listener == null || registeredListeners.contains(listener)) {
            return;
        }

        try {
            Bukkit.getPluginManager().registerEvents(listener, plugin);
            registeredListeners.add(listener);
            if (plugin.getDebugSystem() != null && plugin.shouldLogListeners()) {
                plugin.getDebugSystem().log("Listeners", "Registered " + listener.getClass().getName());
            }
        } catch (Throwable throwable) {
            plugin.getDebugSystem().logError("Listeners",
                    "Failed to register " + listener.getClass().getName(), throwable);
        }
    }

    public void unregisterListener(Listener listener) {
        if (listener == null) {
            return;
        }

        try {
            HandlerList.unregisterAll(listener);
        } catch (Throwable ignored) {
        }
        registeredListeners.remove(listener);

        if (plugin.getDebugSystem() != null && plugin.shouldLogListeners()) {
            plugin.getDebugSystem().log("Listeners", "Unregistered " + listener.getClass().getName());
        }
    }

    public void unregisterAllListeners() {
        for (Listener listener : registeredListeners) {
            try {
                HandlerList.unregisterAll(listener);
            } catch (Throwable ignored) {
            }
        }
        registeredListeners.clear();
    }

    public int getListenerCount() {
        return registeredListeners.size();
    }
}
