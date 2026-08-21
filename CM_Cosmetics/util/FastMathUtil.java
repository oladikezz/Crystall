package net.schalker.SMPS.modules.cosmetics.util;

/**
 *     
 *   ProCosmetics
 */
public final class FastMathUtil {

    private static final int SIN_BITS = 12;
    private static final int SIN_MASK = ~(-1 << SIN_BITS);
    private static final int SIN_COUNT = SIN_MASK + 1;

    private static final float RAD_FULL = (float) (Math.PI * 2.0d);
    private static final float DEG_FULL = 360.0f;
    private static final float RAD_TO_INDEX = SIN_COUNT / RAD_FULL;
    private static final float DEG_TO_INDEX = SIN_COUNT / DEG_FULL;

    private static final float[] SIN = new float[SIN_COUNT];
    private static final float[] COS = new float[SIN_COUNT];

    public static final float DEGREES_TO_RADIANS = 0.017453292f;
    public static final float PI = 3.1415926535897f;

    static {
        for (int i = 0; i < SIN_COUNT; i++) {
            SIN[i] = (float) Math.sin((i + 0.5f) / SIN_COUNT * RAD_FULL);
            COS[i] = (float) Math.cos((i + 0.5f) / SIN_COUNT * RAD_FULL);
        }
        double rad = Math.PI / 180.0d;
        for (int i = 0; i < 360; i += 90) {
            SIN[(int) (i * DEG_TO_INDEX) & SIN_MASK] = (float) Math.sin(i * rad);
            COS[(int) (i * DEG_TO_INDEX) & SIN_MASK] = (float) Math.cos(i * rad);
        }
    }

    public static float sin(float rad) {
        return SIN[(int) (rad * RAD_TO_INDEX) & SIN_MASK];
    }

    public static float cos(float rad) {
        return COS[(int) (rad * RAD_TO_INDEX) & SIN_MASK];
    }

    public static float toRadians(float angle) {
        return DEGREES_TO_RADIANS * angle;
    }

    public static float toRadians(double angle) {
        return (float) (DEGREES_TO_RADIANS * angle);
    }
}
