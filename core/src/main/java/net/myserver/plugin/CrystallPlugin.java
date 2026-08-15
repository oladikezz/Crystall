package net.myserver.plugin;

/**
 * Base interface for all Crystall plugins.
 * 
 * To create a plugin:
 * 1. Implement this interface.
 * 2. Create META-INF/crystall-plugin.json in your JAR:
 *    {"name": "MyPlugin", "version": "1.0", "main": "com.example.MyPlugin"}
 * 3. Place the JAR in the server's plugins/ folder.
 */
public interface CrystallPlugin {

    /**
     * Called when the plugin is loaded and enabled.
     * Use this to register event listeners, commands, etc.
     */
    void onEnable(PluginContext context);

    /**
     * Called when the server is shutting down.
     * Use this to save data and clean up resources.
     */
    void onDisable();

    /**
     * Returns the plugin's display name (used in logs).
     */
    default String getName() {
        return getClass().getSimpleName();
    }

    /**
     * Returns the plugin's version string.
     */
    default String getVersion() {
        return "1.0";
    }
}
