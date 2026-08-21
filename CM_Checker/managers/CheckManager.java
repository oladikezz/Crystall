package net.schalker.SMPS.modules.checker.managers;

import java.time.Duration;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import net.schalker.DoAPI.DoAPI;
import net.schalker.SMPS.modules.checker.CheckerModule;

public class CheckManager {
   private static final String BAN_STATUS_DEFAULT = "default";
   private static final String BAN_STATUS_FAILED = "failed";
   private static final String BAN_STATUS_DENIED = "denied";
   private static final String BAN_STATUS_QUIT = "quit";

   private static final String[] RANDOM_EFFECTS = new String[] {
      "default",
      "scale",
      "rocket",
      "smile",
      "cage",
      "storm",
      "chain",
      "laser",
      "blackhole",
      "ring",
      "freeze",
      "portal",
      "thorns",
      "pulse"
   };
   private final DoAPI plugin;
   private final CheckerModule module;
   private final Map<UUID, CheckSession> sessions = new ConcurrentHashMap<>();
   private final Map<UUID, UUID> staffLastTarget = new ConcurrentHashMap<>();

   public CheckManager(DoAPI plugin, CheckerModule module) {
      this.plugin = plugin;
      this.module = module;
   }

   public boolean isChecking(UUID playerId) {
      return this.sessions.containsKey(playerId);
   }

   public CheckSession getSession(UUID playerId) {
      return this.sessions.get(playerId);
   }

   public CheckSession getSessionByName(String playerName) {
      for (CheckSession session : this.sessions.values()) {
         if (session.getTargetName().equalsIgnoreCase(playerName)) {
            return session;
         }
      }

      return null;
   }

   public void startCheck(CommandSender staff, Player target) {
      if (this.isChecking(target.getUniqueId())) {
         this.sendMessage(staff, this.module.getMessage("already-checking").replace("{player}", target.getName()));
         return;
      }

      UUID staffId = staff instanceof Player ? ((Player)staff).getUniqueId() : null;
      String staffName = staff.getName();
      CheckSession session = new CheckSession(target.getUniqueId(), target.getName(), staffId, staffName);
      this.sessions.put(target.getUniqueId(), session);
      if (staffId != null) {
         this.staffLastTarget.put(staffId, target.getUniqueId());
      }

      this.plugin.getSchedulerManager().runEntityTask(target, "checker-capture", () -> {
         session.captureState(target);
      });

      this.applyFreeze(target);
      this.applyBlindness(target);
      this.sendTitle(target);
      this.sendMessage(target, this.module.getMessage("check-message"));
      this.sendMessage(staff, this.module.getMessage("check-started").replace("{player}", target.getName()));
      this.startActionbar(target);
      this.startChatReminder(target);
   }

   public void confirmCheck(CommandSender sender, UUID targetId) {
      this.confirmCheck(sender, targetId, null);
   }

   public void confirmCheck(CommandSender sender, UUID targetId, String reasonOverride) {
      CheckSession session = this.sessions.get(targetId);
      if (session == null) {
         this.sendMessage(sender, this.module.getMessage("not-checking"));
         return;
      }

      Player target = Bukkit.getPlayer(targetId);
      if (target != null) {
         this.removeBlindness(target);
         this.restoreState(target, session);
         this.sendMessage(target, this.module.getMessage("check-finished"));
         this.playConfirmEffects(target);
      }

      this.sendMessage(sender, this.module.getMessage("check-finished-staff").replace("{player}", session.getTargetName()));
      this.stopTasks(session);
      this.sessions.remove(targetId);
   }

   public void failCheck(CommandSender sender, UUID targetId, String reasonOverride) {
      CheckSession session = this.sessions.get(targetId);
      if (session == null) {
         this.sendMessage(sender, this.module.getMessage("not-checking"));
         return;
      }

      Player target = Bukkit.getPlayer(targetId);
      this.stopTasks(session);
      if (target != null) {
         this.removeBlindness(target);
         this.restoreState(target, session);
         this.sendMessage(target, this.module.getMessage("check-failed-target"));
      }

      session.setBanStatus(BAN_STATUS_FAILED);
      session.setBanReasonOverride(this.normalizeReason(reasonOverride));
      this.executeBan(session.getTargetName(), sender.getName());
      this.sessions.remove(targetId);
      this.sendMessage(sender, this.module.getMessage("check-failed-staff").replace("{player}", session.getTargetName()));
   }

   public void denyCheck(CommandSender sender, UUID targetId, String reasonOverride) {
      CheckSession session = this.sessions.get(targetId);
      if (session == null) {
         this.sendMessage(sender, this.module.getMessage("not-checking"));
         return;
      }

      session.setBanStatus(BAN_STATUS_DENIED);
      session.setBanReasonOverride(this.normalizeReason(reasonOverride));

      Player target = Bukkit.getPlayer(targetId);
      this.stopTasks(session);
      if (target != null) {
         this.removeBlindness(target);
         String effect = this.resolveDenyEffect(null);
         if ("scale".equalsIgnoreCase(effect)) {
            this.startScaleDenySequence(target, session, sender.getName());
         } else if ("rocket".equalsIgnoreCase(effect)) {
            this.startRocketDenySequence(target, session, sender.getName());
         } else if ("smile".equalsIgnoreCase(effect)) {
            this.startSmileDenySequence(target, session, sender.getName());
         } else if ("cage".equalsIgnoreCase(effect)) {
            this.startCageDenySequence(target, session, sender.getName());
         } else if ("storm".equalsIgnoreCase(effect)) {
            this.startStormDenySequence(target, session, sender.getName());
         } else if ("chain".equalsIgnoreCase(effect)) {
            this.startChainDenySequence(target, session, sender.getName());
         } else if ("laser".equalsIgnoreCase(effect)) {
            this.startLaserDenySequence(target, session, sender.getName());
         } else if ("blackhole".equalsIgnoreCase(effect)) {
            this.startBlackholeDenySequence(target, session, sender.getName());
         } else if ("ring".equalsIgnoreCase(effect)) {
            this.startRingDenySequence(target, session, sender.getName());
         } else if ("freeze".equalsIgnoreCase(effect)) {
            this.startFreezeDenySequence(target, session, sender.getName());
         } else if ("portal".equalsIgnoreCase(effect)) {
            this.startPortalDenySequence(target, session, sender.getName());
         } else if ("thorns".equalsIgnoreCase(effect)) {
            this.startThornsDenySequence(target, session, sender.getName());
         } else if ("pulse".equalsIgnoreCase(effect)) {
            this.startPulseDenySequence(target, session, sender.getName());
         } else {
            this.startDenySequence(target, session, sender.getName());
         }
      } else {
         this.executeBan(session.getTargetName(), sender.getName());
         this.sessions.remove(targetId);
      }
      this.sendMessage(sender, this.module.getMessage("check-denied").replace("{player}", session.getTargetName()));
   }

   public void handleQuit(Player player) {
      CheckSession session = this.sessions.get(player.getUniqueId());
      if (session != null) {
         this.removeBlindness(player);
         this.resetPlayerState(player);
         this.setScale(player, 1.0);
         session.setBanStatus(BAN_STATUS_QUIT);
         this.executeBan(player.getName(), session.getStaffName());
         this.stopTasks(session);
         this.sessions.remove(player.getUniqueId());
      }
   }

   public UUID getLastTarget(UUID staffId) {
      return this.staffLastTarget.get(staffId);
   }

   /**
    * Очищает данные стаффа при выходе
    */
   public void clearStaffData(UUID staffId) {
      this.staffLastTarget.remove(staffId);
   }

   public void clearAll() {
      for (CheckSession session : this.sessions.values()) {
         this.stopTasks(session);
      }

      this.sessions.clear();
      this.staffLastTarget.clear();
   }

   private void applyFreeze(Player target) {
      if (!this.module.isFeatureEnabled("freeze", true)) {
         return;
      }
      this.plugin.getSchedulerManager().runEntityTask(target, "checker-freeze", () -> {
         target.setWalkSpeed(0.0F);
         target.setFlySpeed(0.0F);
         target.setSprinting(false);
         target.setSneaking(false);
      });
   }

   private void applyBlindness(Player target) {
      if (!this.module.isFeatureEnabled("blindness", true)) {
         return;
      }
      this.plugin.getSchedulerManager().runEntityTask(target, "checker-blindness", () -> {
         PotionEffect effect = new PotionEffect(PotionEffectType.BLINDNESS, PotionEffect.INFINITE_DURATION, 0, false, false, false);
         target.addPotionEffect(effect, true);
      });
   }

   private void removeBlindness(Player target) {
      this.plugin.getSchedulerManager().runEntityTask(target, "checker-blindness-remove", () -> {
         target.removePotionEffect(PotionEffectType.BLINDNESS);
      });
   }

   private void playConfirmEffects(Player target) {
      this.plugin.getSchedulerManager().runEntityTask(target, "checker-confirm-effects-" + target.getUniqueId(), () -> {
         Location location = target.getLocation();
         this.playSoundAll(location, Sound.ENTITY_PLAYER_LEVELUP, 0.4F, 1.5F);
         if (this.module.isFeatureEnabled("confirm-sphere", true)) {
            this.spawnConfirmSphere(location);
         }
      });
   }

   private void restoreState(Player target, CheckSession session) {
      if (!this.module.isFeatureEnabled("freeze", true)) {
         return;
      }
      this.plugin.getSchedulerManager().runEntityTask(target, "checker-restore", () -> {
         target.setWalkSpeed(session.getWalkSpeed());
         target.setFlySpeed(session.getFlySpeed());
      });
   }

   public void resetPlayerState(Player target) {
      if (!this.module.isFeatureEnabled("freeze", true)) {
         return;
      }
      this.plugin.getSchedulerManager().runEntityTask(target, "checker-reset", () -> {
         target.setWalkSpeed(0.2F);
         target.setFlySpeed(0.1F);
         target.setSprinting(false);
      });
   }

   private void startActionbar(Player target) {
      if (!this.module.isFeatureEnabled("actionbar", true)) {
         return;
      }
      FileConfiguration config = this.module.getConfig();
      long interval = config.getLong("settings.actionbar-interval-ticks", 40L);
      if (interval <= 0L) {
         return;
      }

      String taskName = "checker-actionbar-" + target.getUniqueId();
      CheckSession session = this.sessions.get(target.getUniqueId());
      if (session != null) {
         session.setActionbarTask(taskName);
      }

      long delay = Math.max(1L, interval);
      this.plugin.getSchedulerManager().runEntityTaskTimer(target, taskName, () -> {
         if (!target.isOnline() || !this.isChecking(target.getUniqueId())) {
            this.plugin.getSchedulerManager().cancelTask(taskName);
            return;
         }

         target.sendActionBar(this.deserialize(this.module.getMessage("actionbar")));
      }, delay, interval);
   }

   private void startChatReminder(Player target) {
      if (!this.module.isFeatureEnabled("chat-reminder", true)) {
         return;
      }
      FileConfiguration config = this.module.getConfig();
      long interval = config.getLong("settings.chat-interval-ticks", 200L);
      if (interval <= 0L) {
         return;
      }

      String taskName = "checker-chat-" + target.getUniqueId();
      CheckSession session = this.sessions.get(target.getUniqueId());
      if (session != null) {
         session.setChatTask(taskName);
      }

      this.plugin.getSchedulerManager().runEntityTaskTimer(target, taskName, () -> {
         if (!target.isOnline() || !this.isChecking(target.getUniqueId())) {
            this.plugin.getSchedulerManager().cancelTask(taskName);
            return;
         }

         target.sendMessage(this.module.getMessage("check-message"));
      }, interval, interval);
   }

   private void stopTasks(CheckSession session) {
      if (session.getActionbarTask() != null) {
         this.plugin.getSchedulerManager().cancelTask(session.getActionbarTask());
      }

      if (session.getChatTask() != null) {
         this.plugin.getSchedulerManager().cancelTask(session.getChatTask());
      }
   }

   private void sendTitle(Player target) {
      if (!this.module.isFeatureEnabled("title", true)) {
         return;
      }
      FileConfiguration config = this.module.getConfig();
      String title = this.module.getMessage("title");
      String subtitle = this.module.getMessage("subtitle");
      int fadeIn = config.getInt("settings.title.fade-in", 10);
      int stay = config.getInt("settings.title.stay", 60);
      int fadeOut = config.getInt("settings.title.fade-out", 10);
      this.plugin.getSchedulerManager().runEntityTask(target, "checker-title", () -> {
         target.showTitle(Title.title(
            this.deserialize(title),
            this.deserialize(subtitle),
            Title.Times.times(Duration.ofMillis(fadeIn * 50L), Duration.ofMillis(stay * 50L), Duration.ofMillis(fadeOut * 50L))
         ));
      });
   }

   private void executeBan(String playerName, String staffName) {
      CheckSession session = this.getSessionByName(playerName);
      String reasonOverride = session != null ? session.getBanReasonOverride() : null;
      String status = session != null ? session.getBanStatus() : BAN_STATUS_DEFAULT;
      this.executeBan(playerName, staffName, status, reasonOverride);
   }

   private void executeBan(String playerName, String staffName, String status, String reasonOverride) {
      FileConfiguration config = this.module.getConfig();
      String command = config.getString("ban.command", "ban {player} {duration} {reason}");
      String duration = config.getString("ban.duration", "30d");
      String reason = this.resolveBanReason(config, status, reasonOverride);
      String finalCommand = command
         .replace("{player}", playerName)
         .replace("{duration}", duration)
         .replace("{reason}", reason)
         .replace("{staff}", staffName);
      this.plugin.getSchedulerManager().runTaskLater("checker-ban-" + playerName, () -> {
         Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
      }, 1L);
   }

   private String normalizeReason(String reason) {
      if (reason == null) {
         return null;
      }
      String trimmed = reason.trim();
      return trimmed.isEmpty() ? null : trimmed;
   }

   private String resolveBanReason(FileConfiguration config, String status, String reasonOverride) {
      if (reasonOverride != null) {
         return reasonOverride;
      }

      String fallbackReason = config.getString("ban.reason", "Правило 5.1. Если хотите обжаловать — создайте тикет в поддержку.");
      String normalizedStatus = this.normalizeBanStatus(status);
      String statusReason = config.getString("ban.reasons." + normalizedStatus);

      if (statusReason == null && BAN_STATUS_DENIED.equals(normalizedStatus)) {
         // Backward-compatible alias for servers using "rejected" wording.
         statusReason = config.getString("ban.reasons.rejected");
      }

      if (statusReason == null || statusReason.trim().isEmpty()) {
         return fallbackReason;
      }
      return statusReason;
   }

   private String normalizeBanStatus(String status) {
      if (status == null || status.isBlank()) {
         return BAN_STATUS_DEFAULT;
      }

      String normalized = status.toLowerCase();
      if (normalized.equals("rejected")) {
         return BAN_STATUS_DENIED;
      }

      if (normalized.equals(BAN_STATUS_FAILED) || normalized.equals(BAN_STATUS_DENIED) || normalized.equals(BAN_STATUS_QUIT)) {
         return normalized;
      }
      return BAN_STATUS_DEFAULT;
   }

   private void startDenySequence(Player target, CheckSession session, String staffName) {
      UUID targetId = target.getUniqueId();
      int totalTicks = 40;
      double totalLift = 7.0;
      double step = totalLift / totalTicks;
      String taskName = "checker-deny-rise-" + targetId;
      session.setDenyInProgress(true);

      this.plugin.getSchedulerManager().runEntityTask(target, "checker-deny-start", () -> {
         target.setWalkSpeed(0.0F);
         target.setFlySpeed(0.0F);
         target.setSprinting(false);
         target.setSneaking(false);
         target.setGravity(false);
      });

      int[] ticks = new int[] { 0 };
      this.plugin.getSchedulerManager().runEntityTaskTimer(target, taskName, () -> {
         if (!this.sessions.containsKey(targetId)) {
            this.plugin.getSchedulerManager().cancelTask(taskName);
            return;
         }
         if (!target.isOnline()) {
            this.plugin.getSchedulerManager().cancelTask(taskName);
            this.executeBan(session.getTargetName(), staffName);
            this.sessions.remove(targetId);
            return;
         }

         Location next = target.getLocation().clone().add(0.0, step, 0.0);
         if (ticks[0] == 0) {
            this.playSoundAll(next, Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.5F, 1.0F);
         }
         target.teleportAsync(next);
         this.spawnDenyRings(next);

         ticks[0]++;
         if (ticks[0] >= totalTicks) {
            this.plugin.getSchedulerManager().cancelTask(taskName);
            this.finishDenySequence(target, staffName);
            this.sessions.remove(targetId);
         }
      }, 1L, 1L);
   }

   private void startScaleDenySequence(Player target, CheckSession session, String staffName) {
      UUID targetId = target.getUniqueId();
      int min = this.getConfigInt("scale-effect.min", 1);
      int max = this.getConfigInt("scale-effect.max", 10);
      if (max < min) {
         int swap = min;
         min = max;
         max = swap;
      }
      int durationTicks = this.getConfigInt("scale-effect.duration-ticks", 40);
      if (durationTicks < 1) {
         durationTicks = 1;
      }

      int startScale = min;
      int targetScale = max;
      this.plugin.getSchedulerManager().runEntityTask(target, "checker-deny-scale-set-" + targetId, () -> {
         this.setScale(target, startScale);
      });

      if (targetScale <= startScale) {
         this.finishScaleDenySequence(target, session, staffName);
         return;
      }

      double[] current = new double[] { startScale };
      double stepValue = (targetScale - startScale) / (double) durationTicks;
      if (stepValue <= 0.0) {
         stepValue = 0.01;
      }
      final double startValue = startScale;
      final double targetValue = targetScale;
      final int durationValue = durationTicks;
      final double stepFinal = stepValue;
      int[] ticks = new int[] { 0 };
      String taskName = "checker-deny-scale-" + targetId;
      this.plugin.getSchedulerManager().runEntityTaskTimer(target, taskName, () -> {
         if (!this.sessions.containsKey(targetId)) {
            this.plugin.getSchedulerManager().cancelTask(taskName);
            return;
         }
         if (!target.isOnline()) {
            this.plugin.getSchedulerManager().cancelTask(taskName);
            this.executeBan(session.getTargetName(), staffName);
            this.sessions.remove(targetId);
            return;
         }

         ticks[0]++;
         double next = startValue + (stepFinal * ticks[0]);
         if (next >= targetValue || ticks[0] >= durationValue) {
            this.setScale(target, targetValue);
            this.plugin.getSchedulerManager().cancelTask(taskName);
            this.finishScaleDenySequence(target, session, staffName);
            return;
         }
         current[0] = next;
         this.setScale(target, current[0]);
      }, 1L, 1L);
   }

   private void startRocketDenySequence(Player target, CheckSession session, String staffName) {
      UUID targetId = target.getUniqueId();
      int height = this.getConfigInt("rocket-effect.height", 8);
      int durationTicks = this.getConfigInt("rocket-effect.duration-ticks", 40);
      int intervalTicks = this.getConfigInt("rocket-effect.interval-ticks", 2);
      double radius = this.getConfigDouble("rocket-effect.radius", 1.5);
      int smokeCount = this.getConfigInt("rocket-effect.smoke-count", 12);
      if (height < 1) {
         height = 1;
      }
      if (durationTicks < 1) {
         durationTicks = 1;
      }
      if (intervalTicks < 1) {
         intervalTicks = 1;
      }

      int steps = Math.max(1, durationTicks / intervalTicks);
      double stepValue = height / (double) steps;
      double[] current = new double[] { height };

      String taskName = "checker-deny-rocket-" + targetId;
      this.plugin.getSchedulerManager().runEntityTaskTimer(target, taskName, () -> {
         if (!this.sessions.containsKey(targetId)) {
            this.plugin.getSchedulerManager().cancelTask(taskName);
            return;
         }
         if (!target.isOnline()) {
            this.plugin.getSchedulerManager().cancelTask(taskName);
            this.executeBan(session.getTargetName(), staffName);
            this.sessions.remove(targetId);
            return;
         }

         Location base = target.getLocation();
         double offsetX = (ThreadLocalRandom.current().nextDouble() * 2.0 - 1.0) * radius;
         double offsetZ = (ThreadLocalRandom.current().nextDouble() * 2.0 - 1.0) * radius;
         Location impact = base.clone().add(offsetX, current[0], offsetZ);
         this.spawnRocketExplosion(impact, smokeCount);
         this.playSoundAll(impact, Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 1.0F);

         current[0] -= stepValue;
         if (current[0] <= 0.0) {
            this.plugin.getSchedulerManager().cancelTask(taskName);
            this.finishRocketDenySequence(target, session, staffName);
         }
      }, 1L, intervalTicks);
   }

   private void startSmileDenySequence(Player target, CheckSession session, String staffName) {
      UUID targetId = target.getUniqueId();
      int durationTicks = this.getConfigInt("smile-effect.duration-ticks", 40);
      if (durationTicks < 1) {
         durationTicks = 1;
      }
      double startOffset = this.getConfigDouble("smile-effect.start-offset", 3.0);
      double yOffset = this.getConfigDouble("smile-effect.y-offset", 1.0);
      double cubeSize = this.getConfigDouble("smile-effect.cube-size", 0.6);
      int particleCount = this.getConfigInt("smile-effect.particle-count", 25);
      Material cubeMaterial = this.getConfigMaterial("smile-effect.material", Material.BLACK_CONCRETE);
      BlockData blockData = cubeMaterial.createBlockData();

      final int durationValue = durationTicks;
      final double startOffsetValue = startOffset;
      final double yOffsetValue = yOffset;
      final double cubeSizeValue = cubeSize;
      final int particleCountValue = particleCount;
      final BlockData blockDataValue = blockData;

      int[] ticks = new int[] { 0 };
      String taskName = "checker-deny-smile-" + targetId;
      this.plugin.getSchedulerManager().runEntityTaskTimer(target, taskName, () -> {
         if (!this.sessions.containsKey(targetId)) {
            this.plugin.getSchedulerManager().cancelTask(taskName);
            return;
         }
         if (!target.isOnline()) {
            this.plugin.getSchedulerManager().cancelTask(taskName);
            this.executeBan(session.getTargetName(), staffName);
            this.sessions.remove(targetId);
            return;
         }

         ticks[0]++;
         double progress = Math.min(1.0, ticks[0] / (double) durationValue);
         double distance = startOffsetValue * (1.0 - progress);

         Location base = target.getLocation();
         Vector right = this.getRightVector(base);
         Location left = base.clone().add(right.clone().multiply(-distance)).add(0.0, yOffsetValue, 0.0);
         Location rightLoc = base.clone().add(right.clone().multiply(distance)).add(0.0, yOffsetValue, 0.0);

         this.spawnSmileCube(left, blockDataValue, cubeSizeValue, particleCountValue);
         this.spawnSmileCube(rightLoc, blockDataValue, cubeSizeValue, particleCountValue);

         if (progress >= 1.0) {
            this.playSoundAll(base, Sound.ENTITY_PLAYER_BURP, 1.0F, 1.0F);
            this.plugin.getSchedulerManager().cancelTask(taskName);
            this.finishSmileDenySequence(target, session, staffName);
         }
      }, 1L, 1L);
   }

   private void startCageDenySequence(Player target, CheckSession session, String staffName) {
      UUID targetId = target.getUniqueId();
      int durationTicks = this.getConfigInt("cage-effect.duration-ticks", 40);
      if (durationTicks < 1) {
         durationTicks = 1;
      }
      double startOffset = this.getConfigDouble("cage-effect.start-offset", 3.0);
      double endOffset = this.getConfigDouble("cage-effect.end-offset", 1.0);
      double height = this.getConfigDouble("cage-effect.height", 2.5);
      double step = this.getConfigDouble("cage-effect.step", 0.5);
      if (step <= 0.0) {
         step = 0.5;
      }
      Material material = this.getConfigMaterial("cage-effect.material", Material.IRON_BARS);
      BlockData blockData = material.createBlockData();

      final int durationValue = durationTicks;
      final double startOffsetValue = startOffset;
      final double endOffsetValue = endOffset;
      final double heightValue = height;
      final double stepValue = step;
      final BlockData blockDataValue = blockData;

      int[] ticks = new int[] { 0 };
      String taskName = "checker-deny-cage-" + targetId;
      this.plugin.getSchedulerManager().runEntityTaskTimer(target, taskName, () -> {
         if (!this.sessions.containsKey(targetId)) {
            this.plugin.getSchedulerManager().cancelTask(taskName);
            return;
         }
         if (!target.isOnline()) {
            this.plugin.getSchedulerManager().cancelTask(taskName);
            this.executeBan(session.getTargetName(), staffName);
            this.sessions.remove(targetId);
            return;
         }

         ticks[0]++;
         double progress = Math.min(1.0, ticks[0] / (double) durationValue);
         double offset = startOffsetValue + (endOffsetValue - startOffsetValue) * progress;

         Location base = target.getLocation();
         this.spawnCage(base, blockDataValue, offset, heightValue, stepValue);

         if (progress >= 1.0) {
            this.playSoundAll(base, Sound.BLOCK_IRON_DOOR_CLOSE, 1.0F, 1.0F);
            this.plugin.getSchedulerManager().cancelTask(taskName);
            this.finishCageDenySequence(target, session, staffName);
         }
      }, 1L, 1L);
   }

   private void startStormDenySequence(Player target, CheckSession session, String staffName) {
      UUID targetId = target.getUniqueId();
      int durationTicks = this.getConfigInt("storm-effect.duration-ticks", 60);
      if (durationTicks < 1) {
         durationTicks = 1;
      }
      double radius = this.getConfigDouble("storm-effect.radius", 1.6);
      double height = this.getConfigDouble("storm-effect.height", 3.0);
      int particles = this.getConfigInt("storm-effect.particles", 12);
      if (particles < 4) {
         particles = 4;
      }

      final int durationValue = durationTicks;
      final double radiusValue = radius;
      final double heightValue = height;
      final int particlesValue = particles;
      int[] ticks = new int[] { 0 };

      String taskName = "checker-deny-storm-" + targetId;
      this.plugin.getSchedulerManager().runEntityTaskTimer(target, taskName, () -> {
         if (!this.sessions.containsKey(targetId)) {
            this.plugin.getSchedulerManager().cancelTask(taskName);
            return;
         }
         if (!target.isOnline()) {
            this.plugin.getSchedulerManager().cancelTask(taskName);
            this.executeBan(session.getTargetName(), staffName);
            this.sessions.remove(targetId);
            return;
         }

         ticks[0]++;
         double progress = ticks[0] / (double) durationValue;
         double angle = ticks[0] * 0.35;
         double y = (ticks[0] % 20) / 20.0 * heightValue;
         Location base = target.getLocation();
         for (int i = 0; i < particlesValue; i++) {
            double a = angle + (Math.PI * 2.0) * i / particlesValue;
            double x = Math.cos(a) * radiusValue;
            double z = Math.sin(a) * radiusValue;
            Location loc = base.clone().add(x, y, z);
            base.getWorld().spawnParticle(Particle.SMOKE, loc, 1, 0.02, 0.02, 0.02, 0.0);
         }

         if (progress >= 1.0) {
            this.spawnFlash(base);
            this.plugin.getSchedulerManager().cancelTask(taskName);
            this.finishSimpleDeny(target, session, staffName);
         }
      }, 1L, 1L);
   }

   private void startChainDenySequence(Player target, CheckSession session, String staffName) {
      UUID targetId = target.getUniqueId();
      int durationTicks = this.getConfigInt("chain-effect.duration-ticks", 30);
      if (durationTicks < 1) {
         durationTicks = 1;
      }
      double startDistance = this.getConfigDouble("chain-effect.start-distance", 3.5);
      int points = this.getConfigInt("chain-effect.points", 6);
      if (points < 2) {
         points = 2;
      }

      final int durationValue = durationTicks;
      final double startDistanceValue = startDistance;
      final int pointsValue = points;
      int[] ticks = new int[] { 0 };

      String taskName = "checker-deny-chain-" + targetId;
      this.plugin.getSchedulerManager().runEntityTaskTimer(target, taskName, () -> {
         if (!this.sessions.containsKey(targetId)) {
            this.plugin.getSchedulerManager().cancelTask(taskName);
            return;
         }
         if (!target.isOnline()) {
            this.plugin.getSchedulerManager().cancelTask(taskName);
            this.executeBan(session.getTargetName(), staffName);
            this.sessions.remove(targetId);
            return;
         }

         ticks[0]++;
         double progress = Math.min(1.0, ticks[0] / (double) durationValue);
         double distance = startDistanceValue * (1.0 - progress);
         Location base = target.getLocation();
         this.spawnChainLine(base, new Vector(1, 0, 0), distance, pointsValue);
         this.spawnChainLine(base, new Vector(-1, 0, 0), distance, pointsValue);
         this.spawnChainLine(base, new Vector(0, 0, 1), distance, pointsValue);
         this.spawnChainLine(base, new Vector(0, 0, -1), distance, pointsValue);

         if (progress >= 1.0) {
            this.playSoundAll(base, Sound.BLOCK_CHAIN_HIT, 0.8F, 1.0F);
            this.plugin.getSchedulerManager().cancelTask(taskName);
            this.finishSimpleDeny(target, session, staffName);
         }
      }, 1L, 1L);
   }

   private void startLaserDenySequence(Player target, CheckSession session, String staffName) {
      UUID targetId = target.getUniqueId();
      int durationTicks = this.getConfigInt("laser-effect.duration-ticks", 20);
      if (durationTicks < 1) {
         durationTicks = 1;
      }
      double height = this.getConfigDouble("laser-effect.height", 8.0);
      int points = this.getConfigInt("laser-effect.points", 16);
      if (points < 4) {
         points = 4;
      }

      final int durationValue = durationTicks;
      final double heightValue = height;
      final int pointsValue = points;
      int[] ticks = new int[] { 0 };

      String taskName = "checker-deny-laser-" + targetId;
      this.plugin.getSchedulerManager().runEntityTaskTimer(target, taskName, () -> {
         if (!this.sessions.containsKey(targetId)) {
            this.plugin.getSchedulerManager().cancelTask(taskName);
            return;
         }
         if (!target.isOnline()) {
            this.plugin.getSchedulerManager().cancelTask(taskName);
            this.executeBan(session.getTargetName(), staffName);
            this.sessions.remove(targetId);
            return;
         }

         ticks[0]++;
         Location base = target.getLocation();
         this.spawnLaserBeam(base, heightValue, pointsValue);

         if (ticks[0] >= durationValue) {
            this.playSoundAll(base, Sound.ENTITY_GUARDIAN_ATTACK, 1.0F, 1.2F);
            this.spawnFlash(base);
            this.plugin.getSchedulerManager().cancelTask(taskName);
            this.finishSimpleDeny(target, session, staffName);
         }
      }, 1L, 1L);
   }

   private void startBlackholeDenySequence(Player target, CheckSession session, String staffName) {
      UUID targetId = target.getUniqueId();
      int durationTicks = this.getConfigInt("blackhole-effect.duration-ticks", 40);
      if (durationTicks < 1) {
         durationTicks = 1;
      }
      double startRadius = this.getConfigDouble("blackhole-effect.start-radius", 2.5);
      double endRadius = this.getConfigDouble("blackhole-effect.end-radius", 0.2);
      int particles = this.getConfigInt("blackhole-effect.particles", 16);
      if (particles < 6) {
         particles = 6;
      }

      final int durationValue = durationTicks;
      final double startRadiusValue = startRadius;
      final double endRadiusValue = endRadius;
      final int particlesValue = particles;
      int[] ticks = new int[] { 0 };

      String taskName = "checker-deny-blackhole-" + targetId;
      this.plugin.getSchedulerManager().runEntityTaskTimer(target, taskName, () -> {
         if (!this.sessions.containsKey(targetId)) {
            this.plugin.getSchedulerManager().cancelTask(taskName);
            return;
         }
         if (!target.isOnline()) {
            this.plugin.getSchedulerManager().cancelTask(taskName);
            this.executeBan(session.getTargetName(), staffName);
            this.sessions.remove(targetId);
            return;
         }

         ticks[0]++;
         double progress = Math.min(1.0, ticks[0] / (double) durationValue);
         double radius = startRadiusValue + (endRadiusValue - startRadiusValue) * progress;
         double angle = ticks[0] * 0.5;
         Location base = target.getLocation();
         for (int i = 0; i < particlesValue; i++) {
            double a = angle + (Math.PI * 2.0) * i / particlesValue;
            double x = Math.cos(a) * radius;
            double z = Math.sin(a) * radius;
            double y = 0.5 + Math.sin(angle + i) * 0.5;
            Location loc = base.clone().add(x, y, z);
            base.getWorld().spawnParticle(Particle.PORTAL, loc, 1, 0.0, 0.0, 0.0, 0.0);
         }

         if (progress >= 1.0) {
            this.spawnFlash(base);
            this.plugin.getSchedulerManager().cancelTask(taskName);
            this.finishSimpleDeny(target, session, staffName);
         }
      }, 1L, 1L);
   }

   private void startRingDenySequence(Player target, CheckSession session, String staffName) {
      UUID targetId = target.getUniqueId();
      int durationTicks = this.getConfigInt("ring-effect.duration-ticks", 40);
      if (durationTicks < 1) {
         durationTicks = 1;
      }
      int redTicks = this.getConfigInt("ring-effect.red-duration-ticks", 10);
      if (redTicks < 0) {
         redTicks = 0;
      }
      int rings = this.getConfigInt("ring-effect.rings", 3);
      if (rings < 1) {
         rings = 1;
      }
      double startRadius = this.getConfigDouble("ring-effect.start-radius", 2.5);
      double endRadius = this.getConfigDouble("ring-effect.end-radius", 0.2);
      int points = this.getConfigInt("ring-effect.points", 24);
      if (points < 8) {
         points = 8;
      }

      final int durationValue = durationTicks;
      final int redValue = redTicks;
      final int ringsValue = rings;
      final double startRadiusValue = startRadius;
      final double endRadiusValue = endRadius;
      final int pointsValue = points;
      int[] ticks = new int[] { 0 };

      String taskName = "checker-deny-ring-" + targetId;
      this.plugin.getSchedulerManager().runEntityTaskTimer(target, taskName, () -> {
         if (!this.sessions.containsKey(targetId)) {
            this.plugin.getSchedulerManager().cancelTask(taskName);
            return;
         }
         if (!target.isOnline()) {
            this.plugin.getSchedulerManager().cancelTask(taskName);
            this.executeBan(session.getTargetName(), staffName);
            this.sessions.remove(targetId);
            return;
         }

         ticks[0]++;
         Location base = target.getLocation();
         if (ticks[0] == durationValue + 1) {
            this.playSoundAll(base, Sound.BLOCK_BEACON_ACTIVATE, 1.0F, 1.0F);
         }
         boolean redPhase = ticks[0] > durationValue;
         double progress = Math.min(1.0, Math.min(ticks[0], durationValue) / (double) durationValue);
         double radius = startRadiusValue + (endRadiusValue - startRadiusValue) * progress;
         for (int r = 0; r < ringsValue; r++) {
            double y = 0.3 + (r * 0.5);
            Color color = redPhase ? Color.RED : Color.AQUA;
            this.spawnDustRing(base.clone().add(0.0, y, 0.0), radius, color, 1.2F, pointsValue);
         }

         if (redPhase && ticks[0] >= durationValue + redValue) {
            this.plugin.getSchedulerManager().cancelTask(taskName);
            this.finishRingDenySequence(target, session, staffName);
         }
      }, 1L, 1L);
   }

   private void startFreezeDenySequence(Player target, CheckSession session, String staffName) {
      UUID targetId = target.getUniqueId();
      int durationTicks = this.getConfigInt("freeze-effect.duration-ticks", 30);
      if (durationTicks < 1) {
         durationTicks = 1;
      }
      double radius = this.getConfigDouble("freeze-effect.radius", 1.5);
      int particles = this.getConfigInt("freeze-effect.particles", 20);
      if (particles < 6) {
         particles = 6;
      }

      final int durationValue = durationTicks;
      final double radiusValue = radius;
      final int particlesValue = particles;
      int[] ticks = new int[] { 0 };

      String taskName = "checker-deny-freeze-" + targetId;
      this.plugin.getSchedulerManager().runEntityTaskTimer(target, taskName, () -> {
         if (!this.sessions.containsKey(targetId)) {
            this.plugin.getSchedulerManager().cancelTask(taskName);
            return;
         }
         if (!target.isOnline()) {
            this.plugin.getSchedulerManager().cancelTask(taskName);
            this.executeBan(session.getTargetName(), staffName);
            this.sessions.remove(targetId);
            return;
         }

         ticks[0]++;
         Location base = target.getLocation();
         this.spawnRandomSphere(base, radiusValue, Particle.SNOWFLAKE, particlesValue);

         if (ticks[0] >= durationValue) {
            this.playSoundAll(base, Sound.BLOCK_GLASS_BREAK, 0.8F, 1.2F);
            this.spawnFlash(base);
            this.plugin.getSchedulerManager().cancelTask(taskName);
            this.finishSimpleDeny(target, session, staffName);
         }
      }, 1L, 1L);
   }

   private void startPortalDenySequence(Player target, CheckSession session, String staffName) {
      UUID targetId = target.getUniqueId();
      int durationTicks = this.getConfigInt("portal-effect.duration-ticks", 30);
      if (durationTicks < 1) {
         durationTicks = 1;
      }
      double radius = this.getConfigDouble("portal-effect.radius", 1.5);
      int particles = this.getConfigInt("portal-effect.particles", 30);
      if (particles < 6) {
         particles = 6;
      }

      final int durationValue = durationTicks;
      final double radiusValue = radius;
      final int particlesValue = particles;
      int[] ticks = new int[] { 0 };

      String taskName = "checker-deny-portal-" + targetId;
      this.plugin.getSchedulerManager().runEntityTaskTimer(target, taskName, () -> {
         if (!this.sessions.containsKey(targetId)) {
            this.plugin.getSchedulerManager().cancelTask(taskName);
            return;
         }
         if (!target.isOnline()) {
            this.plugin.getSchedulerManager().cancelTask(taskName);
            this.executeBan(session.getTargetName(), staffName);
            this.sessions.remove(targetId);
            return;
         }

         ticks[0]++;
         Location base = target.getLocation();
         this.spawnRandomSphere(base, radiusValue, Particle.PORTAL, particlesValue);
         this.spawnRandomSphere(base, radiusValue, Particle.REVERSE_PORTAL, particlesValue / 2);

         if (ticks[0] >= durationValue) {
            this.playSoundAll(base, Sound.BLOCK_PORTAL_TRAVEL, 0.9F, 1.0F);
            this.spawnFlash(base);
            this.plugin.getSchedulerManager().cancelTask(taskName);
            this.finishSimpleDeny(target, session, staffName);
         }
      }, 1L, 1L);
   }

   private void startThornsDenySequence(Player target, CheckSession session, String staffName) {
      UUID targetId = target.getUniqueId();
      int durationTicks = this.getConfigInt("thorns-effect.duration-ticks", 25);
      if (durationTicks < 1) {
         durationTicks = 1;
      }
      double radius = this.getConfigDouble("thorns-effect.radius", 1.5);
      int points = this.getConfigInt("thorns-effect.points", 16);
      if (points < 6) {
         points = 6;
      }
      Material material = this.getConfigMaterial("thorns-effect.material", Material.CRIMSON_STEM);
      BlockData blockData = material.createBlockData();

      final int durationValue = durationTicks;
      final double radiusValue = radius;
      final int pointsValue = points;
      final BlockData blockDataValue = blockData;
      int[] ticks = new int[] { 0 };

      String taskName = "checker-deny-thorns-" + targetId;
      this.plugin.getSchedulerManager().runEntityTaskTimer(target, taskName, () -> {
         if (!this.sessions.containsKey(targetId)) {
            this.plugin.getSchedulerManager().cancelTask(taskName);
            return;
         }
         if (!target.isOnline()) {
            this.plugin.getSchedulerManager().cancelTask(taskName);
            this.executeBan(session.getTargetName(), staffName);
            this.sessions.remove(targetId);
            return;
         }

         ticks[0]++;
         Location base = target.getLocation().clone().add(0.0, 0.1, 0.0);
         this.spawnBlockRing(base, blockDataValue, radiusValue, pointsValue);

         if (ticks[0] >= durationValue) {
            this.spawnFlash(base);
            this.plugin.getSchedulerManager().cancelTask(taskName);
            this.finishSimpleDeny(target, session, staffName);
         }
      }, 1L, 1L);
   }

   private void startPulseDenySequence(Player target, CheckSession session, String staffName) {
      UUID targetId = target.getUniqueId();
      int pulses = this.getConfigInt("pulse-effect.pulses", 3);
      int intervalTicks = this.getConfigInt("pulse-effect.interval-ticks", 10);
      if (pulses < 1) {
         pulses = 1;
      }
      if (intervalTicks < 1) {
         intervalTicks = 1;
      }
      double startRadius = this.getConfigDouble("pulse-effect.start-radius", 0.6);
      double endRadius = this.getConfigDouble("pulse-effect.end-radius", 2.8);
      int points = this.getConfigInt("pulse-effect.points", 24);
      if (points < 8) {
         points = 8;
      }

      final int pulsesValue = pulses;
      final int intervalValue = intervalTicks;
      final double startRadiusValue = startRadius;
      final double endRadiusValue = endRadius;
      final int pointsValue = points;
      int[] ticks = new int[] { 0 };
      int totalTicks = pulsesValue * intervalValue;

      String taskName = "checker-deny-pulse-" + targetId;
      this.plugin.getSchedulerManager().runEntityTaskTimer(target, taskName, () -> {
         if (!this.sessions.containsKey(targetId)) {
            this.plugin.getSchedulerManager().cancelTask(taskName);
            return;
         }
         if (!target.isOnline()) {
            this.plugin.getSchedulerManager().cancelTask(taskName);
            this.executeBan(session.getTargetName(), staffName);
            this.sessions.remove(targetId);
            return;
         }

         ticks[0]++;
         if (ticks[0] % intervalValue == 0) {
            int index = Math.min(pulsesValue - 1, (ticks[0] / intervalValue) - 1);
            double t = pulsesValue <= 1 ? 1.0 : index / (double) (pulsesValue - 1);
            double radius = startRadiusValue + (endRadiusValue - startRadiusValue) * t;
            Location base = target.getLocation();
            this.spawnDustRing(base.clone().add(0.0, 1.0, 0.0), radius, Color.LIME, 1.1F, pointsValue);
            this.playSoundAll(base, Sound.BLOCK_NOTE_BLOCK_PLING, 0.6F, 1.6F);
         }

         if (ticks[0] >= totalTicks) {
            Location base = target.getLocation();
            this.spawnFlash(base);
            this.plugin.getSchedulerManager().cancelTask(taskName);
            this.finishSimpleDeny(target, session, staffName);
         }
      }, 1L, 1L);
   }

   private void finishDenySequence(Player target, String staffName) {
      if (target == null || !target.isOnline()) {
         return;
      }
      Location location = target.getLocation();
      World world = location.getWorld();
      if (world != null) {
         if (this.isDenyDropEnabled()) {
            this.dropAllItems(target);
         }
         this.playSoundAll(location, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0F, 0.1F);
         this.playSoundAll(location, Sound.ITEM_TOTEM_USE, 1.0F, 1.0F);
         world.createExplosion(location.getX(), location.getY(), location.getZ(), 2.0F, false, false, target);
         world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 0.7F, 1.0F);
      }
      target.setGravity(true);
      this.executeBan(target.getName(), staffName);
   }

   private void spawnDenyRings(Location center) {
      if (center == null || center.getWorld() == null) {
         return;
      }
      World world = center.getWorld();
      Particle.DustOptions dust = new Particle.DustOptions(Color.RED, 1.5F);
      double radius = 1.5;
      int points = 16;
      for (int ring = 0; ring < 3; ring++) {
         double y = 0.2 + (ring * 0.6);
         for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0) * i / points;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location loc = center.clone().add(x, y, z);
            world.spawnParticle(Particle.DUST, loc, 1, 0.0, 0.0, 0.0, 0.0, dust);
         }
      }
   }

   private void spawnConfirmSphere(Location center) {
      if (center == null || center.getWorld() == null) {
         return;
      }
      World world = center.getWorld();
      Particle.DustOptions dust = new Particle.DustOptions(Color.LIME, 1.2F);
      double radius = 1.6;
      int points = 72;
      for (int i = 0; i < points; i++) {
         double theta = Math.acos(2.0 * Math.random() - 1.0);
         double phi = Math.random() * Math.PI * 2.0;
         double x = radius * Math.sin(theta) * Math.cos(phi);
         double y = radius * Math.cos(theta);
         double z = radius * Math.sin(theta) * Math.sin(phi);
         Location loc = center.clone().add(x, y + 1.0, z);
         world.spawnParticle(Particle.DUST, loc, 1, 0.0, 0.0, 0.0, 0.0, dust);
      }
   }

   private void spawnCage(Location base, BlockData blockData, double offset, double height, double step) {
      if (base == null || base.getWorld() == null) {
         return;
      }
      World world = base.getWorld();
      double half = Math.max(0.1, offset);
      double yStart = 0.0;
      double yEnd = Math.max(0.5, height);

      for (double y = yStart; y <= yEnd; y += step) {
         for (double x = -half; x <= half; x += step) {
            world.spawnParticle(Particle.BLOCK, base.clone().add(x, y, half), 1, 0.0, 0.0, 0.0, 0.0, blockData);
            world.spawnParticle(Particle.BLOCK, base.clone().add(x, y, -half), 1, 0.0, 0.0, 0.0, 0.0, blockData);
         }
         for (double z = -half; z <= half; z += step) {
            world.spawnParticle(Particle.BLOCK, base.clone().add(half, y, z), 1, 0.0, 0.0, 0.0, 0.0, blockData);
            world.spawnParticle(Particle.BLOCK, base.clone().add(-half, y, z), 1, 0.0, 0.0, 0.0, 0.0, blockData);
         }
      }
   }

   private void spawnChainLine(Location base, Vector direction, double distance, int points) {
      if (base == null || base.getWorld() == null) {
         return;
      }
      Vector dir = direction.clone().normalize();
      for (int i = 0; i <= points; i++) {
         double t = i / (double) points;
         Location loc = base.clone().add(dir.clone().multiply(distance * (1.0 - t)));
         base.getWorld().spawnParticle(Particle.CRIT, loc, 1, 0.0, 0.0, 0.0, 0.0);
      }
   }

   private void spawnLaserBeam(Location base, double height, int points) {
      if (base == null || base.getWorld() == null) {
         return;
      }
      for (int i = 0; i <= points; i++) {
         double t = i / (double) points;
         Location loc = base.clone().add(0.0, height * (1.0 - t), 0.0);
         base.getWorld().spawnParticle(Particle.END_ROD, loc, 1, 0.0, 0.0, 0.0, 0.0);
         if (i % 2 == 0) {
            base.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 1, 0.0, 0.0, 0.0, 0.0);
         }
      }
   }

   private void spawnDustRing(Location center, double radius, Color color, float size, int points) {
      if (center == null || center.getWorld() == null) {
         return;
      }
      Particle.DustOptions dust = new Particle.DustOptions(color, size);
      for (int i = 0; i < points; i++) {
         double angle = (Math.PI * 2.0) * i / points;
         double x = Math.cos(angle) * radius;
         double z = Math.sin(angle) * radius;
         Location loc = center.clone().add(x, 0.0, z);
         center.getWorld().spawnParticle(Particle.DUST, loc, 1, 0.0, 0.0, 0.0, 0.0, dust);
      }
   }

   private void spawnBlockRing(Location center, BlockData blockData, double radius, int points) {
      if (center == null || center.getWorld() == null) {
         return;
      }
      for (int i = 0; i < points; i++) {
         double angle = (Math.PI * 2.0) * i / points;
         double x = Math.cos(angle) * radius;
         double z = Math.sin(angle) * radius;
         Location loc = center.clone().add(x, 0.0, z);
         center.getWorld().spawnParticle(Particle.BLOCK, loc, 1, 0.0, 0.0, 0.0, 0.0, blockData);
      }
   }

   private void spawnRandomSphere(Location center, double radius, Particle particle, int count) {
      if (center == null || center.getWorld() == null) {
         return;
      }
      for (int i = 0; i < count; i++) {
         double u = Math.random();
         double v = Math.random();
         double theta = 2.0 * Math.PI * u;
         double phi = Math.acos(2.0 * v - 1.0);
         double r = radius * Math.cbrt(Math.random());
         double x = r * Math.sin(phi) * Math.cos(theta);
         double y = r * Math.cos(phi);
         double z = r * Math.sin(phi) * Math.sin(theta);
         Location loc = center.clone().add(x, y, z);
         center.getWorld().spawnParticle(particle, loc, 1, 0.0, 0.0, 0.0, 0.0);
      }
   }

   private void spawnFlash(Location location) {
      if (location == null || location.getWorld() == null) {
         return;
      }
      location.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, location, 1, 0.0, 0.0, 0.0, 0.0);
   }

   private void spawnRingScatter(Location location) {
      if (location == null || location.getWorld() == null) {
         return;
      }
      int count = this.getConfigInt("ring-effect.scatter-count", 80);
      double offset = this.getConfigDouble("ring-effect.scatter-offset", 1.2);
      double speed = this.getConfigDouble("ring-effect.scatter-speed", 0.25);
      Particle.DustOptions dust = new Particle.DustOptions(Color.RED, 1.2F);
      location.getWorld().spawnParticle(Particle.DUST, location.clone().add(0.0, 1.0, 0.0),
         count, offset, offset, offset, speed, dust);
   }

   private void spawnRingAfterBan(Location location) {
      if (location == null || location.getWorld() == null) {
         return;
      }
      int ticksTotal = this.getConfigInt("ring-effect.after-ban-ticks", 20);
      int points = this.getConfigInt("ring-effect.after-ban-points", 24);
      double radius = this.getConfigDouble("ring-effect.after-ban-radius", 1.0);
      if (ticksTotal < 1) {
         return;
      }
      if (points < 8) {
         points = 8;
      }
      final int pointsValue = points;
      final double radiusValue = radius;
      final Location base = location.clone();
      String taskName = "checker-ring-after-" + System.nanoTime();
      int[] ticks = new int[] { 0 };
      this.plugin.getSchedulerManager().runTaskTimer(taskName, () -> {
         ticks[0]++;
         if (ticks[0] > ticksTotal) {
            this.plugin.getSchedulerManager().cancelTask(taskName);
            return;
         }
         this.spawnDustRing(base.clone().add(0.0, 1.0, 0.0), radiusValue, Color.RED, 1.2F, pointsValue);
      }, 1L, 1L);
   }

   private void spawnSmileCube(Location location, BlockData blockData, double cubeSize, int particleCount) {
      if (location == null || location.getWorld() == null) {
         return;
      }
      World world = location.getWorld();
      world.spawnParticle(Particle.BLOCK, location, particleCount, cubeSize, cubeSize, cubeSize, 0.0, blockData);
   }

   private void spawnRocketExplosion(Location location, int smokeCount) {
      if (location == null || location.getWorld() == null) {
         return;
      }
      World world = location.getWorld();
      world.spawnParticle(Particle.EXPLOSION, location, 1, 0.0, 0.0, 0.0, 0.0);
      world.spawnParticle(Particle.LARGE_SMOKE, location, smokeCount, 0.2, 0.2, 0.2, 0.01);
   }

   private void dropAllItems(Player target) {
      if (target == null || !target.isOnline()) {
         return;
      }
      World world = target.getWorld();
      Location location = target.getLocation();
      PlayerInventory inventory = target.getInventory();
      if (world == null || inventory == null) {
         return;
      }

      List<ItemStack> toDrop = new ArrayList<>();
      this.collectItemStacks(toDrop, inventory.getStorageContents());
      this.collectItemStacks(toDrop, inventory.getArmorContents());
      this.collectItemStack(toDrop, inventory.getItemInOffHand());

      inventory.clear();
      inventory.setArmorContents(new ItemStack[4]);
      inventory.setItemInOffHand(new ItemStack(Material.AIR));

      for (ItemStack item : toDrop) {
         this.dropItemStack(world, location, item);
      }
   }

   private void dropItemStacks(World world, Location location, ItemStack[] items) {
      if (items == null) {
         return;
      }
      for (ItemStack item : items) {
         this.dropItemStack(world, location, item);
      }
   }

   private void dropItemStack(World world, Location location, ItemStack item) {
      if (item == null) {
         return;
      }
      if (item.getType() == Material.AIR) {
         return;
      }
      world.dropItemNaturally(location, item);
   }

   private void finishRocketDenySequence(Player target, CheckSession session, String staffName) {
      if (target == null || !target.isOnline()) {
         return;
      }
      Location location = target.getLocation();
      if (this.isDenyDropEnabled()) {
         this.dropAllItems(target);
      }
      this.executeBan(session.getTargetName(), staffName);
      this.sessions.remove(target.getUniqueId());
   }

   private void finishSmileDenySequence(Player target, CheckSession session, String staffName) {
      if (target == null || !target.isOnline()) {
         return;
      }
      if (this.isDenyDropEnabled()) {
         this.dropAllItems(target);
      }
      this.executeBan(session.getTargetName(), staffName);
      this.sessions.remove(target.getUniqueId());
   }

   private void finishCageDenySequence(Player target, CheckSession session, String staffName) {
      if (target == null || !target.isOnline()) {
         return;
      }
      this.spawnFlash(target.getLocation());
      this.finishSimpleDeny(target, session, staffName);
   }

   private void finishSimpleDeny(Player target, CheckSession session, String staffName) {
      if (target == null || !target.isOnline()) {
         return;
      }
      if (this.isDenyDropEnabled()) {
         this.dropAllItems(target);
      }
      this.executeBan(session.getTargetName(), staffName);
      this.sessions.remove(target.getUniqueId());
   }

   private void finishRingDenySequence(Player target, CheckSession session, String staffName) {
      if (target == null || !target.isOnline()) {
         return;
      }
      Location base = target.getLocation();
      this.spawnRingScatter(base);
      if (this.isDenyDropEnabled()) {
         this.dropAllItems(target);
      }
      this.executeBan(session.getTargetName(), staffName);
      this.sessions.remove(target.getUniqueId());
      this.spawnRingAfterBan(base);
   }

   private void finishScaleDenySequence(Player target, CheckSession session, String staffName) {
      if (target == null || !target.isOnline()) {
         return;
      }
      Location location = target.getLocation();
      if (this.isDenyDropEnabled()) {
         this.dropAllItems(target);
      }
      this.playSoundAll(location, Sound.ENTITY_PLAYER_HURT_DROWN, 1.0F, 2.0F);
      this.setScale(target, 1.0);
      this.executeBan(session.getTargetName(), staffName);
      this.sessions.remove(target.getUniqueId());
   }

   private void collectItemStacks(List<ItemStack> target, ItemStack[] items) {
      if (items == null) {
         return;
      }
      for (ItemStack item : items) {
         this.collectItemStack(target, item);
      }
   }

   private void collectItemStack(List<ItemStack> target, ItemStack item) {
      if (item == null) {
         return;
      }
      if (item.getType() == Material.AIR) {
         return;
      }
      target.add(item);
   }

   private void setScale(Player target, double value) {
      if (target == null) {
         return;
      }
      var attribute = target.getAttribute(org.bukkit.attribute.Attribute.SCALE);
      if (attribute != null) {
         attribute.setBaseValue(value);
      }
   }

   private String getDenyEffectType() {
      FileConfiguration config = this.getEffectsConfig();
      if (config == null) {
         return "default";
      }
      String value = config.getString("deny-effect", "default");
      if (value == null || value.isEmpty()) {
         return "default";
      }
      String normalized = value.toLowerCase();
      if (normalized.equals("random")) {
         return this.pickRandomDenyEffect();
      }
      if (normalized.equals("default") || normalized.equals("scale") || normalized.equals("rocket")
         || normalized.equals("smile") || normalized.equals("cage") || normalized.equals("storm")
         || normalized.equals("chain") || normalized.equals("laser") || normalized.equals("blackhole")
         || normalized.equals("ring") || normalized.equals("freeze") || normalized.equals("portal")
         || normalized.equals("thorns") || normalized.equals("pulse")) {
         return normalized;
      }
      return "default";
   }

   private String resolveDenyEffect(String override) {
      if (override != null && !override.isEmpty()) {
         String normalized = override.toLowerCase();
         if (normalized.equals("random")) {
            return this.pickRandomDenyEffect();
         }
         if (normalized.equals("default") || normalized.equals("scale") || normalized.equals("rocket")
            || normalized.equals("smile") || normalized.equals("cage") || normalized.equals("storm")
            || normalized.equals("chain") || normalized.equals("laser") || normalized.equals("blackhole")
            || normalized.equals("ring") || normalized.equals("freeze") || normalized.equals("portal")
            || normalized.equals("thorns") || normalized.equals("pulse")) {
            return normalized;
         }
      }
      return this.getDenyEffectType();
   }

   private String pickRandomDenyEffect() {
      if (RANDOM_EFFECTS.length == 0) {
         return "default";
      }
      int index = ThreadLocalRandom.current().nextInt(RANDOM_EFFECTS.length);
      return RANDOM_EFFECTS[index];
   }

   private int getConfigInt(String path, int defaultValue) {
      FileConfiguration config = this.getEffectsConfig();
      if (config == null) {
         return defaultValue;
      }
      return config.getInt(path, defaultValue);
   }

   private double getConfigDouble(String path, double defaultValue) {
      FileConfiguration config = this.getEffectsConfig();
      if (config == null) {
         return defaultValue;
      }
      return config.getDouble(path, defaultValue);
   }

   private Material getConfigMaterial(String path, Material fallback) {
      FileConfiguration config = this.getEffectsConfig();
      if (config == null) {
         return fallback;
      }
      String name = config.getString(path, null);
      if (name == null || name.isEmpty()) {
         return fallback;
      }
      Material material = Material.matchMaterial(name);
      return material != null ? material : fallback;
   }

   private Vector getRightVector(Location location) {
      Vector direction = location.getDirection().setY(0.0);
      if (direction.lengthSquared() == 0.0) {
         direction = new Vector(0.0, 0.0, 1.0);
      }
      direction.normalize();
      return new Vector(-direction.getZ(), 0.0, direction.getX()).normalize();
   }

   private boolean isDenyDropEnabled() {
      FileConfiguration config = this.getEffectsConfig();
      if (config == null) {
         return true;
      }
      return config.getBoolean("deny-drop-items", true);
   }

   private FileConfiguration getEffectsConfig() {
      FileConfiguration config = this.module.getEffectsConfig();
      if (config != null) {
         return config;
      }
      return this.module.getConfig();
   }

   private void playSoundAll(Location location, Sound sound, float volume, float pitch) {
      if (location == null || sound == null) {
         return;
      }
      World world = location.getWorld();
      if (world == null) {
         return;
      }
      world.playSound(location, sound, volume, pitch);
   }

   private void sendMessage(CommandSender sender, String message) {
      if (message != null && !message.isEmpty()) {
         if (sender instanceof Player player) {
            this.plugin.getSchedulerManager().runEntityTask(player, "checker-message", () -> {
               if (player.isOnline()) {
                  player.sendMessage(message);
               }
            });
         } else {
            sender.sendMessage(message);
         }
      }
   }

   private Component deserialize(String text) {
      return LegacyComponentSerializer.legacyAmpersand().deserialize(text.replace("§", "&"));
   }

   public static class CheckSession {
      private final UUID targetId;
      private final String targetName;
      private final UUID staffId;
      private final String staffName;
      private float walkSpeed;
      private float flySpeed;
      private String actionbarTask;
      private String chatTask;
      private boolean denyInProgress;
      private String banReasonOverride;
      private String banStatus = BAN_STATUS_DEFAULT;

      public CheckSession(UUID targetId, String targetName, UUID staffId, String staffName) {
         this.targetId = targetId;
         this.targetName = targetName;
         this.staffId = staffId;
         this.staffName = staffName;
      }

      public void captureState(Player target) {
         this.walkSpeed = target.getWalkSpeed();
         this.flySpeed = target.getFlySpeed();
      }

      public UUID getTargetId() {
         return this.targetId;
      }

      public String getTargetName() {
         return this.targetName;
      }

      public UUID getStaffId() {
         return this.staffId;
      }

      public String getStaffName() {
         return this.staffName;
      }

      public float getWalkSpeed() {
         return this.walkSpeed;
      }

      public float getFlySpeed() {
         return this.flySpeed;
      }

      public String getActionbarTask() {
         return this.actionbarTask;
      }

      public void setActionbarTask(String actionbarTask) {
         this.actionbarTask = actionbarTask;
      }

      public String getChatTask() {
         return this.chatTask;
      }

      public void setChatTask(String chatTask) {
         this.chatTask = chatTask;
      }

      public boolean isDenyInProgress() {
         return this.denyInProgress;
      }

      public void setDenyInProgress(boolean denyInProgress) {
         this.denyInProgress = denyInProgress;
      }

      public String getBanReasonOverride() {
         return this.banReasonOverride;
      }

      public void setBanReasonOverride(String banReasonOverride) {
         this.banReasonOverride = banReasonOverride;
      }

      public String getBanStatus() {
         return this.banStatus;
      }

      public void setBanStatus(String banStatus) {
         this.banStatus = banStatus;
      }
   }
}
