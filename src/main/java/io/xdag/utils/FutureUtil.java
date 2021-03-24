/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020-2030 The XdagJ Developers
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
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