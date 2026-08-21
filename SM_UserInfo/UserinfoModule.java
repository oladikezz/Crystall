package net.schalker.SMPS.modules.userinfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.DoAPI.core.module.BaseModule;
import net.schalker.DoAPI.core.module.ModuleInfo;
import net.schalker.SMPS.modules.userinfo.commands.UserinfoCommand;
import net.schalker.SMPS.modules.userinfo.database.UserinfoDatabase;
import net.schalker.SMPS.modules.userinfo.listeners.NicknameRevealListener;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class UserinfoModule extends BaseModule {
    public static final String MODULE_NAME = "SM_UserInfo";

    private UserinfoDatabase database;
    private FileConfiguration config;
    private NicknameRevealListener nicknameRevealListener;
    private final Set<String> registeredCommandNames = new HashSet<>();

    public UserinfoModule(DoAPI plugin) {
        super(plugin, new ModuleInfo(MODULE_NAME, "2.1.0", "ivan4", "Поиск данных игрока по таблицам БД"));
    }

    @Override
    public void onEnable() {
        super.onEnable();

        loadConfig();

        this.nicknameRevealListener = new NicknameRevealListener(plugin, this);
        plugin.getListenerManager().registerListener(this.nicknameRevealListener);

        if (!plugin.isDatabaseConnected()) {
            plugin.getLogger().severe(MODULE_NAME + ": Database is not connected! Module disabled.");
            enabled = false;
            return;
        }

        try {
            database = new UserinfoDatabase(plugin);
            registerCommandSafely(new UserinfoCommand(plugin, this));

            plugin.getDebugSystem().log(MODULE_NAME, "Module enabled");
        } catch (Exception e) {
            plugin.getDebugSystem().logError(MODULE_NAME + " init error", e);
            plugin.getLogger().severe(MODULE_NAME + " initialization failed");
            enabled = false;
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();

        if (this.nicknameRevealListener != null) {
            plugin.getListenerManager().unregisterListener(this.nicknameRevealListener);
            this.nicknameRevealListener = null;
        }

        unregisterAllCommands();
        database = null;

        plugin.getDebugSystem().log(MODULE_NAME, "Module disabled");
    }

    @Override
    public void reload() {
        super.reload();
        loadConfig();
        plugin.getDebugSystem().log(MODULE_NAME, "Module reloaded");
    }

    public UserinfoDatabase getDatabase() {
        return database;
    }

    private void loadConfig() {
        this.config = plugin.getModuleManager().loadModuleConfig(MODULE_NAME);
        if (this.config == null) {
            this.config = new YamlConfiguration();
        }
    }

    public boolean isNicknameRevealEnabled() {
        return this.config.getBoolean("nickname-reveal.enabled", true);
    }

    public String getNicknameRevealPermission() {
        return this.config.getString("nickname-reveal.permission", "");
    }

    public long getNicknameRevealCooldownMillis() {
        return this.config.getLong("nickname-reveal.cooldown-ms", 500L);
    }

    public String getNicknameRevealFormat() {
        return this.config.getString("nickname-reveal.format", "&[SECONDARY]Ник: &[MAIN]{player}");
    }

    private void registerCommandSafely(ModuleCommand command) {
        boolean hackEnabled = false;
        try {
            hackEnabled = this.plugin.getPluginReloader().setLifecycleContext();
            unregisterCommandName(command.getName());
            unregisterAliases(command.getAliases());
            this.plugin.getCommandManager().registerModuleCommand(command);
            trackCommand(command.getName(), command.getAliases());
        } catch (Exception e) {
            this.plugin.getDebugSystem().logError("UserInfo command registration failed", e);
        } finally {
            if (hackEnabled) {
                this.plugin.getPluginReloader().clearLifecycleContext();
            }
        }
    }

    private void trackCommand(String name, Collection<String> aliases) {
        if (name != null) {
            this.registeredCommandNames.add(name.toLowerCase());
        }
        if (aliases == null) {
            return;
        }
        for (String alias : aliases) {
            if (alias != null) {
                this.registeredCommandNames.add(alias.toLowerCase());
            }
        }
    }

    private void unregisterAliases(Collection<String> aliases) {
        if (aliases == null) {
            return;
        }
        for (String alias : aliases) {
            unregisterCommandName(alias);
        }
    }

    private void unregisterAllCommands() {
        for (String name : this.registeredCommandNames) {
            unregisterCommandName(name);
        }
        this.registeredCommandNames.clear();
    }

    private void unregisterCommandName(String name) {
        if (name == null) {
            return;
        }

        String key = name.toLowerCase();
        try {
            var commandManager = this.plugin.getCommandManager();
            removeFromSet(commandManager, "registeredCommands", key);
            removeFromMap(commandManager, "moduleCommands", key);
            Object registrar = getField(commandManager, "commandsRegistrar");
            tryUnregisterFromRegistrar(registrar, key);
        } catch (Exception e) {
            this.plugin.getDebugSystem().logError("Failed to unregister command: " + name, e);
        }
        removeFromCommandMap(key);
    }

    private void removeFromSet(Object target, String fieldName, String value) throws Exception {
        Object fieldValue = getField(target, fieldName);
        if (fieldValue instanceof Set<?> set) {
            @SuppressWarnings("unchecked")
            Set<String> stringSet = (Set<String>) set;
            stringSet.remove(value);
        }
    }

    private void removeFromMap(Object target, String fieldName, String key) throws Exception {
        Object fieldValue = getField(target, fieldName);
        if (fieldValue instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> stringMap = (Map<String, Object>) map;
            stringMap.remove(key);
        }
    }

    private Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private void tryUnregisterFromRegistrar(Object registrar, String name) {
        if (registrar == null || name == null) {
            return;
        }

        Method[] methods = registrar.getClass().getMethods();
        for (Method method : methods) {
            if (method.getParameterCount() != 1 || !method.getParameterTypes()[0].equals(String.class)) {
                continue;
            }

            String methodName = method.getName().toLowerCase();
            if (!(methodName.contains("unregister") || methodName.equals("remove") || methodName.equals("removecommand"))) {
                continue;
            }

            try {
                method.invoke(registrar, name);
                return;
            } catch (Exception ignored) {
            }
        }
    }

    private void removeFromCommandMap(String key) {
        try {
            Object commandMap = Bukkit.getServer().getCommandMap();
            if (commandMap == null) {
                return;
            }

            String pluginName = this.plugin.getName();
            String namespaced = pluginName == null ? null : pluginName.toLowerCase() + ":" + key;
            if (tryRemoveFromKnownCommands(commandMap, key, namespaced)) {
                return;
            }
            tryRemoveFromAnyMap(commandMap, key, namespaced);
        } catch (Exception ignored) {
        }
    }

    private boolean tryRemoveFromKnownCommands(Object commandMap, String key, String namespaced) {
        Field field = findField(commandMap.getClass(), "knownCommands");
        if (field == null) {
            return false;
        }

        try {
            field.setAccessible(true);
            Object value = field.get(commandMap);
            if (value instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> stringMap = (Map<String, Object>) map;
                boolean removed = stringMap.remove(key) != null;
                if (namespaced != null) {
                    removed |= stringMap.remove(namespaced) != null;
                }
                return removed;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void tryRemoveFromAnyMap(Object commandMap, String key, String namespaced) {
        for (Class<?> type = commandMap.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(field.getType())) {
                    continue;
                }

                try {
                    field.setAccessible(true);
                    Object value = field.get(commandMap);
                    if (value instanceof Map<?, ?> map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> stringMap = (Map<String, Object>) map;
                        boolean removed = stringMap.remove(key) != null;
                        if (namespaced != null) {
                            removed |= stringMap.remove(namespaced) != null;
                        }
                        if (removed) {
                            return;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    private Field findField(Class<?> type, String fieldName) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }
}
