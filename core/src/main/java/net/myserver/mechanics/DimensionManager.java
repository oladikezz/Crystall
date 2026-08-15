package net.myserver.mechanics;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.world.DimensionType;
import net.myserver.storage.CustomChunkLoader;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DimensionManager {
    public static InstanceContainer netherInstance;
    public static InstanceContainer overworldInstance;
    
    private static final Map<UUID, Long> lastPortalTime = new HashMap<>();

    public static void init(InstanceContainer overworld) {
        overworldInstance = overworld;
        
        DimensionType netherDimension = DimensionType.builder(net.minestom.server.utils.NamespaceID.from("minecraft:the_nether"))
                .ambientLight(0.1f)
                .fixedTime(18000L) // Всегда темно
                .logicalHeight(128)
                .coordinateScale(8.0)
                .ultrawarm(true)
                .hasCeiling(true)
                .build();
                
        MinecraftServer.getDimensionTypeManager().addDimension(netherDimension);

        netherInstance = MinecraftServer.getInstanceManager().createInstanceContainer(netherDimension);
        netherInstance.setGenerator(new NetherGenerator());
        netherInstance.setChunkLoader(new CustomChunkLoader("world_data/nether_chunks"));
    }

    public static void register(GlobalEventHandler handler) {
        handler.addListener(PlayerMoveEvent.class, event -> {
            Player player = event.getPlayer();
            Pos pos = event.getNewPosition();
            Instance instance = player.getInstance();
            if (instance == null) return;

            // Проверяем блок портала в ногах или в голове
            if (instance.getBlock(pos).compare(Block.NETHER_PORTAL) || instance.getBlock(pos.add(0, 1, 0)).compare(Block.NETHER_PORTAL)) {
                
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastPortalTime.getOrDefault(player.getUuid(), 0L) < 3000) {
                    return; // Кулдаун 3 секунды
                }
                
                lastPortalTime.put(player.getUuid(), currentTime);

                if (instance == overworldInstance) {
                    // ТП в Незер (делим X и Z на 8)
                    Pos netherPos = new Pos(pos.x() / 8, pos.y(), pos.z() / 8, pos.yaw(), pos.pitch());
                    player.setInstance(netherInstance, findSafePos(netherInstance, netherPos));
                    player.sendMessage(net.kyori.adventure.text.Component.text("Вы вошли в Нижний мир!", net.kyori.adventure.text.format.NamedTextColor.RED));
                } else if (instance == netherInstance) {
                    // ТП в Обычный мир (умножаем X и Z на 8)
                    Pos overworldPos = new Pos(pos.x() * 8, pos.y(), pos.z() * 8, pos.yaw(), pos.pitch());
                    player.setInstance(overworldInstance, findSafePos(overworldInstance, overworldPos));
                    player.sendMessage(net.kyori.adventure.text.Component.text("Вы вернулись в Обычный мир!", net.kyori.adventure.text.format.NamedTextColor.GREEN));
                }
            }
        });
    }
    
    private static Pos findSafePos(Instance instance, Pos target) {
        // Простой алгоритм: ищем безопасное место с Y=50 до 120
        for (int y = 50; y < 120; y++) {
            if (instance.getBlock((int)target.x(), y, (int)target.z()).isAir() && 
                instance.getBlock((int)target.x(), y + 1, (int)target.z()).isAir()) {
                
                Pos safePos = new Pos(target.x(), y, target.z());
                createPortal(instance, safePos);
                return safePos.add(0.5, 0, 0.5);
            }
        }
        
        // Если не нашли, принудительно создаем на Y=70
        Pos forcedPos = new Pos(target.x(), 70, target.z());
        createPortal(instance, forcedPos);
        return forcedPos.add(0.5, 0, 0.5);
    }
    
    private static void createPortal(Instance instance, Pos basePos) {
        // Очищаем область вокруг
        int bx = (int)basePos.x();
        int by = (int)basePos.y();
        int bz = (int)basePos.z();
        
        for (int y = 0; y < 5; y++) {
            for (int x = -1; x < 3; x++) {
                if (x == -1 || x == 2 || y == 0 || y == 4) {
                    instance.setBlock(bx + x, by + y, bz, Block.OBSIDIAN);
                } else {
                    instance.setBlock(bx + x, by + y, bz, Block.NETHER_PORTAL);
                }
                // Очистка блоков спереди и сзади рамки
                instance.setBlock(bx + x, by + y, bz - 1, Block.AIR);
                instance.setBlock(bx + x, by + y, bz + 1, Block.AIR);
            }
        }
        
        // Платформа 4x3 из обсидиана, чтобы игрок не упал
        for (int x = -1; x < 3; x++) {
            for (int z = -1; z < 2; z++) {
                instance.setBlock(bx + x, by - 1, bz + z, Block.OBSIDIAN);
            }
        }
    }
}
