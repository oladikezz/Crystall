package net.schalker.SMPS.modules.stats;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class StatsData {
   private final UUID uuid;
   private volatile String name;
   private final AtomicLong totalMinutes = new AtomicLong();
   private final AtomicLong monthlyMinutes = new AtomicLong();
   private final AtomicLong weeklyMinutes = new AtomicLong();
   private final AtomicLong dailyMinutes = new AtomicLong();
   private final AtomicLong lastMonth = new AtomicLong();
   private final AtomicLong lastWeek = new AtomicLong();
   private final AtomicLong lastJoin = new AtomicLong();
   private final AtomicLong firstJoin = new AtomicLong();
   private final AtomicLong deaths = new AtomicLong();
   private final AtomicLong playerKills = new AtomicLong();
   private final AtomicLong mobKills = new AtomicLong();
   private final AtomicLong blocksBroken = new AtomicLong();
   private final AtomicLong blocksPlaced = new AtomicLong();
   private final AtomicLong itemsCrafted = new AtomicLong();
   private final AtomicLong distWalkCenti = new AtomicLong();
   private final AtomicLong distSwimCenti = new AtomicLong();
   private final AtomicLong distFlyCenti = new AtomicLong();
   private final AtomicLong chatMessages = new AtomicLong();
   private final AtomicLong achievements = new AtomicLong();
   private volatile long lastPlaytimeUpdate;
   private volatile long lastMoveUpdate;
   private volatile long carrySeconds;
   private volatile int lastDayKey;
   private volatile Location lastLocation;

   public StatsData(UUID uuid, String name) {
      this.uuid = uuid;
      this.name = name;
   }

   public UUID getUuid() {
      return this.uuid;
   }

   public String getName() {
      return this.name;
   }

   public void setName(String name) {
      if (name != null && !name.isEmpty()) {
         this.name = name;
      }
   }

   public void incrementDeaths() {
      this.deaths.incrementAndGet();
   }

   public void incrementPlayerKills() {
      this.playerKills.incrementAndGet();
   }

   public void incrementMobKills() {
      this.mobKills.incrementAndGet();
   }

   public void incrementBlocksBroken() {
      this.blocksBroken.incrementAndGet();
   }

   public void incrementBlocksPlaced() {
      this.blocksPlaced.incrementAndGet();
   }

   public void incrementItemsCrafted(long amount) {
      if (amount <= 0) {
         return;
      }
      this.itemsCrafted.addAndGet(amount);
   }

   public void incrementChatMessages() {
      this.chatMessages.incrementAndGet();
   }

   public void setAchievements(long count) {
      this.achievements.set(count);
   }

   public void markLogin(long nowMillis) {
      long nowSec = nowMillis / 1000L;
      if (this.firstJoin.get() == 0L) {
         this.firstJoin.set(nowSec);
      }
      this.lastJoin.set(nowSec);
      this.lastPlaytimeUpdate = nowMillis;
      this.lastMoveUpdate = nowMillis;
      this.refreshPeriods(nowMillis);
   }

   public void updatePlaytime(long nowMillis) {
      this.refreshPeriods(nowMillis);
      long last = this.lastPlaytimeUpdate;
      if (last <= 0L) {
         this.lastPlaytimeUpdate = nowMillis;
         return;
      }
      long deltaMillis = nowMillis - last;
      if (deltaMillis <= 0L) {
         return;
      }
      long deltaSeconds = deltaMillis / 1000L;
      if (deltaSeconds <= 0L) {
         return;
      }
      this.lastPlaytimeUpdate = nowMillis;
      this.lastJoin.set(nowMillis / 1000L);

      this.carrySeconds += deltaSeconds;
      long addMinutes = this.carrySeconds / 60L;
      this.carrySeconds = this.carrySeconds % 60L;
      if (addMinutes <= 0L) {
         return;
      }
      this.totalMinutes.addAndGet(addMinutes);
      this.dailyMinutes.addAndGet(addMinutes);
      this.weeklyMinutes.addAndGet(addMinutes);
      this.monthlyMinutes.addAndGet(addMinutes);
   }

   public void recordMove(Player player, Location from, Location to, long nowMillis, long minIntervalMillis) {
      if (player == null || from == null || to == null) {
         return;
      }
      if (nowMillis - this.lastMoveUpdate < minIntervalMillis) {
         return;
      }
      if (from.getWorld() == null || to.getWorld() == null) {
         return;
      }
      if (!from.getWorld().equals(to.getWorld())) {
         this.lastLocation = to.clone();
         this.lastMoveUpdate = nowMillis;
         return;
      }
      double distance = from.distance(to);
      if (distance <= 0D) {
         return;
      }
      long centi = Math.round(distance * 100.0D);
      if (centi <= 0L) {
         return;
      }
      if (player.isFlying()) {
         this.distFlyCenti.addAndGet(centi);
      } else if (player.isSwimming() || player.isInWater()) {
         this.distSwimCenti.addAndGet(centi);
      } else {
         this.distWalkCenti.addAndGet(centi);
      }
      this.lastLocation = to.clone();
      this.lastMoveUpdate = nowMillis;
   }

   public Location getLastLocation() {
      return this.lastLocation;
   }

   public StatsSnapshot snapshot() {
      return new StatsSnapshot(
         this.uuid,
         this.name,
         this.totalMinutes.get(),
         this.monthlyMinutes.get(),
         this.weeklyMinutes.get(),
         this.dailyMinutes.get(),
         (int) this.lastMonth.get(),
         (int) this.lastWeek.get(),
         this.lastJoin.get(),
         this.firstJoin.get(),
         this.deaths.get(),
         this.playerKills.get(),
         this.mobKills.get(),
         this.blocksBroken.get(),
         this.blocksPlaced.get(),
         this.itemsCrafted.get(),
         this.distWalkCenti.get(),
         this.distSwimCenti.get(),
         this.distFlyCenti.get(),
         this.chatMessages.get(),
         this.achievements.get(),
         true
      );
   }

   public void loadFromSnapshot(StatsSnapshot snapshot) {
      if (snapshot == null) {
         return;
      }
      this.setName(snapshot.getName());
      this.totalMinutes.set(snapshot.getTotalMinutes());
      this.monthlyMinutes.set(snapshot.getMonthlyMinutes());
      this.weeklyMinutes.set(snapshot.getWeeklyMinutes());
      this.dailyMinutes.set(snapshot.getDailyMinutes());
      this.lastMonth.set(snapshot.getLastMonth());
      this.lastWeek.set(snapshot.getLastWeek());
      this.lastJoin.set(snapshot.getLastJoin());
      this.firstJoin.set(snapshot.getFirstJoin());
      this.deaths.set(snapshot.getDeaths());
      this.playerKills.set(snapshot.getPlayerKills());
      this.mobKills.set(snapshot.getMobKills());
      this.blocksBroken.set(snapshot.getBlocksBroken());
      this.blocksPlaced.set(snapshot.getBlocksPlaced());
      this.itemsCrafted.set(snapshot.getItemsCrafted());
      this.distWalkCenti.set(snapshot.getDistWalkCenti());
      this.distSwimCenti.set(snapshot.getDistSwimCenti());
      this.distFlyCenti.set(snapshot.getDistFlyCenti());
      this.chatMessages.set(snapshot.getChatMessages());
      this.achievements.set(snapshot.getAchievements());
      if (snapshot.getLastJoin() > 0L) {
         this.lastDayKey = this.toDayKey(snapshot.getLastJoin());
      }
   }

   public void addSnapshot(StatsSnapshot snapshot) {
      if (snapshot == null) {
         return;
      }
      this.totalMinutes.addAndGet(snapshot.getTotalMinutes());
      this.monthlyMinutes.addAndGet(snapshot.getMonthlyMinutes());
      this.weeklyMinutes.addAndGet(snapshot.getWeeklyMinutes());
      this.dailyMinutes.addAndGet(snapshot.getDailyMinutes());
      this.deaths.addAndGet(snapshot.getDeaths());
      this.playerKills.addAndGet(snapshot.getPlayerKills());
      this.mobKills.addAndGet(snapshot.getMobKills());
      this.blocksBroken.addAndGet(snapshot.getBlocksBroken());
      this.blocksPlaced.addAndGet(snapshot.getBlocksPlaced());
      this.itemsCrafted.addAndGet(snapshot.getItemsCrafted());
      this.distWalkCenti.addAndGet(snapshot.getDistWalkCenti());
      this.distSwimCenti.addAndGet(snapshot.getDistSwimCenti());
      this.distFlyCenti.addAndGet(snapshot.getDistFlyCenti());
      this.chatMessages.addAndGet(snapshot.getChatMessages());
      this.achievements.set(Math.max(this.achievements.get(), snapshot.getAchievements()));
      if (snapshot.getLastJoin() > this.lastJoin.get()) {
         this.lastJoin.set(snapshot.getLastJoin());
      }
      if (snapshot.getFirstJoin() > 0L && this.firstJoin.get() == 0L) {
         this.firstJoin.set(snapshot.getFirstJoin());
      }
   }

   private void refreshPeriods(long nowMillis) {
      LocalDate date = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).toLocalDate();
      int dayKey = date.getYear() * 10000 + date.getMonthValue() * 100 + date.getDayOfMonth();
      int monthKey = date.getYear() * 100 + date.getMonthValue();
      int weekKey = date.getYear() * 100 + date.get(WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear());

      if (this.lastDayKey == 0) {
         this.lastDayKey = dayKey;
      }
      if (dayKey != this.lastDayKey) {
         this.dailyMinutes.set(0L);
         this.lastDayKey = dayKey;
      }

      long storedMonth = this.lastMonth.get();
      if (storedMonth == 0L) {
         this.lastMonth.set(monthKey);
      } else if (storedMonth != monthKey) {
         this.monthlyMinutes.set(0L);
         this.lastMonth.set(monthKey);
      }

      long storedWeek = this.lastWeek.get();
      if (storedWeek == 0L) {
         this.lastWeek.set(weekKey);
      } else if (storedWeek != weekKey) {
         this.weeklyMinutes.set(0L);
         this.lastWeek.set(weekKey);
      }
   }

   private int toDayKey(long epochSeconds) {
      LocalDate date = Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).toLocalDate();
      return date.getYear() * 10000 + date.getMonthValue() * 100 + date.getDayOfMonth();
   }
}
