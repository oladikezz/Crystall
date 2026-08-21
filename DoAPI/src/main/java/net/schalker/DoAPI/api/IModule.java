package net.schalker.DoAPI.api;

import net.schalker.DoAPI.core.module.ModuleInfo;

public interface IModule {

    void onEnable();

    void onDisable();

    void reload();

    ModuleInfo getModuleInfo();

    boolean isEnabled();
}
