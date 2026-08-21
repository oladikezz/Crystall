package net.schalker.SMPS.modules.announces;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.DoAPI.core.module.BaseModule;
import net.schalker.DoAPI.core.module.ModuleInfo;
import net.schalker.SMPS.modules.announces.commands.ConsoleAlertCommand;
import net.schalker.SMPS.modules.announces.commands.ConsoleAlertLogCommand;
import net.schalker.SMPS.modules.announces.format.AnnounceFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.time.Duration;

public class AnnouncesModule extends BaseModule {
    private static final String MODULE_NAME = "SM_Announces";
    private static final String DEFAULT_SOUND = "minecraft:block.note_block.chime";

    private FileConfiguration config;
    private FileConfiguration messages;
    private AnnounceFormatter formatter;
    private final Set<String> registeredCommandNames = new HashSet<>();

    public AnnouncesModule(DoAPI plugin) {
        super(plugin, new ModuleInfo(MODULE_NAME, "1.0.0", "deforce_", "Console alerts with chat/title + MiniMessage"));
    }

    @Override
    public void onEnable() {
        super.onEnable();

        this.plugin.getModuleManager().saveModuleDefaultConfig(MODULE_NAME);
        this.plugin.getModuleManager().saveModuleDefaultConfig(MODULE_NAME, "messages.yml", "messages.yml");

        loadConfigs();
        this.formatter = new AnnounceFormatter(this.plugin, this);

        if (isCommandEnabled("consolealert")) {
            registerCommandSafely(new ConsoleAlertCommand(this.plugin, this));
        }
        if (isCommandEnabled("consolealertlog")) {
            registerCommandSafely(new ConsoleAlertLogCommand(this.plugin, this));
        }

        this.plugin.getDebugSystem().log("Announces", "SM_Announces enabled");
    }

    @Override
    public void onDisable() {
        super.onDisable();
        unregisterAllCommands();
        this.plugin.getDebugSystem().log("Announces", "SM_Announces disabled");
    }

    @Override
    public void reload() {
        super.reload();
        loadConfigs();
        if (this.formatter == null) {
            this.formatter = new AnnounceFormatter(this.plugin, this);
        }
    }

    private void loadConfigs() {
        this.config = this.plugin.getModuleManager().loadModuleConfig(MODULE_NAME);
        this.messages = this.plugin.getModuleManager().loadModuleConfig(MODULE_NAME, "messages.yml");

        if (this.config == null) {
            this.config = new YamlConfiguration();
        }
        if (this.messages == null) {
            this.messages = new YamlConfiguration();
        }
    }

    public boolean isCommandEnabled(String commandKey) {
        return this.config.getBoolean("commands." + commandKey + ".enabled", true);
    }

    public AnnounceFormatter getFormatter() {
        if (this.formatter == null) {
            this.formatter = new AnnounceFormatter(this.plugin, this);
        }
        return this.formatter;
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

    public void sendAlert(Player player, ConsoleAlertCommand.AlertType type, Component content, String soundName) {
        if (player == null || content == null) {
            return;
        }

        String taskName = "announces-alert-" + player.getUniqueId() + "-" + System.nanoTime();
        this.plugin.getSchedulerManager().runEntityTask(player, taskName, () -> {
            if (!player.isOnline()) {
                return;
            }

            switch (type) {
                case CHAT -> player.sendMessage(content);
                case TITLE -> player.showTitle(buildTitle(content));
                case ALL -> {
                    player.sendMessage(content);
                    player.showTitle(buildTitle(content));
                }
            }

            playNotificationSound(player, soundName);
        });
    }

    public String getDefaultSound() {
        String configured = this.config.getString("sound.default", DEFAULT_SOUND);
        if (configured == null || configured.isBlank()) {
            return DEFAULT_SOUND;
        }
        return configured.trim();
    }

    public boolean isConsoleLogEnabled() {
        return this.config.getBoolean("logging.console-sent-feedback", true);
    }

    public void setConsoleLogEnabled(boolean enabled) {
        this.config.set("logging.console-sent-feedback", enabled);
        saveConfigSafe();
    }

    public boolean toggleConsoleLogEnabled() {
        boolean next = !isConsoleLogEnabled();
        setConsoleLogEnabled(next);
        return next;
    }

    public boolean isValidSoundKey(String soundName) {
        if (soundName == null || soundName.isBlank()) {
            return true;
        }
        if ("none".equalsIgnoreCase(soundName)) {
            return true;
        }
        return NamespacedKey.fromString(soundName.toLowerCase(Locale.ROOT)) != null;
    }

    private void playNotificationSound(Player player, String soundName) {
        String resolved = (soundName == null || soundName.isBlank()) ? getDefaultSound() : soundName.trim();
        if ("none".equalsIgnoreCase(resolved)) {
            return;
        }

        float volume = (float) this.config.getDouble("sound.volume", 1.0D);
        float pitch = (float) this.config.getDouble("sound.pitch", 1.0D);

        try {
            player.playSound(player.getLocation(), resolved, volume, pitch);
        } catch (Exception ex) {
            this.plugin.getDebugSystem().logWarning("Announces", "Failed to play notification sound: " + resolved, ex);
        }
    }

    private void saveConfigSafe() {
        try {
            File folder = this.plugin.getModuleManager().getModuleDataFolder(MODULE_NAME);
            if (folder == null) {
                return;
            }
            if (!folder.exists()) {
                folder.mkdirs();
            }
            this.config.save(new File(folder, "config.yml"));
        } catch (Exception ex) {
            this.plugin.getDebugSystem().logWarning("Announces", "Failed to save config.yml", ex);
        }
    }

    private Title buildTitle(Component titleComponent) {
        String subtitleRaw = this.config.getString("title.subtitle", "");
        Component subtitle = subtitleRaw == null || subtitleRaw.isBlank()
            ? Component.empty()
            : getFormatter().format(subtitleRaw);

        int fadeInTicks = this.config.getInt("title.fade-in", 10);
        int stayTicks = this.config.getInt("title.stay", 60);
        int fadeOutTicks = this.config.getInt("title.fade-out", 10);

        Title.Times times = Title.Times.times(
            Duration.ofMillis(Math.max(0, fadeInTicks) * 50L),
            Duration.ofMillis(Math.max(0, stayTicks) * 50L),
            Duration.ofMillis(Math.max(0, fadeOutTicks) * 50L)
        );

        return Title.title(titleComponent, subtitle, times);
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
            this.plugin.getDebugSystem().logError("Announces command registration failed", e);
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
}

