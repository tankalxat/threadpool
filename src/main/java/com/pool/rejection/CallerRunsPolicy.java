package com.pool.rejection;

import com.pool.CustomThreadPool;
import com.pool.PoolLogger;

/**
 * Выполняет отклонённую задачу в потоке вызывающего.
 * Обеспечивает естественный backpressure - отправляющий поток замедляется.
 */
public class CallerRunsPolicy implements RejectionPolicy {

    @Override
    public void reject(Runnable task, CustomThreadPool pool) {
        String desc = task.toString();
        PoolLogger.taskRejected(desc, "CallerRunsPolicy (выполнение в потоке вызывающего)");

        if (!pool.isShutdown()) {
            task.run();
        }
    }
}
