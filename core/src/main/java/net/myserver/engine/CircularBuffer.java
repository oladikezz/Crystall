package net.myserver.engine;

import java.util.function.Consumer;

/**
 * Кольцевой буфер (Circular Ring Buffer) с нулевыми промежуточными аллокациями.
 * Идеально подходит для очередей тикинга жидкостей и отложенных задач.
 */
public class CircularBuffer<E> {
    private final Object[] elements;
    private final int capacity;
    private final int mask;
    private int head = 0;
    private int tail = 0;
    private int size = 0;

    public CircularBuffer(int initialCapacityPowerOfTwo) {
        int cap = 1;
        while (cap < initialCapacityPowerOfTwo) {
            cap <<= 1;
        }
        this.capacity = cap;
        this.mask = cap - 1;
        this.elements = new Object[cap];
    }

    public synchronized boolean offer(E element) {
        if (size == capacity) {
            return false; // Буфер полон
        }
        elements[tail] = element;
        tail = (tail + 1) & mask;
        size++;
        return true;
    }

    @SuppressWarnings("unchecked")
    public synchronized E poll() {
        if (size == 0) {
            return null;
        }
        E element = (E) elements[head];
        elements[head] = null; // Помощь GC
        head = (head + 1) & mask;
        size--;
        return element;
    }

    public synchronized int size() {
        return size;
    }

    public synchronized boolean isEmpty() {
        return size == 0;
    }

    public synchronized void clear() {
        for (int i = 0; i < capacity; i++) {
            elements[i] = null;
        }
        head = 0;
        tail = 0;
        size = 0;
    }

    @SuppressWarnings("unchecked")
    public synchronized void drainTo(Consumer<E> consumer, int maxElements) {
        int count = Math.min(size, maxElements);
        for (int i = 0; i < count; i++) {
            E element = (E) elements[head];
            elements[head] = null;
            head = (head + 1) & mask;
            size--;
            if (element != null) {
                consumer.accept(element);
            }
        }
    }
}
