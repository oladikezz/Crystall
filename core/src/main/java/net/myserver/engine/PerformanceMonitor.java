package net.myserver.engine;

import net.minestom.server.MinecraftServer;
import net.minestom.server.timer.TaskSchedule;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Высокоточный монитор производительности сервера (MSPT, TPS, Heap, GC).
 * Измеряет реальное время каждого тика в миллисекундах и микросекундах.
 */
public class PerformanceMonitor {
    private static final int SAMPLE_SIZE = 100;
    private static final double[] msptSamples = new double[SAMPLE_SIZE];
    private static int sampleIndex = 0;
    private static double rollingAverageMspt = 0.0;
    private static double maxMspt = 0.0;
    private static double minMspt = 0.0;

    private static final AtomicLong lastTickTimeNano = new AtomicLong(System.nanoTime());

    public static void init() {
        MinecraftServer.getSchedulerManager().buildTask(() -> {
            long now = System.nanoTime();
            long prev = lastTickTimeNano.getAndSet(now);
            long elapsedNano = now - prev;
            double mspt = elapsedNano / 1_000_000.0;

            synchronized (msptSamples) {
                msptSamples[sampleIndex] = mspt;
                sampleIndex = (sampleIndex + 1) % SAMPLE_SIZE;

                double sum = 0;
                double max = 0;
                double min = Double.MAX_VALUE;
                for (double s : msptSamples) {
                    if (s > 0) {
                        sum += s;
                        if (s > max) max = s;
                        if (s < min) min = s;
                    }
                }
                rollingAverageMspt = sum / SAMPLE_SIZE;
                maxMspt = max;
                minMspt = (min == Double.MAX_VALUE) ? 0.0 : min;
            }
        }).repeat(TaskSchedule.tick(1)).schedule();
    }

    public static double getRollingAverageMspt() {
        return Math.round(rollingAverageMspt * 100.0) / 100.0;
    }

    public static double getMaxMspt() {
        return Math.round(maxMspt * 100.0) / 100.0;
    }

    public static double getMinMspt() {
        return Math.round(minMspt * 100.0) / 100.0;
    }

    public static double getEstimatedTps() {
        double avg = getRollingAverageMspt();
        if (avg <= 50.0) return 20.0;
        return Math.round((1000.0 / avg) * 10.0) / 10.0;
    }

    public static long getUsedMemoryMb() {
        Runtime r = Runtime.getRuntime();
        return (r.totalMemory() - r.freeMemory()) / (1024 * 1024);
    }

    public static long getMaxMemoryMb() {
        return Runtime.getRuntime().maxMemory() / (1024 * 1024);
    }
}
