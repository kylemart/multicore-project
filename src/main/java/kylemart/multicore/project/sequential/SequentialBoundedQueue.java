package kylemart.multicore.project.sequential;

/**
 * A sequential queue with a defined, maximum capacity.
 *
 * @param <T> the type of element the container will hold
 */
public class SequentialBoundedQueue<T> implements SequentialQueue<T> {

    /**
     * The inner, unbounded queue that this container wraps.
     */
    private final SequentialQueue<T> values = new SequentialUnboundedQueue<>();

    /**
     * The maximum number of elements this container can hold.
     */
    private final int capacity;

    /**
     * Creates a new bounded sequential queue with a maximal capacity.
     *
     * @param capacity the maximal number of elements the container can hold
     */
    public SequentialBoundedQueue(int capacity) {
        this.capacity = capacity;
    }

    /**
     * If the container has not yet reached its maximal capacity, the value will be added to the end of the queue and
     * a return value of <code>true</code> will be yielded; otherwise, the return value yielded will be
     * <code>false</code>.
     *
     * @param value the value being enqueued
     * @return true if the enqueue operation succeeded
     */
    @Override
    public boolean enqueue(T value) {
        return values.size() < capacity && values.enqueue(value);
    }

    @Override
    public T dequeue() {
        return values.dequeue();
    }

    @Override
    public int size() {
        return values.size();
    }
}
