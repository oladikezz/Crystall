package net.myserver.admin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.sun.net.httpserver.HttpServer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class RestApiManager {
    private static final int PORT = 25566;
    private static final Gson gson = new Gson();

    public static void init() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

            server.createContext("/api/status", exchange -> {
                JsonObject response = new JsonObject();
                response.addProperty("online", MinecraftServer.getConnectionManager().getOnlinePlayers().size());
                response.addProperty("tps", MinecraftServer.TICK_PER_SECOND); // Ванильный таргет TPS
                
                Runtime runtime = Runtime.getRuntime();
                long memoryUsed = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
                response.addProperty("memoryUsedMb", memoryUsed);

                String resStr = gson.toJson(response);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, resStr.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(resStr.getBytes());
                }
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

                String resStr = gson.toJson(players);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, resStr.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(resStr.getBytes());
                }
            });

            server.createContext("/metrics", exchange -> {
                String metrics = MetricsExporter.generateMetrics();
                exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
                exchange.sendResponseHeaders(200, metrics.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(metrics.getBytes());
                }
            });

            server.setExecutor(null); // Использовать дефолтный Executor
            server.start();
            System.out.println("REST API Monitor started on port " + PORT);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
