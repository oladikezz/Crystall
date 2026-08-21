package net.myserver.engine.primitive;

import java.util.Arrays;
import java.util.function.LongConsumer;

/**
 * Высокопроизводительный примитивный хэш-сет (LongSet) на открытой адресации.
 * Идеален для отслеживания загруженных/грязных чанков и координат.
 */
public class LongOpenHashSet {
    private static final float LOAD_FACTOR = 0.75f;
    private static final long EMPTY_KEY = Long.MIN_VALUE;

    private long[] table;
    private int mask;
    private int size;
    private int threshold;

    public LongOpenHashSet() {
        this(16);
    }

    public LongOpenHashSet(int initialCapacity) {
        int cap = 1;
        while (cap < initialCapacity) {
            cap <<= 1;
        }
        this.table = new long[cap];
        Arrays.fill(table, EMPTY_KEY);
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

    public synchronized boolean add(long key) {
        if (key == EMPTY_KEY) {
            key = EMPTY_KEY + 1;
        }

        if (size >= threshold) {
            rehash(table.length << 1);
        }

        int index = hash(key) & mask;
        while (table[index] != EMPTY_KEY) {
            if (table[index] == key) {
                return false; // Уже существует
            }
            index = (index + 1) & mask;
        }

        table[index] = key;
        size++;
        return true;
    }

    public synchronized boolean contains(long key) {
        if (key == EMPTY_KEY) {
            key = EMPTY_KEY + 1;
        }

        int index = hash(key) & mask;
        while (table[index] != EMPTY_KEY) {
            if (table[index] == key) {
                return true;
            }
            index = (index + 1) & mask;
        }
        return false;
    }

    public synchronized boolean remove(long key) {
        if (key == EMPTY_KEY) {
            key = EMPTY_KEY + 1;
        }

        int index = hash(key) & mask;
        while (table[index] != EMPTY_KEY) {
            if (table[index] == key) {
                table[index] = EMPTY_KEY;
                size--;
                cleanupShift(index);
                return true;
            }
            index = (index + 1) & mask;
        }
        return false;
    }

    private void cleanupShift(int start) {
        int current = (start + 1) & mask;
        while (table[current] != EMPTY_KEY) {
            long keyToShift = table[current];
            table[current] = EMPTY_KEY;
            size--;

            add(keyToShift);
            current = (current + 1) & mask;
        }
    }

    private void rehash(int newCapacity) {
        long[] oldTable = table;
        this.table = new long[newCapacity];
        Arrays.fill(table, EMPTY_KEY);
        this.mask = newCapacity - 1;
        this.threshold = (int) (newCapacity * LOAD_FACTOR);
        this.size = 0;

        for (long key : oldTable) {
            if (key != EMPTY_KEY) {
                add(key);
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
        Arrays.fill(table, EMPTY_KEY);
        size = 0;
    }

    public synchronized void forEach(LongConsumer action) {
        for (long key : table) {
            if (key != EMPTY_KEY) {
                action.accept(key);
            }
        }
    }
}
