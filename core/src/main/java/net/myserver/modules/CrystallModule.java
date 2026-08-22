package net.myserver.modules;

import net.minestom.server.event.GlobalEventHandler;

public interface CrystallModule {
    /**
     * Unique identifier for the module (e.g. "vanish", "clans", "cosmetics").
     */
    String getId();

    /**
     * User-friendly display name of the module.
     */
    String getName();

    /**
     * Description of what this module does.
     */
    String getDescription();

    /**
     * Called when the module is enabled.
     */
    void onEnable(GlobalEventHandler eventHandler);

    /**
     * Called when the module is disabled.
     */
    void onDisable();

    /**
     * Default enabled status from configuration.
     */
    default boolean isEnabledByDefault() {
        return true;
    }
}
