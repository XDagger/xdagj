package io.xdag.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


import java.time.Duration;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public class FutureUtil {
    private static final Logger LOG = LogManager.getLogger();

    public static <T> void ignoreFuture(final Future<T> future) {
    }

    static void runWithFixedDelay(
            AsyncRunner runner,
            ExceptionThrowingRunnable runnable,
            Cancellable task,
            final Duration duration,
            Consumer<Throwable> exceptionHandler) {

        runner
                .runAfterDelay(
                        () -> {
                            if (!task.isCancelled()) {
                                try {
                                    runnable.run();
                                } catch (Throwable throwable) {
                                    try {
                                        exceptionHandler.accept(throwable);
                                    } catch (Exception e) {
                                        LOG.warn("Exception in exception handler", e);
                                    }
                                } finally {
                                    runWithFixedDelay(runner, runnable, task, duration, exceptionHandler);
                                }
                            }
                        },
                        duration)
                .finish(() -> {
                }, exceptionHandler);
    }

    static Cancellable createCancellable() {
        return new Cancellable() {
            private volatile boolean cancelled;

            @Override
            public void cancel() {
                cancelled = true;
            }

            @Override
            public boolean isCancelled() {
                return cancelled;
            }
        };
    }
}