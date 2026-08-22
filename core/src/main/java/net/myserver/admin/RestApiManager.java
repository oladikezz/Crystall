package net.myserver.admin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.sun.net.httpserver.HttpServer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.myserver.engine.AdaptiveTickEngine;
import net.myserver.engine.DynamicViewDistance;
import net.myserver.engine.FastMath;
import net.myserver.engine.PerformanceMonitor;
import net.myserver.network.StressTestRunner;
import net.myserver.utils.FastNoiseLite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public class RestApiManager {
    private static final Logger log = LoggerFactory.getLogger(RestApiManager.class);
    private static final int PORT = 25566;
    private static final Gson gson = new Gson();
    private static HttpServer server;

    public static void init() {
        try {
            server = HttpServer.create(new InetSocketAddress(PORT), 0);

            // Основной статус сервера
            server.createContext("/api/status", exchange -> {
                JsonObject response = new JsonObject();
                response.addProperty("online", MinecraftServer.getConnectionManager().getOnlinePlayers().size());
                response.addProperty("activeBots", StressTestRunner.getActiveBotsCount());
                response.addProperty("tps", PerformanceMonitor.getEstimatedTps());
                response.addProperty("mspt", PerformanceMonitor.getRollingAverageMspt());
                response.addProperty("viewDistance", DynamicViewDistance.getCurrentViewDistance());
                response.addProperty("memoryUsedMb", PerformanceMonitor.getUsedMemoryMb());
                response.addProperty("memoryMaxMb", PerformanceMonitor.getMaxMemoryMb());

                sendJsonResponse(exchange, response);
            });

            // Детальная производительность ядра
            server.createContext("/api/performance", exchange -> {
                JsonObject perf = new JsonObject();
                perf.addProperty("averageMspt", PerformanceMonitor.getRollingAverageMspt());
                perf.addProperty("minMspt", PerformanceMonitor.getMinMspt());
                perf.addProperty("maxMspt", PerformanceMonitor.getMaxMspt());
                perf.addProperty("estimatedTps", PerformanceMonitor.getEstimatedTps());
                perf.addProperty("currentViewDistance", DynamicViewDistance.getCurrentViewDistance());
                perf.addProperty("activeTickChunks", AdaptiveTickEngine.getActiveChunksCount());
                perf.addProperty("currentTick", AdaptiveTickEngine.getCurrentTick());
                perf.addProperty("heapUsedMb", PerformanceMonitor.getUsedMemoryMb());
                perf.addProperty("heapMaxMb", PerformanceMonitor.getMaxMemoryMb());

                sendJsonResponse(exchange, perf);
            });

            // API микробенчмарка
            server.createContext("/api/benchmark", exchange -> {
                long startStd = System.nanoTime();
                double s1 = 0;
                for (int i = 0; i < 500_000; i++) s1 += Math.sin(i * 0.001);
                long stdNs = System.nanoTime() - startStd;

                long startFast = System.nanoTime();
                float s2 = 0;
                for (int i = 0; i < 500_000; i++) s2 += FastMath.sin(i * 0.001f);
                long fastNs = System.nanoTime() - startFast;

                FastNoiseLite noise = new FastNoiseLite(1337);
                long startNoise = System.nanoTime();
                for (int x = 0; x < 50; x++) {
                    for (int z = 0; z < 50; z++) {
                        noise.GetNoise(x, 64, z);
                    }
                }
                long noiseNs = System.nanoTime() - startNoise;

                JsonObject b = new JsonObject();
                b.addProperty("stdMathMs", stdNs / 1_000_000.0);
                b.addProperty("fastMathMs", fastNs / 1_000_000.0);
                b.addProperty("mathSpeedup", String.format("%.2fx", (double) stdNs / Math.max(1, fastNs)));
                b.addProperty("noiseGen2500BlocksMs", noiseNs / 1_000_000.0);
                sendJsonResponse(exchange, b);
            });

            // Управление стресс-тестом
            server.createContext("/api/stresstest", exchange -> {
                URI requestURI = exchange.getRequestURI();
                String query = requestURI.getQuery();
                Map<String, String> queryParams = parseQueryParams(query);

                String action = queryParams.getOrDefault("action", "status");
                int count = 100;
                try {
                    if (queryParams.containsKey("count")) {
                        count = Integer.parseInt(queryParams.get("count"));
                    }
                } catch (Exception ignored) {}

                JsonObject response = new JsonObject();

                if ("start".equalsIgnoreCase(action)) {
                    Instance instance = MinecraftServer.getInstanceManager().getInstances().stream().findFirst().orElse(null);
                    if (instance != null) {
                        StressTestRunner.startStressTest(instance, count);
                        response.addProperty("status", "started");
                        response.addProperty("botsSpawned", count);
                    } else {
                        response.addProperty("status", "error");
                        response.addProperty("message", "Instance not found");
                    }
                } else if ("stop".equalsIgnoreCase(action)) {
                    StressTestRunner.stopStressTest();
                    response.addProperty("status", "stopped");
                } else {
                    response.addProperty("status", "running");
                    response.addProperty("activeBots", StressTestRunner.getActiveBotsCount());
                }

                sendJsonResponse(exchange, response);
            });

            // Список игроков онлайн
            server.createContext("/api/players", exchange -> {
                JsonArray players = new JsonArray();
                for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                    JsonObject pObj = new JsonObject();
                    pObj.addProperty("name", player.getUsername());
                    pObj.addProperty("uuid", player.getUuid().toString());
                    pObj.addProperty("ping", player.getLatency());
                    players.add(pObj);
                }

                sendJsonResponse(exchange, players);
            });

            // Метрики Prometheus
            server.createContext("/metrics", exchange -> {
                String metrics = MetricsExporter.generateMetrics();
                exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
                byte[] bytes = metrics.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            });

            // Использование виртуальных потоков Java 25 для неблокирующей обработки
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.start();
            log.info("[RestAPI] REST API Monitor and Metrics server started on port {}", PORT);

        } catch (IOException e) {
            log.error("[RestAPI] Failed to start REST API Monitor on port {}: {}", PORT, e.getMessage(), e);
        }
    }

    public static void stop() {
        if (server != null) {
            try {
                server.stop(0);
                log.info("[RestAPI] REST API Monitor stopped gracefully.");
            } catch (Exception e) {
                log.warn("[RestAPI] Error stopping REST API server: {}", e.getMessage());
            }
        }
    }

    private static void sendJsonResponse(com.sun.net.httpserver.HttpExchange exchange, Object data) throws IOException {
        String resStr = gson.toJson(data);
        byte[] bytes = resStr.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) return params;
        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length > 1) {
                params.put(pair[0].toLowerCase(), pair[1]);
            } else if (pair.length == 1) {
                params.put(pair[0].toLowerCase(), "");
            }
        }
        return params;
    }
}
