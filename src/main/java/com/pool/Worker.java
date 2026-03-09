package com.pool;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Рабочий поток, непрерывно извлекающий задачи из своей выделенной очереди.
 * <p>
 * Жизненный цикл:
 * - Извлекает задачи с таймаутом keepAliveTime
 * - Выполняет задачи, логируя начало/завершение/ошибки
 * - Завершается по таймауту простоя, если число потоков > corePoolSize (с учётом minSpareThreads)
 * - Завершается при shutdown/прерывании
 */
public class Worker implements Runnable {

    private final int id;
    private final BlockingQueue<Runnable> queue;
    private final CustomThreadPool pool;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private volatile Thread thread;

    public Worker(int id, BlockingQueue<Runnable> queue, CustomThreadPool pool) {
        this.id = id;
        this.queue = queue;
        this.pool = pool;
    }

    public int getId() {
        return id;
    }

    public BlockingQueue<Runnable> getQueue() {
        return queue;
    }

    public boolean isRunning() {
        return running.get();
    }

    public void setThread(Thread thread) {
        this.thread = thread;
    }

    public Thread getThread() {
        return thread;
    }

    public void interrupt() {
        running.set(false);
        Thread t = this.thread;

        if (t != null) {
            t.interrupt();
        }
    }

    @Override
    public void run() {
        this.thread = Thread.currentThread();
        String threadName = Thread.currentThread().getName();

        try {
            while (running.get()) {
                Runnable task;

                try {
                    task = queue.poll(
                            pool.getConfig().getKeepAliveTime(),
                            pool.getConfig().getTimeUnit());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                if (task == null) {
                    if (pool.canWorkerTerminate(this)) {
                        PoolLogger.idleTimeout(threadName);
                        break;
                    }

                    continue;
                }

                if (pool.isStopped()) {
                    break;
                }

                executeTask(task, threadName);
            }
        } finally {
            running.set(false);
            pool.onWorkerTerminated(this);
            PoolLogger.threadTerminated(threadName);
        }
    }

    private void executeTask(Runnable task, String threadName) {
        String desc = task.toString();
        PoolLogger.taskExecuting(threadName, desc);
        pool.onWorkerBusy(this);
        long start = System.currentTimeMillis();

        try {
            task.run();
            long elapsed = System.currentTimeMillis() - start;
            PoolLogger.taskCompleted(threadName, desc, elapsed);
        } catch (Throwable t) {
            PoolLogger.taskFailed(threadName, desc, t);

            if (t instanceof Error) {
                throw (Error) t;
            }
        } finally {
            pool.onWorkerIdle(this);
        }
    }
}
