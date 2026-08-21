package net.schalker.SMPS.modules.quietban.transport;

import io.netty.channel.Channel;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.bukkit.entity.Player;

public final class ChannelResolver {

   private static final int MAX_DEPTH = 4;

   private ChannelResolver() {
   }

   public static Channel resolve(Player player) {
      if (player == null) {
         return null;
      }
      try {
         Object handle = player.getClass().getMethod("getHandle").invoke(player);
         return search(handle, 0, Collections.newSetFromMap(new IdentityHashMap<>()));
      } catch (Throwable throwable) {
         return null;
      }
   }

   private static Channel search(Object source, int depth, Set<Object> visited) {
      if (source == null || depth > MAX_DEPTH || !visited.add(source)) {
         return null;
      }

      List<Object> candidates = new ArrayList<>();
      Class<?> type = source.getClass();
      while (type != null && type != Object.class) {
         for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
               continue;
            }
            Object value = read(field, source);
            if (value instanceof Channel channel) {
               return channel;
            }
            if (isNetworkCandidate(value)) {
               candidates.add(value);
            }
         }
         type = type.getSuperclass();
      }

      for (Object candidate : candidates) {
         Channel channel = search(candidate, depth + 1, visited);
         if (channel != null) {
            return channel;
         }
      }
      return null;
   }

   private static boolean isNetworkCandidate(Object value) {
      if (value == null) {
         return false;
      }
      String className = value.getClass().getName();
      return className.startsWith("net.minecraft.network")
         || className.startsWith("net.minecraft.server.network")
         || className.contains("PacketListener")
         || className.contains("Connection");
   }

   private static Object read(Field field, Object source) {
      try {
         field.setAccessible(true);
         return field.get(source);
      } catch (Throwable throwable) {
         return null;
      }
   }
}
