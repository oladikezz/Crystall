package net.schalker.DoAPI.core.module;

import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.api.IModule;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredListener;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ModuleManager {

    static class LoadedModule {

        final IModule module;
        final URLClassLoader classLoader;
        final File jarFile;
        final ModuleInfo info;
        final String packagePrefix;

        LoadedModule(IModule module, URLClassLoader classLoader, File jarFile, ModuleInfo info, String packagePrefix) {
            this.module = module;
            this.classLoader = classLoader;
            this.jarFile = jarFile;
            this.info = info;
            this.packagePrefix = packagePrefix;
        }
    }

    private final DoAPI plugin;
    private final Map<String, LoadedModule> modules = new LinkedHashMap<>();
    private final List<String> loadOrder = new ArrayList<>();
    private final File modulesFolder;

    public ModuleManager(DoAPI plugin) {
        this.plugin = plugin;
        this.modulesFolder = new File(plugin.getDataFolder(), "modules");
        if (!modulesFolder.exists() && !modulesFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create modules folder " + modulesFolder.getPath());
        }
    }

    public File getModulesFolder() {
        return modulesFolder;
    }

    public boolean shouldAutoLoadModules() {
        return plugin.getConfigManager().getConfig().getBoolean("auto-load-modules", true);
    }

    public List<String> getIgnoredModules() {
        return plugin.getConfigManager().getConfig().getStringList("ignored-modules");
    }

    public void discoverAndLoadModules() {
        File[] jars = modulesFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            plugin.getLogger().info("No module JARs found in " + modulesFolder.getPath());
            return;
        }

        List<String> ignored = getIgnoredModules();
        for (File jar : jars) {
            try {
                ModuleInfo info = readModuleInfo(jar);
                if (info == null) {
                    plugin.getLogger().warning("Skipping " + jar.getName() + ": module.yml missing or invalid");
                    continue;
                }
                if (containsIgnoreCase(ignored, info.getName()) || containsIgnoreCase(ignored, jar.getName())) {
                    plugin.getLogger().info("Skipping ignored module " + info.getName());
                    continue;
                }
                loadModule(jar);
            } catch (Throwable throwable) {
                plugin.getLogger().severe("Failed to load module " + jar.getName() + ": " + throwable.getMessage());
                plugin.getDebugSystem().logError("ModuleManager",
                        "Failed to load module " + jar.getName(), throwable);
            }
        }
    }

    public boolean loadModule(File file) throws Exception {
        if (file == null || !file.isFile()) {
            throw new IOException("Module file does not exist");
        }

        ModuleInfo info = readModuleInfo(file);
        if (info == null) {
            throw new IOException("module.yml missing or invalid in " + file.getName());
        }

        String key = info.getName().toLowerCase(Locale.ROOT);
        synchronized (modules) {
            if (modules.containsKey(key)) {
                plugin.getLogger().warning("Module " + info.getName() + " is already loaded");
                return false;
            }
        }

        String mainClass = readMainClass(file);
        if (mainClass == null || mainClass.isBlank()) {
            throw new IOException("module.yml of " + file.getName() + " has no 'main' entry");
        }

        ModuleClassLoader loader = new ModuleClassLoader(
                info.getName(),
                new URL[]{file.toURI().toURL()},
                plugin.getClass().getClassLoader());

        IModule module;
        try {
            Class<?> mainType = Class.forName(mainClass, true, loader);
            if (!IModule.class.isAssignableFrom(mainType)) {
                throw new IOException(mainClass + " does not implement IModule");
            }
            module = instantiate(mainType);
        } catch (Throwable throwable) {
            closeQuietly(loader);
            throw new Exception("Could not instantiate " + mainClass + ": " + throwable, throwable);
        }

        int lastDot = mainClass.lastIndexOf('.');
        String packagePrefix = lastDot > 0 ? mainClass.substring(0, lastDot) : mainClass;

        synchronized (modules) {
            modules.put(key, new LoadedModule(module, loader, file, info, packagePrefix));
            loadOrder.add(info.getName());
        }

        if (loader.hasLegacyClasses()) {
            plugin.getLogger().warning("Module " + info.getName()
                    + " was built against the old SMPS core and is running through the compatibility remapper."
                    + " Rebuild it against DoAPI to remove this warning.");
        }

        if (plugin.shouldLogModuleEvents()) {
            plugin.getDebugSystem().log("ModuleManager", "Loaded " + info);
        }
        return true;
    }

    private IModule instantiate(Class<?> type) throws Exception {
        for (Constructor<?> constructor : type.getConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            if (parameters.length == 1 && parameters[0].isAssignableFrom(DoAPI.class)) {
                return (IModule) constructor.newInstance(plugin);
            }
        }
        return (IModule) type.getDeclaredConstructor().newInstance();
    }

    private ModuleInfo readModuleInfo(File file) {
        try (JarFile jar = new JarFile(file)) {
            JarEntry entry = jar.getJarEntry("module.yml");
            if (entry == null) {
                return null;
            }
            try (InputStream stream = jar.getInputStream(entry)) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
                String name = yaml.getString("name");
                if (name == null || name.isBlank()) {
                    return null;
                }
                return new ModuleInfo(
                        name,
                        yaml.getString("version", "1.0.0"),
                        yaml.getString("author", "Unknown"),
                        yaml.getString("description", ""));
            }
        } catch (Throwable throwable) {
            return null;
        }
    }

    private String readMainClass(File file) {
        try (JarFile jar = new JarFile(file)) {
            JarEntry entry = jar.getJarEntry("module.yml");
            if (entry == null) {
                return null;
            }
            try (InputStream stream = jar.getInputStream(entry)) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
                return yaml.getString("main");
            }
        } catch (Throwable throwable) {
            return null;
        }
    }

    public boolean unloadModule(String name) {
        LoadedModule loaded = resolve(name);
        if (loaded == null) {
            return false;
        }

        if (loaded.module.isEnabled()) {
            disableModule(loaded.info.getName());
        }

        synchronized (modules) {
            modules.remove(loaded.info.getName().toLowerCase(Locale.ROOT));
            loadOrder.remove(loaded.info.getName());
        }

        unregisterListenersOf(loaded.classLoader);
        closeQuietly(loaded.classLoader);

        if (plugin.shouldLogModuleEvents()) {
            plugin.getDebugSystem().log("ModuleManager", "Unloaded " + loaded.info.getName());
        }
        return true;
    }

    public boolean reloadModule(String name) {
        LoadedModule loaded = resolve(name);
        if (loaded == null) {
            return false;
        }

        try {
            loaded.module.reload();
            if (plugin.shouldLogModuleEvents()) {
                plugin.getDebugSystem().log("ModuleManager", "Reloaded " + loaded.info.getName());
            }
            return true;
        } catch (Throwable throwable) {
            plugin.getDebugSystem().logError(loaded.info.getName(), "Reload failed", throwable);
            return false;
        }
    }

    public void enableAllModules() {
        for (String name : snapshotLoadOrder()) {
            enableModule(name);
        }
    }

    public void disableAllModules() {
        List<String> order = snapshotLoadOrder();
        Collections.reverse(order);
        for (String name : order) {
            disableModule(name);
        }
    }

    public void unloadAllModules() {
        List<String> order = snapshotLoadOrder();
        Collections.reverse(order);
        for (String name : order) {
            unloadModule(name);
        }
    }

    public void reloadAllModules() {
        for (String name : snapshotLoadOrder()) {
            reloadModule(name);
        }
    }

    public boolean enableModule(String name) {
        LoadedModule loaded = resolve(name);
        if (loaded == null || loaded.module.isEnabled()) {
            return false;
        }

        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(loaded.classLoader);
            loaded.module.onEnable();
            if (plugin.shouldLogModuleEvents()) {
                plugin.getDebugSystem().log("ModuleManager", "Enabled " + loaded.info.getName());
            }
            return true;
        } catch (Throwable throwable) {
            plugin.getDebugSystem().logError(loaded.info.getName(), "Enable failed", throwable);
            return false;
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    public boolean disableModule(String name) {
        LoadedModule loaded = resolve(name);
        if (loaded == null || !loaded.module.isEnabled()) {
            return false;
        }

        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(loaded.classLoader);
            loaded.module.onDisable();
            if (plugin.shouldLogModuleEvents()) {
                plugin.getDebugSystem().log("ModuleManager", "Disabled " + loaded.info.getName());
            }
            return true;
        } catch (Throwable throwable) {
            plugin.getDebugSystem().logError(loaded.info.getName(), "Disable failed", throwable);
            return false;
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    public IModule getModule(String name) {
        LoadedModule loaded = resolve(name);
        return loaded == null ? null : loaded.module;
    }

    public Collection<IModule> getAllModules() {
        List<IModule> result = new ArrayList<>();
        synchronized (modules) {
            for (LoadedModule loaded : modules.values()) {
                result.add(loaded.module);
            }
        }
        return result;
    }

    public int getModuleCount() {
        synchronized (modules) {
            return modules.size();
        }
    }

    public int getEnabledModuleCount() {
        int count = 0;
        synchronized (modules) {
            for (LoadedModule loaded : modules.values()) {
                if (loaded.module.isEnabled()) {
                    count++;
                }
            }
        }
        return count;
    }

    public Collection<String> getModuleNames() {
        return snapshotLoadOrder();
    }

    public ModuleInfo getModuleInfo(String name) {
        LoadedModule loaded = resolve(name);
        return loaded == null ? null : loaded.info;
    }

    public String getModuleNameForClass(String className) {
        if (className == null) {
            return null;
        }
        synchronized (modules) {
            for (LoadedModule loaded : modules.values()) {
                if (className.startsWith(loaded.packagePrefix)) {
                    return loaded.info.getName();
                }
            }
        }
        return null;
    }

    public boolean loadModuleByFileName(String fileName) {
        return loadModuleByFileNameAndGetName(fileName) != null;
    }

    public String loadModuleByFileNameAndGetName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }

        String normalized = fileName.toLowerCase(Locale.ROOT).endsWith(".jar") ? fileName : fileName + ".jar";
        File file = new File(modulesFolder, normalized);
        if (!file.isFile()) {
            plugin.getLogger().warning("Module file not found: " + normalized);
            return null;
        }

        try {
            if (!loadModule(file)) {
                return null;
            }
            ModuleInfo info = readModuleInfo(file);
            if (info == null) {
                return null;
            }
            enableModule(info.getName());
            return info.getName();
        } catch (Throwable throwable) {
            plugin.getDebugSystem().logError("ModuleManager", "Failed to load " + normalized, throwable);
            return null;
        }
    }

    public void reloadConfig() {
        reloadAllModules();
    }

    public YamlConfiguration loadOrCreateConfig(String moduleName, String fileName) {
        File folder = getModuleDataFolder(moduleName);
        File file = new File(folder, fileName);

        if (!file.exists()) {
            saveModuleDefaultConfig(moduleName, fileName, fileName);
        }
        if (!file.exists()) {
            return new YamlConfiguration();
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    public boolean saveModuleDefaultConfig(String moduleName, String jarPath, String targetPath) {
        LoadedModule loaded = resolve(moduleName);
        if (loaded == null) {
            return false;
        }

        File target = new File(getModuleDataFolder(moduleName), targetPath);
        if (target.exists()) {
            return false;
        }

        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return false;
        }

        try (JarFile jar = new JarFile(loaded.jarFile)) {
            JarEntry entry = jar.getJarEntry(jarPath);
            if (entry == null) {
                return false;
            }
            try (InputStream stream = jar.getInputStream(entry)) {
                Files.copy(stream, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Throwable throwable) {
            plugin.getDebugSystem().logError("ModuleManager",
                    "Failed to extract " + jarPath + " from " + moduleName, throwable);
            return false;
        }
    }

    public boolean saveModuleDefaultConfig(String moduleName) {
        return saveModuleDefaultConfig(moduleName, "config.yml", "config.yml");
    }

    public YamlConfiguration loadModuleConfig(String moduleName, String fileName) {
        return loadOrCreateConfig(moduleName, fileName);
    }

    public YamlConfiguration loadModuleConfig(String moduleName) {
        return loadOrCreateConfig(moduleName, "config.yml");
    }

    public File getModuleDataFolder(String moduleName) {
        LoadedModule loaded = resolve(moduleName);
        String folderName = loaded == null ? moduleName : loaded.info.getName();

        File folder = new File(modulesFolder, folderName);
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create data folder for module " + folderName);
        }
        return folder;
    }

    public List<String> getUnloadedModuleFiles() {
        File[] jars = modulesFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".jar"));
        List<String> result = new ArrayList<>();
        if (jars == null) {
            return result;
        }

        for (File jar : jars) {
            ModuleInfo info = readModuleInfo(jar);
            if (info == null || resolve(info.getName()) == null) {
                result.add(jar.getName());
            }
        }
        return result;
    }

    private LoadedModule resolve(String name) {
        if (name == null) {
            return null;
        }
        synchronized (modules) {
            return modules.get(name.toLowerCase(Locale.ROOT));
        }
    }

    private List<String> snapshotLoadOrder() {
        synchronized (modules) {
            return new ArrayList<>(loadOrder);
        }
    }

    private void unregisterListenersOf(ClassLoader loader) {
        try {
            for (HandlerList handlerList : HandlerList.getHandlerLists()) {
                for (RegisteredListener registered : handlerList.getRegisteredListeners()) {
                    Listener listener = registered.getListener();
                    if (listener.getClass().getClassLoader() == loader) {
                        handlerList.unregister(listener);
                        plugin.getListenerManager().unregisterListener(listener);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void closeQuietly(URLClassLoader loader) {
        try {
            loader.close();
        } catch (Throwable ignored) {
        }
    }

    private boolean containsIgnoreCase(List<String> values, String candidate) {
        if (values == null || candidate == null) {
            return false;
        }
        for (String value : values) {
            if (value != null && value.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }
}
