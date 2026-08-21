package ru.lor.watcher;

import net.schalker.DoAPI.DoAPI;
import org.bukkit.Server;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import ru.lor.watcher.config.ConfigManager;
import ru.lor.watcher.events.AutoEventListener;
import ru.lor.watcher.events.StreamerChatListener;
import ru.lor.watcher.events.WatcherChatListener;
import ru.lor.watcher.events.WatcherInteractListener;
import ru.lor.watcher.events.WatcherRitualListener;
import ru.lor.watcher.gui.GuiListener;
import ru.lor.watcher.manager.AiBrainManager;
import ru.lor.watcher.manager.AutonomousStalkerManager;
import ru.lor.watcher.manager.DiscordWebhookManager;
import ru.lor.watcher.manager.EventManager;
import ru.lor.watcher.manager.InputSessionManager;
import ru.lor.watcher.manager.LogManager;
import ru.lor.watcher.manager.TelegramBotManager;
import ru.lor.watcher.manager.WatcherManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class WatcherPlugin {

    public static final String MODULE_NAME = "SM_Watcher";
    public static final String VERSION = "1.1.0";

    private final DoAPI core;
    private final File dataFolder;
    private final Map<UUID, String> lastSelectedTargets = new ConcurrentHashMap<>();
    private final List<Listener> registeredListeners = new ArrayList<>();

    private ConfigManager configManager;
    private LogManager logManager;
    private InputSessionManager inputSessionManager;
    private WatcherManager watcherManager;
    private EventManager eventManager;
    private DiscordWebhookManager discordWebhookManager;
    private TelegramBotManager telegramBotManager;
    private AiBrainManager aiBrainManager;
    private AutonomousStalkerManager autonomousStalkerManager;
    private StreamerChatListener streamerChatListener;
    private WatcherInteractListener interactListener;

    public WatcherPlugin(DoAPI core) {
        this.core = core;
        this.dataFolder = core.getModuleManager().getModuleDataFolder(MODULE_NAME);
    }

    public void enable() {
        this.configManager = new ConfigManager(this);
        this.logManager = new LogManager(this);
        this.inputSessionManager = new InputSessionManager(this);
        this.watcherManager = new WatcherManager(this);
        this.eventManager = new EventManager(this);
        this.discordWebhookManager = new DiscordWebhookManager(this);
        this.telegramBotManager = new TelegramBotManager(this);
        this.aiBrainManager = new AiBrainManager(this);
        this.autonomousStalkerManager = new AutonomousStalkerManager(this);
        this.streamerChatListener = new StreamerChatListener(this);

        registerListener(new GuiListener(this));
        registerListener(this.inputSessionManager);
        registerListener(new AutoEventListener(this));
        registerListener(this.streamerChatListener);
        registerListener(new WatcherChatListener(this));
        registerListener(new WatcherRitualListener(this));

        this.interactListener = new WatcherInteractListener(this);
        this.interactListener.register();

        this.telegramBotManager.start();
        this.autonomousStalkerManager.start();

        getLogger().info("[SM_Watcher] Модуль Смотрящего v" + VERSION + " запущен");
    }

    public void disable() {
        if (this.telegramBotManager != null) {
            this.telegramBotManager.stop();
        }
        if (this.autonomousStalkerManager != null) {
            this.autonomousStalkerManager.stop();
        }
        if (this.eventManager != null) {
            this.eventManager.stop();
        }
        if (this.interactListener != null) {
            this.interactListener.unregister();
            this.interactListener = null;
        }

        for (Listener listener : this.registeredListeners) {
            try {
                this.core.getListenerManager().unregisterListener(listener);
            } catch (Throwable ignored) {
            }
        }
        this.registeredListeners.clear();

        if (this.watcherManager != null) {
            this.watcherManager.despawnAll();
        }
        this.lastSelectedTargets.clear();

        getLogger().info("[SM_Watcher] Модуль Смотрящего остановлен");
    }

    public void reloadConfig() {
        if (this.configManager != null) {
            this.configManager.reloadAll();
        }
        if (this.streamerChatListener != null) {
            this.streamerChatListener.updatePattern();
        }
        if (this.eventManager != null) {
            this.eventManager.loadEvents();
        }
    }

    private void registerListener(Listener listener) {
        this.core.getListenerManager().registerListener(listener);
        this.registeredListeners.add(listener);
    }

    public DoAPI getCore() {
        return core;
    }

    public Plugin getBukkitPlugin() {
        return core;
    }

    public Logger getLogger() {
        return core.getLogger();
    }

    public Server getServer() {
        return core.getServer();
    }

    public File getDataFolder() {
        return dataFolder;
    }

    public boolean saveResource(String fileName) {
        return core.getModuleManager().saveModuleDefaultConfig(MODULE_NAME, fileName, fileName);
    }

    public void setLastSelectedTarget(UUID adminUuid, String targetName) {
        if (targetName != null) {
            this.lastSelectedTargets.put(adminUuid, targetName);
        } else {
            this.lastSelectedTargets.remove(adminUuid);
        }
    }

    public String getLastSelectedTarget(UUID adminUuid) {
        return this.lastSelectedTargets.get(adminUuid);
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public LogManager getLogManager() {
        return logManager;
    }

    public InputSessionManager getInputSessionManager() {
        return inputSessionManager;
    }

    public WatcherManager getWatcherManager() {
        return watcherManager;
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public DiscordWebhookManager getDiscordWebhookManager() {
        return discordWebhookManager;
    }

    public TelegramBotManager getTelegramBotManager() {
        return telegramBotManager;
    }

    public AiBrainManager getAiBrainManager() {
        return aiBrainManager;
    }

    public AutonomousStalkerManager getAutonomousStalkerManager() {
        return autonomousStalkerManager;
    }

    public StreamerChatListener getStreamerChatListener() {
        return streamerChatListener;
    }
}
