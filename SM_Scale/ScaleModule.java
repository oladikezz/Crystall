package net.schalker.SMPS.modules.scale;

import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.DoAPI.core.module.BaseModule;
import net.schalker.DoAPI.core.module.ModuleInfo;
import net.schalker.SMPS.modules.scale.commands.ScaleCommand;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ScaleModule extends BaseModule {
    private YamlConfiguration config;
    private final Set<UUID> activeAnimations = ConcurrentHashMap.newKeySet();
    private final Set<String> registeredCommandNames = new HashSet<>();

    public ScaleModule(DoAPI plugin) {
        super(plugin, loadModuleInfo());
    }

    private static ModuleInfo loadModuleInfo() {
        try (InputStream stream = ScaleModule.class.getClassLoader().getResourceAsStream("module.yml")) {
            if (stream != null) {
                YamlConfiguration yml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
                return new ModuleInfo(
                    yml.getString("name", "SM_Scale"),
                    yml.getString("version", "1.0.0"),
                    yml.getString("author", "deforce_"),
                    yml.getString("description", "Player scaling module")
                );
            }
        } catch (Exception ignored) {}
        return new ModuleInfo("SM_Scale", "1.0.0", "deforce_", "Player scaling module");
    }

    @Override
    public void onEnable() {
        super.onEnable();

        this.config = this.plugin.getModuleManager().loadModuleConfig("SM_Scale");
        if (this.config == null) {
            this.plugin.getModuleManager().saveModuleDefaultConfig("SM_Scale");
            this.config = this.plugin.getModuleManager().loadModuleConfig("SM_Scale");
        }

        registerCommandSafely(new ScaleCommand(this.plugin, this));
        this.plugin.getDebugSystem().log("Scale", "Scale module enabled");
    }

    @Override
    public void onDisable() {
        super.onDisable();

        for (UUID uuid : activeAnimations) {
            this.plugin.getSchedulerManager().cancelTask("scale-anim-" + uuid);
        }
        activeAnimations.clear();

        unregisterAllCommands();

        this.plugin.getDebugSystem().log("Scale", "Scale module disabled");
    }

    @Override
    public void reload() {
        super.reload();
        this.config = this.plugin.getModuleManager().loadModuleConfig("SM_Scale");
        if (this.config == null) {
            this.config = new YamlConfiguration();
        }
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
            this.plugin.getDebugSystem().logError("Scale command registration failed", e);
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
            this.plugin.getDebugSystem().logError("Failed to unregister command (manager): " + name, e);
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

    public YamlConfiguration getConfig() {
        if (config == null) {
            this.config = this.plugin.getModuleManager().loadModuleConfig("SM_Scale");
            if (this.config == null) {
                this.config = new YamlConfiguration();
            }
        }
        return config;
    }

    @SuppressWarnings("removal")
    private AttributeInstance getScaleAttribute(Player player) {
        // 1.21.x: primary id from /attribute is minecraft:scale.
        try {
            Attribute attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("scale"));
            if (attribute != null) {
                AttributeInstance instance = player.getAttribute(attribute);
                if (instance != null) {
                    return instance;
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            Attribute attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.scale"));
            if (attribute != null) {
                AttributeInstance instance = player.getAttribute(attribute);
                if (instance != null) {
                    return instance;
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            Attribute byName = Attribute.valueOf("SCALE");
            AttributeInstance instance = player.getAttribute(byName);
            if (instance != null) {
                return instance;
            }
        } catch (IllegalArgumentException ignored) {
        }

        try {
            Attribute byName = Attribute.valueOf("GENERIC_SCALE");
            AttributeInstance instance = player.getAttribute(byName);
            if (instance != null) {
                return instance;
            }
        } catch (IllegalArgumentException ignored) {
        }

        for (Attribute attribute : Attribute.values()) {
            try {
                NamespacedKey key = attribute.getKey();
                if (key != null && key.getKey().contains("scale")) {
                    AttributeInstance instance = player.getAttribute(attribute);
                    if (instance != null) {
                        return instance;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public void animateScale(Player player, double targetScale) {
        UUID uuid = player.getUniqueId();
        String startTaskName = "scale-anim-start-" + uuid;
        String taskName = "scale-anim-" + uuid;

        this.plugin.getSchedulerManager().cancelTask(startTaskName);
        this.plugin.getSchedulerManager().cancelTask(taskName);
        activeAnimations.add(uuid);

        this.plugin.getSchedulerManager().runEntityTask(player, startTaskName, () -> {
            if (!player.isOnline()) {
                activeAnimations.remove(uuid);
                return;
            }

            final AttributeInstance scaleAttribute = getScaleAttribute(player);
            if (scaleAttribute == null) {
                this.plugin.getLogger().warning("SM_Scale: Could not find scale attribute for player " + player.getName());
                activeAnimations.remove(uuid);
                return;
            }

            final double startScale = scaleAttribute.getBaseValue();
            final int steps = 20;
            final double stepSize = (targetScale - startScale) / steps;

            this.plugin.getSchedulerManager().runEntityTaskTimer(player, taskName, new Runnable() {
                private int currentStep = 0;

                @Override
                public void run() {
                    if (!player.isOnline()) {
                        plugin.getSchedulerManager().cancelTask(taskName);
                        activeAnimations.remove(uuid);
                        return;
                    }

                    currentStep++;
                    scaleAttribute.setBaseValue(startScale + (stepSize * currentStep));

                    if (currentStep >= steps) {
                        scaleAttribute.setBaseValue(targetScale);
                        plugin.getSchedulerManager().cancelTask(taskName);
                        activeAnimations.remove(uuid);
                    }
                }
            }, 1L, 1L);
        });
    }

    public String getMessage(String path) {
        if (config == null) getConfig();
        String msg = config.getString("messages." + path);
        if (msg == null) return path;
        return this.plugin.applyColors(msg);
    }
}
