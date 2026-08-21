package net.schalker.SMPS.modules.phaseguard;

import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.DoAPI.core.module.BaseModule;
import net.schalker.DoAPI.core.module.ModuleInfo;
import net.schalker.SMPS.modules.phaseguard.commands.PhaseGuardCommand;
import net.schalker.SMPS.modules.phaseguard.listeners.MovementListener;
import net.schalker.SMPS.modules.phaseguard.listeners.SessionListener;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PhaseGuardModule extends BaseModule {

    public static final String MODULE_NAME = "SM_PhaseGuard";
    public static final String PERMISSION_BYPASS = "phaseguard.bypass";
    public static final String PERMISSION_ALERT = "phaseguard.alert";
    public static final String PERMISSION_ADMIN = "phaseguard.admin";

    private final Map<UUID, TrackedPlayer> tracked = new ConcurrentHashMap<>();

    private volatile PhaseGuardSettings settings;
    private FileConfiguration messages;
    private MovementListener movementListener;
    private SessionListener sessionListener;

    public PhaseGuardModule(DoAPI plugin) {
        super(plugin, new ModuleInfo(MODULE_NAME, "1.0.0", "DoIT",
                "Блокирует перемещение сквозь блоки (noclip / phase)"));
    }

    @Override
    public void onEnable() {
        super.onEnable();
        try {
            loadConfigs();
            registerPermissions();

            this.movementListener = new MovementListener(this);
            this.sessionListener = new SessionListener(this);
            this.plugin.getListenerManager().registerListener(this.movementListener);
            this.plugin.getListenerManager().registerListener(this.sessionListener);

            registerCommand(new PhaseGuardCommand(this.plugin, this));

            this.plugin.getDebugSystem().log("PhaseGuard",
                    "Модуль PhaseGuard включен (режим: " + this.settings.getMode() + ")");
        } catch (Throwable throwable) {
            this.plugin.getDebugSystem().logError("PhaseGuard", "Не удалось включить модуль", throwable);
            this.enabled = false;
            unregisterListeners();
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        unregisterListeners();
        this.tracked.clear();
        this.plugin.getDebugSystem().log("PhaseGuard", "Модуль PhaseGuard выключен");
    }

    @Override
    public void reload() {
        super.reload();
        loadConfigs();
    }

    private void unregisterListeners() {
        if (this.movementListener != null) {
            this.plugin.getListenerManager().unregisterListener(this.movementListener);
            this.movementListener = null;
        }
        if (this.sessionListener != null) {
            this.plugin.getListenerManager().unregisterListener(this.sessionListener);
            this.sessionListener = null;
        }
    }

    private void loadConfigs() {
        FileConfiguration config = this.plugin.getModuleManager().loadModuleConfig(MODULE_NAME);
        if (config == null) {
            config = new YamlConfiguration();
        }
        FileConfiguration loadedMessages = this.plugin.getModuleManager().loadModuleConfig(MODULE_NAME, "messages.yml");
        this.messages = loadedMessages == null ? new YamlConfiguration() : loadedMessages;

        PhaseGuardSettings loaded = PhaseGuardSettings.from(config);
        for (String unknown : loaded.getUnknownBlockNames()) {
            this.plugin.getDebugSystem().logWarning("PhaseGuard",
                    "Неизвестный материал в detection.ignored-blocks: " + unknown);
        }
        this.settings = loaded;
    }

    private void registerPermissions() {
        Map<String, Boolean> children = new LinkedHashMap<>();
        children.put(PERMISSION_BYPASS, true);
        children.put(PERMISSION_ALERT, true);

        addPermission(new Permission(PERMISSION_BYPASS,
                "Не проверять игрока на проход сквозь блоки", PermissionDefault.OP));
        addPermission(new Permission(PERMISSION_ALERT,
                "Получать оповещения о попытках пройти сквозь блоки", PermissionDefault.OP));
        addPermission(new Permission(PERMISSION_ADMIN,
                "Управление модулем PhaseGuard", PermissionDefault.OP, children));
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

    private void registerCommand(ModuleCommand command) {
        boolean lifecycle = false;
        try {
            lifecycle = this.plugin.getPluginReloader().setLifecycleContext();
            this.plugin.getCommandManager().registerModuleCommand(command);
        } catch (Throwable throwable) {
            this.plugin.getDebugSystem().logError("PhaseGuard", "Не удалось зарегистрировать команду", throwable);
        } finally {
            if (lifecycle) {
                this.plugin.getPluginReloader().clearLifecycleContext();
            }
        }
    }

    public DoAPI getApi() {
        return this.plugin;
    }

    public PhaseGuardSettings getSettings() {
        return this.settings;
    }

    public TrackedPlayer getTracked(UUID playerId) {
        return this.tracked.computeIfAbsent(playerId, key -> new TrackedPlayer());
    }

    public TrackedPlayer peekTracked(UUID playerId) {
        return this.tracked.get(playerId);
    }

    public void forget(UUID playerId) {
        this.tracked.remove(playerId);
    }

    public Map<UUID, TrackedPlayer> getTrackedPlayers() {
        return this.tracked;
    }

    public String getMessage(String key, String fallback) {
        String message = this.messages != null ? this.messages.getString(key) : null;
        if (message == null) {
            message = fallback;
        }
        if (message == null || message.isEmpty()) {
            return "";
        }
        return this.plugin.applyColors(message);
    }
}
