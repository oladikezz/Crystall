package net.schalker.SMPS.modules.flags;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FlagTracker {
   private final Map<UUID, Map<FlagType, ActionList>> trackedActions = new ConcurrentHashMap<>();
   private final Map<UUID, Map<FlagType, Long>> lastNotification = new ConcurrentHashMap<>();
   // Severity-aware cooldowns: keyed by "flagKey:SEVERITY"
   private final Map<UUID, Map<String, Long>> lastSeverityNotification = new ConcurrentHashMap<>();

   public void trackAction(UUID playerId, FlagType flagType, long timestamp) {
      this.trackedActions
         .computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
         .computeIfAbsent(flagType, k -> new ActionList())
         .add(timestamp);
   }

   public int getActionCount(UUID playerId, FlagType flagType, long timeWindowMs) {
      Map<FlagType, ActionList> playerActions = this.trackedActions.get(playerId);
      if (playerActions == null) {
         return 0;
      }
      ActionList actions = playerActions.get(flagType);
      if (actions == null) {
         return 0;
      }
      return actions.getCountInWindow(timeWindowMs);
   }

   public void clearActions(UUID playerId, FlagType flagType) {
      Map<FlagType, ActionList> playerActions = this.trackedActions.get(playerId);
      if (playerActions != null) {
         playerActions.remove(flagType);
      }
   }

   public void clearAll(UUID playerId) {
      this.trackedActions.remove(playerId);
      this.lastNotification.remove(playerId);
      this.lastSeverityNotification.remove(playerId);
   }

   /**
    * Clear ALL tracking data for all players.
    */
   public void clearAll() {
      this.trackedActions.clear();
      this.lastNotification.clear();
      this.lastSeverityNotification.clear();
   }

   public boolean canNotify(UUID playerId, FlagType flagType, long cooldownMs) {
      if (cooldownMs <= 0) {
         return true;
      }
      Long last = this.lastNotification
         .computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
         .get(flagType);
      if (last == null) {
         return true;
      }
      long now = System.currentTimeMillis();
      return (now - last) >= cooldownMs;
   }

   public void markNotified(UUID playerId, FlagType flagType) {
      this.lastNotification
         .computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
         .put(flagType, System.currentTimeMillis());
   }

   /**
    * Check if a notification can be sent for a specific severity level.
    * Each severity level has its own independent cooldown, so a LOW flag
    * firing does NOT prevent a HIGH flag from firing later.
    */
   public boolean canNotifyForSeverity(UUID playerId, FlagType flagType,
                                        FlagType.FlagSeverity severity, long cooldownMs) {
      if (cooldownMs <= 0) return true;
      if (playerId == null) return true;
      String key = flagType.getKey() + ":" + severity.name();
      Long last = this.lastSeverityNotification
         .computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
         .get(key);
      if (last == null) return true;
      return (System.currentTimeMillis() - last) >= cooldownMs;
   }

   /**
    * Mark that a notification was sent for a specific severity level.
    */
   public void markNotifiedForSeverity(UUID playerId, FlagType flagType,
                                        FlagType.FlagSeverity severity) {
      if (playerId == null) return;
      String key = flagType.getKey() + ":" + severity.name();
      this.lastSeverityNotification
         .computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
         .put(key, System.currentTimeMillis());
   }

   public void cleanup(long maxAgeMs) {
      long cutoff = System.currentTimeMillis() - maxAgeMs;
      for (Map<FlagType, ActionList> playerActions : this.trackedActions.values()) {
         for (ActionList actions : playerActions.values()) {
            actions.removeOlderThan(cutoff);
         }
      }
   }

   private static class ActionList {
      private final List<Long> timestamps = new ArrayList<>();

      public synchronized void add(long timestamp) {
         this.timestamps.add(timestamp);
      }

      public synchronized int getCountInWindow(long windowMs) {
         if (this.timestamps.isEmpty()) {
            return 0;
         }
         long cutoff = System.currentTimeMillis() - windowMs;
         int count = 0;
         for (Long timestamp : this.timestamps) {
            if (timestamp >= cutoff) {
               count++;
            }
         }
         return count;
      }

      public synchronized void removeOlderThan(long cutoff) {
         Iterator<Long> iterator = this.timestamps.iterator();
         while (iterator.hasNext()) {
            if (iterator.next() < cutoff) {
               iterator.remove();
            }
         }
      }
   }
}
