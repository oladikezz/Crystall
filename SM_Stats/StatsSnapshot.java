package net.schalker.SMPS.modules.stats;

import java.util.UUID;

public class StatsSnapshot {
   private final UUID uuid;
   private final String name;
   private final long totalMinutes;
   private final long monthlyMinutes;
   private final long weeklyMinutes;
   private final long dailyMinutes;
   private final int lastMonth;
   private final int lastWeek;
   private final long lastJoin;
   private final long firstJoin;
   private final long deaths;
   private final long playerKills;
   private final long mobKills;
   private final long blocksBroken;
   private final long blocksPlaced;
   private final long itemsCrafted;
   private final long distWalkCenti;
   private final long distSwimCenti;
   private final long distFlyCenti;
   private final long chatMessages;
   private final long achievements;
   private final boolean isOnline;

   public StatsSnapshot(UUID uuid,
                        String name,
                        long totalMinutes,
                        long monthlyMinutes,
                        long weeklyMinutes,
                        long dailyMinutes,
                        int lastMonth,
                        int lastWeek,
                        long lastJoin,
                        long firstJoin,
                        long deaths,
                        long playerKills,
                        long mobKills,
                        long blocksBroken,
                        long blocksPlaced,
                        long itemsCrafted,
                        long distWalkCenti,
                        long distSwimCenti,
                        long distFlyCenti,
                        long chatMessages,
                        long achievements,
                        boolean isOnline) {
      this.uuid = uuid;
      this.name = name;
      this.totalMinutes = totalMinutes;
      this.monthlyMinutes = monthlyMinutes;
      this.weeklyMinutes = weeklyMinutes;
      this.dailyMinutes = dailyMinutes;
      this.lastMonth = lastMonth;
      this.lastWeek = lastWeek;
      this.lastJoin = lastJoin;
      this.firstJoin = firstJoin;
      this.deaths = deaths;
      this.playerKills = playerKills;
      this.mobKills = mobKills;
      this.blocksBroken = blocksBroken;
      this.blocksPlaced = blocksPlaced;
      this.itemsCrafted = itemsCrafted;
      this.distWalkCenti = distWalkCenti;
      this.distSwimCenti = distSwimCenti;
      this.distFlyCenti = distFlyCenti;
      this.chatMessages = chatMessages;
      this.achievements = achievements;
      this.isOnline = isOnline;
   }

   public UUID getUuid() {
      return this.uuid;
   }

   public String getName() {
      return this.name;
   }

   public long getTotalMinutes() {
      return this.totalMinutes;
   }

   public long getMonthlyMinutes() {
      return this.monthlyMinutes;
   }

   public long getWeeklyMinutes() {
      return this.weeklyMinutes;
   }

   public long getDailyMinutes() {
      return this.dailyMinutes;
   }

   public int getLastMonth() {
      return this.lastMonth;
   }

   public int getLastWeek() {
      return this.lastWeek;
   }

   public long getLastJoin() {
      return this.lastJoin;
   }

   public long getFirstJoin() {
      return this.firstJoin;
   }

   public long getDeaths() {
      return this.deaths;
   }

   public long getPlayerKills() {
      return this.playerKills;
   }

   public long getMobKills() {
      return this.mobKills;
   }

   public long getBlocksBroken() {
      return this.blocksBroken;
   }

   public long getBlocksPlaced() {
      return this.blocksPlaced;
   }

   public long getItemsCrafted() {
      return this.itemsCrafted;
   }

   public long getDistWalkCenti() {
      return this.distWalkCenti;
   }

   public long getDistSwimCenti() {
      return this.distSwimCenti;
   }

   public long getDistFlyCenti() {
      return this.distFlyCenti;
   }

   public long getChatMessages() {
      return this.chatMessages;
   }

   public long getAchievements() {
      return this.achievements;
   }

   public boolean isOnline() {
      return this.isOnline;
   }

   public long getTotalDistanceCenti() {
      return this.distWalkCenti + this.distSwimCenti + this.distFlyCenti;
   }
}