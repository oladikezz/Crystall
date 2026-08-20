package net.myserver.engine;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Thread-Local пул переиспользуемых объектов для критических путей тикинга и физики.
 * Полностью устраняет аллокации временных Pos/Vec/Point объектов.
 */
public class ObjectPool {
    private static final int MAX_POOL_SIZE = 128;

    // Пул для изменяемых позиций
    public static class MutablePos {
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;

        public MutablePos set(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        public MutablePos set(double x, double y, double z, float yaw, float pitch) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            return this;
        }

        public Pos toImmutable() {
            return new Pos(x, y, z, yaw, pitch);
        }
    }

    private static final ThreadLocal<Deque<MutablePos>> posPool = ThreadLocal.withInitial(ArrayDeque::new);

    public static MutablePos acquirePos(double x, double y, double z) {
        Deque<MutablePos> deque = posPool.get();
        MutablePos pos = deque.pollFirst();
        if (pos == null) {
            pos = new MutablePos();
        }
        return pos.set(x, y, z);
    }

    public static MutablePos acquirePos(Point p) {
        return acquirePos(p.x(), p.y(), p.z());
    }

    public static void release(MutablePos pos) {
        if (pos == null) return;
        Deque<MutablePos> deque = posPool.get();
        if (deque.size() < MAX_POOL_SIZE) {
            deque.offerFirst(pos);
        }
    }
}
