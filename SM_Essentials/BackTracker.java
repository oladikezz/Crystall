package net.schalker.SMPS.modules.essentials;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import net.schalker.DoAPI.DoAPI;

public final class BackTracker {
   private static final Map<UUID, Location> LAST_LOCATIONS = new ConcurrentHashMap<>();
   private static final Map<UUID, Boolean> SKIP_NEXT = new ConcurrentHashMap<>();
   private static final String PDC_KEY = "back_location";

   private BackTracker() {
   }

   public static void record(DoAPI plugin, Player player) {
      if (player == null) {
         return;
      }
      Location location = player.getLocation();
      if (location == null) {
         return;
      }
      record(plugin, player, location);
   }

   public static void record(DoAPI plugin, Player player, Location from) {
      if (player == null || from == null) {
         return;
      }
      LAST_LOCATIONS.put(player.getUniqueId(), from.clone());
      saveToPdc(plugin, player, from);
   }

   public static Location pop(DoAPI plugin, Player player) {
      if (player == null) {
         return null;
      }
      UUID playerId = player.getUniqueId();
      Location location = LAST_LOCATIONS.remove(playerId);
      if (location == null) {
         location = loadFromPdc(plugin, player);
      }
      clearPdc(plugin, player);
      return location != null ? location.clone() : null;
   }


   public static Location getLast(DoAPI plugin, Player player) {
      if (player == null) {
         return null;
      }
      UUID playerId = player.getUniqueId();
      Location location = LAST_LOCATIONS.get(playerId);
      if (location == null) {
         location = loadFromPdc(plugin, player);
      }
      return location != null ? location.clone() : null;
   }

   public static Location swap(DoAPI plugin, Player player, Location newLocation) {
      if (player == null) {
         return null;
      }
      Location last = getLast(plugin, player);
      if (newLocation != null) {
         record(plugin, player, newLocation);
      }
      return last;
   }

   public static void markSkipNext(UUID playerId) {
      if (playerId == null) {
         return;
      }
      SKIP_NEXT.put(playerId, Boolean.TRUE);
   }

   public static boolean consumeSkipNext(UUID playerId) {
      if (playerId == null) {
         return false;
      }
      return SKIP_NEXT.remove(playerId) != null;
   }

   private static NamespacedKey key(DoAPI plugin) {
      if (plugin == null) {
         return null;
      }
      return new NamespacedKey(plugin, PDC_KEY);
   }

   private static void saveToPdc(DoAPI plugin, Player player, Location location) {
      NamespacedKey key = key(plugin);
      if (key == null || player == null || location == null || location.getWorld() == null) {
         return;
      }
      String encoded = encode(location);
      if (encoded == null) {
         return;
      }
      PersistentDataContainer container = player.getPersistentDataContainer();
      container.set(key, PersistentDataType.STRING, encoded);
   }

   private static Location loadFromPdc(DoAPI plugin, Player player) {
      NamespacedKey key = key(plugin);
      if (key == null || player == null) {
         return null;
      }
      PersistentDataContainer container = player.getPersistentDataContainer();
      String encoded = container.get(key, PersistentDataType.STRING);
      return decode(encoded);
   }

   private static void clearPdc(DoAPI plugin, Player player) {
      NamespacedKey key = key(plugin);
      if (key == null || player == null) {
         return;
      }
      player.getPersistentDataContainer().remove(key);
   }

   private static String encode(Location location) {
      if (location == null || location.getWorld() == null) {
         return null;
      }
      return location.getWorld().getName()
         + ";" + location.getX()
         + ";" + location.getY()
         + ";" + location.getZ()
         + ";" + location.getYaw()
         + ";" + location.getPitch();
   }

   private static Location decode(String encoded) {
      if (encoded == null || encoded.isEmpty()) {
         return null;
      }
      String[] parts = encoded.split(";");
      if (parts.length < 6) {
         return null;
      }
      World world = Bukkit.getWorld(parts[0]);
      if (world == null) {
         return null;
      }
      try {
         double x = Double.parseDouble(parts[1]);
         double y = Double.parseDouble(parts[2]);
         double z = Double.parseDouble(parts[3]);
         float yaw = Float.parseFloat(parts[4]);
         float pitch = Float.parseFloat(parts[5]);
         return new Location(world, x, y, z, yaw, pitch);
      } catch (NumberFormatException e) {
         return null;
      }
   }
}
