package site.deforce.SM_Clans.logging;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.LinkedBlockingDeque;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import site.deforce.SM_Clans.SM_Clans;
import site.deforce.SM_Clans.models.Clan;

public class ClanAuditLogger {
   public static final int COLOR_PURCHASE = 3066993;
   public static final int COLOR_RENT = 3447003;
   public static final int COLOR_DISBAND = 15158332;
   public static final int COLOR_MEMBER = 10181046;
   public static final int COLOR_TAX = 15844367;
   private final SM_Clans module;
   private final ClanLogDatabase logDb;
   private final LinkedBlockingDeque<String> queue = new LinkedBlockingDeque();
   private volatile boolean running = false;
   private volatile String webhookUrl;
   private volatile String username;
   private long minIntervalMs;
   private int queueLimit;
   private volatile boolean webhookTaxCollect = true;
   private Thread worker;

   public ClanAuditLogger(SM_Clans module, ClanLogDatabase logDb) {
      super();
      this.module = module;
      this.logDb = logDb;
   }

   public void start() {
      this.reloadConfig();
      if (!this.running) {
         if (!this.isEnabled()) {
            this.module.getPlugin().getDebugSystem().log("ClanAudit", "Audit webhook disabled (no URL/enabled flag)");
         } else {
            this.running = true;
            this.worker = new Thread(this::drainLoop, "SM_Clans-AuditWebhook");
            this.worker.setDaemon(true);
            this.worker.start();
            this.module.getPlugin().getDebugSystem().log("ClanAudit", "Audit webhook worker started");
         }
      }
   }

   public void shutdown() {
      this.running = false;
      if (this.worker != null) {
         this.worker.interrupt();
         this.worker = null;
      }

      this.queue.clear();
   }

   private void reloadConfig() {
      FileConfiguration config = this.module.getConfig();
      if (config != null) {
         this.webhookUrl = config.getString("audit-log.webhook-url", "");
         this.username = config.getString("audit-log.username", "Clan Audit");
         this.minIntervalMs = Math.max(0L, config.getLong("audit-log.min-interval-ms", 2000L));
         this.queueLimit = Math.max(1, config.getInt("audit-log.queue-limit", 200));
         this.webhookTaxCollect = config.getBoolean("audit-log.log-tax-collect", true);
      }
   }

   public boolean isEnabled() {
      FileConfiguration config = this.module.getConfig();
      if (config != null && config.getBoolean("audit-log.enabled", false)) {
         return this.webhookUrl != null && !this.webhookUrl.isBlank();
      } else {
         return false;
      }
   }

   public void logPurchase(Player actor, Clan clan, String actionCode, String detail, long cost, long newBalance) {
      this.persist(actionCode, actor, clan, detail, cost, newBalance);
      List<Field> fields = new ArrayList();
      fields.add(new Field("Покупка", detail, false));
      fields.add(new Field("Стоимость", cost + " ар", true));
      fields.add(new Field("Казна после", newBalance + " ар", true));
      this.log(3066993, "\ud83d\udcb8 Покупка в гильдии", actor, clan, fields);
   }

   public void logCreation(Player creator, Clan clan, long cost) {
      this.persist("CREATE", creator, clan, (String)null, cost, (Long)null);
      List<Field> fields = new ArrayList();
      fields.add(new Field("Стоимость", cost + " ар (из инвентаря)", true));
      this.log(3066993, "✨ Создана гильдия", creator, clan, fields);
   }

   public void logRent(Clan clan, long rent, long newBalance) {
      this.persist("RENT", (Player)null, clan, "Участников: " + clan.getMemberCount(), rent, newBalance);
      List<Field> fields = new ArrayList();
      fields.add(new Field("Аренда", rent + " ар", true));
      fields.add(new Field("Участников", String.valueOf(clan.getMemberCount()), true));
      fields.add(new Field("Казна после", newBalance + " ар", true));
      this.log(3447003, "\ud83c\udfe6 Списана аренда", (Player)null, clan, fields);
   }

   public void logDisband(Clan clan, String reason) {
      this.persist("DISBAND", (Player)null, clan, reason, (Long)null, (Long)null);
      List<Field> fields = new ArrayList();
      fields.add(new Field("Причина", reason, false));
      this.log(15158332, "\ud83d\uddd1 Гильдия распущена", (Player)null, clan, fields);
   }

   public void logPlayerDisband(Player actor, Clan clan) {
      this.persist("DISBAND", actor, clan, "Распущена лидером", (Long)null, (Long)null);
      List<Field> fields = new ArrayList();
      fields.add(new Field("Причина", "Распущена лидером", false));
      this.log(15158332, "\ud83d\uddd1 Гильдия распущена", actor, clan, fields);
   }

   public void logDeposit(Player actor, Clan clan, long amount, long newBalance) {
      this.persist("DEPOSIT", actor, clan, (String)null, amount, newBalance);
      List<Field> fields = new ArrayList();
      fields.add(new Field("Внесено", amount + " ар", true));
      fields.add(new Field("Казна после", newBalance + " ар", true));
      this.log(3447003, "\ud83d\udce5 Пополнение казны", actor, clan, fields);
   }

   public void logWithdraw(Player actor, Clan clan, long amount, long newBalance) {
      this.persist("WITHDRAW", actor, clan, (String)null, amount, newBalance);
      List<Field> fields = new ArrayList();
      fields.add(new Field("Снято", amount + " ар", true));
      fields.add(new Field("Казна после", newBalance + " ар", true));
      this.log(15158332, "\ud83d\udce4 Снятие из казны", actor, clan, fields);
   }

   public void logJoin(Player actor, Clan clan) {
      this.persist("JOIN", actor, clan, (String)null, (Long)null, (Long)null);
      this.log(10181046, "➕ Игрок вступил", actor, clan, new ArrayList());
   }

   public void logLeave(Player actor, Clan clan) {
      this.persist("LEAVE", actor, clan, (String)null, (Long)null, (Long)null);
      this.log(10181046, "➖ Игрок вышел", actor, clan, new ArrayList());
   }

   public void logKick(Player actor, Clan clan, String targetName) {
      this.persist("KICK", actor, clan, targetName, (Long)null, (Long)null);
      List<Field> fields = new ArrayList();
      fields.add(new Field("Исключён", targetName, true));
      this.log(10181046, "\ud83d\udc62 Исключение из гильдии", actor, clan, fields);
   }

   public void logRoleChange(Player actor, Clan clan, String targetName, String newRole) {
      this.persist("ROLE_CHANGE", actor, clan, targetName + " → " + newRole, (Long)null, (Long)null);
      List<Field> fields = new ArrayList();
      fields.add(new Field("Участник", targetName, true));
      fields.add(new Field("Новая роль", newRole, true));
      this.log(10181046, "\ud83c\udf96 Смена роли", actor, clan, fields);
   }

   public void logAdminTreasury(Player admin, Clan clan, String actionCode, long amount, long newBalance) {
      this.persist(actionCode, admin, clan, (String)null, amount, newBalance);
      boolean add = "ADMIN_ADD".equals(actionCode);
      List<Field> fields = new ArrayList();
      fields.add(new Field(add ? "Выдано" : "Изъято", amount + " ар", true));
      fields.add(new Field("Казна после", newBalance + " ар", true));
      this.log(add ? 3447003 : 15158332, add ? "\ud83d\udee0 Админ: выдача в казну" : "\ud83d\udee0 Админ: изъятие из казны", admin, clan, fields);
   }

   public void logTaxCollect(Clan clan, long amount, long poolBalance) {
      this.persist("TAX_COLLECT", (Player)null, clan, "В казну президента", amount, poolBalance);
      if (this.webhookTaxCollect) {
         List<Field> fields = new ArrayList();
         fields.add(new Field("Налог", amount + " ар", true));
         fields.add(new Field("Казна президента", poolBalance + " ар", true));
         this.log(15844367, "\ud83c\udfdb Налог в казну президента", (Player)null, clan, fields);
      }
   }

   public void logTaxWithdraw(Player president, long amount, long poolBalance) {
      this.persist("TAX_WITHDRAW", president, (Clan)null, (String)null, amount, poolBalance);
      List<Field> fields = new ArrayList();
      fields.add(new Field("Снято", amount + " ар", true));
      fields.add(new Field("Казна президента", poolBalance + " ар", true));
      this.log(15844367, "\ud83c\udfdb Президент снял налоги", president, (Clan)null, fields);
   }

   public void logTransfer(Player actor, Clan clan, String newLeaderName) {
      this.persist("TRANSFER", actor, clan, newLeaderName, (Long)null, (Long)null);
      List<Field> fields = new ArrayList();
      fields.add(new Field("Новый лидер", newLeaderName, true));
      this.log(10181046, "\ud83d\udc51 Передача лидерства", actor, clan, fields);
   }

   private void persist(String actionCode, Player actor, Clan clan, String target, Long amount, Long balance) {
      if (this.logDb != null) {
         String actorName = actor != null ? actor.getName() : "SYSTEM";
         String actorUuid = actor != null ? actor.getUniqueId().toString() : null;
         String clanTag = clan != null ? stripColors(clan.getTag()) : null;
         String clanId = clan != null ? clan.getClanId() : null;
         this.logDb.record(actionCode, actorName, actorUuid, clanTag, clanId, target, amount, balance);
      }
   }

   private void log(int color, String title, Player actor, Clan clan, List<Field> fields) {
      if (this.isEnabled()) {
         List<Field> allFields = new ArrayList();
         if (clan != null) {
            allFields.add(new Field("Гильдия", "[" + stripColors(clan.getTag()) + "] " + stripColors(clan.getName()), false));
         }

         if (actor != null) {
            allFields.add(new Field("Игрок", actor.getName(), true));
         }

         allFields.addAll(fields);
         this.enqueue(this.buildEmbedJson(color, title, allFields));
      }
   }

   private void enqueue(String payload) {
      while(this.queue.size() >= this.queueLimit) {
         this.queue.pollFirst();
      }

      this.queue.offerLast(payload);
   }

   private String buildEmbedJson(int color, String title, List<Field> fields) {
      SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
      isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      String isoTimestamp = isoFormat.format(new Date());
      StringBuilder json = new StringBuilder();
      json.append("{");
      if (this.username != null && !this.username.isBlank()) {
         json.append("\"username\":\"").append(escapeJson(this.username)).append("\",");
      }

      json.append("\"embeds\":[{");
      json.append("\"title\":\"").append(escapeJson(title)).append("\",");
      json.append("\"color\":").append(color).append(",");
      json.append("\"fields\":[");

      for(int i = 0; i < fields.size(); ++i) {
         Field field = (Field)fields.get(i);
         if (i > 0) {
            json.append(",");
         }

         json.append("{");
         json.append("\"name\":\"").append(escapeJson(field.name)).append("\",");
         json.append("\"value\":\"").append(escapeJson(field.value.isEmpty() ? "-" : field.value)).append("\",");
         json.append("\"inline\":").append(field.inline);
         json.append("}");
      }

      json.append("],");
      json.append("\"timestamp\":\"").append(isoTimestamp).append("\"");
      json.append("}]");
      json.append("}");
      return json.toString();
   }

   private void drainLoop() {
      while(true) {
         if (this.running) {
            String payload;
            try {
               payload = (String)this.queue.takeFirst();
            } catch (InterruptedException var6) {
               Thread.currentThread().interrupt();
               return;
            }

            long backoff = this.send(payload);

            try {
               Thread.sleep(Math.max(backoff, this.minIntervalMs));
               continue;
            } catch (InterruptedException var5) {
               Thread.currentThread().interrupt();
            }
         }

         return;
      }
   }

   private long send(String payload) {
      String url = this.webhookUrl;
      if (url != null && !url.isBlank()) {
         HttpURLConnection connection = null;

         long var7;
         try {
            connection = (HttpURLConnection)URI.create(url).toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "SMPS-Clans/1.0");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setDoOutput(true);
            OutputStream os = connection.getOutputStream();

            try {
               os.write(payload.getBytes(StandardCharsets.UTF_8));
            } catch (Throwable var15) {
               if (os != null) {
                  try {
                     os.close();
                  } catch (Throwable var14) {
                     var15.addSuppressed(var14);
                  }
               }

               throw var15;
            }

            if (os != null) {
               os.close();
            }

            int code = connection.getResponseCode();
            if (code != 429) {
               if (code < 200 || code >= 300) {
                  this.module.getPlugin().getDebugSystem().log("ClanAudit", "Webhook returned HTTP " + code + "; dropping message");
               }

               long var20 = 0L;
               return var20;
            }

            long retryMs = this.parseRetryAfter(connection);
            this.queue.offerFirst(payload);
            var7 = retryMs;
         } catch (Exception exception) {
            this.module.getPlugin().getDebugSystem().log("ClanAudit", "Webhook send failed: " + exception.getMessage());
            long retryMs = 0L;
            return retryMs;
         } finally {
            if (connection != null) {
               connection.disconnect();
            }

         }

         return var7;
      } else {
         return 0L;
      }
   }

   private long parseRetryAfter(HttpURLConnection connection) {
      String header = connection.getHeaderField("Retry-After");
      if (header != null && !header.isBlank()) {
         try {
            double seconds = Double.parseDouble(header.trim());
            return Math.max(1000L, (long)(seconds * 1000.0));
         } catch (NumberFormatException var5) {
         }
      }

      return 2000L;
   }

   private static String stripColors(String text) {
      if (text == null) {
         return "";
      } else {
         String noLegacy = text.replaceAll("(?i)[&§][0-9a-fk-orx]", "");
         return noLegacy.replaceAll("(?i)(?:&#|#)[0-9a-f]{6}", "").trim();
      }
   }

   private static String escapeJson(String text) {
      return text == null ? "" : text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
   }

   private static final class Field {
      private final String name;
      private final String value;
      private final boolean inline;

      private Field(String name, String value, boolean inline) {
         super();
         this.name = name == null ? "" : name;
         this.value = value == null ? "" : value;
         this.inline = inline;
      }
   }
}
