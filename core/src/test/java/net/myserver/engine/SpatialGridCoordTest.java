package net.myserver.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SpatialGridCoordTest {

    @Test
    public void testPackChunkCoord() {
        int cx = 15;
        int cz = -30;
        long packed = SpatialGrid.packChunkCoord(cx, cz);
        
        long expected = FastMath.packChunkPos(cx, cz);
        assertEquals(expected, packed, "SpatialGrid packing must match FastMath.packChunkPos");
    }

    @Test
    public void testPackBoundaryValues() {
        int[] testValues = {0, 1, -1, 1000, -1000, 30000000, -30000000};
        for (int x : testValues) {
            for (int z : testValues) {
                long packed = SpatialGrid.packChunkCoord(x, z);
                long expected = FastMath.packChunkPos(x, z);
                assertEquals(expected, packed);
            }
        }
    }
}
