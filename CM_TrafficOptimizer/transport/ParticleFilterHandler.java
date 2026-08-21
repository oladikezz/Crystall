package net.schalker.SMPS.modules.trafficoptimizer.transport;

import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public class ParticleFilterHandler extends ChannelOutboundHandlerAdapter {

   public static final String NAME = "doapi_netopt_particles";

   private final AtomicLong dropped = new AtomicLong();

   private volatile double dropChance;
   private volatile List<String> markers;

   public ParticleFilterHandler(double dropChance, List<String> markers) {
      this.dropChance = clamp(dropChance);
      this.markers = markers == null ? List.of() : markers;
   }

   public void update(double newDropChance, List<String> newMarkers) {
      this.dropChance = clamp(newDropChance);
      this.markers = newMarkers == null ? List.of() : newMarkers;
   }

   public long getDropped() {
      return this.dropped.get();
   }

   @Override
   public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
      double chance = this.dropChance;
      if (chance <= 0.0D || !this.matches(msg)) {
         super.write(ctx, msg, promise);
         return;
      }
      if (chance < 1.0D && ThreadLocalRandom.current().nextDouble() >= chance) {
         super.write(ctx, msg, promise);
         return;
      }

      ReferenceCountUtil.release(msg);
      promise.trySuccess();
      this.dropped.incrementAndGet();
   }

   private boolean matches(Object msg) {
      if (msg == null) {
         return false;
      }
      List<String> current = this.markers;
      if (current.isEmpty()) {
         return false;
      }
      String className = msg.getClass().getName();
      for (String marker : current) {
         if (!marker.isEmpty() && className.contains(marker)) {
            return true;
         }
      }
      return false;
   }

   private static double clamp(double value) {
      return Math.min(1.0D, Math.max(0.0D, value));
   }
}
