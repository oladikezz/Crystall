package net.schalker.SMPS.modules.phaseguard.listeners;

import net.schalker.SMPS.modules.phaseguard.PhaseGuardModule;
import net.schalker.SMPS.modules.phaseguard.PhaseGuardSettings;
import net.schalker.SMPS.modules.phaseguard.TrackedPlayer;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Locale;

public class MovementListener implements Listener {

    private static final int REGION_CHECK_RADIUS = 1;
    private static final double BODY_TOP = 1.7;
    private static final double BODY_BOTTOM = 0.05;

    private final PhaseGuardModule module;

    public MovementListener(PhaseGuardModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent) {
            return;
        }

        PhaseGuardSettings settings = module.getSettings();
        if (settings == null || !settings.isEnabled()) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        World world = to.getWorld();
        if (world == null || !world.equals(from.getWorld())) {
            return;
        }
        if (settings.isWorldIgnored(world.getName())) {
            return;
        }

        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        if (distanceSquared < settings.getMinDistanceSquared() || distanceSquared > settings.getMaxDistanceSquared()) {
            return;
        }

        Player player = event.getPlayer();
        if (!isCheckable(player, settings)) {
            return;
        }

        long now = System.currentTimeMillis();
        TrackedPlayer tracked = module.getTracked(player.getUniqueId());
        if (tracked.isInGrace(now)) {
            return;
        }
        if (player.hasPermission(PhaseGuardModule.PERMISSION_BYPASS)) {
            return;
        }
        if (!Bukkit.isOwnedByCurrentRegion(from, REGION_CHECK_RADIUS)
                || !Bukkit.isOwnedByCurrentRegion(to, REGION_CHECK_RADIUS)) {
            return;
        }

        Block obstruction = findObstruction(world, from, dx, dy, dz, Math.sqrt(distanceSquared), settings);
        if (obstruction == null) {
            refreshSafePoint(world, to, tracked, now, settings);
            return;
        }

        handleViolation(event, world, player, from, to, obstruction, tracked, now, settings);
    }

    private boolean isCheckable(Player player, PhaseGuardSettings settings) {
        GameMode gameMode = player.getGameMode();
        if (gameMode == GameMode.SPECTATOR) {
            return false;
        }
        if (gameMode == GameMode.CREATIVE && settings.isIgnoreCreative()) {
            return false;
        }
        if (player.isInsideVehicle()) {
            return false;
        }

        Pose pose = player.getPose();
        if (pose == Pose.SWIMMING || pose == Pose.SPIN_ATTACK || pose == Pose.SLEEPING
                || pose == Pose.DYING || pose == Pose.LONG_JUMPING) {
            return false;
        }
        return pose != Pose.FALL_FLYING || settings.isCheckWhileGliding();
    }

    private Block findObstruction(World world, Location from, double dx, double dy, double dz,
                                  double distance, PhaseGuardSettings settings) {
        Location origin = from.clone();
        origin.setY(origin.getY() + settings.getRayHeight());

        Vector direction = new Vector(dx / distance, dy / distance, dz / distance);
        RayTraceResult result = world.rayTraceBlocks(origin, direction, distance, FluidCollisionMode.NEVER, true);
        if (result == null) {
            return null;
        }

        Block block = result.getHitBlock();
        if (block == null || settings.isBlockIgnored(block.getType())) {
            return null;
        }
        return block;
    }

    private void refreshSafePoint(World world, Location to, TrackedPlayer tracked, long now,
                                  PhaseGuardSettings settings) {
        if (!tracked.shouldRefreshSafePoint(now, settings.getSafeUpdateIntervalMillis())) {
            return;
        }
        if (to.clone().add(0.0, -0.1, 0.0).getBlock().isPassable() || !isBodyClear(world, to)) {
            return;
        }
        tracked.setSafePoint(to.clone(), now);
    }

    private boolean isBodyClear(World world, Location location) {
        Location head = location.clone();
        head.setY(head.getY() + BODY_TOP);
        RayTraceResult result = world.rayTraceBlocks(head, new Vector(0.0, -1.0, 0.0),
                BODY_TOP - BODY_BOTTOM, FluidCollisionMode.NEVER, true);
        return result == null;
    }

    private void handleViolation(PlayerMoveEvent event, World world, Player player, Location from, Location to,
                                 Block obstruction, TrackedPlayer tracked, long now,
                                 PhaseGuardSettings settings) {
        int recent = tracked.registerViolation(now, settings.getViolationWindowMillis());

        if (recent == 1) {
            module.getApi().getDebugSystem().log("PhaseGuard", String.format(Locale.ROOT,
                    "%s прошёл сквозь %s на %s %.2f %.2f %.2f",
                    player.getName(), obstruction.getType().name(), obstruction.getWorld().getName(),
                    to.getX(), to.getY(), to.getZ()));
        }

        if (settings.getMode() == PhaseGuardSettings.Mode.BLOCK) {
            if (isBodyClear(world, from)) {
                event.setCancelled(true);
            } else {
                rescue(player, tracked, now, settings);
            }
        }

        if (settings.isNotifyPlayer() && tracked.tryNotify(now, settings.getNotifyCooldownMillis())) {
            String message = module.getMessage("blocked", "&[SECONDARY]Проход сквозь блоки запрещён.");
            if (!message.isEmpty()) {
                player.sendMessage(message);
            }
        }

        if (recent < settings.getAlertThreshold() || !tracked.tryAlert(now, settings.getAlertCooldownMillis())) {
            return;
        }

        module.getApi().getDebugSystem().logWarning("PhaseGuard", String.format(Locale.ROOT,
                "%s пытается пройти сквозь блоки (%s) в %s %.1f %.1f %.1f, нарушений подряд: %d",
                player.getName(), obstruction.getType().name(), obstruction.getWorld().getName(),
                to.getX(), to.getY(), to.getZ(), recent));

        if (settings.isAlertStaff()) {
            broadcastAlert(player, to, obstruction, recent);
        }
    }

    private void rescue(Player player, TrackedPlayer tracked, long now, PhaseGuardSettings settings) {
        if (!settings.isRescueFromBlocks()) {
            return;
        }
        Location safe = tracked.getSafePoint();
        if (safe == null || safe.getWorld() == null || !safe.getWorld().equals(player.getWorld())) {
            return;
        }
        tracked.grantGrace(now, settings.getGraceTeleportMillis());
        player.teleportAsync(safe, PlayerTeleportEvent.TeleportCause.PLUGIN);
    }

    private void broadcastAlert(Player player, Location to, Block obstruction, int recent) {
        String template = module.getMessage("alert",
                "&[MAIN]{player} &[SECONDARY]пытается пройти сквозь блоки: &[MAIN]{block} &[SECONDARY]в &[MAIN]{world} {x} {y} {z} &[SECONDARY]({count})");
        if (template.isEmpty()) {
            return;
        }

        String message = template
                .replace("{player}", player.getName())
                .replace("{block}", obstruction.getType().name())
                .replace("{world}", to.getWorld().getName())
                .replace("{x}", String.format(Locale.ROOT, "%.1f", to.getX()))
                .replace("{y}", String.format(Locale.ROOT, "%.1f", to.getY()))
                .replace("{z}", String.format(Locale.ROOT, "%.1f", to.getZ()))
                .replace("{count}", String.valueOf(recent));

        module.getApi().getSchedulerManager().runGlobalTask("phaseguard-alert-" + player.getUniqueId(), () -> {
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission(PhaseGuardModule.PERMISSION_ALERT)) {
                    staff.sendMessage(message);
                }
            }
        });
    }
}
