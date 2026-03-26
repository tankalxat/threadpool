package com.pool.distribution;

import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * Распределяет задачи в очередь с наименьшим количеством ожидающих элементов.
 */
public class LeastLoadedDistributor implements TaskDistributor {

    @Override
    public int selectQueue(List<BlockingQueue<Runnable>> queues) {
        int minIndex = 0;
        int minSize = Integer.MAX_VALUE;

        for (int i = 0; i < queues.size(); i++) {
            int size = queues.get(i).size();

            if (size < minSize) {
                minSize = size;
                minIndex = i;
            }
        }

        return minIndex;
    }
}
