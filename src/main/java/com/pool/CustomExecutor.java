package com.pool;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

/**
 * Интерфейс кастомного исполнителя для отправки задач,
 * плавного завершения и немедленной остановки.
 */
public interface CustomExecutor extends Executor {

    @Override
    void execute(Runnable command);

    <T> Future<T> submit(Callable<T> callable);

    void shutdown();

    List<Runnable> shutdownNow();
}
