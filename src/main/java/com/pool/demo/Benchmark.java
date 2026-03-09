package com.pool.demo;

import com.pool.CustomThreadPool;
import com.pool.PoolConfig;
import com.pool.PoolLogger;
import com.pool.distribution.LeastLoadedDistributor;
import com.pool.distribution.RoundRobinDistributor;
import com.pool.distribution.TaskDistributor;
import com.pool.rejection.CallerRunsPolicy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Бенчмарк производительности: сравнение CustomThreadPool со стандартными пулами JDK.
 * Запуск: mvn compile && java -cp target/classes com.pool.Benchmark
 */
public class Benchmark {

    private static final int WARMUP_TASKS = 500;
    private static final int[] TASK_COUNTS = {1_000, 5_000, 10_000};
    private static final int CORE = 4;
    private static final int MAX = 8;
    private static final int QUEUE_SIZE = 100;
    private static final long TASK_DURATION_NS = 10_000; // ~10 микросекунд CPU-работы

    public static void main(String[] args) throws Exception {
        PoolLogger.setEnabled(false);
        System.out.println("=== Бенчмарк пула потоков ===");
        System.out.printf("Ядер CPU: %d%n", Runtime.getRuntime().availableProcessors());
        System.out.printf("Core/Max пул: %d/%d, Очередь: %d%n%n", CORE, MAX, QUEUE_SIZE);

        System.out.println("Прогрев...");
        benchmarkCustomPool(WARMUP_TASKS, new RoundRobinDistributor());
        benchmarkThreadPoolExecutor(WARMUP_TASKS);
        benchmarkForkJoinPool(WARMUP_TASKS);

        System.out.println();
        System.out.printf("%-25s | %12s | %12s | %12s | %15s%n",
                "Реализация", "1 000 задач", "5 000 задач", "10 000 задач", "Пропускная сп.");
        System.out.println("-".repeat(90));

        long[] customRR = new long[TASK_COUNTS.length];

        for (int i = 0; i < TASK_COUNTS.length; i++) {
            customRR[i] = benchmarkCustomPool(TASK_COUNTS[i], new RoundRobinDistributor());
        }

        printRow("Custom (RoundRobin)", customRR);

        long[] customLL = new long[TASK_COUNTS.length];

        for (int i = 0; i < TASK_COUNTS.length; i++) {
            customLL[i] = benchmarkCustomPool(TASK_COUNTS[i], new LeastLoadedDistributor());
        }

        printRow("Custom (LeastLoaded)", customLL);

        long[] tpe = new long[TASK_COUNTS.length];

        for (int i = 0; i < TASK_COUNTS.length; i++) {
            tpe[i] = benchmarkThreadPoolExecutor(TASK_COUNTS[i]);
        }

        printRow("ThreadPoolExecutor", tpe);

        long[] fjp = new long[TASK_COUNTS.length];

        for (int i = 0; i < TASK_COUNTS.length; i++) {
            fjp[i] = benchmarkForkJoinPool(TASK_COUNTS[i]);
        }

        printRow("ForkJoinPool", fjp);

        System.out.println();
        System.out.println("Тип нагрузки: CPU-bound (вычисления без блокировок, ~10 мкс на задачу).");
        System.out.println("При I/O-нагрузке (сеть, диск, БД) соотношение результатов может измениться.");
    }

    private static long benchmarkCustomPool(int taskCount, TaskDistributor distributor) throws Exception {
        PoolConfig config = PoolConfig.builder()
                .poolName("Bench")
                .corePoolSize(CORE)
                .maxPoolSize(MAX)
                .keepAliveTime(60, TimeUnit.SECONDS)
                .queueSize(QUEUE_SIZE)
                .distributor(distributor)
                .rejectionPolicy(new CallerRunsPolicy())
                .build();

        CustomThreadPool pool = new CustomThreadPool(config);
        CountDownLatch latch = new CountDownLatch(taskCount);

        long start = System.nanoTime();

        for (int i = 0; i < taskCount; i++) {
            pool.execute(() -> {
                spinWork(TASK_DURATION_NS);
                latch.countDown();
            });
        }

        if (!latch.await(60, TimeUnit.SECONDS)) {
            System.err.println("Предупреждение: не все задачи завершились за 60 секунд");
        }

        long elapsed = (System.nanoTime() - start) / 1_000_000;

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
        return elapsed;
    }

    private static long benchmarkThreadPoolExecutor(int taskCount) throws Exception {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                CORE, MAX, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE_SIZE * CORE),
                new ThreadPoolExecutor.CallerRunsPolicy());

        CountDownLatch latch = new CountDownLatch(taskCount);

        long start = System.nanoTime();

        for (int i = 0; i < taskCount; i++) {
            executor.execute(() -> {
                spinWork(TASK_DURATION_NS);
                latch.countDown();
            });
        }

        if (!latch.await(60, TimeUnit.SECONDS)) {
            System.err.println("Предупреждение: не все задачи завершились за 60 секунд");
        }

        long elapsed = (System.nanoTime() - start) / 1_000_000;

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        return elapsed;
    }

    private static long benchmarkForkJoinPool(int taskCount) throws Exception {
        ForkJoinPool pool = new ForkJoinPool(CORE);
        CountDownLatch latch = new CountDownLatch(taskCount);

        long start = System.nanoTime();

        for (int i = 0; i < taskCount; i++) {
            pool.execute(() -> {
                spinWork(TASK_DURATION_NS);
                latch.countDown();
            });
        }

        if (!latch.await(60, TimeUnit.SECONDS)) {
            System.err.println("Предупреждение: не все задачи завершились за 60 секунд");
        }

        long elapsed = (System.nanoTime() - start) / 1_000_000;

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
        return elapsed;
    }

    private static void spinWork(long durationNs) {
        long start = System.nanoTime();

        while (System.nanoTime() - start < durationNs) {
            Thread.onSpinWait();
        }
    }

    private static void printRow(String name, long[] times) {
        double throughput = (times.length > 0 && times[times.length - 1] > 0)
                ? (double) TASK_COUNTS[TASK_COUNTS.length - 1] / times[times.length - 1] * 1000
                : 0;

        System.out.printf("%-25s | %9d мс | %9d мс | %9d мс | %12.0f з/с%n",
                name, times[0], times[1], times[2], throughput);
    }
}
