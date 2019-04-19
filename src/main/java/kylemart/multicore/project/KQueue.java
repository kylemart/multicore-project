package kylemart.multicore.project;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class KQueue<E> implements ConcurrentQueue<E> {

    /**
     *
     */
    private final AtomicInteger headIndex = new AtomicInteger();

    /**
     *
     */
    private final AtomicInteger tailIndex = new AtomicInteger();

    /**
     *
     */
    private final Segment<E>[] segments;

    /**
     *
     * @param k
     * @param segmentCount
     */
    @SuppressWarnings("unchecked")
    public KQueue(int k, int segmentCount) {
        segments = (Segment<E>[]) new Segment[segmentCount];
        for (int index = 0; index < segmentCount; index++) {
            segments[index] = new Segment<>(k);
        }
    }

    /**
     *
     * @param element
     * @return
     */
    @Override
    public boolean enqueue(E element) {
        FindResult<E> found = new FindResult<>();

        while (true) {
            int oldTailIndex = tailIndex.get();
            int oldHeadIndex = headIndex.get();

            if (oldTailIndex != tailIndex.get()) {
                continue;
            }

            Segment<E> oldTailSegment = segments[oldTailIndex];
            Segment<E> oldHeadSegment = segments[oldHeadIndex];

            if (oldTailSegment.getEmptySlot(found)) {
                if (oldTailSegment.slots.compareAndSet(found.index, found.element, element)) {
                    if (committed(oldTailIndex, oldTailSegment, found.index, element)) {
                        return true;
                    }
                }
            }
            else {
                if (nextSegmentIndex(oldTailIndex) == oldHeadIndex) {
                    if (oldHeadSegment.isOccupied()) {
                        if (oldHeadIndex == headIndex.get()) {
                            return false;
                        }
                    }
                    else {
                        advanceHeadIndex(oldHeadIndex);
                    }
                }
                advanceTailIndex(oldTailIndex);
            }
        }
    }

    /**
     *
     * @return
     */
    @Override
    public E dequeue() {
        FindResult<E> found = new FindResult<>();

        while (true) {
            int oldTailIndex = tailIndex.get();
            int oldHeadIndex = headIndex.get();

            if (oldHeadIndex != headIndex.get()) {
                continue;
            }

            Segment<E> oldHeadSegment = segments[oldHeadIndex];

            if (oldHeadSegment.getFirstElement(found)) {
                if (oldTailIndex == oldHeadIndex) {
                    advanceTailIndex(oldTailIndex);
                }
                if (oldHeadSegment.slots.compareAndSet(found.index, found.element, null)) {
                    return found.element;
                }
            }
            else {
                if (oldTailIndex == oldHeadIndex && oldTailIndex == tailIndex.get()) {
                    return null;
                }
                advanceHeadIndex(oldHeadIndex);
            }
        }
    }

    private boolean committed(int oldTailIndex, Segment<E> oldTailSegment, int slotIndex, E expectedElement) {
        if (oldTailSegment.slots.get(slotIndex) != expectedElement) {
            return false;
        }

        int currentHeadIndex = headIndex.get();
        int currentTailIndex = tailIndex.get();

        if (currentTailIndex < currentHeadIndex && (oldTailIndex <= currentTailIndex || currentHeadIndex < oldTailIndex) ||
            currentHeadIndex <= currentTailIndex && (currentHeadIndex < oldTailIndex && currentTailIndex < oldTailIndex)) {
            return true;
        }
        else if (currentTailIndex < currentHeadIndex && (oldTailIndex <= currentTailIndex || currentHeadIndex <= oldTailIndex) ||
                currentHeadIndex <= currentTailIndex && (currentHeadIndex <= oldTailIndex && currentTailIndex < oldTailIndex)) {
            return !oldTailSegment.slots.compareAndSet(slotIndex, expectedElement, null);
        }
        else {
            if (headIndex.compareAndSet(currentHeadIndex, currentHeadIndex)) {
                return true;
            }
            return !oldTailSegment.slots.compareAndSet(slotIndex, expectedElement, null);
        }
    }

    /**
     *
     * @param segmentIndex
     * @return
     */
    private int nextSegmentIndex(int segmentIndex) {
        return (segmentIndex + 1) % segments.length;
    }

    /**
     *
     * @param oldHeadIndex
     */
    private void advanceHeadIndex(int oldHeadIndex) {
        headIndex.compareAndSet(oldHeadIndex, nextSegmentIndex(oldHeadIndex));
    }

    /**
     *
     * @param oldTailIndex
     */
    private void advanceTailIndex(int oldTailIndex) {
        tailIndex.compareAndSet(oldTailIndex, nextSegmentIndex(oldTailIndex));
    }

    /**
     *
     * @param <E>
     */
    private static class Segment<E> {

        /**
         *
         */
        final AtomicReferenceArray<E> slots;

        /**
         *
         * @param k
         */
        Segment(int k) {
            slots = new AtomicReferenceArray<>(k);
        }

        /**
         *
         * @param result
         * @return
         */
        boolean getEmptySlot(FindResult<E> result) {
            for (int index = 0; index < slots.length(); index++) {
                E element = slots.get(index);
                if (element == null) {
                    result.set(null, index);
                    return true;
                }
            }
            return false;
        }

        /**
         *
         * @param result
         * @return
         */
        boolean getFirstElement(FindResult<E> result) {
            for (int index = 0; index < slots.length(); index++) {
                E element = slots.get(index);
                if (element != null) {
                    result.set(element, index);
                    return true;
                }
            }
            return false;
        }

        /**
         *
         * @return
         */
        boolean isOccupied() {
            for (int index = 0; index < slots.length(); index++) {
                E element = slots.get(index);
                if (element != null) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     *
     * @param <V>
     */
    private static class FindResult<V> {

        V element;
        int index;

        void set(V element, int index) {
            this.element = element;
            this.index = index;
        }
    }
}
