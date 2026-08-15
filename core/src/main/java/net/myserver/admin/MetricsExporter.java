package net.myserver.admin;

import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.Instance;
import net.minestom.server.timer.TaskSchedule;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Exports server metrics in Prometheus text format on the existing HTTP server.
 * Tracks TPS, MSPT, memory, online players, loaded chunks.
 */
public class MetricsExporter {
    
    private static final AtomicLong lastTickNanos = new AtomicLong(System.nanoTime());
    private static volatile double currentMspt = 0.0;
    private static volatile double currentTps = 20.0;

    /**
     * Start the tick measurement task. Call once at startup.
     */
    public static void init() {
        MinecraftServer.getSchedulerManager().buildTask(() -> {
            long now = System.nanoTime();
            long elapsed = now - lastTickNanos.getAndSet(now);
            currentMspt = elapsed / 1_000_000.0; // nanoseconds -> milliseconds
            currentTps = Math.min(20.0, 1_000.0 / currentMspt); // cap at 20 TPS
        }).repeat(TaskSchedule.tick(1)).schedule();
    }

    /**
     * Generates Prometheus-compatible text output.
     */
    public static String generateMetrics() {
        StringBuilder sb = new StringBuilder();

        int online = MinecraftServer.getConnectionManager().getOnlinePlayers().size();
        appendMetric(sb, "crystall_players_online", "gauge", "Number of online players", online);

        appendMetric(sb, "crystall_tps", "gauge", "Server ticks per second", String.format("%.2f", currentTps));
        appendMetric(sb, "crystall_mspt", "gauge", "Milliseconds per tick", String.format("%.2f", currentMspt));

        Runtime runtime = Runtime.getRuntime();
        long usedBytes = runtime.totalMemory() - runtime.freeMemory();
        long maxBytes = runtime.maxMemory();
        appendMetric(sb, "crystall_memory_used_bytes", "gauge", "JVM heap memory used in bytes", usedBytes);
        appendMetric(sb, "crystall_memory_max_bytes", "gauge", "JVM max heap memory in bytes", maxBytes);

        int totalChunks = 0;
        for (Instance instance : MinecraftServer.getInstanceManager().getInstances()) {
            totalChunks += instance.getChunks().size();
        }
        appendMetric(sb, "crystall_chunks_loaded", "gauge", "Total loaded chunks across all instances", totalChunks);

        int totalInstances = MinecraftServer.getInstanceManager().getInstances().size();
        appendMetric(sb, "crystall_instances_count", "gauge", "Number of active instances", totalInstances);

        return sb.toString();
    }

    private static void appendMetric(StringBuilder sb, String name, String type, String help, Object value) {
        sb.append("# HELP ").append(name).append(' ').append(help).append('\n');
        sb.append("# TYPE ").append(name).append(' ').append(type).append('\n');
        sb.append(name).append(' ').append(value).append('\n');
    }
}
