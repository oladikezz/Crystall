package site.deforce.SM_Example;

import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.api.IModule;
import net.schalker.DoAPI.core.module.ModuleInfo;

/**
 * Minimal SM_Example scaffold.
 * Keeps module metadata + lifecycle hooks only.
 */
public class SM_Example implements IModule {

    private final DoAPI plugin;
    private final ModuleInfo info;
    private boolean enabled = false;

    // Constructor with SMPS parameter (preferred)
    public SM_Example(DoAPI plugin) {
        this.plugin = plugin;
        this.info = new ModuleInfo("SM_Example", "0.0.0", "Unknown", "Scaffold only");
    }

    @Override
    public void onEnable() {
        enabled = true;
        plugin.getDebugSystem().log("SM_Example", "Enabled (scaffold only)");
    }

    @Override
    public void onDisable() {
        enabled = false;
        plugin.getDebugSystem().log("SM_Example", "Disabled (scaffold only)");
    }

    @Override
    public void reload() {
        plugin.getDebugSystem().log("SM_Example", "Reloaded (scaffold only)");
    }

    @Override
    public ModuleInfo getModuleInfo() {
        return info;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
