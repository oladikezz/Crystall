package net.schalker.SMPS.modules.flags;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class DiscordWebhook {
   private final String webhookUrl;

   public DiscordWebhook(String webhookUrl) {
      this.webhookUrl = webhookUrl;
   }

   public void sendFlagEmbed(FlagEvent event) {
      if (this.webhookUrl == null || this.webhookUrl.isEmpty() || this.webhookUrl.equals("your-webhook-url-here")) {
         return;
      }

      try {
         // Build description
         StringBuilder description = new StringBuilder();
         description.append("**Игрок:** ").append(escapeJson(event.getPlayerName())).append("\\n");
         
         if (event.getLocation() != null) {
            description.append("**Координаты:** ").append(event.getCoordinates()).append("\\n");
            description.append("**Мир:** ").append(escapeJson(event.getWorld())).append("\\n");
         }
         
         if (event.getValue() > 0) {
            description.append("**Значение:** ").append(event.getValue()).append("\\n");
         }
         
         if (event.getDetails() != null && !event.getDetails().isEmpty()) {
            description.append("**Детали:** ").append(escapeJson(event.getDetails())).append("\\n");
         }

         description.append("**Уровень:** ").append(event.getResolvedSeverity().getName()).append("\\n");

         // Format timestamp
         SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US);
         sdf.setTimeZone(TimeZone.getTimeZone("Europe/Moscow"));
         String timeString = sdf.format(new Date(event.getTimestamp()));
         
         SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
         String isoTimestamp = isoFormat.format(new Date(event.getTimestamp()));
         
         // Build JSON manually
         StringBuilder json = new StringBuilder();
         json.append("{");
         json.append("\"embeds\":[{");
         json.append("\"title\":\"⚠ ").append(escapeJson(event.getFlagType().getDisplayName())).append("\",");
         json.append("\"description\":\"").append(description).append("\",");
         json.append("\"color\":").append(event.getResolvedSeverity().getColor()).append(",");
         json.append("\"thumbnail\":{\"url\":\"https://minotar.net/avatar/").append(event.getPlayerName()).append("/64\"},");
         json.append("\"footer\":{\"text\":\"").append(timeString).append(" МСК\"},");
         json.append("\"timestamp\":\"").append(isoTimestamp).append("\"");
         json.append("}]");
         json.append("}");
         
         this.sendPayload(json.toString());
      } catch (Exception e) {
         // Silent fail
      }
   }

   private void sendPayload(String jsonPayload) {
      try {
         URL url = new URL(this.webhookUrl);
         HttpURLConnection connection = (HttpURLConnection) url.openConnection();
         connection.setRequestMethod("POST");
         connection.setRequestProperty("Content-Type", "application/json");
         connection.setRequestProperty("User-Agent", "SMPS-Flags/1.0");
         connection.setDoOutput(true);
         
         try (OutputStream os = connection.getOutputStream()) {
            byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
         }
         
         int responseCode = connection.getResponseCode();
         if (responseCode < 200 || responseCode >= 300) {
            // Failed to send
         }
         
         connection.disconnect();
      } catch (Exception e) {
         // Silent fail
      }
   }

   private String escapeJson(String text) {
      if (text == null) {
         return "";
      }
      return text.replace("\\", "\\\\")
                 .replace("\"", "\\\"")
                 .replace("\n", "\\n")
                 .replace("\r", "\\r")
                 .replace("\t", "\\t");
   }
}
