package net.schalker.SMPS.modules.trafficoptimizer;

import java.util.UUID;

public class PlayerNetworkState {

   private final UUID playerId;
   private final long joinedAt;

   private volatile double smoothedPing = -1.0D;
   private volatile int samples;
   private volatile OptimizationLevel level = OptimizationLevel.NONE;
   private volatile long lastChangeAt;
   private volatile long goodSince;
   private volatile boolean baselineCaptured;
   private volatile boolean skipped;
   private volatile String skipReason = "";

   private volatile int baselineView;
   private volatile int baselineSimulation;
   private volatile int baselineSend;

   public PlayerNetworkState(UUID playerId, long now) {
      this.playerId = playerId;
      this.joinedAt = now;
   }

   public UUID getPlayerId() {
      return this.playerId;
   }

   public long getJoinedAt() {
      return this.joinedAt;
   }

   public double getSmoothedPing() {
      return this.smoothedPing;
   }

   public int getSamples() {
      return this.samples;
   }

   public OptimizationLevel getLevel() {
      return this.level;
   }

   public void setLevel(OptimizationLevel level, long now) {
      this.level = level;
      this.lastChangeAt = now;
   }

   public long getLastChangeAt() {
      return this.lastChangeAt;
   }

   public long getGoodSince() {
      return this.goodSince;
   }

   public void setGoodSince(long goodSince) {
      this.goodSince = goodSince;
   }

   public boolean isBaselineCaptured() {
      return this.baselineCaptured;
   }

   public boolean isSkipped() {
      return this.skipped;
   }

   public String getSkipReason() {
      return this.skipReason;
   }

   public void markSkipped(String reason) {
      this.skipped = true;
      this.skipReason = reason == null ? "" : reason;
   }

   public void clearSkipped() {
      this.skipped = false;
      this.skipReason = "";
   }

   public int getBaselineView() {
      return this.baselineView;
   }

   public int getBaselineSimulation() {
      return this.baselineSimulation;
   }

   public int getBaselineSend() {
      return this.baselineSend;
   }

   public void captureBaseline(int view, int simulation, int send) {
      this.baselineView = view;
      this.baselineSimulation = simulation;
      this.baselineSend = send;
      this.baselineCaptured = true;
   }

   public double recordPing(int ping, double smoothing) {
      this.samples++;
      if (this.smoothedPing < 0.0D) {
         this.smoothedPing = ping;
      } else {
         this.smoothedPing = this.smoothedPing * (1.0D - smoothing) + ping * smoothing;
      }
      return this.smoothedPing;
   }
}
