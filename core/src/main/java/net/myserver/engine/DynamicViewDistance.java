package net.myserver.engine;

import net.minestom.server.MinecraftServer;
import net.minestom.server.timer.TaskSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Умная система адаптивной дальности прорисовки (Smart Dynamic View Distance).
 * Автоматически масштабирует радиус видимости чанков в зависимости от реального MSPT сервера.
 */
public class DynamicViewDistance {
    private static final Logger log = LoggerFactory.getLogger(DynamicViewDistance.class);

    private static final int MAX_VIEW_DISTANCE = 10;
    private static final int MIN_VIEW_DISTANCE = 4;
    private static int currentViewDistance = 8;
    private static boolean enabled = true;

    public static void init() {
        MinecraftServer.getSchedulerManager().buildTask(() -> {
            if (!enabled) return;

            double mspt = PerformanceMonitor.getRollingAverageMspt();
            int targetViewDistance = currentViewDistance;

            if (mspt > 50.0) {
                // Аварийный режим перегрузки
                targetViewDistance = MIN_VIEW_DISTANCE;
            } else if (mspt > 42.0) {
                // Высокая нагрузка - понижаем на 1
                targetViewDistance = Math.max(MIN_VIEW_DISTANCE, currentViewDistance - 1);
            } else if (mspt < 25.0) {
                // Низкая нагрузка - повышаем на 1
                targetViewDistance = Math.min(MAX_VIEW_DISTANCE, currentViewDistance + 1);
            }

            if (targetViewDistance != currentViewDistance) {
                currentViewDistance = targetViewDistance;
                log.info("[DynamicVD] Динамическая дальность прорисовки скорректирована: {} чанков (MSPT: {} ms)", 
                        currentViewDistance, mspt);
            }
        }).repeat(TaskSchedule.seconds(5)).schedule();
    }

    public static int getCurrentViewDistance() {
        return currentViewDistance;
    }

    public static void setEnabled(boolean state) {
        enabled = state;
    }

    public static boolean isEnabled() {
        return enabled;
    }
}
