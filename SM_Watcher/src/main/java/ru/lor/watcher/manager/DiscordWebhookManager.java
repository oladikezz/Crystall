package ru.lor.watcher.manager;

import org.bukkit.Bukkit;
import ru.lor.watcher.WatcherPlugin;
import ru.lor.watcher.model.WatcherSpawnSettings;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

public class DiscordWebhookManager {

    private final WatcherPlugin plugin;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public DiscordWebhookManager(WatcherPlugin plugin) {
        this.plugin = plugin;
    }

    public void sendSpawnNotification(String targetPlayerName, String executorName, WatcherSpawnSettings settings) {
        boolean enabled = plugin.getConfigManager().getConfig().getBoolean("discord.enabled", false);
        String url = plugin.getConfigManager().getConfig().getString("discord.webhook-url", "");

        if (!enabled || url == null || url.trim().isEmpty() || !url.startsWith("http")) {
            return;
        }

        Bukkit.getAsyncScheduler().runNow(plugin.getBukkitPlugin(), task -> {
            try {
                String isoTime = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
                String posType = settings.getPositionType().getDisplayName();
                String behaviorType = settings.getBehaviorType().getDisplayName();

                String jsonPayload = String.format(
                        "{"
                                + "\"username\": \"Watcher (Смотрящий)\","
                                + "\"embeds\": [{"
                                + "  \"title\": \"👁 Смотрящий появился!\","
                                + "  \"description\": \"Смотрящий начал наблюдать за игроком **%s**.\","
                                + "  \"color\": 8388736,"
                                + "  \"fields\": ["
                                + "    {\"name\": \"Игрок\", \"value\": \"%s\", \"inline\": true},"
                                + "    {\"name\": \"Вызвал\", \"value\": \"%s\", \"inline\": true},"
                                + "    {\"name\": \"Позиция\", \"value\": \"%s\", \"inline\": true},"
                                + "    {\"name\": \"Поведение\", \"value\": \"%s\", \"inline\": true}"
                                + "  ],"
                                + "  \"timestamp\": \"%s\""
                                + "}]"
                                + "}",
                        escapeJson(targetPlayerName),
                        escapeJson(targetPlayerName),
                        escapeJson(executorName),
                        escapeJson(posType),
                        escapeJson(behaviorType),
                        isoTime
                );

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());

            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Failed to send Discord webhook", e);
            }
        });
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
