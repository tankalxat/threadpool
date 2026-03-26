package com.pool;

import com.pool.distribution.TaskDistributor;
import com.pool.rejection.RejectionPolicy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Кастомный пул потоков с отдельными очередями для каждого воркера,
 * настраиваемой балансировкой нагрузки, политиками отказа,
 * поддержкой minSpareThreads и подробным логированием.
 */
public class CustomThreadPool implements CustomExecutor {

    private static final int RUNNING = 0;
    private static final int SHUTDOWN = 1;
    private static final int STOP = 2;
    private static final int TERMINATED = 3;

    private volatile int state = RUNNING;

    private final PoolConfig config;
    private final CustomThreadFactory threadFactory;
    private final List<Worker> workers = new ArrayList<>();
    private final List<BlockingQueue<Runnable>> queues = new ArrayList<>();
    private final ReentrantLock mainLock = new ReentrantLock();
    private final AtomicInteger workerCount = new AtomicInteger(0);
    private final AtomicInteger idleCount = new AtomicInteger(0);
    private final CountDownLatch terminationLatch = new CountDownLatch(1);

    public CustomThreadPool(PoolConfig config) {
        this.config = config;
        this.threadFactory = new CustomThreadFactory(config.getPoolName());

        mainLock.lock();
        try {
            for (int i = 0; i < config.getCorePoolSize(); i++) {
                addWorker();
            }
        } finally {
            mainLock.unlock();
        }
    }

    @Override
    public void execute(Runnable command) {
        if (command == null) throw new NullPointerException("Задача не должна быть null");

        if (state >= SHUTDOWN) {
            config.getRejectionPolicy().reject(command, this);
            return;
        }

        ensureSpareThreads();

        if (!tryEnqueue(command)) {
            if (!tryScaleUpAndEnqueue(command)) {
                config.getRejectionPolicy().reject(command, this);
            }
        }
    }

    @Override
    public <T> Future<T> submit(Callable<T> callable) {
        if (callable == null) throw new NullPointerException("Callable не должен быть null");
        FutureTask<T> futureTask = new FutureTask<>(callable);
        execute(futureTask);
        return futureTask;
    }

    @Override
    public void shutdown() {
        mainLock.lock();
        try {
            if (state >= SHUTDOWN) return;
            state = SHUTDOWN;
            PoolLogger.shutdownInitiated(config.getPoolName());
        } finally {
            mainLock.unlock();
        }
    }

    @Override
    public List<Runnable> shutdownNow() {
        mainLock.lock();
        try {
            state = STOP;
            PoolLogger.shutdownNowInitiated(config.getPoolName());

            for (Worker worker : workers) {
                worker.interrupt();
            }

            List<Runnable> remaining = new ArrayList<>();
            for (BlockingQueue<Runnable> queue : queues) {
                queue.drainTo(remaining);
            }
            return remaining;
        } finally {
            mainLock.unlock();
        }
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return terminationLatch.await(timeout, unit);
    }

    public String getName() {
        return config.getPoolName();
    }

    public PoolConfig getConfig() {
        return config;
    }

    public boolean isShutdown() {
        return state >= SHUTDOWN;
    }

    public boolean isStopped() {
        return state >= STOP;
    }

    public boolean isTerminated() {
        return state == TERMINATED;
    }

    public int getWorkerCount() {
        return workerCount.get();
    }

    public int getIdleCount() {
        return idleCount.get();
    }

    public int getActiveCount() {
        return workerCount.get() - idleCount.get();
    }

    public List<BlockingQueue<Runnable>> getQueues() {
        return Collections.unmodifiableList(queues);
    }

    public int getTotalQueueSize() {
        int total = 0;
        for (BlockingQueue<Runnable> q : queues) {
            total += q.size();
        }
        return total;
    }

    void onWorkerBusy(Worker worker) {
        idleCount.decrementAndGet();
    }

    void onWorkerIdle(Worker worker) {
        idleCount.incrementAndGet();
        ensureSpareThreads();
    }

    void onWorkerTerminated(Worker worker) {
        workerCount.decrementAndGet();
        int oldSize;

        mainLock.lock();
        try {
            oldSize = workers.size();
            workers.remove(worker);
            queues.remove(worker.getQueue());

            int newSize = workers.size();
            if (newSize < oldSize) {
                PoolLogger.scaleDown(config.getPoolName(), oldSize, newSize);
            }

            if (state >= SHUTDOWN && workers.isEmpty()) {
                state = TERMINATED;
                PoolLogger.poolTerminated(config.getPoolName());
                terminationLatch.countDown();
            }
        } finally {
            mainLock.unlock();
        }
    }

    boolean canWorkerTerminate(Worker worker) {
        int currentCount = workerCount.get();
        int currentIdle = idleCount.get();

        if (currentCount <= config.getCorePoolSize()) {
            // кроме случая, когда пул завершается и очередь пуста
            return state >= SHUTDOWN && worker.getQueue().isEmpty();
        }

        return currentIdle > config.getMinSpareThreads();
    }

    private Worker addWorker() {
        int id = workers.size();
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(config.getQueueSize());
        Worker worker = new Worker(id, queue, this);

        workers.add(worker);
        queues.add(queue);

        Thread thread = threadFactory.newThread(worker);
        worker.setThread(thread);

        workerCount.incrementAndGet();
        idleCount.incrementAndGet();
        thread.start();

        return worker;
    }

    private boolean tryEnqueue(Runnable command) {
        List<BlockingQueue<Runnable>> currentQueues;
        mainLock.lock();
        try {
            currentQueues = new ArrayList<>(queues);
        } finally {
            mainLock.unlock();
        }

        if (currentQueues.isEmpty()) return false;

        int selected = config.getDistributor().selectQueue(currentQueues);
        BlockingQueue<Runnable> queue = currentQueues.get(selected);

        if (queue.offer(command)) {
            PoolLogger.taskAccepted(selected, command.toString());
            return true;
        }

        for (int i = 0; i < currentQueues.size(); i++) {
            if (i == selected) continue;
            if (currentQueues.get(i).offer(command)) {
                PoolLogger.taskAccepted(i, command.toString());
                return true;
            }
        }

        return false;
    }

    private boolean tryScaleUpAndEnqueue(Runnable command) {
        mainLock.lock();
        try {
            if (workerCount.get() >= config.getMaxPoolSize()) {
                return false;
            }

            int oldSize = workers.size();
            Worker newWorker = addWorker();
            PoolLogger.scaleUp(config.getPoolName(), oldSize, workers.size());

            if (newWorker.getQueue().offer(command)) {
                PoolLogger.taskAccepted(workers.size() - 1, command.toString());
                return true;
            }
            return false;
        } finally {
            mainLock.unlock();
        }
    }

    private void ensureSpareThreads() {
        if (config.getMinSpareThreads() <= 0) return;

        int idle = idleCount.get();
        if (idle >= config.getMinSpareThreads()) return;

        mainLock.lock();
        try {
            idle = idleCount.get();
            int currentWorkers = workerCount.get();

            while (idle < config.getMinSpareThreads() && currentWorkers < config.getMaxPoolSize()) {
                int oldSize = workers.size();
                Worker w = addWorker();
                PoolLogger.spareThreadCreated(
                        w.getThread().getName(), idle, config.getMinSpareThreads());
                PoolLogger.scaleUp(config.getPoolName(), oldSize, workers.size());
                idle = idleCount.get();
                currentWorkers = workerCount.get();
            }
        } finally {
            mainLock.unlock();
        }
    }
}
