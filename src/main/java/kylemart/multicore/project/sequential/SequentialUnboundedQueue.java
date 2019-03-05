package kylemart.multicore.project.sequential;

import java.util.LinkedList;

/**
 * A sequential queue with an undefined maximal capacity.
 *
 * @param <T> the type of element the container will hold
 */
public class SequentialUnboundedQueue<T> implements SequentialQueue<T> {

    /**
     * A linked list of values comprising the queue.
     */
    private final LinkedList<T> values = new LinkedList<>();

    @Override
    public boolean enqueue(T value) {
        values.addLast(value);
        return true;
    }

    @Override
    public T dequeue() {
        return values.isEmpty() ? null : values.getFirst();
    }

    @Override
    public int size() {
        return values.size();
    }

}
