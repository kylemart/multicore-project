package kylemart.multicore.project.concurrent;

import kylemart.multicore.project.Queue;
import org.jetbrains.annotations.NotNull;

import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * A lock-free, bounded-size, K-FIFO Queue, as described in the paper "Fast and Scalable, Lock-Free k-FIFO Queues".
 *
 * @author Kyle Martinez
 */
public class BoundedKQueue<E> implements Queue<E> {

    private final static Random random = new Random();

    private final AtomicReference<AtomicValue<Integer>> head = new AtomicReference<>(new AtomicValue<>(0, 0));
    private final AtomicReference<AtomicValue<Integer>> tail = new AtomicReference<>(new AtomicValue<>(0, 0));
    private final AtomicReferenceArray<AtomicValue<E>> slots;
    private final int k;

    public BoundedKQueue(int k, int numSegments) {
        this.k = k;
        slots = new AtomicReferenceArray<>(k * numSegments);
        for (int index = 0; index < slots.length(); ++index) {
            slots.set(index, new AtomicValue<>(null, 0));
        }
    }

    @Override
    public boolean enqueue(@NotNull E item) {
        while (true) {
            AtomicValue<Integer> oldTail = tail.get();
            AtomicValue<Integer> oldHead = head.get();

            int[] indexOut = new int[1];
            AtomicValue<E> oldItem = tryFindEmptySlot(oldTail.value, k, indexOut);
            int index = indexOut[0];

            if (oldTail == tail.get()) {
                if (oldItem.value == null) {
                    AtomicValue<E> newItem = new AtomicValue<>(item, oldItem.version + 1);
                    if (slots.compareAndSet(oldTail.value + index, oldItem, newItem)) {
                        if (isCommitted(oldTail, newItem, index)) {
                            return true;
                        }
                    }
                } else {
                    if (oldTail.value + k == oldHead.value) {
                        if (isSegmentNotEmpty(oldHead.value, k)) {
                            if (oldHead == head.get()) {
                                return false;
                            }
                        } else {
                            advanceHead(oldHead, k);
                        }
                    }
                    advanceTail(oldTail, k);
                }
            }
        }
    }

    @Override
    public E dequeue() {
        while (true) {
            AtomicValue<Integer> oldTail = tail.get();
            AtomicValue<Integer> oldHead = head.get();

            int[] indexOut = new int[1];
            AtomicValue<E> oldItem = tryFindItem(oldHead.value, k, indexOut);
            int index = indexOut[0];

            if (oldHead == head.get()) {
                if (oldItem.value != null) {
                    if (oldHead.value.equals(oldTail.value)) {
                        advanceTail(oldTail, k);
                    }
                    AtomicValue<E> emptyItem = new AtomicValue<>(null, oldItem.version + 1);
                    if (slots.compareAndSet(oldHead.value + index, oldItem, emptyItem)) {
                        return oldItem.value;
                    }
                } else {
                    if (oldHead == oldTail && oldTail == tail.get()) {
                        return null;
                    }
                    advanceHead(oldHead, k);
                }
            }
        }
    }

    private boolean isSegmentNotEmpty(int from, int to) {
        final int start = from + random.nextInt(to);
        final int limit = to - from;

        for (int offset = 0; offset < limit; ++offset) {
            int index = (start + offset) % to + from;
            AtomicValue<E> item = slots.get(index);
            if (item.value != null) {
                return true;
            }
        }

        return false;
    }

    private AtomicValue<E> tryFindEmptySlot(int from, int to, int[] indexOut) {
        final int start = from + random.nextInt(to);
        final int limit = to - from;

        for (int offset = 0; offset < limit; ++offset) {
            int index = (start + offset) % to + from;
            AtomicValue<E> item = slots.get(index);
            if (item.value == null) {
                indexOut[0] = index;
                return item;
            }
        }

        return slots.get(start);
    }

    private AtomicValue<E> tryFindItem(int from, int to, int[] indexOut) {
        for (int index = from; index < to; ++index) {
            AtomicValue<E> item = slots.get(index);
            if (item.value != null) {
                indexOut[0] = index;
                return item;
            }
        }

        return slots.get(from);
    }

    private boolean isCommitted(AtomicValue<Integer> oldTail, AtomicValue<E> newItem, int index) {
        if (slots.get(oldTail.value + index) != newItem) {
            return true;
        }

        AtomicValue<Integer> currentHead = head.get();
        AtomicValue<Integer> currentTail = tail.get();

        AtomicValue<E> emptyItem = new AtomicValue<>(null, newItem.version + 1);

        if (currentHead.value < oldTail.value && oldTail.value <= currentTail.value) {
            return true;
        } else if (oldTail.value < currentTail.value || currentHead.value < oldTail.value) {
            return !slots.compareAndSet(oldTail.value + index, newItem, emptyItem);
        }

        AtomicValue<Integer> newHead = new AtomicValue<>(currentHead.value, currentHead.version + 1);
        return (head.compareAndSet(currentHead, newHead) || !slots.compareAndSet(oldTail.value, newItem, emptyItem));
    }

    private void advanceTail(AtomicValue<Integer> oldTail, int by) {
        int advancedValue = (oldTail.value + by) % slots.length();
        AtomicValue<Integer> newTail = new AtomicValue<>(advancedValue, oldTail.version + 1);
        tail.compareAndSet(oldTail, newTail);
    }

    private void advanceHead(AtomicValue<Integer> oldHead, int by) {
        int advancedValue = (oldHead.value + by) % slots.length();
        AtomicValue<Integer> newHead = new AtomicValue<>(advancedValue, oldHead.version + 1);
        head.compareAndSet(oldHead, newHead);
    }

    private static class AtomicValue<V> {

        final V value;
        final int version;

        AtomicValue(V value, int version) {
            this.value = value;
            this.version = version;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        BoundedKQueue<Integer> queue = new BoundedKQueue<>(1, 10);

        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 100; ++i) {
                queue.enqueue(1);
                System.out.println("t1: added 1");
                System.out.println("t1: got " + queue.dequeue());
            }
        });
        t1.start();

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 100; ++i) {
                queue.enqueue(2);
                System.out.println("t2: added 2");
                System.out.println("t2: got " + queue.dequeue());
            }
        });
        t2.start();

        Thread t3 = new Thread(() -> {
            for (int i = 0; i < 100; ++i) {
                queue.enqueue(3);
                System.out.println("t3: added 3");
                System.out.println("t3: got " + queue.dequeue());
            }
        });
        t3.start();

        t1.join();
        t2.join();
        t3.join();
    }
}
