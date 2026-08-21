package net.schalker.DoAPI.core.module;

import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.api.IModule;

public abstract class BaseModule implements IModule {

    protected final DoAPI plugin;
    protected final ModuleInfo moduleInfo;
    protected boolean enabled;

    public BaseModule(DoAPI plugin, ModuleInfo moduleInfo) {
        this.plugin = plugin;
        this.moduleInfo = moduleInfo;
    }

    @Override
    public void onEnable() {
        this.enabled = true;
    }

    @Override
    public void onDisable() {
        this.enabled = false;
    }

    @Override
    public void reload() {
    }

    @Override
    public ModuleInfo getModuleInfo() {
        return moduleInfo;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    protected DoAPI getPlugin() {
        return plugin;
    }
}
