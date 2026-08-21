package net.schalker.SMPS.modules.quietban;

import java.util.Locale;

public enum QuietBanLevel {

   QUIET("quiet", "Тихий"),
   MEDIUM("medium", "Средний"),
   AGGRESSIVE("aggressive", "Агрессивный");

   private final String key;
   private final String displayName;

   QuietBanLevel(String key, String displayName) {
      this.key = key;
      this.displayName = displayName;
   }

   public String getKey() {
      return this.key;
   }

   public String getDisplayName() {
      return this.displayName;
   }

   public static QuietBanLevel parse(String raw) {
      if (raw == null) {
         return null;
      }
      return switch (raw.trim().toLowerCase(Locale.ROOT)) {
         case "quiet", "low", "light", "1", "тихий" -> QUIET;
         case "medium", "mid", "normal", "2", "средний" -> MEDIUM;
         case "aggressive", "hard", "high", "3", "агрессивный" -> AGGRESSIVE;
         default -> null;
      };
   }

   public static QuietBanLevel fromKey(String key) {
      if (key == null) {
         return null;
      }
      for (QuietBanLevel level : values()) {
         if (level.key.equalsIgnoreCase(key)) {
            return level;
         }
      }
      return null;
   }
}
