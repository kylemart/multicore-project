package kylemart.multicore.project;

import org.jetbrains.annotations.NotNull;
import org.multiverse.api.Stm;
import org.multiverse.api.StmUtils;
import org.multiverse.api.Txn;
import org.multiverse.api.callables.TxnBooleanCallable;
import org.multiverse.api.callables.TxnCallable;
import org.multiverse.api.callables.TxnVoidCallable;
import org.multiverse.api.collections.TxnQueue;
import static org.multiverse.api.GlobalStmInstance.getGlobalStmInstance;
import static org.multiverse.api.TxnThreadLocal.clearThreadLocalTxn;
import static org.multiverse.api.TxnThreadLocal.getRequiredThreadLocalTxn;

import org.multiverse.api.lifecycle.TxnEvent;
import org.multiverse.api.lifecycle.TxnListener;
import org.multiverse.api.references.TxnInteger;
import org.multiverse.api.references.TxnRef;
import org.multiverse.collections.NaiveTxnLinkedList;

import static org.multiverse.api.StmUtils.*;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;

public class STMQueue<I> implements ConcurrentQueue<I> {

    private NaiveTxnLinkedList<I> transactionalLinkedList;
    private Stm stm;

    public STMQueue(int capacity)
    {
        this.stm = getGlobalStmInstance();
        clearThreadLocalTxn();
        this.transactionalLinkedList = new NaiveTxnLinkedList<I>(this.stm, capacity);
    }

    @Override
    public boolean enqueue(I value) {
        return StmUtils.atomic(new TxnBooleanCallable() {
            @Override
            public boolean call(Txn txn) throws Exception {
                try
                {
                    transactionalLinkedList.addLast(txn, value);
                    return true;

                }catch(IllegalStateException expected) {
                    return false;
                }
            }
        });
    }

    @Override
    public I dequeue() {

        return StmUtils.atomic(new TxnCallable<I>() {
            @Override
            public I call(Txn txn) throws Exception{
                try{
                    return transactionalLinkedList.removeFirst(txn);
                } catch (NoSuchElementException exception) {
                    return null;
                }
            }
        });
    }

    public I nothing(I not) {
        return not;
    }

    public static void main(String[] args) throws InterruptedException
    {
        STMQueue<Integer> stm = new STMQueue<>(100);

        Thread t1 = new Thread(() -> {
            for(int i = 0; i < 1; i++) {
                stm.enqueue(i);
            }
            System.out.println("Enqueue done");
        });

        Thread t2 = new Thread(() -> {
            for(int i = 0; i < 50; i++) {
                stm.dequeue();
            }
            System.out.println("Dequeue done");
        });

        Thread t3 = new Thread(() -> {
            for(int i = 0; i < 50; i++) {
                stm.dequeue();
            }
            System.out.println("De done");
        });

        t1.start();
        t2.start();
        t3.start();

        t1.join();
        t2.join();
        t3.join();

        for(int i = 0; i < 100; i++) {
            System.out.println(stm.dequeue() + " -");
        }
    }
}
