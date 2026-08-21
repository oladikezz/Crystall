package net.schalker.SMPS.modules.stats.managers;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.schalker.DoAPI.DoAPI;
import net.schalker.SMPS.modules.stats.StatsData;
import net.schalker.SMPS.modules.stats.StatsDatabase;
import net.schalker.SMPS.modules.stats.StatsSnapshot;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class StatsManager {
   private final DoAPI plugin;
   private final StatsDatabase database;
   private final Map<UUID, StatsData> dataMap = new ConcurrentHashMap<>();
   private final long moveIntervalMillis;

   public StatsManager(DoAPI plugin, StatsDatabase database, long moveIntervalMillis) {
      this.plugin = plugin;
      this.database = database;
      this.moveIntervalMillis = moveIntervalMillis;
   }

   public StatsData getData(UUID uuid) {
      return this.dataMap.get(uuid);
   }

   public StatsSnapshot getSnapshot(UUID uuid) {
      StatsData data = this.dataMap.get(uuid);
      if (data == null) {
         return null;
      }
      long now = System.currentTimeMillis();
      data.updatePlaytime(now);
      return data.snapshot();
   }

   public void handleJoin(Player player) {
      if (player == null) {
         return;
      }
      UUID uuid = player.getUniqueId();
      String name = player.getName();
      long now = System.currentTimeMillis();

      StatsData data = this.dataMap.computeIfAbsent(uuid, id -> new StatsData(id, name));
      data.setName(name);
      data.markLogin(now);
      data.recordMove(player, player.getLocation(), player.getLocation(), now, 0L);

      this.plugin.getSchedulerManager().runAsync("stats-load-" + uuid, () -> {
         StatsSnapshot snapshot = this.database.loadByUuid(uuid);
         if (snapshot == null) {
            this.database.setOnline(uuid, true);
            return;
         }
         StatsSnapshot delta = data.snapshot();
         data.loadFromSnapshot(snapshot);
         data.addSnapshot(delta);
         data.setName(name);
         data.markLogin(System.currentTimeMillis());
         this.database.setOnline(uuid, true);
      });
   }

   public void handleQuit(Player player) {
      if (player == null) {
         return;
      }
      UUID uuid = player.getUniqueId();
      StatsData data = this.dataMap.get(uuid);
      if (data == null) {
         return;
      }
      long now = System.currentTimeMillis();
      data.updatePlaytime(now);
      StatsSnapshot snapshot = data.snapshot();
      this.plugin.getSchedulerManager().runAsync("stats-save-quit-" + uuid, () -> {
         this.database.saveSnapshot(snapshot);
         this.database.setOnline(uuid, false);
      });
      this.dataMap.remove(uuid);
   }

   public void saveAllAsync() {
      long now = System.currentTimeMillis();
      for (StatsData data : this.dataMap.values()) {
         data.updatePlaytime(now);
         StatsSnapshot snapshot = data.snapshot();
         this.plugin.getSchedulerManager().runAsync("stats-save-" + data.getUuid(), () -> {
            this.database.saveSnapshot(snapshot);
         });
      }
   }

   public void saveAllSync() {
      long now = System.currentTimeMillis();
      for (StatsData data : this.dataMap.values()) {
         data.updatePlaytime(now);
         StatsSnapshot snapshot = data.snapshot();
         this.database.saveSnapshot(snapshot);
      }
   }

   public void clearAll() {
      this.dataMap.clear();
   }

   public void resetData(UUID uuid, String name) {
      if (uuid == null) {
         return;
      }
      StatsData data = new StatsData(uuid, name);
      long now = System.currentTimeMillis();
      data.markLogin(now);
      this.dataMap.put(uuid, data);
   }

   public void recordMove(Player player, Location from, Location to) {
      StatsData data = this.dataMap.get(player.getUniqueId());
      if (data == null) {
         return;
      }
      data.recordMove(player, from, to, System.currentTimeMillis(), this.moveIntervalMillis);
   }

   public void incrementBlocksBroken(Player player) {
      StatsData data = this.dataMap.get(player.getUniqueId());
      if (data != null) {
         data.incrementBlocksBroken();
      }
   }

   public void incrementBlocksPlaced(Player player) {
      StatsData data = this.dataMap.get(player.getUniqueId());
      if (data != null) {
         data.incrementBlocksPlaced();
      }
   }

   public void incrementDeaths(Player player) {
      StatsData data = this.dataMap.get(player.getUniqueId());
      if (data != null) {
         data.incrementDeaths();
      }
   }

   public void incrementPlayerKills(Player killer) {
      StatsData data = this.dataMap.get(killer.getUniqueId());
      if (data != null) {
         data.incrementPlayerKills();
      }
   }

   public void incrementMobKills(Player killer) {
      StatsData data = this.dataMap.get(killer.getUniqueId());
      if (data != null) {
         data.incrementMobKills();
      }
   }

   public void incrementItemsCrafted(Player player, long amount) {
      StatsData data = this.dataMap.get(player.getUniqueId());
      if (data != null) {
         data.incrementItemsCrafted(amount);
      }
   }

   public void incrementChat(Player player) {
      StatsData data = this.dataMap.get(player.getUniqueId());
      if (data != null) {
         data.incrementChatMessages();
      }
   }

   public void setAchievements(Player player, long count) {
      if (player == null) {
         return;
      }
      StatsData data = this.dataMap.get(player.getUniqueId());
      if (data != null) {
         data.setAchievements(Math.max(0L, count));
      }
   }
}
