package net.myserver;

import net.myserver.engine.CircularBuffer;
import net.myserver.engine.ObjectPool;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class EngineBuffersTest {

    @Test
    public void testCircularBuffer() {
        CircularBuffer<String> buffer = new CircularBuffer<>(8);
        assertTrue(buffer.isEmpty());
        assertEquals(0, buffer.size());

        assertTrue(buffer.offer("Item-1"));
        assertTrue(buffer.offer("Item-2"));
        assertTrue(buffer.offer("Item-3"));

        assertEquals(3, buffer.size());
        assertEquals("Item-1", buffer.poll());
        assertEquals("Item-2", buffer.poll());
        assertEquals(1, buffer.size());

        assertEquals("Item-3", buffer.poll());
        assertTrue(buffer.isEmpty());
        assertNull(buffer.poll());
    }

    @Test
    public void testObjectPool() {
        ObjectPool.MutablePos pos = ObjectPool.acquirePos(10.5, 64.0, -20.5);
        assertNotNull(pos);
        assertEquals(10.5, pos.x);
        assertEquals(64.0, pos.y);
        assertEquals(-20.5, pos.z);

        ObjectPool.release(pos);
        ObjectPool.MutablePos recycled = ObjectPool.acquirePos(0, 0, 0);
        assertNotNull(recycled);
    }
}
