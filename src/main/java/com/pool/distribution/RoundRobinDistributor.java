package com.pool.distribution;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Распределяет задачи циклически (Round Robin) по всем очередям.
 */
public class RoundRobinDistributor implements TaskDistributor {

    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public int selectQueue(List<BlockingQueue<Runnable>> queues) {
        return Math.floorMod(counter.getAndIncrement(), queues.size());
    }
}
