package net.schalker.SMPS.modules.trafficoptimizer.transport;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.schalker.DoAPI.DoAPI;
import org.bukkit.entity.Player;

public class ParticleFilterManager {

   private static final String PIPELINE_ANCHOR = "packet_handler";

   private final DoAPI plugin;
   private final Map<UUID, Channel> attached = new ConcurrentHashMap<>();

   private volatile List<String> markers = List.of();
   private volatile boolean warned;

   public ParticleFilterManager(DoAPI plugin) {
      this.plugin = plugin;
   }

   public void setMarkers(List<String> markers) {
      this.markers = markers == null ? List.of() : List.copyOf(markers);
   }

   public int getAttachedCount() {
      return this.attached.size();
   }

   public long getDropped(UUID playerId) {
      Channel channel = this.attached.get(playerId);
      if (channel == null) {
         return 0L;
      }
      ChannelHandler handler = channel.pipeline().get(ParticleFilterHandler.NAME);
      return handler instanceof ParticleFilterHandler filter ? filter.getDropped() : 0L;
   }

   public void apply(Player player, double dropChance) {
      if (dropChance <= 0.0D) {
         this.detach(player.getUniqueId());
         return;
      }

      UUID playerId = player.getUniqueId();
      List<String> current = this.markers;
      if (current.isEmpty()) {
         return;
      }

      Channel channel = this.attached.get(playerId);
      if (channel == null) {
         channel = ChannelResolver.resolve(player);
         if (channel == null) {
            if (!this.warned) {
               this.warned = true;
               this.plugin.getDebugSystem().logWarning("TrafficOptimizer",
                  "Не удалось получить сетевой канал игрока, фильтр частиц не применяется");
            }
            return;
         }
         this.attached.put(playerId, channel);
      }

      Channel target = channel;
      target.eventLoop().execute(() -> {
         try {
            ChannelPipeline pipeline = target.pipeline();
            ChannelHandler existing = pipeline.get(ParticleFilterHandler.NAME);
            if (existing instanceof ParticleFilterHandler filter) {
               filter.update(dropChance, current);
               return;
            }
            ParticleFilterHandler filter = new ParticleFilterHandler(dropChance, current);
            if (pipeline.get(PIPELINE_ANCHOR) != null) {
               pipeline.addBefore(PIPELINE_ANCHOR, ParticleFilterHandler.NAME, filter);
            } else {
               pipeline.addLast(ParticleFilterHandler.NAME, filter);
            }
         } catch (Throwable throwable) {
            this.attached.remove(playerId);
            this.plugin.getDebugSystem().logError("TrafficOptimizer",
               "Не удалось установить фильтр частиц", throwable);
         }
      });
   }

   public void detach(UUID playerId) {
      Channel channel = this.attached.remove(playerId);
      if (channel == null) {
         return;
      }
      channel.eventLoop().execute(() -> {
         try {
            if (channel.pipeline().get(ParticleFilterHandler.NAME) != null) {
               channel.pipeline().remove(ParticleFilterHandler.NAME);
            }
         } catch (Throwable ignored) {
            return;
         }
      });
   }

   public void detachAll() {
      for (UUID playerId : List.copyOf(this.attached.keySet())) {
         this.detach(playerId);
      }
      this.attached.clear();
   }
}
