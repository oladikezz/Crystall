package net.schalker.SMPS.modules.phaseguard;

import org.bukkit.Location;

public final class TrackedPlayer {

    private volatile long graceUntil;
    private volatile long lastNotify;
    private volatile long lastAlert;
    private volatile long lastSafeUpdate;
    private volatile long lastViolation;
    private volatile Location safeLocation;
    private volatile int recentViolations;
    private volatile int totalViolations;

    public boolean isInGrace(long now) {
        return now < graceUntil;
    }

    public void grantGrace(long now, long durationMillis) {
        long until = now + durationMillis;
        if (until > graceUntil) {
            graceUntil = until;
        }
    }

    public synchronized int registerViolation(long now, long windowMillis) {
        if (now - lastViolation > windowMillis) {
            recentViolations = 0;
        }
        lastViolation = now;
        recentViolations++;
        totalViolations++;
        return recentViolations;
    }

    public boolean tryNotify(long now, long cooldownMillis) {
        if (now - lastNotify < cooldownMillis) {
            return false;
        }
        lastNotify = now;
        return true;
    }

    public boolean tryAlert(long now, long cooldownMillis) {
        if (now - lastAlert < cooldownMillis) {
            return false;
        }
        lastAlert = now;
        return true;
    }

    public boolean shouldRefreshSafePoint(long now, long intervalMillis) {
        return now - lastSafeUpdate >= intervalMillis;
    }

    public void setSafePoint(Location location, long now) {
        safeLocation = location;
        lastSafeUpdate = now;
    }

    public Location getSafePoint() {
        return safeLocation;
    }

    public int getRecentViolations() {
        return recentViolations;
    }

    public int getTotalViolations() {
        return totalViolations;
    }

    public void reset() {
        recentViolations = 0;
        totalViolations = 0;
        lastViolation = 0L;
    }
}
