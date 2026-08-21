package net.schalker.SMPS.modules.adminlist;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class AdminListWebhook {
   private String webhookUrl;
   private final OnlineListImageRenderer imageRenderer = new OnlineListImageRenderer();

   public AdminListWebhook(String webhookUrl) {
      this.webhookUrl = webhookUrl;
   }

   public void setWebhookUrl(String webhookUrl) {
      this.webhookUrl = webhookUrl;
   }

   public void sendAdminListEmbed(
      AdminListModule module,
      String action,
      String triggerPlayer,
      List<OnlineEntry> onlineEntries,
      int maxPlayers,
      boolean isJoin
   ) {
      if (this.webhookUrl == null || this.webhookUrl.isBlank()) {
         return;
      }

      try {
         int onlineCount = onlineEntries == null ? 0 : onlineEntries.size();
         String actionText = action == null ? "" : action;
         String playerText = triggerPlayer == null ? "" : triggerPlayer;

         String actionFieldName = this.resolveTemplate(module.getActionFieldName(), actionText, playerText, onlineCount, maxPlayers);
         String actionFieldValue = this.resolveTemplate(module.getActionFieldValueFormat(), actionText, playerText, onlineCount, maxPlayers);

         String embedTitle = this.resolveTemplate(module.getEmbedTitle(), actionText, playerText, onlineCount, maxPlayers);
         String embedFooter = this.resolveTemplate(module.getEmbedFooter(), actionText, playerText, onlineCount, maxPlayers);

         byte[] imageBytes = this.imageRenderer.render(
            onlineEntries,
            maxPlayers,
            module.getImageTitle(),
            module.getFontZipPath(),
            module.getFontEntryPath()
         );

         SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
         isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
         String isoTimestamp = isoFormat.format(new Date());

         SimpleDateFormat footerFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US);
         footerFormat.setTimeZone(TimeZone.getTimeZone("Europe/Moscow"));
         String footerDate = footerFormat.format(new Date());

         int embedColor = isJoin ? module.getJoinEmbedColor() : module.getQuitEmbedColor();

         StringBuilder json = new StringBuilder();
         json.append("{");
         json.append("\"embeds\":[{");
         json.append("\"title\":\"").append(escapeJson(embedTitle)).append("\",");
         json.append("\"color\":").append(embedColor).append(",");
         if (!actionFieldName.isBlank() && !actionFieldValue.isBlank()) {
            json.append("\"fields\":[");
            json.append("{");
            json.append("\"name\":\"").append(escapeJson(actionFieldName)).append("\",");
            json.append("\"value\":\"").append(escapeJson(actionFieldValue)).append("\",");
            json.append("\"inline\":false");
            json.append("}");
            json.append("],");
         }
         json.append("\"image\":{\"url\":\"attachment://online.png\"},");
         json.append("\"footer\":{\"text\":\"")
            .append(escapeJson(embedFooter)).append(" • ").append(escapeJson(footerDate)).append(" МСК\"},");
         json.append("\"timestamp\":\"").append(isoTimestamp).append("\"");
         json.append("}]");
         json.append("}");

         this.sendPayload(json.toString(), imageBytes);
      } catch (Exception ignored) {
      }
   }

   private void sendPayload(String jsonPayload, byte[] imageBytes) {
      try {
         URL url = new URL(this.webhookUrl);
         HttpURLConnection connection = (HttpURLConnection) url.openConnection();
         connection.setRequestMethod("POST");
         String boundary = "----SMPSAdminList" + System.currentTimeMillis();
         connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
         connection.setRequestProperty("User-Agent", "SMPS-AdminList/1.0");
         connection.setDoOutput(true);

         try (OutputStream os = connection.getOutputStream()) {
            this.writePart(os, boundary, "payload_json", "application/json", jsonPayload.getBytes(StandardCharsets.UTF_8), null);
            if (imageBytes != null && imageBytes.length > 0) {
               this.writePart(os, boundary, "files[0]", "image/png", imageBytes, "online.png");
            }
            os.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
         }

         int responseCode = connection.getResponseCode();
         if (responseCode < 200 || responseCode >= 300) {
            connection.getErrorStream();
         }
         connection.disconnect();
      } catch (Exception ignored) {
      }
   }

   private void writePart(OutputStream os, String boundary, String fieldName, String contentType, byte[] content, String fileName) throws Exception {
      os.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
      if (fileName == null) {
         os.write(("Content-Disposition: form-data; name=\"" + fieldName + "\"\r\n").getBytes(StandardCharsets.UTF_8));
      } else {
         os.write(("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + fileName + "\"\r\n").getBytes(StandardCharsets.UTF_8));
      }
      os.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
      os.write(content);
      os.write("\r\n".getBytes(StandardCharsets.UTF_8));
   }

   public static final class OnlineEntry {
      private final String name;
      private final String uuid;
      private final int ping;
      private final boolean admin;
      private final java.awt.Color prefixColor;

      public OnlineEntry(String name, String uuid, int ping, boolean admin, java.awt.Color prefixColor) {
         this.name = name;
         this.uuid = uuid;
         this.ping = ping;
         this.admin = admin;
         this.prefixColor = prefixColor;
      }

      public String name() {
         return this.name;
      }

      public String uuid() {
         return this.uuid;
      }

      public int ping() {
         return this.ping;
      }

      public boolean isAdmin() {
         return this.admin;
      }

      public java.awt.Color prefixColor() {
         return this.prefixColor;
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

   private String resolveTemplate(String template, String action, String player, int online, int max) {
      String value = template == null ? "" : template;
      return value
         .replace("{action}", action)
         .replace("{player}", player)
         .replace("{online}", String.valueOf(online))
         .replace("{max}", String.valueOf(max));
   }
}
