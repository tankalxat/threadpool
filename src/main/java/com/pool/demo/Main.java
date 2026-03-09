package com.pool.demo;

import com.pool.CustomThreadPool;
import com.pool.PoolConfig;
import com.pool.distribution.LeastLoadedDistributor;
import com.pool.distribution.RoundRobinDistributor;
import com.pool.distribution.TaskDistributor;
import com.pool.rejection.AbortPolicy;
import com.pool.rejection.CallerRunsPolicy;
import com.pool.rejection.DiscardOldestPolicy;
import com.pool.rejection.DiscardPolicy;
import com.pool.rejection.RejectionPolicy;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Демонстрационная программа, показывающая все возможности пула потоков.
 * Запуск всех сценариев: mvn exec:java
 * Запуск одного сценария: mvn exec:java -Dexec.args="3"
 */
public class Main {

    public static void main(String[] args) throws Exception {

        if (args.length > 0) {
            int scenario = Integer.parseInt(args[0]);
            runScenario(scenario);
        } else {

            for (int i = 1; i <= 8; i++) {
                runScenario(i);
            }
        }
    }

    private static void runScenario(int num) throws Exception {
        System.out.println();
        System.out.println("=".repeat(80));
        System.out.printf("  СЦЕНАРИЙ %d: %s%n", num, scenarioName(num));
        System.out.println("=".repeat(80));
        System.out.println();

        switch (num) {
            case 1 -> scenario1_basicWork();
            case 2 -> scenario2_scaleUp();
            case 3 -> scenario3_idleTimeout();
            case 4 -> scenario4_rejection();
            case 5 -> scenario5_shutdown();
            case 6 -> scenario6_minSpareThreads();
            case 7 -> scenario7_completableFuture();
            case 8 -> scenario8_shutdownNow();
            default -> System.out.println("Неизвестный сценарий: " + num);
        }

        System.out.println();
        Thread.sleep(500);
    }

    private static String scenarioName(int num) {
        return switch (num) {
            case 1 -> "Базовая работа";
            case 2 -> "Масштабирование при нагрузке";
            case 3 -> "Таймаут простоя и сжатие пула";
            case 4 -> "Переполнение очереди и отклонение";
            case 5 -> "Плавное завершение (Shutdown)";
            case 6 -> "Резервные потоки (minSpareThreads)";
            case 7 -> "Совместимость с CompletableFuture";
            case 8 -> "Немедленная остановка (shutdownNow)";
            default -> "Неизвестный";
        };
    }

    private static void scenario1_basicWork() throws Exception {
        CustomThreadPool pool = createPool(2, 4, 5, 5, 0, new RoundRobinDistributor());

        for (int i = 1; i <= 5; i++) {
            pool.execute(namedTask("Задача-" + i, 500 + (i * 200)));
        }

        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);
        System.out.println("\n>> Все 5 задач выполнены. Пул завершён.");
    }

    private static void scenario2_scaleUp() throws Exception {
        CustomThreadPool pool = createPool(2, 4, 3, 5, 0, new RoundRobinDistributor());

        System.out.println(">> Отправка 15 задач (core=2, max=4, queueSize=3)...");

        for (int i = 1; i <= 15; i++) {
            try {
                pool.execute(namedTask("Работа-" + i, 1000));
            } catch (RejectedExecutionException e) {
                System.out.println(">> Задача Работа-" + i + " отклонена (ожидаемо при высокой нагрузке)");
            }
        }

        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);
        System.out.println("\n>> Пул масштабировался для обработки нагрузки, затем завершился.");
    }

    private static void scenario3_idleTimeout() throws Exception {
        CustomThreadPool pool = createPool(2, 4, 3, 3, 0, new RoundRobinDistributor());

        System.out.println(">> Отправка 10 задач для масштабирования (keepAlive=3с)...");

        for (int i = 1; i <= 10; i++) {
            try {
                pool.execute(namedTask("Дело-" + i, 500));
            } catch (RejectedExecutionException ignored) {
            }
        }

        System.out.println(">> Ожидание завершения задач и таймаута простоя...");
        Thread.sleep(8000);
        System.out.printf(">> Воркеров после простоя: %d (ожидается ~corePoolSize=2)%n", pool.getWorkerCount());

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
    }

    private static void scenario4_rejection() throws Exception {
        System.out.println("--- 4a: AbortPolicy ---");
        CustomThreadPool pool1 = createPool(1, 2, 2, 5, 0,
                new RoundRobinDistributor(), new AbortPolicy());

        int rejected = 0;

        for (int i = 1; i <= 15; i++) {
            try {
                pool1.execute(namedTask("Abort-" + i, 2000));
            } catch (RejectedExecutionException e) {
                rejected++;
            }
        }

        System.out.printf(">> %d задач отклонено политикой AbortPolicy%n", rejected);
        pool1.shutdown();
        pool1.awaitTermination(30, TimeUnit.SECONDS);

        System.out.println("\n--- 4b: CallerRunsPolicy ---");
        CustomThreadPool pool2 = createPool(1, 2, 2, 5, 0,
                new RoundRobinDistributor(), new CallerRunsPolicy());

        for (int i = 1; i <= 10; i++) {
            pool2.execute(namedTask("Caller-" + i, 800));
        }

        System.out.println(">> Все задачи обработаны (часть — в потоке вызывающего)");
        pool2.shutdown();
        pool2.awaitTermination(30, TimeUnit.SECONDS);

        System.out.println("\n--- 4c: DiscardPolicy ---");
        CustomThreadPool pool3 = createPool(1, 2, 2, 5, 0,
                new RoundRobinDistributor(), new DiscardPolicy());

        for (int i = 1; i <= 10; i++) {
            pool3.execute(namedTask("Discard-" + i, 1500));
        }

        pool3.shutdown();
        pool3.awaitTermination(30, TimeUnit.SECONDS);
        System.out.println(">> Часть задач молча отброшена");

        System.out.println("\n--- 4d: DiscardOldestPolicy ---");
        CustomThreadPool pool4 = createPool(1, 2, 2, 5, 0,
                new RoundRobinDistributor(), new DiscardOldestPolicy());

        for (int i = 1; i <= 10; i++) {
            pool4.execute(namedTask("Oldest-" + i, 1500));
        }

        pool4.shutdown();
        pool4.awaitTermination(30, TimeUnit.SECONDS);
        System.out.println(">> Старые задачи вытеснены новыми");
    }

    private static void scenario5_shutdown() throws Exception {
        CustomThreadPool pool = createPool(2, 4, 5, 5, 0, new RoundRobinDistributor());

        for (int i = 1; i <= 6; i++) {
            pool.execute(namedTask("До-" + i, 1000));
        }

        System.out.println(">> Вызов shutdown()...");
        pool.shutdown();

        try {
            pool.execute(namedTask("После-shutdown", 100));
            System.out.println(">> ОШИБКА: задача принята после shutdown!");
        } catch (RejectedExecutionException e) {
            System.out.println(">> Задача корректно отклонена после shutdown.");
        }

        pool.awaitTermination(30, TimeUnit.SECONDS);
        System.out.println(">> Все задачи до shutdown выполнены. Пул завершён: " + pool.isTerminated());
    }

    private static void scenario6_minSpareThreads() throws Exception {
        CustomThreadPool pool = createPool(2, 6, 5, 5, 2, new LeastLoadedDistributor());

        System.out.println(">> Отправка 4 длинных задач (core=2, minSpare=2, max=6)...");

        for (int i = 1; i <= 4; i++) {
            pool.execute(namedTask("Резерв-" + i, 3000));
        }

        Thread.sleep(500);
        System.out.printf(">> Текущие воркеры: %d, свободные: %d%n",
                pool.getWorkerCount(), pool.getIdleCount());
        System.out.println(">> Пул должен был создать резервные потоки для поддержания minSpareThreads=2");

        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);
    }

    private static void scenario7_completableFuture() throws Exception {
        CustomThreadPool pool = createPool(2, 4, 5, 5, 0, new RoundRobinDistributor());

        System.out.println(">> Использование пула с CompletableFuture.supplyAsync()...");

        CompletableFuture<String> future1 = CompletableFuture.supplyAsync(() -> {
            sleep(500);
            return "Результат-A";
        }, pool);

        CompletableFuture<String> future2 = CompletableFuture.supplyAsync(() -> {
            sleep(500);
            return "Результат-B";
        }, pool);

        CompletableFuture<String> combined = future1.thenCombine(future2,
                (a, b) -> a + " + " + b);

        String result = combined.get(10, TimeUnit.SECONDS);
        System.out.println(">> Результат CompletableFuture: " + result);

        Future<Integer> future3 = pool.submit(() -> {
            sleep(300);
            return 42;
        });

        System.out.println(">> Результат submit(Callable): " + future3.get(10, TimeUnit.SECONDS));

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
    }

    private static void scenario8_shutdownNow() throws Exception {
        CustomThreadPool pool = createPool(2, 4, 5, 5, 0, new RoundRobinDistributor());

        System.out.println(">> Отправка 10 длинных задач...");

        for (int i = 1; i <= 10; i++) {
            try {
                pool.execute(namedTask("Срочная-" + i, 5000));
            } catch (RejectedExecutionException ignored) {
            }
        }

        Thread.sleep(500);
        System.out.println(">> Вызов shutdownNow()...");
        List<Runnable> remaining = pool.shutdownNow();
        System.out.printf(">> Невыполненных задач из очередей: %d%n", remaining.size());

        pool.awaitTermination(10, TimeUnit.SECONDS);
        System.out.println(">> Пул немедленно остановлен: " + pool.isTerminated());
    }

    private static CustomThreadPool createPool(int core, int max, int queueSize,
                                               int keepAliveSec, int minSpare,
                                               TaskDistributor distributor) {
        return createPool(core, max, queueSize, keepAliveSec, minSpare, distributor, new AbortPolicy());
    }

    private static CustomThreadPool createPool(int core, int max, int queueSize,
                                               int keepAliveSec, int minSpare,
                                               TaskDistributor distributor,
                                               RejectionPolicy policy) {
        PoolConfig config = PoolConfig.builder()
                .poolName("MyPool")
                .corePoolSize(core)
                .maxPoolSize(max)
                .keepAliveTime(keepAliveSec, TimeUnit.SECONDS)
                .queueSize(queueSize)
                .minSpareThreads(minSpare)
                .distributor(distributor)
                .rejectionPolicy(policy)
                .build();

        System.out.println(">> Конфигурация пула: " + config);
        return new CustomThreadPool(config);
    }

    private static Runnable namedTask(String name, long durationMs) {
        return new Runnable() {
            @Override
            public void run() {
                sleep(durationMs);
            }

            @Override
            public String toString() {
                return name + " (" + durationMs + "мс)";
            }
        };
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
