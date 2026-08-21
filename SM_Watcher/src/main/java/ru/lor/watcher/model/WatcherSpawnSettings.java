package ru.lor.watcher.model;

import org.bukkit.Location;

public class WatcherSpawnSettings implements Cloneable {

    private double spawnDistance = 5.0;
    private int durationSeconds = 30;
    private boolean infiniteDuration = false;
    private double despawnDistance = 20.0;
    private WatcherPositionType positionType = WatcherPositionType.BEHIND;
    private WatcherBehaviorType behaviorType = WatcherBehaviorType.STATIC;
    private Location customLocation = null;
    private boolean freezingEnabled = true;
    private boolean jumpscareEnabled = true;
    private boolean aiMessageEnabled = true;
    private boolean broadcastToAll = false; // false = only nearby players (<=32 blocks), true = everyone on server
    private String soundPreset = "ANCIENT_HORROR"; // "ANCIENT_HORROR", "SPECTRAL_WHISPER", "SHADOW_DISCHARGE", "NONE"
    private String soundName = null;
    private String messageText = null;

    public WatcherSpawnSettings() {
    }

    public double getSpawnDistance() {
        return spawnDistance;
    }

    public void setSpawnDistance(double spawnDistance) {
        this.spawnDistance = spawnDistance;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public boolean isInfiniteDuration() {
        return infiniteDuration;
    }

    public void setInfiniteDuration(boolean infiniteDuration) {
        this.infiniteDuration = infiniteDuration;
    }

    public double getDespawnDistance() {
        return despawnDistance;
    }

    public void setDespawnDistance(double despawnDistance) {
        this.despawnDistance = despawnDistance;
    }

    public WatcherPositionType getPositionType() {
        return positionType;
    }

    public void setPositionType(WatcherPositionType positionType) {
        this.positionType = positionType;
    }

    public WatcherBehaviorType getBehaviorType() {
        return behaviorType;
    }

    public void setBehaviorType(WatcherBehaviorType behaviorType) {
        this.behaviorType = behaviorType;
    }

    public Location getCustomLocation() {
        return customLocation;
    }

    public void setCustomLocation(Location customLocation) {
        this.customLocation = customLocation;
    }

    public boolean isFreezingEnabled() {
        return freezingEnabled;
    }

    public void setFreezingEnabled(boolean freezingEnabled) {
        this.freezingEnabled = freezingEnabled;
    }

    public boolean isJumpscareEnabled() {
        return jumpscareEnabled;
    }

    public void setJumpscareEnabled(boolean jumpscareEnabled) {
        this.jumpscareEnabled = jumpscareEnabled;
    }

    public boolean isAiMessageEnabled() {
        return aiMessageEnabled;
    }

    public void setAiMessageEnabled(boolean aiMessageEnabled) {
        this.aiMessageEnabled = aiMessageEnabled;
    }

    public boolean isBroadcastToAll() {
        return broadcastToAll;
    }

    public void setBroadcastToAll(boolean broadcastToAll) {
        this.broadcastToAll = broadcastToAll;
    }

    public String getSoundPreset() {
        return soundPreset;
    }

    public void setSoundPreset(String soundPreset) {
        this.soundPreset = soundPreset;
    }

    // Legacy compatibility stubs
    public String getSoundName() { return soundName; }
    public void setSoundName(String soundName) { this.soundName = soundName; }
    public String getMessageText() { return messageText; }
    public void setMessageText(String messageText) { this.messageText = messageText; }
    public String getTitleText() { return null; }
    public void setTitleText(String titleText) {}
    public String getSubtitleText() { return null; }
    public void setSubtitleText(String subtitleText) {}
    public String getActionBarText() { return null; }
    public void setActionBarText(String actionBarText) {}
    public int getDarknessDuration() { return 0; }
    public void setDarknessDuration(int d) {}
    public int getBlindnessDuration() { return 0; }
    public void setBlindnessDuration(int b) {}
    public int getSlowFallingDuration() { return 0; }
    public void setSlowFallingDuration(int s) {}
    public int getLevitationDuration() { return 0; }
    public void setLevitationDuration(int l) {}

    @Override
    public WatcherSpawnSettings clone() {
        try {
            WatcherSpawnSettings copy = (WatcherSpawnSettings) super.clone();
            if (this.customLocation != null) {
                copy.customLocation = this.customLocation.clone();
            }
            return copy;
        } catch (CloneNotSupportedException e) {
            WatcherSpawnSettings copy = new WatcherSpawnSettings();
            copy.spawnDistance = this.spawnDistance;
            copy.durationSeconds = this.durationSeconds;
            copy.infiniteDuration = this.infiniteDuration;
            copy.despawnDistance = this.despawnDistance;
            copy.positionType = this.positionType;
            copy.behaviorType = this.behaviorType;
            if (this.customLocation != null) {
                copy.customLocation = this.customLocation.clone();
            }
            copy.freezingEnabled = this.freezingEnabled;
            copy.jumpscareEnabled = this.jumpscareEnabled;
            copy.aiMessageEnabled = this.aiMessageEnabled;
            copy.broadcastToAll = this.broadcastToAll;
            copy.soundPreset = this.soundPreset;
            copy.soundName = this.soundName;
            copy.messageText = this.messageText;
            return copy;
        }
    }
}
