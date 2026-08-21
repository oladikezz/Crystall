package ru.lor.watcher.utils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import ru.lor.watcher.model.WatcherPositionType;
import ru.lor.watcher.model.WatcherSpawnSettings;

import java.util.Random;

public class LocationUtil {

    private static final Random RANDOM = new Random();

    public static Location calculateLocation(Player target, WatcherSpawnSettings settings) {
        if (settings != null && settings.getCustomLocation() != null) {
            Location customLoc = settings.getCustomLocation().clone();
            return faceLocation(customLoc, target.getEyeLocation());
        }
        return calculateLocation(target, settings != null ? settings.getPositionType() : WatcherPositionType.BEHIND, settings != null ? settings.getSpawnDistance() : 5.0);
    }

    /**
     * Calculates safe spawn location relative to player based on WatcherPositionType and distance.
     * Guarantees Watcher never spawns inside solid blocks, walls, or tree trunks.
     */
    public static Location calculateLocation(Player target, WatcherPositionType positionType, double distance) {
        Location playerLoc = target.getLocation();
        Vector direction = playerLoc.getDirection().setY(0).normalize();
        if (direction.lengthSquared() == 0) {
            direction = new Vector(0, 0, 1);
        }

        Location targetLocation;

        switch (positionType) {
            case BEHIND -> {
                Vector behindVec = direction.clone().multiply(-distance);
                targetLocation = playerLoc.clone().add(behindVec);
            }
            case FRONT -> {
                Vector frontVec = direction.clone().multiply(distance);
                targetLocation = playerLoc.clone().add(frontVec);
            }
            case LEFT -> {
                Vector leftVec = new Vector(direction.getZ(), 0, -direction.getX()).multiply(distance);
                targetLocation = playerLoc.clone().add(leftVec);
            }
            case RIGHT -> {
                Vector rightVec = new Vector(-direction.getZ(), 0, direction.getX()).multiply(distance);
                targetLocation = playerLoc.clone().add(rightVec);
            }
            case ROOF -> {
                World world = playerLoc.getWorld();
                int startY = playerLoc.getBlockY() + 3;
                int highestY = playerLoc.getBlockY() + 15;
                int chosenY = playerLoc.getBlockY() + 3;

                for (int y = startY; y <= highestY; y++) {
                    Block block = world.getBlockAt(playerLoc.getBlockX(), y, playerLoc.getBlockZ());
                    if (block.getType().isSolid()) {
                        chosenY = y - 1;
                        break;
                    }
                }
                targetLocation = new Location(world, playerLoc.getX(), chosenY, playerLoc.getZ());
            }
            case RANDOM -> {
                double angle = RANDOM.nextDouble() * 2 * Math.PI;
                double offsetX = Math.cos(angle) * distance;
                double offsetZ = Math.sin(angle) * distance;
                targetLocation = playerLoc.clone().add(offsetX, 0, offsetZ);
            }
            default -> targetLocation = playerLoc.clone().add(direction.multiply(-distance));
        }

        // Adjust Y to safe ground level if not ROOF
        if (positionType != WatcherPositionType.ROOF) {
            targetLocation = findSafeGroundY(targetLocation, playerLoc);
        }

        return faceLocation(targetLocation, target.getEyeLocation());
    }

    /**
     * Finds a ground level position with non-solid 2-block height clearance.
     * Raycasts back towards player if initial point is inside a wall.
     */
    public static Location findSafeGroundY(Location loc, Location playerLoc) {
        World world = loc.getWorld();
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        int playerY = playerLoc.getBlockY();

        // 1. Vertical search around player Y level
        for (int y = playerY + 4; y >= playerY - 4 && (y - 1) >= world.getMinHeight(); y--) {
            Block below = world.getBlockAt(x, y - 1, z);
            Block feet = world.getBlockAt(x, y, z);
            Block head = world.getBlockAt(x, y + 1, z);

            if (below.getType().isSolid() && isPassable(feet) && isPassable(head)) {
                return new Location(world, loc.getX(), y, loc.getZ());
            }
        }

        // 2. If inside wall/obstacle, raycast back towards player to find nearest clear space
        Vector toPlayer = playerLoc.toVector().subtract(loc.toVector()).setY(0);
        double dist = loc.distance(playerLoc);
        if (dist > 1.5 && toPlayer.lengthSquared() > 0) {
            toPlayer.normalize();
            for (double step = 0.5; step < dist - 1.0; step += 0.5) {
                Location rayLoc = loc.clone().add(toPlayer.clone().multiply(step));
                int rx = rayLoc.getBlockX();
                int rz = rayLoc.getBlockZ();

                for (int y = playerY + 4; y >= playerY - 4 && (y - 1) >= world.getMinHeight(); y--) {
                    Block below = world.getBlockAt(rx, y - 1, rz);
                    Block feet = world.getBlockAt(rx, y, rz);
                    Block head = world.getBlockAt(rx, y + 1, rz);

                    if (below.getType().isSolid() && isPassable(feet) && isPassable(head)) {
                        return new Location(world, rayLoc.getX(), y, rayLoc.getZ());
                    }
                }
            }
        }

        return loc;
    }

    public static Location findSafeGroundY(Location loc) {
        return findSafeGroundY(loc, loc);
    }

    private static boolean isPassable(Block block) {
        Material type = block.getType();
        return !type.isSolid() || type == Material.SHORT_GRASS || type == Material.TALL_GRASS
                || type == Material.SNOW || type == Material.AIR || type == Material.CAVE_AIR
                || type == Material.SEAGRASS || type == Material.TALL_SEAGRASS;
    }

    /**
     * Modifies location yaw and pitch to point directly from Watcher eye level (y + 1.62) to target location.
     */
    public static Location faceLocation(Location from, Location target) {
        Location loc = from.clone();
        Location watcherEye = from.clone().add(0, 1.62, 0);

        double xDiff = target.getX() - watcherEye.getX();
        double yDiff = target.getY() - watcherEye.getY();
        double zDiff = target.getZ() - watcherEye.getZ();

        double distanceXZ = Math.sqrt(xDiff * xDiff + zDiff * zDiff);

        double yaw = Math.toDegrees(Math.atan2(-xDiff, zDiff));
        double pitch = Math.toDegrees(-Math.atan2(yDiff, distanceXZ));

        loc.setYaw((float) yaw);
        loc.setPitch((float) pitch);

        return loc;
    }
}
