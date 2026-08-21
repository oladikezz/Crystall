package net.myserver.engine.primitive;

import java.util.Arrays;
import java.util.function.BiConsumer;

/**
 * Высокопроизводительная примитивная хэш-таблица (Long -> Object) на открытой адресации.
 * Исключает боксинг примитивных ключей long и создание промежуточных Entry объектов.
 */
public class Long2ObjectOpenHashMap<V> {
    private static final float LOAD_FACTOR = 0.75f;
    private static final long EMPTY_KEY = Long.MIN_VALUE;

    private long[] keys;
    private Object[] values;
    private int mask;
    private int size;
    private int threshold;

    public Long2ObjectOpenHashMap() {
        this(16);
    }

    public Long2ObjectOpenHashMap(int initialCapacity) {
        int cap = 1;
        while (cap < initialCapacity) {
            cap <<= 1;
        }
        this.keys = new long[cap];
        Arrays.fill(keys, EMPTY_KEY);
        this.values = new Object[cap];
        this.mask = cap - 1;
        this.threshold = (int) (cap * LOAD_FACTOR);
        this.size = 0;
    }

    private static int hash(long key) {
        long h = key ^ (key >>> 32);
        h = (h ^ (h >>> 16)) * 0x45d9f3b;
        h = (h ^ (h >>> 16)) * 0x45d9f3b;
        h = h ^ (h >>> 16);
        return (int) h;
    }

    public synchronized V put(long key, V value) {
        if (key == EMPTY_KEY) {
            key = EMPTY_KEY + 1; // Защита от маркера пустоты
        }

        if (size >= threshold) {
            rehash(keys.length << 1);
        }

        int index = hash(key) & mask;
        while (keys[index] != EMPTY_KEY) {
            if (keys[index] == key) {
                @SuppressWarnings("unchecked")
                V old = (V) values[index];
                values[index] = value;
                return old;
            }
            index = (index + 1) & mask;
        }

        keys[index] = key;
        values[index] = value;
        size++;
        return null;
    }

    @SuppressWarnings("unchecked")
    public synchronized V get(long key) {
        if (key == EMPTY_KEY) {
            key = EMPTY_KEY + 1;
        }

        int index = hash(key) & mask;
        while (keys[index] != EMPTY_KEY) {
            if (keys[index] == key) {
                return (V) values[index];
            }
            index = (index + 1) & mask;
        }
        return null;
    }

    public synchronized V getOrDefault(long key, V defaultValue) {
        V v = get(key);
        return v != null ? v : defaultValue;
    }

    public synchronized boolean containsKey(long key) {
        return get(key) != null;
    }

    @SuppressWarnings("unchecked")
    public synchronized V remove(long key) {
        if (key == EMPTY_KEY) {
            key = EMPTY_KEY + 1;
        }

        int index = hash(key) & mask;
        while (keys[index] != EMPTY_KEY) {
            if (keys[index] == key) {
                V old = (V) values[index];
                keys[index] = EMPTY_KEY;
                values[index] = null;
                size--;
                cleanupShift(index);
                return old;
            }
            index = (index + 1) & mask;
        }
        return null;
    }

    private void cleanupShift(int start) {
        int current = (start + 1) & mask;
        while (keys[current] != EMPTY_KEY) {
            long keyToShift = keys[current];
            Object valToShift = values[current];
            keys[current] = EMPTY_KEY;
            values[current] = null;
            size--;

            put(keyToShift, (V) valToShift);
            current = (current + 1) & mask;
        }
    }

    private void rehash(int newCapacity) {
        long[] oldKeys = keys;
        Object[] oldValues = values;

        this.keys = new long[newCapacity];
        Arrays.fill(keys, EMPTY_KEY);
        this.values = new Object[newCapacity];
        this.mask = newCapacity - 1;
        this.threshold = (int) (newCapacity * LOAD_FACTOR);
        this.size = 0;

        for (int i = 0; i < oldKeys.length; i++) {
            if (oldKeys[i] != EMPTY_KEY) {
                @SuppressWarnings("unchecked")
                V val = (V) oldValues[i];
                put(oldKeys[i], val);
            }
        }
    }

    public synchronized int size() {
        return size;
    }

    public synchronized boolean isEmpty() {
        return size == 0;
    }

    public synchronized void clear() {
        Arrays.fill(keys, EMPTY_KEY);
        Arrays.fill(values, null);
        size = 0;
    }

    public synchronized void forEach(BiConsumer<Long, V> action) {
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] != EMPTY_KEY) {
                @SuppressWarnings("unchecked")
                V val = (V) values[i];
                action.accept(keys[i], val);
            }
        }
    }
}
