package net.schalker.DoAPI.core.debug;

import net.schalker.DoAPI.DoAPI;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Supplier;
import java.util.logging.Level;

public class DebugSystem {

    private record ServerInfo(String name, String version, int online, int max, double tps) {
    }

    private final DoAPI plugin;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final Object fileLock = new Object();

    private volatile boolean debugEnabled;
    private volatile boolean fileLogging;
    private volatile boolean fileLoggingFailed;
    private volatile File debugFile;
    private volatile WebhookManager webhookManager;

    public DebugSystem(DoAPI plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        reloadSettings();

        this.webhookManager = new WebhookManager(plugin);
        this.webhookManager.initialize();

        if (fileLogging) {
            prepareDebugFile();
        }
    }

    public void reloadSettings() {
        var config = plugin.getConfigManager().getConfig();
        this.debugEnabled = config.getBoolean("debug", true);
        this.fileLogging = config.getBoolean("file-logging", true);
        this.fileLoggingFailed = false;
        this.debugFile = null;

        if (fileLogging) {
            prepareDebugFile();
        }
        if (webhookManager != null) {
            webhookManager.reload();
        }
    }

    private void prepareDebugFile() {
        try {
            File folder = new File(plugin.getDataFolder(), "debug");
            if (!folder.exists() && !folder.mkdirs()) {
                fileLoggingFailed = true;
                return;
            }
            String stamp = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
            this.debugFile = new File(folder, "debug_" + stamp + ".log");
        } catch (Throwable throwable) {
            fileLoggingFailed = true;
        }
    }

    public void log(String message) {
        if (!debugEnabled) {
            return;
        }
        write("INFO", "Core", message);
    }

    public void log(String category, String message) {
        if (!debugEnabled) {
            return;
        }
        write("INFO", category, message);
    }

    public void logError(String message, Throwable throwable) {
        String module = detectModuleFromStackTrace(throwable);
        logError(module, message, throwable);
    }

    public void logError(String module, String message, Throwable throwable) {
        String resolved = module == null || module.isBlank()
                ? detectModuleFromStackTrace(throwable)
                : module;

        plugin.getLogger().log(Level.SEVERE, "[" + resolved + "] " + message, throwable);
        writeToFile("ERROR", resolved, message + System.lineSeparator() + stackTraceToString(throwable));

        if (webhookManager != null) {
            webhookManager.sendError(message, throwable, resolved);
        }
    }

    public void logWarning(String message) {
        logWarning("Core", message, null);
    }

    public void logWarning(String module, String message) {
        logWarning(module, message, null);
    }

    public void logWarning(String module, String message, Throwable throwable) {
        String resolved = module == null || module.isBlank() ? "Core" : module;

        if (throwable == null) {
            plugin.getLogger().warning("[" + resolved + "] " + message);
            writeToFile("WARN", resolved, message);
        } else {
            plugin.getLogger().log(Level.WARNING, "[" + resolved + "] " + message, throwable);
            writeToFile("WARN", resolved, message + System.lineSeparator() + stackTraceToString(throwable));
        }

        if (webhookManager != null) {
            if (throwable == null) {
                webhookManager.sendWarning(message, resolved);
            } else {
                webhookManager.sendWarning(message, throwable, resolved);
            }
        }
    }

    public void setDebugEnabled(boolean enabled) {
        this.debugEnabled = enabled;
        plugin.getConfigManager().getConfig().set("debug", enabled);
        plugin.saveConfig();
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public WebhookManager getWebhookManager() {
        return webhookManager;
    }

    private void write(String level, String category, String message) {
        plugin.getLogger().info("[" + category + "] " + message);
        writeToFile(level, category, message);
    }

    private void writeToFile(String level, String category, String message) {
        if (!fileLogging || fileLoggingFailed) {
            return;
        }

        synchronized (fileLock) {
            if (debugFile == null) {
                prepareDebugFile();
            }
            if (debugFile == null) {
                return;
            }

            String line = "[" + dateFormat.format(new Date()) + "] [" + level + "] ["
                    + category + "] " + message + System.lineSeparator();
            try {
                Files.writeString(debugFile.toPath(), line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                fileLoggingFailed = true;
                plugin.getLogger().warning("File logging disabled: " + e.getMessage());
            }
        }
    }

    private String detectModuleFromStackTrace(Throwable throwable) {
        if (throwable == null || plugin.getModuleManager() == null) {
            return "Core";
        }

        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 10) {
            for (StackTraceElement element : current.getStackTrace()) {
                String module = plugin.getModuleManager().getModuleNameForClass(element.getClassName());
                if (module != null) {
                    return module;
                }
            }
            current = current.getCause();
            depth++;
        }
        return "Core";
    }

    public String getSystemInfo() {
        ServerInfo info = callSync(() -> new ServerInfo(
                Bukkit.getName(),
                Bukkit.getMinecraftVersion(),
                Bukkit.getOnlinePlayers().size(),
                Bukkit.getMaxPlayers(),
                getTPSInternal()));

        if (info == null) {
            return "Server: unknown | Version: unknown | Players: ?/? | TPS: ?";
        }
        return "Server: " + info.name()
                + " | Version: " + info.version()
                + " | Players: " + info.online() + "/" + info.max()
                + " | TPS: " + String.format("%.2f", info.tps());
    }

    private double getTPSInternal() {
        try {
            double[] tps = Bukkit.getTPS();
            return tps.length > 0 ? Math.min(20.0D, tps[0]) : 20.0D;
        } catch (Throwable throwable) {
            return 20.0D;
        }
    }

    private <T> T callSync(Supplier<T> supplier) {
        try {
            if (plugin.getSchedulerManager() == null) {
                return supplier.get();
            }
            return plugin.getSchedulerManager().callGlobalSync("debug-system-info", supplier, 2000L);
        } catch (Throwable throwable) {
            return null;
        }
    }

    private String stackTraceToString(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
