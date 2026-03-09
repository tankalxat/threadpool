package com.pool.rejection;

import com.pool.CustomThreadPool;
import com.pool.PoolLogger;

/**
 * Молча отклоняет задачу.
 */
public class DiscardPolicy implements RejectionPolicy {

    @Override
    public void reject(Runnable task, CustomThreadPool pool) {
        PoolLogger.taskRejected(task.toString(), "DiscardPolicy (задача отброшена)");
    }
}
