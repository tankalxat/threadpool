package com.pool;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Логгер пула потоков.
 */
public final class PoolLogger {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static volatile boolean enabled = true;

    private PoolLogger() {
    }

    public static void setEnabled(boolean enabled) {
        PoolLogger.enabled = enabled;
    }

    private static String timestamp() {
        return LocalTime.now().format(TIME_FMT);
    }

    public static void threadCreated(String threadName) {
        if (!enabled) return;
        System.out.printf("[%s] [ThreadFactory] Создан новый поток: %s%n", timestamp(), threadName);
    }

    public static void threadTerminated(String threadName) {
        if (!enabled) return;
        System.out.printf("[%s] [Worker] %s завершён.%n", timestamp(), threadName);
    }

    public static void taskAccepted(int queueId, String taskDescription) {
        if (!enabled) return;
        System.out.printf("[%s] [Pool] Задача принята в очередь #%d: %s%n", timestamp(), queueId, taskDescription);
    }

    public static void taskExecuting(String threadName, String taskDescription) {
        if (!enabled) return;
        System.out.printf("[%s] [Worker] %s выполняет %s%n", timestamp(), threadName, taskDescription);
    }

    public static void taskCompleted(String threadName, String taskDescription, long elapsedMs) {
        if (!enabled) return;
        System.out.printf("[%s] [Worker] %s завершил %s за %d мс%n",
                timestamp(), threadName, taskDescription, elapsedMs);
    }

    public static void taskFailed(String threadName, String taskDescription, Throwable t) {
        if (!enabled) return;
        System.out.printf("[%s] [Worker] %s ошибка при выполнении %s: %s%n",
                timestamp(), threadName, taskDescription, t.getMessage());
    }

    public static void taskRejected(String taskDescription, String policyName) {
        if (!enabled) return;
        System.out.printf("[%s] [Rejected] Задача %s отклонена из-за перегрузки! Политика: %s%n",
                timestamp(), taskDescription, policyName);
    }

    public static void idleTimeout(String threadName) {
        if (!enabled) return;
        System.out.printf("[%s] [Worker] %s таймаут простоя, останавливается.%n", timestamp(), threadName);
    }

    public static void shutdownInitiated(String poolName) {
        if (!enabled) return;
        System.out.printf("[%s] [Pool] %s shutdown() вызван. Новые задачи не принимаются.%n", timestamp(), poolName);
    }

    public static void shutdownNowInitiated(String poolName) {
        if (!enabled) return;
        System.out.printf("[%s] [Pool] %s shutdownNow() вызван. Прерывание всех воркеров.%n",
                timestamp(), poolName);
    }

    public static void poolTerminated(String poolName) {
        if (!enabled) return;
        System.out.printf("[%s] [Pool] %s полностью завершён.%n", timestamp(), poolName);
    }

    public static void spareThreadCreated(String threadName, int idleCount, int minSpare) {
        if (!enabled) return;
        System.out.printf("[%s] [Pool] Создан резервный поток: %s (свободных=%d < минимум=%d)%n",
                timestamp(), threadName, idleCount, minSpare);
    }

    public static void scaleUp(String poolName, int oldSize, int newSize) {
        if (!enabled) return;
        System.out.printf("[%s] [Pool] %s масштабирование вверх: %d -> %d воркеров%n",
                timestamp(), poolName, oldSize, newSize);
    }

    public static void scaleDown(String poolName, int oldSize, int newSize) {
        if (!enabled) return;
        System.out.printf("[%s] [Pool] %s масштабирование вниз: %d -> %d воркеров%n",
                timestamp(), poolName, oldSize, newSize);
    }
}
