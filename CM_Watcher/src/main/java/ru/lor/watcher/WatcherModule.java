package ru.lor.watcher;

import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.module.BaseModule;
import net.schalker.DoAPI.core.module.ModuleInfo;
import org.bukkit.Bukkit;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import ru.lor.watcher.commands.WatcherModuleCommand;

import java.util.LinkedHashMap;
import java.util.Map;

public class WatcherModule extends BaseModule {

    private static final String MODULE_NAME = WatcherPlugin.MODULE_NAME;

    private WatcherPlugin watcher;

    public WatcherModule(DoAPI plugin) {
        super(plugin, new ModuleInfo(MODULE_NAME, WatcherPlugin.VERSION, "ShaderCoder",
                "Watcher lore entity module"));
    }

    @Override
    public void onEnable() {
        super.onEnable();
        try {
            this.watcher = new WatcherPlugin(this.plugin);
            this.watcher.enable();
            registerPermissions();
            registerCommand();
        } catch (Throwable throwable) {
            this.plugin.getDebugSystem().logError("Watcher module failed to enable", throwable);
            this.enabled = false;
            if (this.watcher != null) {
                try {
                    this.watcher.disable();
                } catch (Throwable ignored) {
                }
                this.watcher = null;
            }
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (this.watcher != null) {
            this.watcher.disable();
            this.watcher = null;
        }
    }

    @Override
    public void reload() {
        super.reload();
        if (this.watcher != null) {
            this.watcher.reloadConfig();
        }
    }

    public WatcherPlugin getWatcher() {
        return watcher;
    }

    private void registerCommand() {
        boolean lifecycle = false;
        try {
            lifecycle = this.plugin.getPluginReloader().setLifecycleContext();
            this.plugin.getCommandManager().registerModuleCommand(new WatcherModuleCommand(this.watcher));
        } catch (Throwable throwable) {
            this.plugin.getDebugSystem().logError("Watcher command registration failed", throwable);
        } finally {
            if (lifecycle) {
                this.plugin.getPluginReloader().clearLifecycleContext();
            }
        }
    }

    private void registerPermissions() {
        Map<String, Boolean> children = new LinkedHashMap<>();
        children.put("watcher.use", true);
        children.put("watcher.spawn", true);
        children.put("watcher.message", true);
        children.put("watcher.events", true);

        addPermission(new Permission("watcher.use",
                "Доступ к GUI Смотрящего", PermissionDefault.OP));
        addPermission(new Permission("watcher.spawn",
                "Спавн и деспавн Смотрящего", PermissionDefault.OP));
        addPermission(new Permission("watcher.message",
                "Рассылка сообщений Смотрящего", PermissionDefault.OP));
        addPermission(new Permission("watcher.events",
                "Управление авто-событиями", PermissionDefault.OP));
        addPermission(new Permission("watcher.admin",
                "Полный доступ к Смотрящему", PermissionDefault.OP, children));
    }

    private void addPermission(Permission permission) {
        if (Bukkit.getPluginManager().getPermission(permission.getName()) != null) {
            return;
        }
        try {
            Bukkit.getPluginManager().addPermission(permission);
        } catch (IllegalArgumentException ignored) {
        }
    }
}
