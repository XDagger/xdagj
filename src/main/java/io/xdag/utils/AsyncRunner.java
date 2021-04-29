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

import com.google.common.base.Preconditions;


import java.time.Duration;
import java.util.function.Consumer;

public interface AsyncRunner {

    default SafeFuture<Void> runAsync(final ExceptionThrowingRunnable action) {
        return runAsync(() -> SafeFuture.fromRunnable(action));
    }

    <U> SafeFuture<U> runAsync(final ExceptionThrowingFutureSupplier<U> action);

    <U> SafeFuture<U> runAfterDelay(ExceptionThrowingFutureSupplier<U> action, final Duration delay);

    default SafeFuture<Void> runAfterDelay(
            final ExceptionThrowingRunnable action, final Duration delay) {
        return runAfterDelay(() -> SafeFuture.fromRunnable(action), delay);
    }

    void shutdown();

    default <U> SafeFuture<U> runAsync(final ExceptionThrowingSupplier<U> action) {
        return runAsync(() -> SafeFuture.of(action));
    }

    default SafeFuture<Void> getDelayedFuture(final Duration delay) {
        return runAfterDelay(() -> SafeFuture.COMPLETE, delay);
    }

    /**
     * Schedules the recurrent task which will be repeatedly executed with the specified delay.
     *
     * <p>The returned instance can be used to cancel the task. Note that {@link Cancellable#cancel()}
     * doesn't interrupt already running task.
     *
     * <p>Whenever the {@code runnable} throws exception it is notified to the {@code
     * exceptionHandler} and the task recurring executions are not interrupted
     */
    default Cancellable runWithFixedDelay(
            final ExceptionThrowingRunnable runnable,
            final Duration delay,
            final Consumer<Throwable> exceptionHandler) {
        Preconditions.checkNotNull(exceptionHandler);

        Cancellable cancellable = FutureUtil.createCancellable();
        FutureUtil.runWithFixedDelay(this, runnable, cancellable, delay, exceptionHandler);
        return cancellable;
    }

    /**
     * Execute the future supplier until it completes normally up to some maximum number of retries.
     *
     * @param action The action to run
     * @param retryDelay The time to wait before retrying
     * @param maxRetries The maximum number of retries. A value of 0 means the action is run only once
     *     (no retries).
     * @param <T> The value returned by the action future
     * @return A future that resolves with the first successful result, or else an error if the
     *     maximum retries are exhausted.
     */
    default <T> SafeFuture<T> runWithRetry(
            final ExceptionThrowingFutureSupplier<T> action,
            final Duration retryDelay,
            final int maxRetries) {

        return SafeFuture.of(action)
                .exceptionallyCompose(
                        err -> {
                            if (maxRetries > 0) {
                                // Retry after delay, decrementing the remaining available retries
                                final int remainingRetries = maxRetries - 1;
                                return runAfterDelay(
                                        () -> runWithRetry(action, retryDelay, remainingRetries), retryDelay);
                            } else {
                                return SafeFuture.failedFuture(err);
                            }
                        });
    }
}
