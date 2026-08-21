package net.myserver;

import net.myserver.engine.primitive.Long2ObjectOpenHashMap;
import net.myserver.engine.primitive.LongOpenHashSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PrimitiveCollectionsTest {

    @Test
    public void testLong2ObjectMapBasicOperations() {
        Long2ObjectOpenHashMap<String> map = new Long2ObjectOpenHashMap<>();
        assertTrue(map.isEmpty());
        assertEquals(0, map.size());

        map.put(100L, "Alpha");
        map.put(200L, "Beta");
        map.put(-300L, "Gamma");

        assertEquals(3, map.size());
        assertEquals("Alpha", map.get(100L));
        assertEquals("Beta", map.get(200L));
        assertEquals("Gamma", map.get(-300L));
        assertNull(map.get(999L));

        // Overwrite
        map.put(100L, "Alpha-Updated");
        assertEquals("Alpha-Updated", map.get(100L));
        assertEquals(3, map.size());

        // Remove
        String removed = map.remove(200L);
        assertEquals("Beta", removed);
        assertEquals(2, map.size());
        assertNull(map.get(200L));
    }

    @Test
    public void testLong2ObjectMapResizing() {
        Long2ObjectOpenHashMap<Integer> map = new Long2ObjectOpenHashMap<>(8);
        for (int i = 0; i < 1000; i++) {
            map.put((long) i, i * 10);
        }

        assertEquals(1000, map.size());
        for (int i = 0; i < 1000; i++) {
            assertEquals(i * 10, map.get((long) i));
        }
    }

    @Test
    public void testLongOpenHashSetBasicOperations() {
        LongOpenHashSet set = new LongOpenHashSet();
        assertTrue(set.isEmpty());

        assertTrue(set.add(12345L));
        assertTrue(set.add(-67890L));
        assertFalse(set.add(12345L)); // Duplicate

        assertEquals(2, set.size());
        assertTrue(set.contains(12345L));
        assertTrue(set.contains(-67890L));
        assertFalse(set.contains(999L));

        assertTrue(set.remove(12345L));
        assertEquals(1, set.size());
        assertFalse(set.contains(12345L));
    }

    @Test
    public void testLongOpenHashSetResizing() {
        LongOpenHashSet set = new LongOpenHashSet(8);
        for (int i = 0; i < 2000; i++) {
            assertTrue(set.add((long) i));
        }

        assertEquals(2000, set.size());
        for (int i = 0; i < 2000; i++) {
            assertTrue(set.contains((long) i));
        }
    }
}
