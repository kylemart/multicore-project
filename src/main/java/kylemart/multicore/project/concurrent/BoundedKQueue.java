package kylemart.multicore.project.concurrent;

import kylemart.multicore.project.Queue;
import org.jetbrains.annotations.NotNull;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * A lock-free, bounded-size, K-FIFO Queue, as described in the paper "Fast and Scalable, Lock-Free k-FIFO Queues".
 *
 * @author Kyle Martinez
 */
public class BoundedKQueue<E> implements Queue<E> {

    private final static Random random = new Random();

    private final AtomicInteger head = new AtomicInteger(0);
    private final AtomicInteger tail = new AtomicInteger(0);
    private final AtomicReferenceArray<KQueueItem<E>> slots;
    private final int k;

    public BoundedKQueue(int k, int numSegments) {
        this.k = k;
        slots = new AtomicReferenceArray<>(k * numSegments);
        for (int index = 0; index < slots.length(); ++index) {
            slots.set(index, new KQueueItem<>(null, 0));
        }
    }

    @Override
    public boolean enqueue(@NotNull E item) {
        while (true) {
            int oldTail = tail.get();
            int oldHead = head.get();

            int[] indexOut = new int[1];
            KQueueItem<E> oldItem = tryFind(SlotType.EMPTY, oldTail, k, indexOut);
            int index = indexOut[0];

            if (oldTail == tail.get()) {
                if (oldItem.value == null) {
                    KQueueItem<E> newItem = new KQueueItem<>(item, oldItem.version + 1);
                    if (slots.compareAndSet(oldTail + index, oldItem, newItem)) {
                        if (isCommitted(oldTail, newItem, index)) {
                            return true;
                        }
                    }
                } else {
                    if (oldTail + k == oldHead) {
                        if (isSegmentNotEmpty(oldHead, k)) {
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
            int oldTail = tail.get();
            int oldHead = head.get();

            int[] indexOut = new int[1];
            KQueueItem<E> oldItem = tryFind(SlotType.NON_EMPTY, oldTail, k, indexOut);
            int index = indexOut[0];

            if (oldHead == head.get()) {
                if (oldItem.value != null) {
                    if (oldHead == oldTail) {
                        advanceTail(oldTail, k);
                    }
                    KQueueItem<E> emptyItem = new KQueueItem<>(null, oldItem.version + 1);
                    if (slots.compareAndSet(oldHead + index, oldItem, emptyItem)) {
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

        int index = start;
        do {
            if (slots.get(index) != null) {
                return true;
            }
            index = (index + 1) % to + from;
        } while (index != start);

        return false;
    }

    private KQueueItem<E> tryFind(SlotType slotType, int from, int to, int[] indexOut) {
        final int start = from + random.nextInt(to);

        int index = start;
        do {
            KQueueItem<E> item = slots.get(index);
            if (slotType == SlotType.EMPTY && item.value == null) {
                indexOut[0] = index;
                return item;
            }
            index = (index + 1) % to + from;
        } while (index != start);

        return slots.get(start);
    }

    private boolean isCommitted(int oldTail, KQueueItem<E> item, int index) {
        // TODO - Need to implement
        return false;
    }

    private void advanceTail(int oldTail, int by) {
        tail.compareAndExchange(oldTail, (oldTail + by) % slots.length());
    }

    private void advanceHead(int oldHead, int by) {
        head.compareAndExchange(oldHead, (oldHead + by) % slots.length());
    }

    private static class KQueueItem<E> {

        final E value;
        final int version;

        KQueueItem(E value, int version) {
            this.value = value;
            this.version = version;
        }
    }

    private enum SlotType {
        NON_EMPTY, EMPTY
    }
}
