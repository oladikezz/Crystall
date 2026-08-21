package net.schalker.DoAPI.core.reload;

import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.api.IModule;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public class PluginReloader {

    private final DoAPI plugin;
    private final AtomicInteger lifecycleDepth = new AtomicInteger();

    private volatile boolean experimentalEnabled;
    private volatile boolean lifecycleHackEnabled;

    public PluginReloader(DoAPI plugin) {
        this.plugin = plugin;
    }

    public void enableExperimental() {
        this.experimentalEnabled = true;
        plugin.getLogger().warning("Experimental mode enabled, use at your own risk");
    }

    public boolean isExperimentalEnabled() {
        return experimentalEnabled;
    }

    public boolean setLifecycleContext() {
        boolean opened = lifecycleDepth.getAndIncrement() == 0;
        lifecycleHackEnabled = true;
        return opened;
    }

    public void clearLifecycleContext() {
        if (lifecycleDepth.decrementAndGet() <= 0) {
            lifecycleDepth.set(0);
            lifecycleHackEnabled = false;
        }
    }

    public boolean isLifecycleHackEnabled() {
        return lifecycleHackEnabled;
    }

    public boolean reloadModule(String name, CommandSender sender) {
        IModule module = plugin.getModuleManager().getModule(name);
        if (module == null) {
            send(sender, "&c✖ Модуль не найден: " + name);
            return false;
        }

        boolean success = plugin.getModuleManager().reloadModule(name);
        send(sender, success
                ? "&[MAIN]§l✔ &[SECONDARY]Модуль перезагружен: &f" + name
                : "&c✖ Не удалось перезагрузить: " + name);
        return success;
    }

    public boolean restartModule(String name, CommandSender sender) {
        IModule module = plugin.getModuleManager().getModule(name);
        if (module == null) {
            send(sender, "&c✖ Модуль не найден: " + name);
            return false;
        }

        plugin.getModuleManager().disableModule(name);
        boolean success = plugin.getModuleManager().enableModule(name);
        send(sender, success
                ? "&[MAIN]§l✔ &[SECONDARY]Модуль перезапущен: &f" + name
                : "&c✖ Не удалось перезапустить: " + name);
        return success;
    }

    public boolean enableModule(String name, CommandSender sender) {
        IModule module = plugin.getModuleManager().getModule(name);
        if (module == null) {
            send(sender, "&c✖ Модуль не найден: " + name);
            return false;
        }
        if (module.isEnabled()) {
            send(sender, "&e⚠ Модуль уже включен: " + name);
            return false;
        }

        boolean success = plugin.getModuleManager().enableModule(name);
        send(sender, success
                ? "&[MAIN]§l✔ &[SECONDARY]Модуль включен: &f" + name
                : "&c✖ Не удалось включить: " + name);
        return success;
    }

    public boolean disableModule(String name, CommandSender sender) {
        IModule module = plugin.getModuleManager().getModule(name);
        if (module == null) {
            send(sender, "&c✖ Модуль не найден: " + name);
            return false;
        }
        if (!module.isEnabled()) {
            send(sender, "&e⚠ Модуль уже выключен: " + name);
            return false;
        }

        boolean success = plugin.getModuleManager().disableModule(name);
        send(sender, success
                ? "&[MAIN]§l✔ &[SECONDARY]Модуль выключен: &f" + name
                : "&c✖ Не удалось выключить: " + name);
        return success;
    }

    public boolean hotReloadPlugin(CommandSender sender) {
        return hotReloadPlugin(sender, false);
    }

    public boolean hotReloadPlugin(CommandSender sender, boolean full) {
        return hotReloadPlugin(sender, full, null);
    }

    public boolean hotReloadPlugin(CommandSender sender, boolean full, String jarName) {
        if (!experimentalEnabled) {
            send(sender, "&c✖ Сначала включите: /doapi experimental enable");
            return false;
        }

        long start = System.currentTimeMillis();
        return full
                ? performFullReload(sender, start, jarName)
                : performSoftReload(sender, start);
    }

    private boolean performSoftReload(CommandSender sender, long start) {
        send(sender, "&[SECONDARY]Soft reload...");

        saveAllData();
        plugin.getConfigManager().reloadConfig();
        plugin.getDebugSystem().reloadSettings();
        plugin.getModuleManager().reloadAllModules();

        send(sender, "&[MAIN]§l✔ &[SECONDARY]Soft reload за "
                + (System.currentTimeMillis() - start) + " мс");
        return true;
    }

    private boolean performFullReload(CommandSender sender, long start, String jarName) {
        send(sender, "&[SECONDARY]Full reload...");

        if (jarName != null && !jarName.isBlank()) {
            File resolved = resolveJarPath(jarName);
            if (resolved == null) {
                send(sender, "&c✖ JAR не найден: " + jarName);
                return false;
            }
            send(sender, "&e⚠ Замена JAR ядра требует перезапуска сервера, "
                    + "перезагружаются только модули и конфиг.");
        }

        try {
            saveAllData();
            plugin.getModuleManager().disableAllModules();
            plugin.getModuleManager().unloadAllModules();
            plugin.getSchedulerManager().cancelAllTasks();
            plugin.getListenerManager().unregisterAllListeners();

            plugin.getConfigManager().reloadConfig();
            plugin.getDebugSystem().reloadSettings();
            plugin.getDatabaseManager().reconnect();

            plugin.getModuleManager().discoverAndLoadModules();
            plugin.getModuleManager().enableAllModules();

            send(sender, "&[MAIN]§l✔ &[SECONDARY]Full reload за "
                    + (System.currentTimeMillis() - start) + " мс &7("
                    + plugin.getModuleManager().getEnabledModuleCount()
                    + "/" + plugin.getModuleManager().getModuleCount() + ")");
            return true;
        } catch (Throwable throwable) {
            plugin.getDebugSystem().logError("PluginReloader", "Full reload failed", throwable);
            send(sender, "&c✖ Full reload не удался: " + throwable.getMessage());
            return false;
        }
    }

    private File getPluginJarFile() {
        try {
            return new File(plugin.getClass().getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
        } catch (Throwable throwable) {
            return null;
        }
    }

    private void saveAllData() {
        for (IModule module : plugin.getModuleManager().getAllModules()) {
            if (!module.isEnabled()) {
                continue;
            }
            try {
                module.reload();
            } catch (Throwable throwable) {
                plugin.getDebugSystem().logWarning(module.getModuleInfo().getName(),
                        "Pre-reload save failed", throwable);
            }
        }
    }

    private File resolveJarPath(String jarName) {
        if (jarName == null || jarName.isBlank()) {
            return null;
        }

        String normalized = jarName.toLowerCase(Locale.ROOT).endsWith(".jar") ? jarName : jarName + ".jar";
        File pluginsFolder = plugin.getDataFolder().getParentFile();
        if (pluginsFolder == null) {
            return null;
        }

        File candidate = new File(pluginsFolder, normalized);
        return candidate.isFile() ? candidate : null;
    }

    public List<String> getAvailableJars() {
        File pluginsFolder = plugin.getDataFolder().getParentFile();
        List<String> result = new ArrayList<>();
        if (pluginsFolder == null) {
            return result;
        }

        File[] jars = pluginsFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".jar"));
        if (jars == null) {
            return result;
        }
        for (File jar : jars) {
            result.add(jar.getName());
        }
        return result;
    }

    public void sendExperimentalInfo(CommandSender sender) {
        File jar = getPluginJarFile();

        send(sender, "&[SECONDARY]Экспериментальный режим: "
                + (experimentalEnabled ? "&aвключен" : "&cвыключен"));
        send(sender, "&[SECONDARY]Lifecycle-контекст: "
                + (lifecycleHackEnabled ? "&aактивен" : "&7неактивен"));
        send(sender, "&[SECONDARY]JAR ядра: &f" + (jar == null ? "неизвестен" : jar.getName()));
        send(sender, "&7softreload — конфиг и модули без выгрузки классов");
        send(sender, "&7fullreload — полная выгрузка и загрузка модулей");
    }

    public void reloadConfig(CommandSender sender) {
        plugin.getConfigManager().reloadConfig();
        plugin.getDebugSystem().reloadSettings();
        send(sender, "&[MAIN]§l✔ &[SECONDARY]Конфигурация перезагружена");
    }

    private void send(CommandSender sender, String message) {
        if (sender != null) {
            sender.sendMessage(plugin.applyColors(message));
        }
    }
}
