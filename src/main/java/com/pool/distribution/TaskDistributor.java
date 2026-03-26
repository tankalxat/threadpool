package com.pool.distribution;

import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * Интерфейс стратегии распределения задач между очередями воркеров.
 */
@FunctionalInterface
public interface TaskDistributor {

    /**
     * Выбирает индекс очереди для размещения следующей задачи.
     *
     * @param queues список очередей воркеров
     * @return индекс выбранной очереди
     */
    int selectQueue(List<BlockingQueue<Runnable>> queues);
}
