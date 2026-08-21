package net.schalker.SMPS.modules.flags;

import java.util.UUID;
import org.bukkit.Location;

public class FlagEvent {
   private final UUID playerId;
   private final String playerName;
   private final FlagType flagType;
   private final long timestamp;
   private final Location location;
   private final String world;
   private final String details;
   private final int value;
   private FlagType.FlagSeverity resolvedSeverity;

   private FlagEvent(Builder builder) {
      this.playerId = builder.playerId;
      this.playerName = builder.playerName;
      this.flagType = builder.flagType;
      this.timestamp = builder.timestamp;
      this.location = builder.location;
      this.world = builder.world;
      this.details = builder.details;
      this.value = builder.value;
      this.resolvedSeverity = builder.resolvedSeverity;
   }

   public UUID getPlayerId() {
      return this.playerId;
   }

   public String getPlayerName() {
      return this.playerName;
   }

   public FlagType getFlagType() {
      return this.flagType;
   }

   public long getTimestamp() {
      return this.timestamp;
   }

   public Location getLocation() {
      return this.location;
   }

   public String getWorld() {
      return this.world;
   }

   public String getDetails() {
      return this.details;
   }

   public int getValue() {
      return this.value;
   }

   public FlagType.FlagSeverity getResolvedSeverity() {
      return this.resolvedSeverity != null ? this.resolvedSeverity : this.flagType.getSeverity();
   }

   public boolean hasManualSeverity() {
      return this.resolvedSeverity != null;
   }

   public void setResolvedSeverity(FlagType.FlagSeverity severity) {
      this.resolvedSeverity = severity;
   }

   public String getCoordinates() {
      if (this.location == null) {
         return "N/A";
      }
      return String.format("%d %d %d", 
         this.location.getBlockX(), 
         this.location.getBlockY(), 
         this.location.getBlockZ());
   }

   public static Builder builder() {
      return new Builder();
   }

   public static class Builder {
      private UUID playerId;
      private String playerName;
      private FlagType flagType;
      private long timestamp = System.currentTimeMillis();
      private Location location;
      private String world;
      private String details = "";
      private int value = 0;
      private FlagType.FlagSeverity resolvedSeverity;

      public Builder playerId(UUID playerId) {
         this.playerId = playerId;
         return this;
      }

      public Builder playerName(String playerName) {
         this.playerName = playerName;
         return this;
      }

      public Builder flagType(FlagType flagType) {
         this.flagType = flagType;
         return this;
      }

      public Builder timestamp(long timestamp) {
         this.timestamp = timestamp;
         return this;
      }

      public Builder location(Location location) {
         this.location = location;
         if (location != null && location.getWorld() != null) {
            this.world = location.getWorld().getName();
         }
         return this;
      }

      public Builder world(String world) {
         this.world = world;
         return this;
      }

      public Builder details(String details) {
         this.details = details;
         return this;
      }

      public Builder value(int value) {
         this.value = value;
         return this;
      }

      public Builder resolvedSeverity(FlagType.FlagSeverity severity) {
         this.resolvedSeverity = severity;
         return this;
      }

      public FlagEvent build() {
         return new FlagEvent(this);
      }
   }
}
