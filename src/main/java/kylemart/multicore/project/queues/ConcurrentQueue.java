package kylemart.multicore.project.queues;

/**
 * An interface for the class of containers that are FIFO queues.
 *
 * @param <T> the type of element the container will hold
 */
public interface ConcurrentQueue<T> {

    /**
     * Enqueues the {@code value} to the end of the queue.
     *
     * @return <code>true</code> if the value was enqueued; <code>false</code> otherwise
     */
    boolean enqueue(T value);

    /**
     * Removes an element from the front of the queue.
     *
     * @return <code>true</code> if a value was removed; <code>false</code> otherwise
     */
    T dequeue();
}
