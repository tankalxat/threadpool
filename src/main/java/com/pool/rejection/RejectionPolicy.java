package com.pool.rejection;

import com.pool.CustomThreadPool;

/**
 * Политика обработки задач, которые не могут быть приняты пулом
 * (все очереди заполнены и достигнуто максимальное число потоков).
 */
@FunctionalInterface
public interface RejectionPolicy {

    /**
     * Вызывается при отклонении задачи.
     *
     * @param task отклонённая задача
     * @param pool пул, отклонивший задачу
     */
    void reject(Runnable task, CustomThreadPool pool);
}
