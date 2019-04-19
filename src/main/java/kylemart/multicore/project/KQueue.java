package kylemart.multicore.project;

import org.jetbrains.annotations.NotNull;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * A bounded, lock-free queue.
 *
 * <p>
 * A bounded lock-free k-FIFO Queue is a lock-free queue where elements may be dequeued out-of-order up to k-1, or
 * as a pool where elements may be dequeued within at most k dequeue operations. The queue is capable of k enqueue
 * and k dequeue operations being performed in parallel.
 * </p>
 *
 * @param <E> the type of elements held in this queue
 * @author Kyle Martinez
 * @see <a href="https://link.springer.com/chapter/10.1007/978-3-642-39958-9_18">Lock-Free k-FIFO Queues</a>
 */
public class KQueue<E> implements ConcurrentQueue<E> {

    /**
     * The index of the head segment; this is where dequeue operations occur.
     */
    private final AtomicInteger headIndex = new AtomicInteger();

    /**
     * The index of the tail segment; this is where enqueue operations occur.
     */
    private final AtomicInteger tailIndex = new AtomicInteger();

    /**
     * This should be read-only. Each k-segment in this array represents a queryable buffer of atomic references.
     */
    private final Segment<E>[] segments;

    /**
     * Instantiates a new KQueue with a specified number of segments and k.
     *
     * @param k            the number of enqueue and dequeue operations that can be performed in parallel
     * @param segmentCount the number of segments
     */
    @SuppressWarnings("unchecked")
    public KQueue(int k, int segmentCount) {
        segments = (Segment<E>[]) new Segment[segmentCount];
        for (int index = 0; index < segmentCount; index++) {
            segments[index] = new Segment<>(k);
        }
    }

    @Override
    public boolean enqueue(@NotNull E element) {
        int emptySlotIndex;

        while (true) {
            int oldTailIndex = tailIndex.get();
            int oldHeadIndex = headIndex.get();
            Segment<E> oldTailSegment = segments[oldTailIndex];
            Segment<E> oldHeadSegment = segments[oldHeadIndex];

            if (oldTailIndex != tailIndex.get()) {
                continue;
            }

            if ((emptySlotIndex = oldTailSegment.getEmptySlotIndex()) >= 0) {
                if (oldTailSegment.slots.compareAndSet(emptySlotIndex, null, element)) {
                    if (committed(oldTailIndex, oldTailSegment, emptySlotIndex, element)) {
                        return true;
                    }
                }
            } else {
                if (nextSegmentIndex(oldTailIndex) == oldHeadIndex) {
                    if (oldHeadSegment.isOccupied()) {
                        if (oldHeadIndex == headIndex.get()) {
                            return false;
                        }
                    } else {
                        advanceHeadIndex(oldHeadIndex);
                    }
                }
                advanceTailIndex(oldTailIndex);
            }
        }
    }

    @Override
    public E dequeue() {
        Segment.FindResult<E> found = new Segment.FindResult<>();

        while (true) {
            int oldTailIndex = tailIndex.get();
            int oldHeadIndex = headIndex.get();
            Segment<E> oldHeadSegment = segments[oldHeadIndex];

            if (oldHeadIndex != headIndex.get()) {
                continue;
            }

            if (oldHeadSegment.findFirstElement(found)) {
                if (oldTailIndex == oldHeadIndex) {
                    advanceTailIndex(oldTailIndex);
                }
                if (oldHeadSegment.slots.compareAndSet(found.index, found.element, null)) {
                    return found.element;
                }
            } else {
                if (oldTailIndex == oldHeadIndex && oldTailIndex == tailIndex.get()) {
                    return null;
                }
                advanceHeadIndex(oldHeadIndex);
            }
        }
    }

    /**
     * Validates an insertion. An insertion is valid if the inserted item already got dequeued at validation time by a
     * concurrent operation or it is in the desired range. Tries to undo the insertion if something went wrong.
     *
     * @param oldTailIndex    the old tail index
     * @param oldTailSegment  the old tail segment
     * @param slotIndex       the index where the expected element should lie
     * @param expectedElement the expected element
     * @return true if successful; false otherwise
     */
    private boolean committed(int oldTailIndex, Segment<E> oldTailSegment, int slotIndex, E expectedElement) {
        if (oldTailSegment.slots.get(slotIndex) != expectedElement) {
            return false;
        }

        int currentHeadIndex = headIndex.get();
        int currentTailIndex = tailIndex.get();

        boolean isInQueue = (currentHeadIndex < currentTailIndex) ?
                (currentHeadIndex <= oldTailIndex && oldTailIndex <= currentTailIndex) :
                (oldTailIndex <= currentTailIndex || currentHeadIndex <= oldTailIndex);

        if (isInQueue && oldTailIndex != currentHeadIndex) {
            return true;
        } else if (!isInQueue) {
            return !oldTailSegment.slots.compareAndSet(slotIndex, expectedElement, null);
        } else {
            if (currentHeadIndex == headIndex.get()) {
                return true;
            }
            return !oldTailSegment.slots.compareAndSet(slotIndex, expectedElement, null);
        }
    }

    /**
     * Returns the next logical index, with wrap-around, after the given index.
     *
     * @param segmentIndex the index to advance from
     * @return the next logical index, with wrap-around, after the base index
     */
    private int nextSegmentIndex(int segmentIndex) {
        return (segmentIndex + 1) % segments.length;
    }

    /**
     * Advances the head index to point to the next logical segment, with wrap-around.
     *
     * @param oldHeadIndex the old head index
     */
    private void advanceHeadIndex(int oldHeadIndex) {
        headIndex.compareAndSet(oldHeadIndex, nextSegmentIndex(oldHeadIndex));
    }

    /**
     * Advances the tail index to point to the next logical segment, with wrap-around.
     *
     * @param oldTailIndex the old tail index
     */
    private void advanceTailIndex(int oldTailIndex) {
        tailIndex.compareAndSet(oldTailIndex, nextSegmentIndex(oldTailIndex));
    }

    /**
     * A k-segment is a queryable buffer of atomic references.
     *
     * @param <E> the type of elements held in this segment
     */
    private static class Segment<E> {

        /**
         * Used in methods that require a random slot index to begin traversing from.
         */
        static final ThreadLocal<Random> randomReference = new ThreadLocal<>() {
            @Override
            protected synchronized Random initialValue() {
                return new Random();
            }
        };

        /**
         * The slots (atomic references) comprising this k-segment.
         */
        final AtomicReferenceArray<E> slots;

        /**
         * Instantiates a new k-segment with a length of k.
         *
         * @param k the length of this k-segment
         */
        Segment(int k) {
            slots = new AtomicReferenceArray<>(k);
        }

        /**
         * @return the index of an empty slot; otherwise -1 if no available slots are found.
         */
        int getEmptySlotIndex() {
            // Random used here as an optimization technique.
            Random random = randomReference.get();
            int start = random.nextInt(slots.length());

            for (int offset = 0; offset < slots.length(); offset++) {
                int index = (start + offset) % slots.length();
                E element = slots.get(index);
                if (element == null) {
                    return index;
                }
            }
            return -1;
        }

        /**
         * Traverses the slot in left-to-right order and returns the index of the first non-empty element found.
         *
         * @return the index of the non-empty slot found; otherwise -1.
         */
        boolean findFirstElement(FindResult<E> result) {
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
         * @return true if the segment contains at least one element; false otherwise
         */
        boolean isOccupied() {
            // Random used here as an optimization technique.
            Random random = randomReference.get();
            int start = random.nextInt(slots.length());

            for (int offset = 0; offset < slots.length(); offset++) {
                int index = (start + offset) % slots.length();
                E element = slots.get(index);
                if (element != null) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Used for storing the result of a find operation.
         *
         * @param <E> the type of the element stored within this result
         */
        private static class FindResult<E> {

            /**
             * Reference to the element found.
             */
            E element;

            /**
             * The index where the element was found.
             */
            int index;

            /**
             * Sets the properties of this result.
             *
             * @param element the element
             * @param index   the index where the element was found
             */
            void set(E element, int index) {
                this.element = element;
                this.index = index;
            }
        }
    }
}
