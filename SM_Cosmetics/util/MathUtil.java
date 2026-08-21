package net.schalker.SMPS.modules.cosmetics.util;

import org.bukkit.util.Vector;
import java.util.concurrent.ThreadLocalRandom;

/**
 *    
 *   ProCosmetics
 */
public final class MathUtil {

    private static final ThreadLocalRandom RANDOM = ThreadLocalRandom.current();

    private MathUtil() {}

    public static double randomRange(double min, double max) {
        return RANDOM.nextDouble() * (max - min) + min;
    }

    public static int randomRange(int min, int max) {
        return RANDOM.nextInt(max - min) + min;
    }

    /**
     *     Y
     */
    public static Vector rotateAroundAxisY(Vector vector, double angle) {
        double x = vector.getX();
        double z = vector.getZ();
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return vector.setX(x * cos + z * sin).setZ(x * -sin + z * cos);
    }

    /**
     *     Z
     */
    public static Vector rotateAroundAxisZ(Vector vector, double angle) {
        double x = vector.getX();
        double y = vector.getY();
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return vector.setX(x * cos - y * sin).setY(x * sin + y * cos);
    }

    /**
     *     X
     */
    public static Vector rotateAroundAxisX(Vector vector, double angle) {
        double y = vector.getY();
        double z = vector.getZ();
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return vector.setY(y * cos - z * sin).setZ(y * sin + z * cos);
    }

    /**
     *  
     */
    public static double lerp(double a, double b, double t) {
        return a == b ? a : a * (1 - t) + b * t;
    }
}
