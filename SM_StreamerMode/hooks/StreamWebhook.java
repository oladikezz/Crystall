package net.schalker.SMPS.modules.streamermode.hooks;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import net.schalker.SMPS.SMPS;
import net.schalker.SMPS.modules.streamermode.StreamerModeModule;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class StreamWebhook {

   private static final int MAX_FIELD_LENGTH = 2000;
   private static final int DEFAULT_COLOR = 0x9146FF;

   private final SMPS plugin;
   private final StreamerModeModule module;
   private final HttpClient httpClient;

   public StreamWebhook(SMPS plugin, StreamerModeModule module) {
      this.plugin = plugin;
      this.module = module;
      this.httpClient = HttpClient.newBuilder()
         .connectTimeout(Duration.ofSeconds(10))
         .build();
   }

   public void sendStreamStart(Player player, String link) {
      if (player == null) {
         return;
      }

      FileConfiguration config = this.module.getConfig();
      if (config == null) {
         this.plugin.getDebugSystem().logWarning("StreamerMode",
            "Discord webhook skipped: module config is not loaded");
         return;
      }
      if (!config.isConfigurationSection("notifications.discord")) {
         this.plugin.getDebugSystem().logWarning("StreamerMode",
            "Discord webhook skipped: 'notifications.discord' is missing from config.yml");
         return;
      }
      if (!config.getBoolean("notifications.discord.enabled", false)) {
         this.plugin.getLogger().info("[StreamerMode] Discord webhook skipped: disabled in config");
         return;
      }

      String url = config.getString("notifications.discord.webhook-url", "");
      if (url == null || url.isBlank()) {
         this.plugin.getDebugSystem().logWarning("StreamerMode",
            "Discord webhook skipped: 'notifications.discord.webhook-url' is empty");
         return;
      }
      if (!isValidUrl(url)) {
         this.plugin.getDebugSystem().logWarning("StreamerMode",
            "Discord webhook skipped: webhook-url must start with http:// or https://");
         return;
      }

      String playerName = player.getName();
      String resolvedLink = link == null ? "" : link;
      String payload = buildPayload(config, playerName, resolvedLink);

      this.plugin.getLogger().info("[StreamerMode] Sending Discord webhook for " + playerName);
      Bukkit.getAsyncScheduler().runNow(this.plugin, task -> post(url, payload));
   }

   private String buildPayload(FileConfiguration config, String playerName, String link) {
      StringBuilder builder = new StringBuilder("{");

      String username = fill(config.getString("notifications.discord.username", "Stream Alert"), playerName, link);
      if (username != null && !username.isBlank()) {
         builder.append("\"username\":\"").append(escape(username)).append("\",");
      }

      String avatar = fill(config.getString("notifications.discord.avatar-url", ""), playerName, link);
      if (avatar != null && !avatar.isBlank()) {
         builder.append("\"avatar_url\":\"").append(escape(avatar)).append("\",");
      }

      String content = fill(config.getString("notifications.discord.content", ""), playerName, link);
      builder.append("\"content\":\"").append(escape(content == null ? "" : content)).append("\"");

      if (config.getBoolean("notifications.discord.embed.enabled", true)) {
         builder.append(",\"embeds\":[").append(buildEmbed(config, playerName, link)).append("]");
      }

      return builder.append("}").toString();
   }

   private String buildEmbed(FileConfiguration config, String playerName, String link) {
      String title = fill(config.getString("notifications.discord.embed.title", "{player} started streaming"),
         playerName, link);
      String description = fill(config.getString("notifications.discord.embed.description", "{link}"),
         playerName, link);
      String thumbnail = fill(config.getString("notifications.discord.embed.thumbnail-url", ""), playerName, link);
      int color = config.getInt("notifications.discord.embed.color", DEFAULT_COLOR);

      StringBuilder embed = new StringBuilder("{");
      embed.append("\"title\":\"").append(escape(title)).append("\",");
      embed.append("\"description\":\"").append(escape(description)).append("\",");
      embed.append("\"color\":").append(color).append(",");

      if (link != null && !link.isBlank() && isValidUrl(link)) {
         embed.append("\"url\":\"").append(escape(link)).append("\",");
      }
      if (thumbnail != null && !thumbnail.isBlank()) {
         embed.append("\"thumbnail\":{\"url\":\"").append(escape(thumbnail)).append("\"},");
      }

      embed.append("\"timestamp\":\"").append(Instant.now().toString()).append("\"");
      return embed.append("}").toString();
   }

   private boolean isValidUrl(String value) {
      String lower = value.toLowerCase();
      return lower.startsWith("http://") || lower.startsWith("https://");
   }

   private String fill(String template, String playerName, String link) {
      if (template == null) {
         return "";
      }
      return template
         .replace("{player}", playerName == null ? "" : playerName)
         .replace("{link}", link == null ? "" : link)
         .replace("{server}", Bukkit.getName());
   }

   private void post(String url, String payload) {
      try {
         HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
            .build();

         HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
         if (response.statusCode() >= 300) {
            this.plugin.getDebugSystem().logWarning("StreamerMode",
               "Discord webhook responded with " + response.statusCode() + ": " + response.body());
         } else {
            this.plugin.getLogger().info("[StreamerMode] Discord webhook delivered ("
               + response.statusCode() + ")");
         }
      } catch (Exception e) {
         this.plugin.getDebugSystem().logError("StreamerMode: Discord webhook delivery failed", e);
      }
   }

   private String escape(String value) {
      if (value == null) {
         return "";
      }

      String trimmed = value.length() > MAX_FIELD_LENGTH ? value.substring(0, MAX_FIELD_LENGTH) : value;
      StringBuilder builder = new StringBuilder(trimmed.length() + 16);
      for (int i = 0; i < trimmed.length(); i++) {
         char current = trimmed.charAt(i);
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
}
