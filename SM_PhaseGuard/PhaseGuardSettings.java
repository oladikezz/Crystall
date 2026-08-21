package net.schalker.SMPS.modules.phaseguard;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class PhaseGuardSettings {

    public enum Mode {
        BLOCK,
        LOG
    }

    private final boolean enabled;
    private final Mode mode;
    private final double rayHeight;
    private final double minDistanceSquared;
    private final double maxDistanceSquared;
    private final boolean checkWhileGliding;
    private final boolean ignoreCreative;
    private final Set<Material> ignoredBlocks;
    private final Set<String> ignoredWorlds;
    private final List<String> unknownBlockNames;
    private final long graceTeleportMillis;
    private final long graceJoinMillis;
    private final long graceRespawnMillis;
    private final long graceWorldChangeMillis;
    private final boolean rescueFromBlocks;
    private final boolean notifyPlayer;
    private final long notifyCooldownMillis;
    private final boolean alertStaff;
    private final int alertThreshold;
    private final long alertCooldownMillis;
    private final long violationWindowMillis;
    private final long safeUpdateIntervalMillis;

    private PhaseGuardSettings(FileConfiguration config) {
        this.enabled = config.getBoolean("enabled", true);
        this.mode = parseMode(config.getString("mode", "BLOCK"));

        this.rayHeight = clamp(config.getDouble("detection.ray-height", 0.65), 0.61, 1.5);
        double minDistance = clamp(config.getDouble("detection.min-distance", 0.08), 0.01, 1.0);
        double maxDistance = clamp(config.getDouble("detection.max-distance", 8.0), minDistance + 0.5, 64.0);
        this.minDistanceSquared = minDistance * minDistance;
        this.maxDistanceSquared = maxDistance * maxDistance;
        this.checkWhileGliding = config.getBoolean("detection.check-while-gliding", false);
        this.ignoreCreative = config.getBoolean("detection.ignore-creative", false);

        this.unknownBlockNames = new ArrayList<>();
        this.ignoredBlocks = readMaterials(config.getStringList("detection.ignored-blocks"), this.unknownBlockNames);

        Set<String> worlds = new HashSet<>();
        for (String world : config.getStringList("ignored-worlds")) {
            if (world != null && !world.isBlank()) {
                worlds.add(world.toLowerCase(Locale.ROOT));
            }
        }
        this.ignoredWorlds = worlds;

        this.graceTeleportMillis = clampMillis(config.getLong("grace.after-teleport-millis", 2000L));
        this.graceJoinMillis = clampMillis(config.getLong("grace.after-join-millis", 4000L));
        this.graceRespawnMillis = clampMillis(config.getLong("grace.after-respawn-millis", 4000L));
        this.graceWorldChangeMillis = clampMillis(config.getLong("grace.after-world-change-millis", 6000L));

        this.rescueFromBlocks = config.getBoolean("response.rescue-from-blocks", true);
        this.notifyPlayer = config.getBoolean("response.notify-player", true);
        this.notifyCooldownMillis = clampMillis(config.getLong("response.notify-cooldown-millis", 3000L));
        this.alertStaff = config.getBoolean("response.alert-staff", true);
        this.alertThreshold = Math.max(1, config.getInt("response.alert-threshold", 3));
        this.alertCooldownMillis = clampMillis(config.getLong("response.alert-cooldown-millis", 8000L));
        this.violationWindowMillis = clampMillis(config.getLong("response.violation-window-millis", 10000L));
        this.safeUpdateIntervalMillis = clampMillis(config.getLong("response.safe-point-interval-millis", 1000L));
    }

    public static PhaseGuardSettings from(FileConfiguration config) {
        return new PhaseGuardSettings(config);
    }

    private static Mode parseMode(String raw) {
        if (raw == null) {
            return Mode.BLOCK;
        }
        try {
            return Mode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return Mode.BLOCK;
        }
    }

    private static Set<Material> readMaterials(List<String> names, List<String> unknownSink) {
        Set<Material> result = EnumSet.noneOf(Material.class);
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            Material material = Material.matchMaterial(name.trim());
            if (material == null) {
                unknownSink.add(name.trim());
                continue;
            }
            result.add(material);
        }
        return result;
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static long clampMillis(long value) {
        return Math.max(0L, Math.min(600000L, value));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Mode getMode() {
        return mode;
    }

    public double getRayHeight() {
        return rayHeight;
    }

    public double getMinDistanceSquared() {
        return minDistanceSquared;
    }

    public double getMaxDistanceSquared() {
        return maxDistanceSquared;
    }

    public boolean isCheckWhileGliding() {
        return checkWhileGliding;
    }

    public boolean isIgnoreCreative() {
        return ignoreCreative;
    }

    public boolean isBlockIgnored(Material material) {
        return ignoredBlocks.contains(material);
    }

    public boolean isWorldIgnored(String worldName) {
        return !ignoredWorlds.isEmpty() && ignoredWorlds.contains(worldName.toLowerCase(Locale.ROOT));
    }

    public List<String> getUnknownBlockNames() {
        return unknownBlockNames;
    }

    public long getGraceTeleportMillis() {
        return graceTeleportMillis;
    }

    public long getGraceJoinMillis() {
        return graceJoinMillis;
    }

    public long getGraceRespawnMillis() {
        return graceRespawnMillis;
    }

    public long getGraceWorldChangeMillis() {
        return graceWorldChangeMillis;
    }

    public boolean isRescueFromBlocks() {
        return rescueFromBlocks;
    }

    public boolean isNotifyPlayer() {
        return notifyPlayer;
    }

    public long getNotifyCooldownMillis() {
        return notifyCooldownMillis;
    }

    public boolean isAlertStaff() {
        return alertStaff;
    }

    public int getAlertThreshold() {
        return alertThreshold;
    }

    public long getAlertCooldownMillis() {
        return alertCooldownMillis;
    }

    public long getViolationWindowMillis() {
        return violationWindowMillis;
    }

    public long getSafeUpdateIntervalMillis() {
        return safeUpdateIntervalMillis;
    }
}
