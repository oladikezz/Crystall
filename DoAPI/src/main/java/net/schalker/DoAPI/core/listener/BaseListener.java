package net.schalker.DoAPI.core.listener;

import net.schalker.DoAPI.DoAPI;
import org.bukkit.event.Listener;

public abstract class BaseListener implements Listener {

    protected final DoAPI plugin;

    public BaseListener(DoAPI plugin) {
        this.plugin = plugin;
    }

    protected DoAPI getPlugin() {
        return plugin;
    }
}
