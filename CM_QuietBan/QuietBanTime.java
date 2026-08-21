package net.schalker.SMPS.modules.quietban;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QuietBanTime {

   private static final Pattern DURATION = Pattern.compile("^(\\d+[smhdw])+$");
   private static final Pattern TOKEN = Pattern.compile("(\\d+)([smhdw])");
   private static final long MAX_DURATION_MILLIS = 3155760000000L;

   private QuietBanTime() {
   }

   public static boolean looksLikeDuration(String raw) {
      if (raw == null || raw.isEmpty()) {
         return false;
      }
      String value = raw.trim().toLowerCase(Locale.ROOT);
      return isPermanentKeyword(value) || DURATION.matcher(value).matches();
   }

   public static long parseMillis(String raw) {
      if (raw == null) {
         return -1L;
      }
      String value = raw.trim().toLowerCase(Locale.ROOT);
      if (isPermanentKeyword(value)) {
         return 0L;
      }
      if (!DURATION.matcher(value).matches()) {
         return -1L;
      }

      long total = 0L;
      Matcher matcher = TOKEN.matcher(value);
      while (matcher.find()) {
         long amount;
         try {
            amount = Long.parseLong(matcher.group(1));
         } catch (NumberFormatException exception) {
            return -1L;
         }
         long unit = switch (matcher.group(2)) {
            case "s" -> 1000L;
            case "m" -> 60000L;
            case "h" -> 3600000L;
            case "d" -> 86400000L;
            case "w" -> 604800000L;
            default -> 0L;
         };
         if (unit == 0L || amount > MAX_DURATION_MILLIS / unit) {
            return -1L;
         }
         total += amount * unit;
         if (total > MAX_DURATION_MILLIS) {
            return -1L;
         }
      }
      return total;
   }

   public static String formatRemaining(long expiresAt, long now) {
      if (expiresAt <= 0L) {
         return "навсегда";
      }
      long left = expiresAt - now;
      if (left <= 0L) {
         return "истёк";
      }
      return format(left);
   }

   public static String format(long millis) {
      if (millis <= 0L) {
         return "навсегда";
      }

      long seconds = millis / 1000L;
      long days = seconds / 86400L;
      seconds %= 86400L;
      long hours = seconds / 3600L;
      seconds %= 3600L;
      long minutes = seconds / 60L;
      seconds %= 60L;

      StringBuilder builder = new StringBuilder();
      if (days > 0L) {
         builder.append(days).append("д ");
      }
      if (hours > 0L) {
         builder.append(hours).append("ч ");
      }
      if (minutes > 0L) {
         builder.append(minutes).append("м ");
      }
      if (seconds > 0L || builder.isEmpty()) {
         builder.append(seconds).append("с");
      }
      return builder.toString().trim();
   }

   private static boolean isPermanentKeyword(String value) {
      return value.equals("perm") || value.equals("permanent") || value.equals("навсегда");
   }
}
