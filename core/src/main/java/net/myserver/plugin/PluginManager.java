package net.myserver.plugin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * Loads and manages Crystall plugins from the plugins/ directory.
 */
public class PluginManager {
    private static final Logger log = LoggerFactory.getLogger(PluginManager.class);
    private static final Gson gson = new Gson();
    private static final List<CrystallPlugin> loadedPlugins = new ArrayList<>();
    private static final List<URLClassLoader> classLoaders = new ArrayList<>();

    public static void loadPlugins() {
        File pluginsDir = new File("plugins");
        if (!pluginsDir.exists()) {
            pluginsDir.mkdirs();
            log.info("[PluginManager] Created plugins/ directory.");
            return;
        }

        File[] jarFiles = pluginsDir.listFiles((dir, name) -> name.endsWith(".jar"));
        if (jarFiles == null || jarFiles.length == 0) {
            log.info("[PluginManager] No plugins found in plugins/ directory.");
            return;
        }

        PluginContext context = new PluginContext();

        for (File jarFile : jarFiles) {
            try {
                loadPlugin(jarFile, context);
            } catch (Exception e) {
                log.error("[PluginManager] Failed to load plugin {}: {}", jarFile.getName(), e.getMessage());
            }
        }

        log.info("[PluginManager] Loaded {} plugin(s).", loadedPlugins.size());
    }

    private static void loadPlugin(File jarFile, PluginContext context) throws Exception {
        // Read plugin descriptor from META-INF/crystall-plugin.json
        JarFile jar = new JarFile(jarFile);
        ZipEntry descriptorEntry = jar.getEntry("META-INF/crystall-plugin.json");

        if (descriptorEntry == null) {
            jar.close();
            log.warn("[PluginManager] {} has no META-INF/crystall-plugin.json, skipping.", jarFile.getName());
            return;
        }

        JsonObject descriptor;
        try (InputStream is = jar.getInputStream(descriptorEntry);
             InputStreamReader reader = new InputStreamReader(is)) {
            descriptor = gson.fromJson(reader, JsonObject.class);
        }
        jar.close();

        String pluginName = descriptor.get("name").getAsString();
        String pluginVersion = descriptor.has("version") ? descriptor.get("version").getAsString() : "1.0";
        String mainClass = descriptor.get("main").getAsString();

        // Load the JAR via URLClassLoader
        URLClassLoader classLoader = new URLClassLoader(
                new URL[]{jarFile.toURI().toURL()},
                PluginManager.class.getClassLoader()
        );
        classLoaders.add(classLoader);

        // Instantiate main class
        Class<?> clazz = classLoader.loadClass(mainClass);
        if (!CrystallPlugin.class.isAssignableFrom(clazz)) {
            log.error("[PluginManager] {} main class does not implement CrystallPlugin!", pluginName);
            return;
        }

        CrystallPlugin plugin = (CrystallPlugin) clazz.getDeclaredConstructor().newInstance();
        plugin.onEnable(context);
        loadedPlugins.add(plugin);

        log.info("[PluginManager] Loaded: {} v{}", pluginName, pluginVersion);
    }

    public static void disableAll() {
        for (CrystallPlugin plugin : loadedPlugins) {
            try {
                plugin.onDisable();
                log.info("[PluginManager] Disabled: {}", plugin.getName());
            } catch (Exception e) {
                log.error("[PluginManager] Error disabling {}: {}", plugin.getName(), e.getMessage());
            }
        }

        for (URLClassLoader cl : classLoaders) {
            try {
                cl.close();
            } catch (Exception ignored) {}
        }
    }

    public static List<CrystallPlugin> getLoadedPlugins() {
        return loadedPlugins;
    }
}
