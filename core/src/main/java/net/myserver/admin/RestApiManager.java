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
import net.myserver.engine.PerformanceMonitor;
import net.myserver.network.StressTestRunner;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class RestApiManager {
    private static final int PORT = 25566;
    private static final Gson gson = new Gson();

    public static void init() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

            // Основной статус
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

            server.createContext("/metrics", exchange -> {
                String metrics = MetricsExporter.generateMetrics();
                exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
                byte[] bytes = metrics.getBytes();
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            });

            server.setExecutor(null);
            server.start();
            System.out.println("REST API Monitor started on port " + PORT);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void sendJsonResponse(com.sun.net.httpserver.HttpExchange exchange, Object data) throws IOException {
        String resStr = gson.toJson(data);
        byte[] bytes = resStr.getBytes();
        exchange.getResponseHeaders().set("Content-Type", "application/json");
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
