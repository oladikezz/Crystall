package net.myserver;

import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.server.ServerListPingEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.Chunk;
import net.minestom.server.ping.ResponseData;
import net.minestom.server.timer.TaskSchedule;
import net.myserver.combat.CombatSystem;
import net.myserver.entities.MobSpawnerSystem;
import net.myserver.storage.AutoSaveSystem;
import net.myserver.storage.BackupManager;
import net.myserver.storage.CustomChunkLoader;
import net.myserver.storage.PlayerDataManager;
import net.myserver.inventory.CraftingSystem;
import net.myserver.redstone.RedstoneSystem;
import net.myserver.mechanics.BlockMechanics;
import net.myserver.permissions.RoleManager;
import net.myserver.permissions.BanManager;
import net.myserver.commands.*;
import net.myserver.social.*;
import net.myserver.admin.*;
import net.minestom.server.event.player.AsyncPlayerPreLoginEvent;
import net.myserver.mechanics.FluidSystem;
import net.myserver.mechanics.PhysicsSystem;
import net.myserver.mechanics.RandomTickSystem;
import net.myserver.world.CustomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        log.info("Starting Minestom Server...");

        // Чтение конфигурации
        Config config = Config.load("config.yml");
        log.info("Loaded configuration: Port={}, MaxPlayers={}, MOTD='{}'", config.port, config.maxPlayers, config.motd);

        // Инициализация сервера
        MinecraftServer minecraftServer = MinecraftServer.init();

        // Создаем инстанс (мир)
        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        InstanceContainer instanceContainer = instanceManager.createInstanceContainer();

        // Установка кастомного загрузчика чанков (Блок 8)
        instanceContainer.setChunkLoader(new CustomChunkLoader("world_data"));
        
        // Database (PostgreSQL или fallback на файлы)
        net.myserver.storage.DatabaseManager.init(config);
        
        net.myserver.utils.LangManager.init();
        
        PlayerDataManager.init();
        RoleManager.init();
        BanManager.init();
        EconomyManager.init();
        ReportManager.init();
        RestApiManager.init();
        net.myserver.admin.MetricsExporter.init();
        ClanManager.init();
        ClaimManager.init();
        
        net.myserver.mechanics.DimensionManager.init(instanceContainer);

        // Настраиваем генерацию (кастомный генератор)
        instanceContainer.setGenerator(new CustomGenerator(12345));
        
        // Регистрация погоды и времени (Блок 13)
        net.myserver.mechanics.WeatherTimeSystem.register(globalEventHandler, instanceContainer);

        // Выгрузка неиспользуемых чанков (каждые 10 секунд)
        MinecraftServer.getSchedulerManager().buildTask(() -> {
            for (Chunk chunk : instanceContainer.getChunks()) {
                if (chunk.getViewers().isEmpty()) {
                    instanceContainer.unloadChunk(chunk);
                }
            }
        }).repeat(TaskSchedule.seconds(10)).schedule();

        GlobalEventHandler globalEventHandler = MinecraftServer.getGlobalEventHandler();

        // Регистрация механик блоков (Блок 4)
        BlockMechanics.register(globalEventHandler);
        PhysicsSystem.register(globalEventHandler);
        FluidSystem.register(globalEventHandler);
        RandomTickSystem.register();
        CraftingSystem.register(globalEventHandler);
        
        CombatSystem.register(globalEventHandler);
        MobSpawnerSystem.register();
        RedstoneSystem.register(globalEventHandler);
        AutoSaveSystem.register();
        BackupManager.register();
        
        var commandManager = MinecraftServer.getCommandManager();
        commandManager.register(new GamemodeCommand());
        commandManager.register(new GiveCommand());
        commandManager.register(new TpCommand());
        commandManager.register(new BanCommand());
        commandManager.register(new KickCommand());
        commandManager.register(new MsgCommand());
        commandManager.register(new ReplyCommand());
        commandManager.register(new MoneyCommand());
        commandManager.register(new PayCommand());
        commandManager.register(new ReportCommand());
        commandManager.register(new ClaimCommand());
        commandManager.register(new ClanCommand());
        commandManager.register(new ShopCommand());

        ChatManager.register(globalEventHandler);
        AntiCheatSystem.register(globalEventHandler);
        ClaimManager.register(globalEventHandler);
        net.myserver.mechanics.DimensionManager.register(globalEventHandler);
        
        // Загрузка плагинов из plugins/
        net.myserver.plugin.PluginManager.loadPlugins();

        // Обработчик пинга сервера (MOTD, игроки)
        globalEventHandler.addListener(ServerListPingEvent.class, event -> {
            ResponseData responseData = event.getResponseData();
            responseData.setDescription(Component.text(config.motd));
            responseData.setMaxPlayer(config.maxPlayers);
        });

        // Проверка бана перед входом
        globalEventHandler.addListener(AsyncPlayerPreLoginEvent.class, event -> {
            if (BanManager.isBanned(event.getPlayerUuid().toString())) {
                // В Minestom нельзя кикнуть до спавна, поэтому просто отменяем вход, 
                // но в актуальных версиях обычно мы используем kick в конфигурации.
                // В данном случае мы не можем напрямую вызвать kick на Player до PlayerInit,
                // поэтому лучше делать проверку в AsyncPlayerConfigurationEvent.
            }
        });

        // Подключение игрока (настройка точки спавна и мира)
        globalEventHandler.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            final Player player = event.getPlayer();
            
            if (BanManager.isBanned(player.getUuid().toString())) {
                player.kick(Component.text(BanManager.getReason(player.getUuid().toString())));
                return;
            }
            
            if (config.resourcePackUrl != null && !config.resourcePackUrl.isEmpty()) {
                net.minestom.server.resourcepack.ResourcePack pack = net.minestom.server.resourcepack.ResourcePack.resourcePack(
                        config.resourcePackUrl, 
                        config.resourcePackHash, 
                        config.resourcePackRequired, 
                        Component.text("Пожалуйста, установите ресурспак сервера.")
                );
                player.setResourcePack(pack);
            }
            
            event.setSpawningInstance(instanceContainer);
            RoleManager.assignRole(player);
            
            // Загрузка данных игрока (Блок 8)
            if (!PlayerDataManager.loadPlayer(player)) {
                // Ищем наивысшую точку на спавне (или просто ставим с запасом)
                player.setRespawnPoint(new Pos(0, 100, 0));
            }
        });

        // Выход игрока
        globalEventHandler.addListener(PlayerDisconnectEvent.class, event -> {
            PlayerDataManager.savePlayer(event.getPlayer());
            log.info("Игрок {} покинул сервер.", event.getPlayer().getUsername());
        });

        // Корректная остановка (graceful shutdown) по SIGTERM / Ctrl+C
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down the server gracefully...");
            
            for (Instance instance : instanceManager.getInstances()) {
                instance.saveChunksToStorage();
                instance.saveInstance();
            }
            for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                PlayerDataManager.savePlayer(player);
            }
            
            net.myserver.plugin.PluginManager.disableAll();
            net.myserver.storage.DatabaseManager.shutdown();
            MinecraftServer.stopCleanly();
            log.info("Server stopped.");
        }));
        
        // Регистрация системы защиты от спама пакетами
        net.myserver.network.SecuritySystem.register(globalEventHandler);
        
        // Включение прокси, если указано в конфиге
        if (config.proxyMode.equalsIgnoreCase("velocity")) {
            net.minestom.server.extras.velocity.VelocityProxy.enable(config.velocitySecret);
            log.info("Velocity proxy support enabled.");
        } else if (config.proxyMode.equalsIgnoreCase("bungeecord")) {
            net.minestom.server.extras.bungee.BungeeCordProxy.enable();
            log.info("BungeeCord proxy support enabled.");
        }

        // Запуск сервера
        minecraftServer.start("0.0.0.0", config.port);
        log.info("Server started on port {}", config.port);
    }
}
