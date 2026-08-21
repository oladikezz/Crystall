package site.deforce.SM_Clans.util;

import site.deforce.SM_Clans.managers.ClanEconomyManager;
import site.deforce.SM_Clans.models.Clan;

public final class ClanUpkeep {
   public static final long DAY_MS = 86400000L;

   private ClanUpkeep() {
      super();
   }

   public static long dueAt(Clan clan, ClanEconomyManager econ) {
      return clan.getLastRentAt() + econ.getRentPeriodMillis();
   }

   public static long remainingMillis(Clan clan, ClanEconomyManager econ) {
      return dueAt(clan, econ) - System.currentTimeMillis();
   }

   public static String formatDuration(long ms) {
      long totalMinutes = Math.max(0L, ms / 60000L);
      long days = totalMinutes / 1440L;
      long hours = totalMinutes % 1440L / 60L;
      long minutes = totalMinutes % 60L;
      if (days > 0L) {
         return days + "д " + hours + "ч";
      } else {
         return hours > 0L ? hours + "ч " + minutes + "м" : minutes + "м";
      }
   }
}
