package io.virgo.virgoNode.Utils;

import java.util.Comparator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PriorityQueueThreadPoolExecutor {
    private static final int DEFAULT_INITIAL_PRIORITY_QUEUE_CAPACITY = 100;
    private static final long THREAD_TIMEOUT_IN_SECS = 60L;
    public static final int DEFAULT_PRIORITY = 0;

    private static final AtomicInteger InstanceCounter = new AtomicInteger(0);

    private final ThreadPoolExecutor internalExecutor;

    public PriorityQueueThreadPoolExecutor(int threadPoolSize) {
        internalExecutor = new ThreadPoolExecutor(threadPoolSize, threadPoolSize, THREAD_TIMEOUT_IN_SECS,
                TimeUnit.SECONDS, createPriorityQueue());
        internalExecutor.allowCoreThreadTimeOut(true);
    }

    public void submit(Runnable runnable, int priority) {
        internalExecutor.execute(new RunnableWithPriority(runnable, priority));
    }

    public void submit(Runnable runnable) {
        submit(runnable, DEFAULT_PRIORITY);
    }

    public ThreadPoolExecutor getInternalThreadPoolExecutor() {
        return internalExecutor;
    }

    private static BlockingQueue<Runnable> createPriorityQueue() {
        return new PriorityBlockingQueue<>(DEFAULT_INITIAL_PRIORITY_QUEUE_CAPACITY,
                new ComparatorForPriorityRunnable());
    }

    private static class RunnableWithPriority implements Runnable {
        final int creationOrder;
        final int priority;
        final Runnable runnable;

        public RunnableWithPriority(Runnable runnable, int priority) {
            this.runnable = runnable;
            this.priority = priority;
            this.creationOrder = InstanceCounter.incrementAndGet();
        }

        @Override
        public void run() {
            runnable.run();
        }
    }

    private static class ComparatorForPriorityRunnable implements Comparator<Runnable> {
        @Override
        public int compare(Runnable r1, Runnable r2) {
            RunnableWithPriority pr1 = (RunnableWithPriority) r1;
            RunnableWithPriority pr2 = (RunnableWithPriority) r2;
            // higher value means higher priority
            int priorityResult = pr2.priority - pr1.priority;
            return priorityResult != 0 ? priorityResult : (pr1.creationOrder - pr2.creationOrder);
        }
    }
}
