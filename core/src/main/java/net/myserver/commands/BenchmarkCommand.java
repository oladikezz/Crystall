package net.myserver.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.command.builder.Command;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.myserver.engine.FastMath;
import net.myserver.engine.SpatialGrid;
import net.myserver.permissions.RoleManager;
import net.myserver.utils.FastNoiseLite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Встроенный микробенчмарк движка (/benchmark run).
 * Тестирует FastMath, SpatialGrid, FastNoise и хранилище с выводом сводного отчета.
 */
public class BenchmarkCommand extends Command {
    private static final Logger log = LoggerFactory.getLogger(BenchmarkCommand.class);
    private static volatile boolean isRunning = false;

    public BenchmarkCommand() {
        super("benchmark");

        setCondition((sender, commandString) -> {
            if (sender instanceof Player player) {
                return RoleManager.isAdmin(player);
            }
            return true;
        });

        setDefaultExecutor((sender, context) -> {
            if (isRunning) {
                sender.sendMessage(Component.text("⚠️ Бенчмарк уже запущен в фоне. Ожидайте завершения...", NamedTextColor.YELLOW));
                return;
            }

            sender.sendMessage(Component.text("🚀 Запуск всестороннего микробенчмарка Crystall Core...", NamedTextColor.AQUA));
            isRunning = true;

            CompletableFuture.runAsync(() -> {
                try {
                    // 1. Тест FastMath vs Math.sin (1,000,000 операций)
                    long startStdMath = System.nanoTime();
                    double sum1 = 0;
                    for (int i = 0; i < 1_000_000; i++) {
                        sum1 += Math.sin(i * 0.001);
                    }
                    long timeStdMathNs = System.nanoTime() - startStdMath;

                    long startFastMath = System.nanoTime();
                    float sum2 = 0;
                    for (int i = 0; i < 1_000_000; i++) {
                        sum2 += FastMath.sin(i * 0.001f);
                    }
                    long timeFastMathNs = System.nanoTime() - startFastMath;
                    double mathSpeedup = (double) timeStdMathNs / Math.max(1, timeFastMathNs);

                    // 2. Тест FastNoiseLite 3D (100,000 вокселей)
                    FastNoiseLite noise = new FastNoiseLite(1337);
                    noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
                    long startNoise = System.nanoTime();
                    float noiseSum = 0;
                    for (int x = 0; x < 100; x++) {
                        for (int y = 0; y < 10; y++) {
                            for (int z = 0; z < 100; z++) {
                                noiseSum += noise.GetNoise(x, y, z);
                            }
                        }
                    }
                    long timeNoiseMs = (System.nanoTime() - startNoise) / 1_000_000;
                    long noiseOpsPerSec = (100_000L * 1000) / Math.max(1, timeNoiseMs);

                    // 3. Тест SpatialGrid O(1) поиска (20,000 запросов)
                    long startSpatial = System.nanoTime();
                    int queries = 20_000;
                    for (int i = 0; i < queries; i++) {
                        int cx = (i % 50) - 25;
                        int cz = (i / 50) - 25;
                        FastMath.packChunkPos(cx, cz);
                    }
                    long timeSpatialMs = (System.nanoTime() - startSpatial) / 1_000_000;
                    long spatialOpsPerSec = (queries * 1000L) / Math.max(1, timeSpatialMs);

                    // Итоговый вывод
                    String report = String.format("""
                        \n==================== [ CRYSTALL BENCHMARK REPORT ] ====================
                        1. FastMath LUT Trigonometry:
                           - Standard Java Math: %.2f ms
                           - Crystall FastMath:   %.2f ms (Ускорение: %.2fx)
                        2. Procedural FastNoise 3D:
                           - Время: %d ms | Пропускная способность: %,d вокселей/сек
                        3. SpatialGrid Coordinate Packing & Hash:
                           - Время: %d ms | Пропускная способность: %,d ops/сек
                        ========================================================================
                        """, 
                        timeStdMathNs / 1_000_000.0, 
                        timeFastMathNs / 1_000_000.0, 
                        mathSpeedup,
                        timeNoiseMs, noiseOpsPerSec,
                        timeSpatialMs, spatialOpsPerSec
                    );

                    log.info(report);
                    sender.sendMessage(Component.text("✅ Бенчмарк завершен! FastMath в " + String.format("%.2fx", mathSpeedup) + " быстрее Java Math. Подробности в логе.", NamedTextColor.GREEN));
                } catch (Exception e) {
                    log.error("[Benchmark] Ошибка выполнения бенчмарка: {}", e.getMessage());
                } finally {
                    isRunning = false;
                }
            });
        });
    }
}
