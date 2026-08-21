package net.schalker.SMPS.modules.quietban.transport;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class ShadowLagHandler extends ChannelDuplexHandler {

   public static final String NAME = "doapi_quietban";

   private final UUID owner;
   private final boolean packetAware;

   private volatile LagProfile profile;
   private volatile List<String> droppablePackets;

   private long nextOutboundRelease;
   private long nextInboundRelease;

   public ShadowLagHandler(UUID owner, LagProfile profile, List<String> droppablePackets, boolean packetAware) {
      this.owner = owner;
      this.profile = profile;
      this.droppablePackets = droppablePackets == null ? List.of() : droppablePackets;
      this.packetAware = packetAware;
   }

   public UUID getOwner() {
      return this.owner;
   }

   public void update(LagProfile newProfile, List<String> newDroppablePackets) {
      this.profile = newProfile;
      this.droppablePackets = newDroppablePackets == null ? List.of() : newDroppablePackets;
   }

   @Override
   public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
      LagProfile current = this.profile;
      long delay = current == null ? 0L : current.nextOutboundDelay();
      if (delay <= 0L) {
         super.write(ctx, msg, promise);
         return;
      }

      long now = System.currentTimeMillis();
      long release = Math.max(now + delay, this.nextOutboundRelease);
      this.nextOutboundRelease = release;
      ctx.executor().schedule(() -> ctx.writeAndFlush(msg, promise), release - now, TimeUnit.MILLISECONDS);
   }

   @Override
   public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      LagProfile current = this.profile;
      if (current == null) {
         super.channelRead(ctx, msg);
         return;
      }

      if (this.packetAware && isDroppable(msg) && current.shouldDrop()) {
         ReferenceCountUtil.release(msg);
         return;
      }

      long delay = current.nextInboundDelay();
      if (delay <= 0L) {
         super.channelRead(ctx, msg);
         return;
      }

      long now = System.currentTimeMillis();
      long release = Math.max(now + delay, this.nextInboundRelease);
      this.nextInboundRelease = release;
      ctx.executor().schedule(() -> ctx.fireChannelRead(msg), release - now, TimeUnit.MILLISECONDS);
   }

   private boolean isDroppable(Object msg) {
      if (msg == null) {
         return false;
      }
      List<String> markers = this.droppablePackets;
      if (markers.isEmpty()) {
         return false;
      }
      String className = msg.getClass().getName();
      for (String marker : markers) {
         if (!marker.isEmpty() && className.contains(marker)) {
            return true;
         }
      }
      return false;
   }
}
