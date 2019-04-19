package kylemart.multicore.project;

import org.multiverse.api.Stm;
import org.multiverse.api.StmUtils;
import org.multiverse.api.Txn;
import org.multiverse.api.callables.TxnBooleanCallable;
import org.multiverse.api.callables.TxnCallable;
import org.multiverse.collections.NaiveTxnLinkedList;

import static org.multiverse.api.GlobalStmInstance.getGlobalStmInstance;
import static org.multiverse.api.TxnThreadLocal.clearThreadLocalTxn;

import java.util.NoSuchElementException;

/**
 * A bounded, lock-free transitional memory queue
 *
 * <p>
 * A bounded lock-free software transactional memory queue is a lock-free queue
 * transactional memory using the Multiverse Software Transactional Memory library.
 * </p>
 *
 * @param <I> The type of elements held in this queue
 * @author Daquaris Chadwick
 */
class STMQueue<I> implements ConcurrentQueue<I> {

    /**
     * The structure that is representing the queue
     */
    private NaiveTxnLinkedList<I> transactionalLinkedList;

    /**
     * The Stm Instance
     */
    private Stm stm;

    /**
     * Instantiates a new STMQueue holding at most capacity
     *
     * @param capacity The maximum members the queue can hold
     */
    public STMQueue(int capacity) {
        this.stm = getGlobalStmInstance();
        clearThreadLocalTxn();
        this.transactionalLinkedList = new NaiveTxnLinkedList<I>(this.stm, capacity);
    }

    @Override
    public boolean enqueue(I value) {
        return StmUtils.atomic(new TxnBooleanCallable() {
            @Override
            public boolean call(Txn txn) throws Exception {
                try {
                    transactionalLinkedList.addLast(txn, value);
                    return true;

                } catch (IllegalStateException expected) {
                    return false;
                }
            }
        });
    }

    @Override
    public I dequeue() {
        return StmUtils.atomic(new TxnCallable<I>() {
            @Override
            public I call(Txn txn) throws Exception {
                try {
                    return transactionalLinkedList.removeFirst(txn);
                } catch (NoSuchElementException exception) {
                    return null;
                }
            }
        });
    }
}
