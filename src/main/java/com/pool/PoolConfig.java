package com.pool;

import com.pool.distribution.RoundRobinDistributor;
import com.pool.distribution.TaskDistributor;
import com.pool.rejection.AbortPolicy;
import com.pool.rejection.RejectionPolicy;

import java.util.concurrent.TimeUnit;

public class PoolConfig {

    private final String poolName;
    private final int corePoolSize;
    private final int maxPoolSize;
    private final long keepAliveTime;
    private final TimeUnit timeUnit;
    private final int queueSize;
    private final int minSpareThreads;
    private final TaskDistributor distributor;
    private final RejectionPolicy rejectionPolicy;

    private PoolConfig(Builder builder) {
        this.poolName = builder.poolName;
        this.corePoolSize = builder.corePoolSize;
        this.maxPoolSize = builder.maxPoolSize;
        this.keepAliveTime = builder.keepAliveTime;
        this.timeUnit = builder.timeUnit;
        this.queueSize = builder.queueSize;
        this.minSpareThreads = builder.minSpareThreads;
        this.distributor = builder.distributor;
        this.rejectionPolicy = builder.rejectionPolicy;
        validate();
    }

    private void validate() {

        if (corePoolSize < 1) {
            throw new IllegalArgumentException("corePoolSize must be >= 1, got " + corePoolSize);
        }

        if (maxPoolSize < corePoolSize) {
            throw new IllegalArgumentException(
                    "maxPoolSize (" + maxPoolSize + ") must be >= corePoolSize (" + corePoolSize + ")");
        }

        if (keepAliveTime < 0) {
            throw new IllegalArgumentException("keepAliveTime must be >= 0, got " + keepAliveTime);
        }

        if (queueSize < 1) {
            throw new IllegalArgumentException("queueSize must be >= 1, got " + queueSize);
        }

        if (minSpareThreads < 0) {
            throw new IllegalArgumentException("minSpareThreads must be >= 0, got " + minSpareThreads);
        }

        if (minSpareThreads > maxPoolSize) {
            throw new IllegalArgumentException(
                    "minSpareThreads (" + minSpareThreads + ") must be <= maxPoolSize (" + maxPoolSize + ")");
        }
    }

    public String getPoolName() {
        return poolName;
    }

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public long getKeepAliveTime() {
        return keepAliveTime;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public int getMinSpareThreads() {
        return minSpareThreads;
    }

    public TaskDistributor getDistributor() {
        return distributor;
    }

    public RejectionPolicy getRejectionPolicy() {
        return rejectionPolicy;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String poolName = "CustomPool";
        private int corePoolSize = 2;
        private int maxPoolSize = 4;
        private long keepAliveTime = 5;
        private TimeUnit timeUnit = TimeUnit.SECONDS;
        private int queueSize = 10;
        private int minSpareThreads = 0;
        private TaskDistributor distributor = new RoundRobinDistributor();
        private RejectionPolicy rejectionPolicy = new AbortPolicy();

        public Builder poolName(String poolName) {
            this.poolName = poolName;
            return this;
        }

        public Builder corePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
            return this;
        }

        public Builder maxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
            return this;
        }

        public Builder keepAliveTime(long keepAliveTime, TimeUnit timeUnit) {
            this.keepAliveTime = keepAliveTime;
            this.timeUnit = timeUnit;
            return this;
        }

        public Builder queueSize(int queueSize) {
            this.queueSize = queueSize;
            return this;
        }

        public Builder minSpareThreads(int minSpareThreads) {
            this.minSpareThreads = minSpareThreads;
            return this;
        }

        public Builder distributor(TaskDistributor distributor) {
            this.distributor = distributor;
            return this;
        }

        public Builder rejectionPolicy(RejectionPolicy rejectionPolicy) {
            this.rejectionPolicy = rejectionPolicy;
            return this;
        }

        public PoolConfig build() {
            return new PoolConfig(this);
        }
    }

    @Override
    public String toString() {
        return "PoolConfig{" +
                "poolName='" + poolName + '\'' +
                ", corePoolSize=" + corePoolSize +
                ", maxPoolSize=" + maxPoolSize +
                ", keepAliveTime=" + keepAliveTime + " " + timeUnit +
                ", queueSize=" + queueSize +
                ", minSpareThreads=" + minSpareThreads +
                ", distributor=" + distributor.getClass().getSimpleName() +
                ", rejectionPolicy=" + rejectionPolicy.getClass().getSimpleName() +
                '}';
    }
}
