package net.myserver;

import net.myserver.engine.FastMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FastMathTest {

    @Test
    public void testSinCosPrecision() {
        for (float deg = 0; deg < 360; deg += 15.0f) {
            float rad = (float) Math.toRadians(deg);
            float expectedSin = (float) Math.sin(rad);
            float actualSin = FastMath.sin(rad);
            assertEquals(expectedSin, actualSin, 0.01f, "Sin mismatch at degree " + deg);

            float expectedCos = (float) Math.cos(rad);
            float actualCos = FastMath.cos(rad);
            assertEquals(expectedCos, actualCos, 0.01f, "Cos mismatch at degree " + deg);
        }
    }

    @Test
    public void testInvSqrt() {
        float[] testValues = {1.0f, 4.0f, 16.0f, 100.0f, 256.0f};
        for (float val : testValues) {
            float expected = (float) (1.0 / Math.sqrt(val));
            float actual = FastMath.invSqrt(val);
            assertEquals(expected, actual, 0.01f, "InvSqrt mismatch for " + val);
        }
    }

    @Test
    public void testChunkPacking() {
        int[][] testPairs = {
            {0, 0},
            {12, -45},
            {-999, 1234},
            {Integer.MAX_VALUE / 2, Integer.MIN_VALUE / 2}
        };

        for (int[] pair : testPairs) {
            int cx = pair[0];
            int cz = pair[1];
            long packed = FastMath.packChunkPos(cx, cz);
            assertEquals(cx, FastMath.unpackChunkX(packed), "ChunkX unpacking mismatch");
            assertEquals(cz, FastMath.unpackChunkZ(packed), "ChunkZ unpacking mismatch");
        }
    }

    @Test
    public void testBlockPosPacking() {
        int x = 12345;
        int y = 64;
        int z = -9876;
        long packed = FastMath.packBlockPos(x, y, z);
        assertEquals(x, FastMath.unpackBlockX(packed));
        assertEquals(y, FastMath.unpackBlockY(packed));
        assertEquals(z, FastMath.unpackBlockZ(packed));
    }
}
