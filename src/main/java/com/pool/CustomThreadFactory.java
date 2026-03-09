package com.pool;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Фабрика, создающая именованные потоки с логированием и обработкой неперехваченных исключений.
 */
public class CustomThreadFactory implements ThreadFactory {

    private final String poolName;
    private final AtomicInteger counter = new AtomicInteger(1);

    public CustomThreadFactory(String poolName) {
        this.poolName = poolName;
    }

    @Override
    public Thread newThread(Runnable r) {
        String threadName = poolName + "-worker-" + counter.getAndIncrement();
        Thread thread = new Thread(r, threadName);
        thread.setDaemon(false);

        thread.setUncaughtExceptionHandler((t, e) ->
                PoolLogger.taskFailed(t.getName(), "неперехваченное исключение", e));

        PoolLogger.threadCreated(threadName);
        return thread;
    }
}
