package com.pool.rejection;

import com.pool.CustomThreadPool;
import com.pool.PoolLogger;

import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * Удаляет самую старую задачу из наиболее заполненной очереди и повторяет отправку.
 * Приоритет свежих данных над устаревшими задачами в очереди.
 */
public class DiscardOldestPolicy implements RejectionPolicy {

    @Override
    public void reject(Runnable task, CustomThreadPool pool) {

        if (pool.isShutdown()) {
            PoolLogger.taskRejected(task.toString(), "DiscardOldestPolicy (пул остановлен, задача отброшена)");
            return;
        }

        List<BlockingQueue<Runnable>> queues = pool.getQueues();
        BlockingQueue<Runnable> fullest = null;
        int fullestIndex = -1;
        int maxSize = 0;

        for (int i = 0; i < queues.size(); i++) {
            int size = queues.get(i).size();

            if (size > maxSize) {
                maxSize = size;
                fullest = queues.get(i);
                fullestIndex = i;
            }
        }

        if (fullest != null) {
            Runnable discarded = fullest.poll();

            if (discarded != null) {
                PoolLogger.taskRejected(discarded.toString(),
                        "DiscardOldestPolicy (старая задача удалена для освобождения места)");
            }
        }

        if (fullest != null && fullest.offer(task)) {
            PoolLogger.taskAccepted(fullestIndex, task.toString());
        } else {
            PoolLogger.taskRejected(task.toString(),
                    "DiscardOldestPolicy (не удалось разместить задачу после удаления старой)");
        }
    }
}
