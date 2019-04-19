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

class StmBoundedQueue<T> implements ConcurrentQueue<T> {
    private final TxnInteger availableItems, availableSpaces;
    private final TxnRef<T>[] items;
    private final TxnInteger head, tail;

    public StmBoundedQueue(int capacity) {
        this.availableItems = newTxnInteger(0);
        this.availableSpaces = newTxnInteger(capacity);
        this.items = makeArray(capacity);
        for (int i=0; i<capacity; i++)
            this.items[i] = StmUtils.<T>newTxnRef();
        this.head = newTxnInteger(0);
        this.tail = newTxnInteger(0);
    }

    @SuppressWarnings("unchecked")
    private static <T> TxnRef<T>[] makeArray(int capacity) {
        // Java's @$#@?!! type system requires this unsafe cast
        return (TxnRef<T>[])new TxnRef[capacity];
    }

    public boolean isEmpty() {
        return atomic(() -> availableItems.get() == 0);
    }

    public boolean isFull() {
        return atomic(() -> availableSpaces.get() == 0);
    }

    public void put(T item) {     // at tail
        atomic(() -> {
            if (availableSpaces.get() == 0)
                retry();
            else {
                availableSpaces.decrement();
                items[tail.get()].set(item);
                tail.set((tail.get() + 1) % items.length);
                availableItems.increment();
            }
        });
    }

    @Override
    public boolean enqueue(T item) {
        return atomic(() -> {
            if (availableSpaces.get() == 0)
            {
                retry();
                return true;
            }
            else {
                availableSpaces.decrement();
                items[tail.get()].set(item);
                tail.set((tail.get() + 1) % items.length);
                availableItems.increment();
                return true;
            }
        });
    }

    @Override
    public T dequeue() {
        return atomic(() -> {
            if (availableItems.get() == 0) {
                return null;    // unreachable
            } else {
                availableItems.decrement();
                T item = items[head.get()].get();
                items[head.get()].set(null);
                head.set((head.get() + 1) % items.length);
                availableSpaces.increment();
                return item;
            }
        });
    }

    public static void main(String[] args) throws InterruptedException {
        for(int i = 0; i < 100; i++)
        {
            StmBoundedQueue<Integer> queue = new StmBoundedQueue<>(100);

            Thread t1 = new Thread(() -> {
                queue.enqueue(1);

            });

            Thread t2 = new Thread(() -> {
                queue.dequeue();
            });

            t1.start();
            t2.start();

            t1.join();
            t2.join();

            System.out.println(queue.dequeue());
            System.out.println("---------------");
        }
    }
}
