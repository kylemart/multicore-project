package kylemart.multicore.project.sequential;

import kylemart.multicore.project.Queue;

/**
 * An interface for class of FIFO queues that are not thread safe.
 *
 * @param <T> the type of element the container will hold
 */
public interface SequentialQueue<T> extends Queue<T> {

    /**
     * @return the number of elements contained within the container
     */
    int size();
}
