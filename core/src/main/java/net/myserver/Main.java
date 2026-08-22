package net.myserver;

import net.kyori.adventure.text.Component;
import net.minestom.server.Auth;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.AsyncPlayerPreLoginEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.server.ServerListPingEvent;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.ping.Status;
import net.minestom.server.timer.TaskSchedule;
import net.myserver.admin.AntiCheatSystem;
import net.myserver.admin.MetricsExporter;
import net.myserver.admin.RestApiManager;
import net.myserver.combat.CombatSystem;
import net.myserver.commands.*;
import net.myserver.engine.AdaptiveTickEngine;
import net.myserver.engine.DynamicViewDistance;
import net.myserver.engine.PerformanceMonitor;
import net.myserver.engine.SpatialGrid;
import net.myserver.entities.MobSpawnerSystem;
import net.myserver.inventory.CraftingSystem;
import net.myserver.mechanics.*;
import net.myserver.network.AntiBotSystem;
import net.myserver.network.SecuritySystem;
import net.myserver.network.StressTestRunner;
import net.myserver.network.WebMapServer;
import net.myserver.permissions.BanManager;
import net.myserver.permissions.RoleManager;
import net.myserver.redstone.RedstoneSystem;
import net.myserver.storage.*;
import net.myserver.world.CustomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Главный входной класс чистого оптимизированного ядра Crystall Core.
 * Содержит исключительно чистый движок и базовые механики Minecraft.
 */
public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        log.info("Starting Minestom Server (Crystall Pure Optimized Core)...");

        // 1. Конфигурация сервера
        Config config = Config.load("config.yml");
        log.info("Loaded configuration: Port={}, MaxPlayers={}, MOTD='{}'", config.port, config.maxPlayers, config.motd);

        // 2. Инициализация аутентификации (Velocity / Bungee / Standalone)
        Auth auth = null;
        if (config.proxyMode.equalsIgnoreCase("velocity")) {
            auth = new Auth.Velocity(Auth.Velocity.secretKey(config.velocitySecret));
            log.info("Velocity proxy authentication enabled.");
        } else if (config.proxyMode.equalsIgnoreCase("bungeecord")) {
            auth = new Auth.Bungee();
            log.info("BungeeCord proxy support enabled.");
        }

        MinecraftServer minecraftServer = (auth != null) ? MinecraftServer.init(auth) : MinecraftServer.init();
        GlobalEventHandler globalEventHandler = MinecraftServer.getGlobalEventHandler();

        // 3. Хранилища, Базы Данных и Локализация
        ChunkMigrator.autoMigrate("world_data");
        DatabaseManager.init(config);
        CoreDataManager.loadAll();
        net.myserver.utils.LangManager.init();
        RestApiManager.init();
        MetricsExporter.init();

        // 4. Движок инновационных супер-оптимизаций
        PerformanceMonitor.init();
        DynamicViewDistance.init();
        AdaptiveTickEngine.init();
        SpatialGrid.register(globalEventHandler);

        // 5. Создание игрового мира (Region Chunk Format + Procedural Generator)
        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        InstanceContainer instanceContainer = instanceManager.createInstanceContainer();
        RegionChunkLoader regionChunkLoader = new RegionChunkLoader("world_data");
        instanceContainer.setChunkLoader(regionChunkLoader);
        instanceContainer.setGenerator(new CustomGenerator(12345));

        DimensionManager.init(instanceContainer);
        DimensionManager.register(globalEventHandler);

        // 6. Базовые ванильные механики Minecraft
        WeatherTimeSystem.register(globalEventHandler, instanceContainer);
        BlockMechanics.register(globalEventHandler);
        PhysicsSystem.register(globalEventHandler);
        FluidSystem.register(globalEventHandler);
        RandomTickSystem.register();
        CraftingSystem.register(globalEventHandler);
        CustomRecipeSystem.register();
        CombatSystem.register(globalEventHandler);
        MobSpawnerSystem.register();
        RedstoneSystem.register(globalEventHandler);
        SleepSystem.register(globalEventHandler);
        CustomFishingSystem.register(globalEventHandler);
        ItemFrameMechanics.register(globalEventHandler);

        // 7. Системы ядра (Безопасность, Фоновое сохранение, Очистка)
        AutoSaveSystem.register();
        BackupManager.register();
        AntiCheatSystem.register(globalEventHandler);
        AntiDupeSystem.register(globalEventHandler);
        AntiBotSystem.register(globalEventHandler);
        SecuritySystem.register(globalEventHandler);
        CleanupSystem.register(globalEventHandler);

        // Умная выгрузка неиспользуемых чанков с периодом покоя (Grace Period 30 сек)
        final Map<Chunk, Long> emptyChunkTimers = new ConcurrentHashMap<>();
        MinecraftServer.getSchedulerManager().buildTask(() -> {
            long now = System.currentTimeMillis();
            for (Chunk chunk : instanceContainer.getChunks()) {
                if (chunk.getViewers().isEmpty()) {
                    long firstEmpty = emptyChunkTimers.computeIfAbsent(chunk, c -> now);
                    if (now - firstEmpty > 30_000) {
                        instanceContainer.unloadChunk(chunk);
                        emptyChunkTimers.remove(chunk);
                    }
                } else {
                    emptyChunkTimers.remove(chunk);
                }
            }
        }).repeat(TaskSchedule.seconds(10)).schedule();

        // 8. Веб-карта
        WebMapServer.start(config);

        // 9. Базовые команды сервера
        var cmdManager = MinecraftServer.getCommandManager();
        cmdManager.register(new GamemodeCommand());
        cmdManager.register(new TpCommand());
        cmdManager.register(new GiveCommand());
        cmdManager.register(new TimeCommand());
        cmdManager.register(new WeatherCommand());
        cmdManager.register(new StopCommand());
        cmdManager.register(new BanCommand());
        cmdManager.register(new KickCommand());
        cmdManager.register(new BenchmarkCommand());
        cmdManager.register(new StressTestRunner.StressTestCommand());

        // 10. Настройка MOTD & Ping
        globalEventHandler.addListener(ServerListPingEvent.class, event -> {
            var status = event.getStatus();
            status = Status.builder(status)
                    .description(Component.text(config.motd))
                    .playerInfo(new Status.PlayerInfo(
                            config.maxPlayers,
                            MinecraftServer.getConnectionManager().getOnlinePlayers().size(),
                            List.of()))
                    .build();
            event.setStatus(status);
        });

        // 11. Обработка подключений и авторизации игроков
        globalEventHandler.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            final Player player = event.getPlayer();

            if (BanManager.isBanned(player.getUuid().toString())) {
                player.kick(Component.text(BanManager.getReason(player.getUuid().toString())));
                return;
            }

            event.setSpawningInstance(instanceContainer);
            RoleManager.assignRole(player);

            if (!PlayerDataManager.loadPlayer(player)) {
                player.setRespawnPoint(new Pos(0, 100, 0));
            }
        });

        globalEventHandler.addListener(PlayerDisconnectEvent.class, event -> {
            PlayerDataManager.savePlayer(event.getPlayer());
            log.info("Игрок {} покинул сервер.", event.getPlayer().getUsername());
        });

        // 12. Корректная остановка сервера (Graceful Shutdown)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down the server gracefully...");

            CoreDataManager.saveAll();

            for (Instance instance : instanceManager.getInstances()) {
                instance.saveChunksToStorage();
                instance.saveInstance();
            }
            for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                PlayerDataManager.savePlayer(player);
            }

            regionChunkLoader.closeAll();
            WebMapServer.stop();
            RestApiManager.stop();
            DatabaseManager.shutdown();
            MinecraftServer.stopCleanly();
            log.info("Server stopped.");
        }));

        // 13. Запуск слушателя сокета
        minecraftServer.start("0.0.0.0", config.port);
        log.info("Server started on port {}", config.port);
    }
}
