package net.schalker.SMPS.modules.cosmetics;

import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.module.BaseModule;
import net.schalker.DoAPI.core.module.ModuleInfo;
import net.schalker.SMPS.modules.cosmetics.commands.CosmeticsCommand;
import net.schalker.SMPS.modules.cosmetics.gui.CosmeticsMenuListener;
import net.schalker.SMPS.modules.cosmetics.gui.CosmeticsMenuManager;
import net.schalker.SMPS.modules.cosmetics.listeners.CosmeticsListener;
import net.schalker.SMPS.modules.cosmetics.managers.CosmeticsManager;
import net.schalker.SMPS.modules.cosmetics.managers.MessageManager;
import net.schalker.SMPS.modules.cosmetics.managers.UserCosmeticsManager;
import net.schalker.SMPS.modules.cosmetics.models.PetCosmetic;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class CosmeticsModule extends BaseModule {
    public static final String MODULE_NAME = "CM_cosmetics";

    private CosmeticsManager cosmeticsManager;
    private UserCosmeticsManager userCosmeticsManager;
    private MessageManager messageManager;
    private CosmeticsMenuManager menuManager;
    private CosmeticsListener cosmeticsListener;
    private CosmeticsMenuListener menuListener;
    private boolean commandRegistered;
    private boolean commandLifecycleUnavailable;
    private static final int COMMAND_REGISTER_RETRY_ATTEMPTS = 20;
    private static final long COMMAND_REGISTER_RETRY_PERIOD_TICKS = 20L;
    private String commandRegisterRetryTaskName;

    public CosmeticsModule(DoAPI plugin) {
        super(plugin, new ModuleInfo(
            MODULE_NAME,
            "2.1.0",
            "ivan4",
            "Cosmetics system for SMPS"
        ));
    }

    @Override
    public void onEnable() {
        super.onEnable();

        // If re-enabling after a previous cycle, clean up stale cosmetics/tasks first
        if (userCosmeticsManager != null) {
            userCosmeticsManager.clearAllCosmetics();
        }

        loadConfigs();

        try {
            initializeManagers();
        } catch (Exception e) {
            plugin.getDebugSystem().logError("CosmeticsModule initialization error", e);
            plugin.getLogger().severe("CosmeticsModule initialization failed, disabling module");
            enabled = false;
            return;
        }

        registerCommandsWithRetry();
        registerListeners();

        plugin.getDebugSystem().log(MODULE_NAME, "Module enabled");
        plugin.getLogger().info("CosmeticsModule loaded");
    }

    @Override
    public void onDisable() {
        super.onDisable();

        closeAllOpenCosmeticsMenus();

        if (userCosmeticsManager != null) {
            userCosmeticsManager.clearAllCosmetics();
        }

        if (cosmeticsListener != null) {
            plugin.getListenerManager().unregisterListener(cosmeticsListener);
        }
        if (menuListener != null) {
            plugin.getListenerManager().unregisterListener(menuListener);
        }

        cosmeticsListener = null;
        menuListener = null;
        menuManager = null;

        stopCommandRegisterRetryTask();
        commandRegistered = false;

        plugin.getDebugSystem().log(MODULE_NAME, "Module disabled");
    }

    @Override
    public void reload() {
        super.reload();
        this.closeAllOpenCosmeticsMenus();
        try {
            // Clean up old cosmetics (despawn entities, cancel scheduler tasks) BEFORE
            // creating new managers, otherwise old tasks keep running alongside new ones
            // and pet/balloon entities are orphaned.
            if (userCosmeticsManager != null) {
                userCosmeticsManager.clearAllCosmetics();
            }

            if (cosmeticsListener != null) {
                plugin.getListenerManager().unregisterListener(cosmeticsListener);
            }
            if (menuListener != null) {
                plugin.getListenerManager().unregisterListener(menuListener);
            }
            stopCommandRegisterRetryTask();
            commandRegistered = false;

            loadConfigs();
            initializeManagers();
            registerCommandsWithRetry();
            registerListeners();
            enabled = true;

            plugin.getDebugSystem().log(MODULE_NAME, "Module reloaded");
        } catch (Exception exception) {
            plugin.getDebugSystem().logError("CosmeticsModule reload error", exception);
            plugin.getLogger().severe("CosmeticsModule reload failed: " + exception.getMessage());
        }
    }

    private void closeAllOpenCosmeticsMenus() {
        if (this.menuManager == null) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (this.menuManager.hasOpenMenu(player.getUniqueId())) {
                player.closeInventory();
            }
            this.menuManager.closeMenu(player.getUniqueId());
        }
    }

    private void loadConfigs() {
        // Keep config extraction strictly in the module data folder (SMPS/modules/...).
        // Avoid loadOrCreateConfig here because it may also create files in SMPS root.
        syncDefaultFile("config.yml");
        syncDefaultFile("messages.yml");
        syncDefaultFile("gui.yml");
        syncDefaultFile("pets.yml");
        syncDefaultFile("particles.yml");
        syncDefaultFile("arrows.yml");
        syncDefaultFile("balloons.yml");
        syncDefaultFile("death_effects.yml");
        syncDefaultFile("mace.yml");
        syncDefaultFile("trident.yml");
        syncDefaultFile("riptide.yml");

        // Remove obsolete config files from previous versions
        deleteObsoleteFile("rarities.yml");
        deleteObsoleteFile("miniatures.yml");

        plugin.getDebugSystem().log(MODULE_NAME, "Configs loaded");
    }

    private void deleteObsoleteFile(String fileName) {
        try {
            var dataFolder = plugin.getModuleManager().getModuleDataFolder(MODULE_NAME);
            if (dataFolder == null) return;
            Path target = dataFolder.toPath().resolve(fileName);
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
        }
    }

    private void syncDefaultFile(String fileName) {
        try {
            var dataFolder = plugin.getModuleManager().getModuleDataFolder(MODULE_NAME);
            if (dataFolder == null) {
                return;
            }

            if (!dataFolder.exists() && !dataFolder.mkdirs()) {
                plugin.getLogger().warning("SM_cosmetics: failed to create data folder: " + dataFolder.getAbsolutePath());
                return;
            }

            Path target = dataFolder.toPath().resolve(fileName);
            boolean needsWrite = !Files.exists(target) || Files.size(target) == 0L;
            if (!needsWrite) {
                return;
            }

            InputStream in = this.getClass().getClassLoader().getResourceAsStream(fileName);
            if (in == null) {
                in = this.getClass().getClassLoader().getResourceAsStream("modules/cosmetics/" + fileName);
            }
            if (in == null) {
                plugin.getLogger().warning("SM_cosmetics: default resource not found in JAR: " + fileName);
                return;
            }

            try (InputStream resource = in) {
                Files.copy(resource, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("SM_cosmetics: failed to sync default file " + fileName + ": " + exception.getMessage());
        }
    }

    private void initializeManagers() {
        messageManager = new MessageManager(plugin);
        cosmeticsManager = new CosmeticsManager(plugin);
        userCosmeticsManager = new UserCosmeticsManager(plugin, this);
        menuManager = new CosmeticsMenuManager(plugin, this);
        configurePetMessages();

        cosmeticsManager.loadCosmetics();

        plugin.getDebugSystem().log(MODULE_NAME, "Managers initialized");
    }

    private void configurePetMessages() {
        if (messageManager == null) {
            return;
        }
        PetCosmetic.configureMessages(
            messageManager.getRaw("pet.actionbar.not-owner"),
            messageManager.getRaw("pet.actionbar.owner"),
            messageManager.getRaw("pet.actionbar.parrot-left"),
            messageManager.getRaw("pet.actionbar.parrot-right"),
            messageManager.getRaw("pet.actionbar.parrot-full")
        );
    }

    private void registerCommandsWithRetry() {
        stopCommandRegisterRetryTask();
        commandRegistered = false;
        commandLifecycleUnavailable = false;

        if (tryRegisterCommands()) {
            return;
        }

        if (commandLifecycleUnavailable) {
            plugin.getLogger().warning("SM_cosmetics: skipping command retry because Paper lifecycle owner context is unavailable. Fallback command handler remains active.");
            return;
        }

        final int[] attempts = {0};
        commandRegisterRetryTaskName = MODULE_NAME + "-command-register-retry";
        plugin.getSchedulerManager().runTaskTimer(commandRegisterRetryTaskName, () -> {
            if (!enabled) {
                stopCommandRegisterRetryTask();
                return;
            }
            if (commandRegistered) {
                stopCommandRegisterRetryTask();
                return;
            }

            attempts[0]++;
            if (tryRegisterCommands()) {
                stopCommandRegisterRetryTask();
                return;
            }

            if (attempts[0] >= COMMAND_REGISTER_RETRY_ATTEMPTS) {
                stopCommandRegisterRetryTask();
                plugin.getLogger().warning("SM_cosmetics: command was not registered after retries. Fallback command handler will stay active.");
            }
        }, COMMAND_REGISTER_RETRY_PERIOD_TICKS, COMMAND_REGISTER_RETRY_PERIOD_TICKS);
    }

    private boolean tryRegisterCommands() {
        try {
            plugin.getCommandManager().registerModuleCommand(new CosmeticsCommand(plugin, this));
            commandRegistered = true;
            commandLifecycleUnavailable = false;
            plugin.getDebugSystem().log(MODULE_NAME, "Commands registered");
            return true;
        } catch (Exception exception) {
            commandRegistered = false;
            if (this.isLifecycleContextMissing(exception)) {
                commandLifecycleUnavailable = true;
            }
            plugin.getDebugSystem().logError("Failed to register cosmetics command in current lifecycle context", exception);
            return false;
        }
    }

    private boolean isLifecycleContextMissing(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("No lifecycle owner context is set")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void stopCommandRegisterRetryTask() {
        if (commandRegisterRetryTaskName != null) {
            plugin.getSchedulerManager().cancelTask(commandRegisterRetryTaskName);
            commandRegisterRetryTaskName = null;
        }
    }

    private void registerListeners() {
        cosmeticsListener = new CosmeticsListener(plugin, this);
        plugin.getListenerManager().registerListener(cosmeticsListener);

        menuListener = new CosmeticsMenuListener(plugin, this, menuManager, messageManager);
        plugin.getListenerManager().registerListener(menuListener);

        plugin.getDebugSystem().log(MODULE_NAME, "Listeners registered");
    }

    public synchronized boolean ensureReady() {
        boolean needsManagers = cosmeticsManager == null || userCosmeticsManager == null || messageManager == null || menuManager == null;
        boolean needsListeners = cosmeticsListener == null || menuListener == null;

        if (!enabled || needsManagers) {
            try {
                // Clean up old state before re-initializing to avoid duplicate tasks
                if (userCosmeticsManager != null) {
                    userCosmeticsManager.clearAllCosmetics();
                }
                loadConfigs();
                initializeManagers();
                enabled = true;
            } catch (Exception exception) {
                plugin.getDebugSystem().logError("CosmeticsModule ensureReady manager init error", exception);
                return false;
            }
        }

        if (needsListeners) {
            try {
                registerListeners();
            } catch (Exception exception) {
                plugin.getDebugSystem().logError("CosmeticsModule ensureReady listener init error", exception);
                return false;
            }
        }

        if (!commandRegistered) {
            if (!commandLifecycleUnavailable && !tryRegisterCommands()) {
                registerCommandsWithRetry();
            }
        }

        return cosmeticsManager != null && userCosmeticsManager != null && messageManager != null && menuManager != null;
    }

    public CosmeticsManager getCosmeticsManager() {
        ensureReady();
        return cosmeticsManager;
    }

    public UserCosmeticsManager getUserCosmeticsManager() {
        ensureReady();
        return userCosmeticsManager;
    }

    public MessageManager getMessageManager() {
        ensureReady();
        return messageManager;
    }

    public CosmeticsMenuManager getMenuManager() {
        ensureReady();
        return menuManager;
    }

    public boolean isCommandRegistered() {
        return commandRegistered;
    }

    public FileConfiguration getConfig() {
        YamlConfiguration cfg = plugin.getModuleManager().loadModuleConfig(MODULE_NAME);
        return cfg != null ? cfg : plugin.getConfigManager().getConfig();
    }

    public FileConfiguration getMessages() {
        YamlConfiguration cfg = plugin.getModuleManager().loadModuleConfig(MODULE_NAME, "messages.yml");
        return cfg != null ? cfg : plugin.getConfigManager().getConfig();
    }
}
