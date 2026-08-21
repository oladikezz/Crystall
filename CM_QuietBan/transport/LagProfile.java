package net.schalker.SMPS.modules.quietban.transport;

import java.util.concurrent.ThreadLocalRandom;

public final class LagProfile {

   private final long outboundDelayMillis;
   private final long inboundDelayMillis;
   private final long jitterMillis;
   private final double dropChance;
   private final long maxDelayMillis;

   public LagProfile(long outboundDelayMillis, long inboundDelayMillis, long jitterMillis,
                     double dropChance, long maxDelayMillis) {
      this.maxDelayMillis = Math.max(0L, maxDelayMillis);
      this.outboundDelayMillis = clamp(outboundDelayMillis);
      this.inboundDelayMillis = clamp(inboundDelayMillis);
      this.jitterMillis = clamp(jitterMillis);
      this.dropChance = Math.min(1.0D, Math.max(0.0D, dropChance));
   }

   public long nextOutboundDelay() {
      return withJitter(this.outboundDelayMillis);
   }

   public long nextInboundDelay() {
      return withJitter(this.inboundDelayMillis);
   }

   public boolean shouldDrop() {
      return this.dropChance > 0.0D && ThreadLocalRandom.current().nextDouble() < this.dropChance;
   }

   public long getOutboundDelayMillis() {
      return this.outboundDelayMillis;
   }

   public long getInboundDelayMillis() {
      return this.inboundDelayMillis;
   }

   public long getJitterMillis() {
      return this.jitterMillis;
   }

   public double getDropChance() {
      return this.dropChance;
   }

   private long withJitter(long base) {
      if (base <= 0L) {
         return 0L;
      }
      long value = base;
      if (this.jitterMillis > 0L) {
         value += ThreadLocalRandom.current().nextLong(this.jitterMillis + 1L);
      }
      return Math.min(this.maxDelayMillis, value);
   }

   private long clamp(long value) {
      return Math.min(this.maxDelayMillis, Math.max(0L, value));
   }
}
