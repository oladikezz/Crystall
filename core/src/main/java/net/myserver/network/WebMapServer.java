package net.myserver.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.myserver.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class WebMapServer {
    private static final Logger log = LoggerFactory.getLogger(WebMapServer.class);
    private static HttpServer server;
    private static boolean hideCoordinates = false;

    public static void start(Config config) {
        if (!config.webMapEnabled) {
            log.info("[WebMap] Веб-карта отключена в конфигурации.");
            return;
        }

        hideCoordinates = config.webMapHideCoordinates;
        int port = config.webMapPort;

        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

            // Главная страница веб-карты
            server.createContext("/", exchange -> {
                String html = """
                    <!DOCTYPE html>
                    <html lang="ru">
                    <head>
                        <meta charset="UTF-8">
                        <title>Crystall Server - Live Web Map</title>
                        <style>
                            body { margin: 0; background: #121214; color: #fff; font-family: 'Segoe UI', sans-serif; display: flex; flex-direction: column; align-items: center; }
                            header { width: 100%; padding: 15px 30px; background: #1a1a1f; box-sizing: border-box; display: flex; justify-content: space-between; border-bottom: 2px solid #2a2a35; }
                            #canvas-container { margin: 20px; border: 2px solid #00ffcc; border-radius: 12px; background: #1e1e24; box-shadow: 0 0 20px rgba(0,255,204,0.2); }
                            canvas { display: block; border-radius: 10px; }
                        </style>
                    </head>
                    <body>
                        <header>
                            <h2>✦ CRYSTALL LIVE MAP ✦</h2>
                            <div id="stats">Загрузка данных...</div>
                        </header>
                        <div id="canvas-container">
                            <canvas id="mapCanvas" width="800" height="600"></canvas>
                        </div>
                        <script>
                            const canvas = document.getElementById('mapCanvas');
                            const ctx = canvas.getContext('2d');

                            async function updateMap() {
                                try {
                                    const res = await fetch('/api/players');
                                    const data = await res.json();
                                    
                                    ctx.fillStyle = '#18181c';
                                    ctx.fillRect(0, 0, canvas.width, canvas.height);
                                    
                                    // Сетка
                                    ctx.strokeStyle = '#282830';
                                    for(let i=0; i<canvas.width; i+=40) { ctx.beginPath(); ctx.moveTo(i,0); ctx.lineTo(i,canvas.height); ctx.stroke(); }
                                    for(let i=0; i<canvas.height; i+=40) { ctx.beginPath(); ctx.moveTo(0,i); ctx.lineTo(canvas.width,i); ctx.stroke(); }
                                    
                                    // Центр (Спавн)
                                    ctx.fillStyle = '#ffaa00';
                                    ctx.beginPath();
                                    ctx.arc(400, 300, 6, 0, Math.PI*2);
                                    ctx.fill();
                                    ctx.fillText('Спавн [0, 0]', 410, 305);

                                    // Игроки
                                    data.players.forEach(p => {
                                        if (p.x !== undefined && p.z !== undefined) {
                                            const px = 400 + (p.x * 2);
                                            const pz = 300 + (p.z * 2);
                                            ctx.fillStyle = '#00ffcc';
                                            ctx.beginPath();
                                            ctx.arc(px, pz, 5, 0, Math.PI*2);
                                            ctx.fill();
                                            ctx.fillStyle = '#ffffff';
                                            ctx.fillText(p.name, px + 8, pz + 4);
                                        }
                                    });

                                    document.getElementById('stats').innerText = 'Онлайн: ' + data.players.length + ' игроков';
                                } catch(e) {}
                            }
                            setInterval(updateMap, 1000);
                            updateMap();
                        </script>
                    </body>
                    </html>
                """;

                byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            });

            // JSON API точек игроков
            server.createContext("/api/players", exchange -> {
                JsonObject json = new JsonObject();
                JsonArray arr = new JsonArray();

                for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                    JsonObject pObj = new JsonObject();
                    pObj.addProperty("name", player.getUsername());
                    if (!hideCoordinates) {
                        pObj.addProperty("x", player.getPosition().x());
                        pObj.addProperty("y", player.getPosition().y());
                        pObj.addProperty("z", player.getPosition().z());
                        pObj.addProperty("health", player.getHealth());
                    }
                    arr.add(pObj);
                }
                json.add("players", arr);

                byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            });

            server.start();
            log.info("[WebMap] Живая веб-карта запущена на http://localhost:{}", port);
        } catch (Exception e) {
            log.warn("[WebMap] Не удалось запустить веб-карту: {}", e.getMessage());
        }
    }

    public static void stop() {
        if (server != null) {
            server.stop(0);
        }
    }
}
