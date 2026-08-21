package net.schalker.SMPS.modules.trafficoptimizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.module.BaseModule;
import net.schalker.DoAPI.core.module.ModuleInfo;
import net.schalker.SMPS.modules.trafficoptimizer.commands.NetOptCommand;
import net.schalker.SMPS.modules.trafficoptimizer.listeners.NetworkListener;
import net.schalker.SMPS.modules.trafficoptimizer.transport.ParticleFilterManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class TrafficOptimizerModule extends BaseModule {

   public static final String PERMISSION_ADMIN = "smnetopt.admin";
   public static final String PERMISSION_EXEMPT = "smnetopt.exempt";

   private static final String SWEEP_TASK = "netopt-sweep";

   private FileConfiguration config;
   private FileConfiguration messages;
   private NetworkListener listener;
   private QuietBanBridge quietBanBridge;
   private ParticleFilterManager particleFilter;

   private final Map<UUID, PlayerNetworkState> states = new ConcurrentHashMap<>();
   private final AtomicInteger restoreBudget = new AtomicInteger();

   private volatile boolean active = true;
   private volatile long checkIntervalTicks = 40L;
   private volatile double smoothing = 0.3D;
   private volatile int samplesBeforeActing = 5;
   private volatile int thresholdLight = 200;
   private volatile int thresholdMedium = 350;
   private volatile int thresholdAggressive = 550;
   private volatile int restoreBelow = 120;
   private volatile int hysteresisMargin = 50;
   private volatile int serverWideGuard = 300;
   private volatile boolean guardActive;
   private volatile long restoreStableMillis = 30000L;
   private volatile long changeCooldownMillis = 8000L;
   private volatile int minViewDistance = 3;
   private volatile int minSimulationDistance = 3;
   private volatile int minSendDistance = 3;
   private volatile int maxRestoresPerCycle = 2;
   private volatile boolean skipQuietBanned = true;
   private volatile boolean skipWhenStatusUnknown = true;
   private volatile boolean notifyPlayer;
   private volatile boolean particlesEnabled = true;
   private volatile double particleDropLight = 0.5D;
   private volatile double particleDropMedium = 0.8D;
   private volatile double particleDropAggressive = 1.0D;

   public TrafficOptimizerModule(DoAPI plugin) {
      super(plugin, new ModuleInfo("SM_TrafficOptimizer", "1.0.0", "SchalkerMC",
         "Адаптивная оптимизация трафика по пингу игрока"));
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.reloadConfigs();
      this.quietBanBridge = new QuietBanBridge(this.plugin);
      this.particleFilter = new ParticleFilterManager(this.plugin);
      this.particleFilter.setMarkers(this.config.getStringList("particles.packet-markers"));

      this.listener = new NetworkListener(this.plugin, this);
      this.plugin.getListenerManager().registerListener(this.listener);

      this.plugin.getCommandManager().registerModuleCommand(new NetOptCommand(this.plugin, this));

      long interval = Math.max(20L, this.checkIntervalTicks);
      this.plugin.getSchedulerManager().runAsyncTimer(SWEEP_TASK, this::sweep, interval, interval);

      this.plugin.getDebugSystem().log("TrafficOptimizer", "Модуль включен, интервал " + interval + " тиков");
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.plugin.getSchedulerManager().cancelTask(SWEEP_TASK);

      if (this.listener != null) {
         this.plugin.getListenerManager().unregisterListener(this.listener);
         this.listener = null;
      }

      for (Player player : this.plugin.getServer().getOnlinePlayers()) {
         PlayerNetworkState state = this.states.get(player.getUniqueId());
         if (state != null && state.isBaselineCaptured() && state.getLevel() != OptimizationLevel.NONE) {
            this.restoreBaseline(player, state);
         }
      }
      if (this.particleFilter != null) {
         this.particleFilter.detachAll();
         this.particleFilter = null;
      }
      this.states.clear();
      this.plugin.getDebugSystem().log("TrafficOptimizer", "Модуль выключен");
   }

   @Override
   public void reload() {
      super.reload();
      this.reloadConfigs();
      if (this.quietBanBridge != null) {
         this.quietBanBridge.invalidate();
      }
      if (this.particleFilter != null) {
         this.particleFilter.setMarkers(this.config.getStringList("particles.packet-markers"));
      }
   }

   public ParticleFilterManager getParticleFilter() {
      return this.particleFilter;
   }

   private double particleDropFor(OptimizationLevel level) {
      if (!this.particlesEnabled) {
         return 0.0D;
      }
      return switch (level) {
         case AGGRESSIVE -> this.particleDropAggressive;
         case MEDIUM -> this.particleDropMedium;
         case LIGHT -> this.particleDropLight;
         case NONE -> 0.0D;
      };
   }

   public boolean isActive() {
      return this.active;
   }

   public void setActive(boolean active) {
      this.active = active;
      if (active) {
         return;
      }
      for (Player player : this.plugin.getServer().getOnlinePlayers()) {
         PlayerNetworkState state = this.states.get(player.getUniqueId());
         if (state != null && state.getLevel() != OptimizationLevel.NONE) {
            this.restoreBaseline(player, state);
         }
      }
   }

   public Map<UUID, PlayerNetworkState> getStates() {
      return this.states;
   }

   public PlayerNetworkState getState(UUID playerId) {
      return this.states.get(playerId);
   }

   public void handleJoin(Player player) {
      this.states.put(player.getUniqueId(), new PlayerNetworkState(player.getUniqueId(), System.currentTimeMillis()));
   }

   public void handleQuit(UUID playerId) {
      this.states.remove(playerId);
      if (this.particleFilter != null) {
         this.particleFilter.detach(playerId);
      }
   }

   public void resetPlayer(Player player) {
      PlayerNetworkState state = this.states.get(player.getUniqueId());
      if (state == null) {
         return;
      }
      if (state.isBaselineCaptured() && state.getLevel() != OptimizationLevel.NONE) {
         this.restoreBaseline(player, state);
      }
      this.states.put(player.getUniqueId(), new PlayerNetworkState(player.getUniqueId(), System.currentTimeMillis()));
   }

   private void sweep() {
      if (!this.active || !this.isEnabled()) {
         return;
      }

      if (this.serverWideGuard > 0 && this.isServerWideProblem()) {
         if (!this.guardActive) {
            this.guardActive = true;
            this.plugin.getDebugSystem().logWarning("TrafficOptimizer",
               "Пинг поднялся у большинства игроков — похоже на проблему сервера, а не каналов. "
                  + "Оптимизация приостановлена.");
         }
         return;
      }
      if (this.guardActive) {
         this.guardActive = false;
         this.plugin.getDebugSystem().log("TrafficOptimizer", "Общий пинг нормализовался, оптимизация возобновлена");
      }

      this.restoreBudget.set(Math.max(0, this.maxRestoresPerCycle));

      for (Player player : this.plugin.getServer().getOnlinePlayers()) {
         UUID playerId = player.getUniqueId();
         this.plugin.getSchedulerManager().runEntityTask(player, "netopt-eval-" + playerId, () -> {
            if (player.isOnline()) {
               this.evaluate(player);
            }
         });
      }
   }

   private void evaluate(Player player) {
      PlayerNetworkState state = this.states.computeIfAbsent(player.getUniqueId(),
         id -> new PlayerNetworkState(id, System.currentTimeMillis()));

      if (!state.isBaselineCaptured() && state.getLevel() == OptimizationLevel.NONE) {
         state.captureBaseline(player.getViewDistance(), player.getSimulationDistance(),
            player.getSendViewDistance());
      }

      long now = System.currentTimeMillis();
      double ping = state.recordPing(Math.max(0, player.getPing()), this.smoothing);

      if (player.hasPermission(PERMISSION_EXEMPT)) {
         this.releaseIfNeeded(player, state, "exempt");
         return;
      }

      if (this.skipQuietBanned) {
         QuietBanBridge.Status status = this.quietBanBridge.check(player);
         if (status == QuietBanBridge.Status.QUIET_BANNED) {
            this.releaseIfNeeded(player, state, "quietban");
            return;
         }
         if (status == QuietBanBridge.Status.UNKNOWN && this.skipWhenStatusUnknown) {
            this.releaseIfNeeded(player, state, "quietban-unknown");
            return;
         }
      }

      state.clearSkipped();

      if (state.getSamples() < this.samplesBeforeActing) {
         return;
      }

      OptimizationLevel current = state.getLevel();
      OptimizationLevel target = this.levelFor(ping);

      if (target.isHeavierThan(current)) {
         state.setGoodSince(0L);
         this.applyLevel(player, state, target, now);
         return;
      }

      if (current == OptimizationLevel.NONE) {
         state.setGoodSince(0L);
         return;
      }

      if (ping <= this.restoreThresholdFor(current)) {
         if (state.getGoodSince() == 0L) {
            state.setGoodSince(now);
         }
      } else {
         state.setGoodSince(0L);
      }

      if (now - state.getLastChangeAt() < this.changeCooldownMillis) {
         return;
      }
      if (state.getGoodSince() == 0L || now - state.getGoodSince() < this.restoreStableMillis) {
         return;
      }
      if (this.restoreBudget.getAndDecrement() <= 0) {
         return;
      }

      this.applyLevel(player, state, current.oneStepLighter(), now);
      state.setGoodSince(now);
   }

   private int restoreThresholdFor(OptimizationLevel level) {
      return switch (level) {
         case AGGRESSIVE -> Math.max(this.thresholdMedium, this.thresholdAggressive - this.hysteresisMargin);
         case MEDIUM -> Math.max(this.thresholdLight, this.thresholdMedium - this.hysteresisMargin);
         case LIGHT, NONE -> this.restoreBelow;
      };
   }

   private boolean isServerWideProblem() {
      List<Double> pings = new ArrayList<>();
      for (PlayerNetworkState state : this.states.values()) {
         if (state.getSamples() >= this.samplesBeforeActing && state.getSmoothedPing() >= 0.0D) {
            pings.add(state.getSmoothedPing());
         }
      }
      if (pings.size() < 4) {
         return false;
      }
      pings.sort(null);
      double median = pings.get(pings.size() / 2);
      return median >= this.serverWideGuard;
   }

   public boolean isGuardActive() {
      return this.guardActive;
   }

   private OptimizationLevel levelFor(double ping) {
      if (ping >= this.thresholdAggressive) {
         return OptimizationLevel.AGGRESSIVE;
      }
      if (ping >= this.thresholdMedium) {
         return OptimizationLevel.MEDIUM;
      }
      if (ping >= this.thresholdLight) {
         return OptimizationLevel.LIGHT;
      }
      return OptimizationLevel.NONE;
   }

   private void releaseIfNeeded(Player player, PlayerNetworkState state, String reason) {
      state.markSkipped(reason);
      if (state.isBaselineCaptured() && state.getLevel() != OptimizationLevel.NONE) {
         this.restoreBaseline(player, state);
      }
   }

   private void applyLevel(Player player, PlayerNetworkState state, OptimizationLevel level, long now) {
      if (!state.isBaselineCaptured()) {
         return;
      }

      int view = clampDown(state.getBaselineView(), level.getViewReduction(), this.minViewDistance);
      int simulation = clampDown(state.getBaselineSimulation(), level.getSimulationReduction(),
         this.minSimulationDistance);
      int send = clampDown(state.getBaselineSend(), level.getSendReduction(), this.minSendDistance);

      this.plugin.getSchedulerManager().runEntityTask(player, "netopt-apply-" + player.getUniqueId(), () -> {
         if (!player.isOnline()) {
            return;
         }
         applyDistances(player, view, simulation, send);
      });

      if (this.particleFilter != null) {
         this.particleFilter.apply(player, this.particleDropFor(level));
      }

      OptimizationLevel previous = state.getLevel();
      state.setLevel(level, now);

      if (this.notifyPlayer && level.isHeavierThan(previous) && level == OptimizationLevel.AGGRESSIVE) {
         String message = this.getMessage("connection-degraded",
            "&[SECONDARY]Соединение нестабильно — дальность прорисовки временно снижена.");
         this.plugin.getSchedulerManager().runEntityTask(player, "netopt-notify-" + player.getUniqueId(), () -> {
            if (player.isOnline()) {
               player.sendMessage(message);
            }
         });
      }

      this.plugin.getDebugSystem().log("TrafficOptimizer", player.getName() + ": " + previous + " -> " + level
         + " (ping " + Math.round(state.getSmoothedPing()) + "ms, view " + view + ")");
   }

   private void restoreBaseline(Player player, PlayerNetworkState state) {
      int view = state.getBaselineView();
      int simulation = state.getBaselineSimulation();
      int send = state.getBaselineSend();

      this.plugin.getSchedulerManager().runEntityTask(player, "netopt-restore-" + player.getUniqueId(), () -> {
         if (!player.isOnline()) {
            return;
         }
         applyDistances(player, view, simulation, send);
      });
      if (this.particleFilter != null) {
         this.particleFilter.detach(player.getUniqueId());
      }
      state.setLevel(OptimizationLevel.NONE, System.currentTimeMillis());
   }

   private static void applyDistances(Player player, int view, int simulation, int send) {
      if (player.getViewDistance() != view) {
         player.setViewDistance(view);
      }
      if (player.getSimulationDistance() != simulation) {
         player.setSimulationDistance(simulation);
      }
      if (player.getSendViewDistance() != send) {
         player.setSendViewDistance(send);
      }
   }

   private static int clampDown(int baseline, int reduction, int floor) {
      if (baseline <= 0) {
         return baseline;
      }
      int limit = Math.min(floor, baseline);
      return Math.max(limit, baseline - reduction);
   }

   private void reloadConfigs() {
      this.config = this.plugin.getModuleManager().loadModuleConfig("SM_TrafficOptimizer");
      this.messages = this.plugin.getModuleManager().loadModuleConfig("SM_TrafficOptimizer", "messages.yml");
      if (this.config == null) {
         this.config = new YamlConfiguration();
      }
      if (this.messages == null) {
         this.messages = new YamlConfiguration();
      }

      this.active = this.config.getBoolean("enabled", true);
      this.checkIntervalTicks = this.config.getLong("check-interval-ticks", 40L);
      this.smoothing = clampDouble(this.config.getDouble("ping.smoothing", 0.3D), 0.05D, 1.0D);
      this.samplesBeforeActing = Math.max(1, this.config.getInt("ping.samples-before-acting", 5));
      this.thresholdLight = this.config.getInt("ping.thresholds.light", 200);
      this.thresholdMedium = this.config.getInt("ping.thresholds.medium", 350);
      this.thresholdAggressive = this.config.getInt("ping.thresholds.aggressive", 550);
      this.restoreBelow = this.config.getInt("ping.restore-below", 120);
      this.hysteresisMargin = Math.max(10, this.config.getInt("ping.hysteresis-margin", 50));
      this.serverWideGuard = this.config.getInt("ping.server-wide-guard", 300);
      this.restoreStableMillis = Math.max(5000L, this.config.getLong("ping.restore-stable-seconds", 30L) * 1000L);
      this.changeCooldownMillis = Math.max(1000L, this.config.getLong("limits.change-cooldown-seconds", 8L) * 1000L);
      this.minViewDistance = Math.max(2, this.config.getInt("limits.min-view-distance", 3));
      this.minSimulationDistance = Math.max(2, this.config.getInt("limits.min-simulation-distance", 3));
      this.minSendDistance = Math.max(2, this.config.getInt("limits.min-send-distance", 3));
      this.maxRestoresPerCycle = Math.max(1, this.config.getInt("limits.max-restores-per-cycle", 2));
      this.skipQuietBanned = this.config.getBoolean("quiet-ban.skip-quiet-banned", true);
      this.skipWhenStatusUnknown = this.config.getBoolean("quiet-ban.skip-when-status-unknown", true);
      this.notifyPlayer = this.config.getBoolean("notify-player", false);
      this.particlesEnabled = this.config.getBoolean("particles.enabled", true);
      this.particleDropLight = clampDouble(this.config.getDouble("particles.drop-chance.light", 0.5D), 0.0D, 1.0D);
      this.particleDropMedium = clampDouble(this.config.getDouble("particles.drop-chance.medium", 0.8D), 0.0D, 1.0D);
      this.particleDropAggressive =
         clampDouble(this.config.getDouble("particles.drop-chance.aggressive", 1.0D), 0.0D, 1.0D);

      if (this.thresholdMedium <= this.thresholdLight) {
         this.thresholdMedium = this.thresholdLight + 100;
      }
      if (this.thresholdAggressive <= this.thresholdMedium) {
         this.thresholdAggressive = this.thresholdMedium + 100;
      }
      if (this.restoreBelow >= this.thresholdLight) {
         this.restoreBelow = Math.max(0, this.thresholdLight - 50);
      }
   }

   private static double clampDouble(double value, double min, double max) {
      return Math.min(max, Math.max(min, value));
   }

   public FileConfiguration getConfig() {
      return this.config;
   }

   public String getMessage(String key, String fallback) {
      String message = this.messages != null ? this.messages.getString(key) : null;
      if (message == null) {
         message = fallback;
      }
      if (message == null || message.isEmpty()) {
         return "";
      }
      return this.plugin.applyColors(message);
   }

   public List<PlayerNetworkState> getDegradedStates() {
      List<PlayerNetworkState> result = new ArrayList<>();
      for (PlayerNetworkState state : this.states.values()) {
         if (state.getLevel() != OptimizationLevel.NONE) {
            result.add(state);
         }
      }
      return result;
   }
}
