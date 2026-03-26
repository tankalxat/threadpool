package com.pool.rejection;

import com.pool.CustomThreadPool;
import com.pool.PoolLogger;

import java.util.concurrent.RejectedExecutionException;

/**
 * Выбрасывает RejectedExecutionException при отклонении задачи.
 * Политика по умолчанию - делает перегрузку немедленно видимой для вызывающего кода.
 */
public class AbortPolicy implements RejectionPolicy {

    @Override
    public void reject(Runnable task, CustomThreadPool pool) {
        String desc = task.toString();
        PoolLogger.taskRejected(desc, "AbortPolicy");
        throw new RejectedExecutionException("Задача " + desc + " отклонена пулом " + pool.getName());
    }
}
