package net.myserver.plugin;

import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandManager;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.instance.InstanceManager;

/**
 * Context object passed to plugins on enable.
 * Provides access to core server APIs.
 */
public class PluginContext {
    private final GlobalEventHandler eventHandler;
    private final CommandManager commandManager;
    private final InstanceManager instanceManager;

    public PluginContext() {
        this.eventHandler = MinecraftServer.getGlobalEventHandler();
        this.commandManager = MinecraftServer.getCommandManager();
        this.instanceManager = MinecraftServer.getInstanceManager();
    }

    public GlobalEventHandler getEventHandler() {
        return eventHandler;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public InstanceManager getInstanceManager() {
        return instanceManager;
    }
}
