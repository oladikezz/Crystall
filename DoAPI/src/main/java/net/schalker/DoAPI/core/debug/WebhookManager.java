package net.schalker.DoAPI.core.debug;

import net.schalker.DoAPI.DoAPI;
import org.bukkit.Bukkit;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;

public class WebhookManager {

    private static final long RATE_LIMIT_MS = 5000L;
    private static final int MAX_EMBED_DESCRIPTION = 4000;

    public enum Severity {
        ERROR("Error", 0xFF3B30, "error"),
        WARNING("Warning", 0xFFCC00, "warning");

        final String title;
        final int color;
        final String filePrefix;

        Severity(String title, int color, String filePrefix) {
            this.title = title;
            this.color = color;
            this.filePrefix = filePrefix;
        }
    }

    private final DoAPI plugin;
    private final HttpClient httpClient;

    private volatile String webhookUrl = "";
    private volatile boolean enabled;
    private volatile boolean sendWarnings;
    private volatile long lastSentTimestamp;

    public WebhookManager(DoAPI plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void initialize() {
        reload();
    }

    public void reload() {
        var config = plugin.getConfigManager().getConfig();
        this.webhookUrl = config.getString("webhook.url", "");
        this.enabled = config.getBoolean("webhook.enabled", true);
        this.sendWarnings = config.getBoolean("webhook.send-warnings", true);
    }

    public boolean isEnabled() {
        return enabled && webhookUrl != null && !webhookUrl.isBlank();
    }

    public boolean isSendWarnings() {
        return sendWarnings;
    }

    public void sendError(String message, Throwable throwable, String module) {
        send(Severity.ERROR, message, throwable, module);
    }

    public void sendWarning(String message, String module) {
        send(Severity.WARNING, message, null, module);
    }

    public void sendWarning(String message, Throwable throwable, String module) {
        send(Severity.WARNING, message, throwable, module);
    }

    private void send(Severity severity, String message, Throwable throwable, String module) {
        if (!isEnabled()) {
            return;
        }
        if (severity == Severity.WARNING && !sendWarnings) {
            return;
        }

        long now = System.currentTimeMillis();
        synchronized (this) {
            if (now - lastSentTimestamp < RATE_LIMIT_MS) {
                return;
            }
            lastSentTimestamp = now;
        }

        String fullText = buildFullText(severity, message, throwable, module);
        String resolvedModule = module == null || module.isBlank() ? "Core" : module;

        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            try {
                if (fullText.length() > MAX_EMBED_DESCRIPTION) {
                    sendAsFile(severity, message, fullText, resolvedModule);
                } else {
                    sendAsEmbed(severity, fullText, resolvedModule);
                }
            } catch (Throwable failure) {
                plugin.getLogger().warning("Webhook delivery failed: " + failure.getMessage());
            }
        });
    }

    private String buildFullText(Severity severity, String message, Throwable throwable, String module) {
        StringBuilder builder = new StringBuilder();
        builder.append("**").append(severity.title).append(":** ")
                .append(stripMarkdown(message == null ? "(no message)" : message))
                .append(System.lineSeparator());

        if (throwable != null) {
            StringWriter writer = new StringWriter();
            throwable.printStackTrace(new PrintWriter(writer));
            builder.append("```")
                    .append(System.lineSeparator())
                    .append(writer)
                    .append(System.lineSeparator())
                    .append("```");
        }
        return builder.toString();
    }

    private void sendAsEmbed(Severity severity, String description, String module) throws Exception {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        String payload = "{\"embeds\":[{"
                + "\"title\":\"" + escapeJsonValue("DoAPI " + severity.title) + "\","
                + "\"description\":\"" + escapeJsonValue(description) + "\","
                + "\"color\":" + severity.color + ","
                + "\"fields\":["
                + "{\"name\":\"Module\",\"value\":\"" + escapeJsonValue(module) + "\",\"inline\":true},"
                + "{\"name\":\"Version\",\"value\":\"" + escapeJsonValue(plugin.getPluginMeta().getVersion()) + "\",\"inline\":true},"
                + "{\"name\":\"Server\",\"value\":\"" + escapeJsonValue(plugin.getDebugSystem().getSystemInfo()) + "\",\"inline\":false}"
                + "],"
                + "\"footer\":{\"text\":\"" + escapeJsonValue(timestamp) + "\"}"
                + "}]}";

        sendJsonPayload(payload);
    }

    private void sendAsFile(Severity severity, String message, String fullText, String module) throws Exception {
        String boundary = "DoAPIBoundary" + System.nanoTime();
        String fileName = severity.filePrefix + "_" + System.currentTimeMillis() + ".txt";

        String payloadJson = "{\"embeds\":[{"
                + "\"title\":\"" + escapeJsonValue("DoAPI " + severity.title) + "\","
                + "\"description\":\"" + escapeJsonValue(stripMarkdown(
                        message == null ? "(no message)" : message)) + "\","
                + "\"color\":" + severity.color + ","
                + "\"fields\":["
                + "{\"name\":\"Module\",\"value\":\"" + escapeJsonValue(module) + "\",\"inline\":true}"
                + "]}]}";

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.write("Content-Disposition: form-data; name=\"payload_json\"\r\n".getBytes(StandardCharsets.UTF_8));
        body.write("Content-Type: application/json\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        body.write(payloadJson.getBytes(StandardCharsets.UTF_8));
        body.write("\r\n".getBytes(StandardCharsets.UTF_8));

        body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(("Content-Disposition: form-data; name=\"files[0]\"; filename=\"" + fileName + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        body.write("Content-Type: text/plain; charset=utf-8\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        body.write(fullText.getBytes(StandardCharsets.UTF_8));
        body.write("\r\n".getBytes(StandardCharsets.UTF_8));

        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            plugin.getLogger().warning("Webhook responded with " + response.statusCode());
        }
    }

    private void sendJsonPayload(String payload) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            plugin.getLogger().warning("Webhook responded with " + response.statusCode()
                    + ": " + response.body());
        }
    }

    private String escapeJsonValue(String value) {
        if (value == null) {
            return "";
        }
        String escaped = escapeJson(value);
        return escaped.length() > MAX_EMBED_DESCRIPTION
                ? escaped.substring(0, MAX_EMBED_DESCRIPTION)
                : escaped;
    }

    private String escapeJson(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            switch (current) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                default -> {
                    if (current < 0x20) {
                        builder.append(String.format("\\u%04x", (int) current));
                    } else {
                        builder.append(current);
                    }
                }
            }
        }
        return builder.toString();
    }

    private String stripMarkdown(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("`", "'")
                .replace("*", "")
                .replace("_", "")
                .replace("~", "");
    }
}
