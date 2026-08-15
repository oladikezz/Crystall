package net.myserver.mechanics;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerLoginEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.server.play.ChangeGameStatePacket;
import net.minestom.server.timer.TaskSchedule;

import java.util.Random;

public class WeatherTimeSystem {
    private static final Random random = new Random();
    
    public static boolean isRaining = false;
    public static boolean isThundering = false;

    public static void register(GlobalEventHandler handler, Instance instance) {
        // Устанавливаем течение времени (1 тик времени за 1 серверный тик)
        instance.setTimeRate(1);
        
        // Синхронизация погоды при входе
        handler.addListener(PlayerLoginEvent.class, event -> {
            Player player = event.getPlayer();
            if (isRaining) {
                player.sendPacket(new ChangeGameStatePacket(ChangeGameStatePacket.Reason.BEGIN_RAINING, 0f));
            }
        });

        // Смена погоды раз в 5 минут
        MinecraftServer.getSchedulerManager().buildTask(() -> {
            int chance = random.nextInt(100);
            
            if (chance < 10) {
                setWeather(instance, true, true);
            } else if (chance < 30) {
                setWeather(instance, true, false);
            } else {
                setWeather(instance, false, false);
            }
        }).repeat(TaskSchedule.minutes(5)).schedule();

        // Удары молнии во время грозы
        MinecraftServer.getSchedulerManager().buildTask(() -> {
            if (!isThundering) return;
            
            for (Player player : instance.getPlayers()) {
                if (random.nextInt(100) < 5) { 
                    int offsetX = random.nextInt(30) - 15;
                    int offsetZ = random.nextInt(30) - 15;
                    double x = player.getPosition().x() + offsetX;
                    double z = player.getPosition().z() + offsetZ;
                    
                    for (int y = 319; y > -64; y--) {
                        if (!instance.getBlock((int)x, y, (int)z).isAir()) {
                            strikeLightning(instance, new Pos(x, y + 1, z));
                            break;
                        }
                    }
                }
            }
        }).repeat(TaskSchedule.seconds(1)).schedule();
    }

    private static void setWeather(Instance instance, boolean rain, boolean thunder) {
        if (rain && !isRaining) {
            ChangeGameStatePacket packet = new ChangeGameStatePacket(ChangeGameStatePacket.Reason.BEGIN_RAINING, 0f);
            for (Player p : instance.getPlayers()) p.sendPacket(packet);
        } else if (!rain && isRaining) {
            ChangeGameStatePacket packet = new ChangeGameStatePacket(ChangeGameStatePacket.Reason.END_RAINING, 0f);
            for (Player p : instance.getPlayers()) p.sendPacket(packet);
        }
        
        isRaining = rain;
        isThundering = thunder;
    }

    private static void strikeLightning(Instance instance, Pos pos) {
        Entity lightning = new Entity(EntityType.LIGHTNING_BOLT);
        lightning.setInstance(instance, pos);
        
        if (instance.getBlock(pos).isAir()) {
            instance.setBlock(pos, Block.FIRE);
        }
        
        MinecraftServer.getSchedulerManager().buildTask(lightning::remove)
            .delay(TaskSchedule.millis(500)).schedule();
    }
}
