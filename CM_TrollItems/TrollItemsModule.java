package net.schalker.SMPS.modules.trollitems;

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
import net.schalker.SMPS.modules.trollitems.commands.TrollItemsCommand;
import net.schalker.SMPS.modules.trollitems.listeners.BowTrollListener;
import net.schalker.SMPS.modules.trollitems.listeners.GravityBombListener;
import net.schalker.SMPS.modules.trollitems.listeners.HandcuffsListener;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Bundles the three admin troll items (bow, handcuffs wand, gravity bomb) that used to be
 * separate modules under a single command and a single permission node, since they always
 * ship together and share the exact same item-marking / command-registration plumbing.
 */
public class TrollItemsModule extends BaseModule {
    private static final String MODULE_NAME = "SM_TrollItems";
    public static final String PERMISSION = "smtrollitems.admin";

    private FileConfiguration config;
    private FileConfiguration messages;
    private BowTrollListener bowListener;
    private HandcuffsListener handcuffsListener;
    private GravityBombListener bombListener;
    private NamespacedKey bowKey;
    private NamespacedKey wandKey;
    private NamespacedKey bombKey;
    private final Set<String> registeredCommandNames = new HashSet<>();

    public TrollItemsModule(DoAPI plugin) {
        super(plugin, new ModuleInfo(MODULE_NAME, "1.0.0", "MeXaNoBoP",
                "Тролль-предметы: лук-крюк, наручники и гравитационная бомба, только для админов"));
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.loadConfigs();
        this.bowKey = new NamespacedKey(this.plugin, "trollitems-bow");
        this.wandKey = new NamespacedKey(this.plugin, "trollitems-wand");
        this.bombKey = new NamespacedKey(this.plugin, "trollitems-bomb");

        this.bowListener = new BowTrollListener(this.plugin, this);
        this.handcuffsListener = new HandcuffsListener(this.plugin, this);
        this.bombListener = new GravityBombListener(this.plugin, this);
        this.plugin.getListenerManager().registerListener(this.bowListener);
        this.plugin.getListenerManager().registerListener(this.handcuffsListener);
        this.plugin.getListenerManager().registerListener(this.bombListener);

        if (this.isCommandEnabled("trollitem")) {
            this.registerCommandSafely(new TrollItemsCommand(this.plugin, this));
        }

        this.plugin.getDebugSystem().log(MODULE_NAME, "Модуль включен");
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (this.bowListener != null) {
            this.plugin.getListenerManager().unregisterListener(this.bowListener);
            this.bowListener.cleanup();
            this.bowListener = null;
        }
        if (this.handcuffsListener != null) {
            this.plugin.getListenerManager().unregisterListener(this.handcuffsListener);
            this.handcuffsListener.cleanup();
            this.handcuffsListener = null;
        }
        if (this.bombListener != null) {
            this.plugin.getListenerManager().unregisterListener(this.bombListener);
            this.bombListener.cleanup();
            this.bombListener = null;
        }
        this.unregisterAllCommands();
        this.plugin.getDebugSystem().log(MODULE_NAME, "Модуль выключен");
    }

    @Override
    public void reload() {
        super.reload();
        this.loadConfigs();
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

    private boolean isCommandEnabled(String key) {
        return this.config.getBoolean("commands." + key + ".enabled", true);
    }

    public String getMessage(String key) {
        String message = this.messages.getString(key, "&cMessage not found: " + key);
        return this.plugin.applyColors(message);
    }

    /** All three troll effects require this at both give-time (command permission) and use-time. */
    public boolean hasPermission(Player player) {
        return player.hasPermission(PERMISSION);
    }

    // ─── Bow ──────────────────────────────────────────────────────────

    public NamespacedKey getBowKey() {
        return this.bowKey;
    }

    public double getHookRange() {
        return this.config.getDouble("settings.bow.hook-range", 30.0);
    }

    public double getHookDistance() {
        return this.config.getDouble("settings.bow.hook-distance", 2.0);
    }

    public double getLaunchPower() {
        return this.config.getDouble("settings.bow.launch-power", 3.0);
    }

    public double getCollisionDamage() {
        return this.config.getDouble("settings.bow.collision-damage", 10.0);
    }

    public ItemStack createTrollBow() {
        ItemStack bow = new ItemStack(Material.BOW);
        ItemMeta meta = bow.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(this.plugin.applyColors(this.config.getString("settings.bow.bow-name", "&c&lХуюк")));
            meta.getPersistentDataContainer().set(this.bowKey, PersistentDataType.BYTE, (byte) 1);
            bow.setItemMeta(meta);
        }
        return bow;
    }

    public boolean isTrollBow(ItemStack item) {
        if (item == null || item.getType() != Material.BOW || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        Byte marker = meta.getPersistentDataContainer().get(this.bowKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    // ─── Handcuffs ────────────────────────────────────────────────────

    public NamespacedKey getWandKey() {
        return this.wandKey;
    }

    /**
     * Both the max distance allowed between two players to cuff them, and the leash
     * length after which the follower gets snapped back to the leader - kept as a single
     * value on purpose: it doubles as the Folia same-region guarantee when creating the
     * cuff (see HandcuffsListener for why that matters).
     */
    public double getPullDistance() {
        return this.config.getDouble("settings.handcuffs.pull-distance", 5.0);
    }

    public long getSelectionTimeoutMillis() {
        return this.config.getLong("settings.handcuffs.selection-timeout-seconds", 20) * 1000L;
    }

    public double getLeashParticleSpacing() {
        return this.config.getDouble("settings.handcuffs.leash-particle-spacing", 0.4);
    }

    public int getLeashParticleIntervalTicks() {
        return this.config.getInt("settings.handcuffs.leash-particle-interval-ticks", 2);
    }

    public Color getLeashParticleColor() {
        String raw = this.config.getString("settings.handcuffs.leash-particle-color", "40,40,40");
        String[] parts = raw.split(",");
        try {
            int r = Integer.parseInt(parts[0].trim());
            int g = Integer.parseInt(parts[1].trim());
            int b = Integer.parseInt(parts[2].trim());
            return Color.fromRGB(r, g, b);
        } catch (Exception e) {
            return Color.fromRGB(40, 40, 40);
        }
    }

    public ItemStack createWand() {
        ItemStack wand = new ItemStack(Material.STICK);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(this.plugin.applyColors(this.config.getString("settings.handcuffs.wand-name", "&e&lНаручники")));
            meta.getPersistentDataContainer().set(this.wandKey, PersistentDataType.BYTE, (byte) 1);
            wand.setItemMeta(meta);
        }
        return wand;
    }

    public boolean isWand(ItemStack item) {
        if (item == null || item.getType() != Material.STICK || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        Byte marker = meta.getPersistentDataContainer().get(this.wandKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    // ─── Gravity bomb ─────────────────────────────────────────────────

    public NamespacedKey getBombKey() {
        return this.bombKey;
    }

    public double getPullRadius() {
        return this.config.getDouble("settings.bomb.pull-radius", 8.0);
    }

    /** Set to false (e.g. while testing solo) to let the thrower get caught by their own bomb too. */
    public boolean isThrowerExcluded() {
        return this.config.getBoolean("settings.bomb.exclude-thrower", true);
    }

    public int getPullDurationTicks() {
        return this.config.getInt("settings.bomb.pull-duration-ticks", 15);
    }

    public double getPullSpeed() {
        return this.config.getDouble("settings.bomb.pull-speed", 0.6);
    }

    public double getTeleportThreshold() {
        return this.config.getDouble("settings.bomb.teleport-threshold", 1.5);
    }

    public int getCageWallHeight() {
        return this.config.getInt("settings.bomb.cage-wall-height", 3);
    }

    public long getCageDurationTicks() {
        return this.config.getLong("settings.bomb.cage-duration-seconds", 10) * 20L;
    }

    public long getThrowCooldownMillis() {
        return this.config.getLong("settings.bomb.throw-cooldown-seconds", 3) * 1000L;
    }

    public int getBombStackSize() {
        return this.config.getInt("settings.bomb.bomb-stack-size", 16);
    }

    public ItemStack createBomb() {
        ItemStack bomb = new ItemStack(Material.SNOWBALL, getBombStackSize());
        ItemMeta meta = bomb.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(this.plugin.applyColors(this.config.getString("settings.bomb.bomb-name", "&5&lГравитационная бомба")));
            meta.getPersistentDataContainer().set(this.bombKey, PersistentDataType.BYTE, (byte) 1);
            bomb.setItemMeta(meta);
        }
        return bomb;
    }

    public boolean isBomb(ItemStack item) {
        if (item == null || item.getType() != Material.SNOWBALL || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        Byte marker = meta.getPersistentDataContainer().get(this.bombKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    // ─── Command registration plumbing (reflection unregister, shared verbatim across modules) ──

    private void registerCommandSafely(ModuleCommand command) {
        boolean hackEnabled = false;
        try {
            hackEnabled = this.plugin.getPluginReloader().setLifecycleContext();
            this.unregisterCommandName(command.getName());
            this.unregisterAliases(command.getAliases());
            this.plugin.getCommandManager().registerModuleCommand(command);
            this.trackCommand(command.getName(), command.getAliases());
        } catch (Exception e) {
            this.plugin.getDebugSystem().logError("TrollItems command registration failed", e);
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
        if (aliases != null) {
            for (String alias : aliases) {
                if (alias != null) {
                    this.registeredCommandNames.add(alias.toLowerCase());
                }
            }
        }
    }

    private void unregisterAliases(Collection<String> aliases) {
        if (aliases == null) {
            return;
        }
        for (String alias : aliases) {
            this.unregisterCommandName(alias);
        }
    }

    private void unregisterAllCommands() {
        for (String name : this.registeredCommandNames) {
            this.unregisterCommandName(name);
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
            this.removeFromSet(commandManager, "registeredCommands", key);
            this.removeFromMap(commandManager, "moduleCommands", key);
            Object registrar = this.getField(commandManager, "commandsRegistrar");
            this.tryUnregisterFromRegistrar(registrar, key);
        } catch (Exception e) {
            this.plugin.getDebugSystem().logError("Failed to unregister command (manager): " + name, e);
        }
        this.removeFromCommandMap(key);
    }

    private void removeFromSet(Object target, String fieldName, String value) throws Exception {
        Object fieldValue = this.getField(target, fieldName);
        if (fieldValue instanceof Set<?> set) {
            @SuppressWarnings("unchecked")
            Set<String> stringSet = (Set<String>) set;
            stringSet.remove(value);
        }
    }

    private void removeFromMap(Object target, String fieldName, String key) throws Exception {
        Object fieldValue = this.getField(target, fieldName);
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
            if (method.getParameterCount() != 1) {
                continue;
            }
            if (!method.getParameterTypes()[0].equals(String.class)) {
                continue;
            }
            String methodName = method.getName().toLowerCase();
            if (methodName.contains("unregister") || methodName.equals("remove") || methodName.equals("removecommand")) {
                try {
                    method.invoke(registrar, name);
                    return;
                } catch (Exception ignored) {
                }
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
            if (this.tryRemoveFromKnownCommands(commandMap, key, namespaced)) {
                return;
            }
            this.tryRemoveFromAnyMap(commandMap, key, namespaced);
        } catch (Exception ignored) {
        }
    }

    private boolean tryRemoveFromKnownCommands(Object commandMap, String key, String namespaced) {
        Field field = this.findField(commandMap.getClass(), "knownCommands");
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
                        boolean removed = false;
                        if (stringMap.containsKey(key)) {
                            stringMap.remove(key);
                            removed = true;
                        }
                        if (namespaced != null && stringMap.containsKey(namespaced)) {
                            stringMap.remove(namespaced);
                            removed = true;
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
