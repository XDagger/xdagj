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

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.*;
import java.util.stream.Stream;

public class SafeFuture<T> extends CompletableFuture<T> {

    public static SafeFuture<Void> COMPLETE = SafeFuture.completedFuture(null);

    public static void reportExceptions(final CompletionStage<?> future) {
        future.exceptionally(
                error -> {
                    final Thread currentThread = Thread.currentThread();
                    currentThread.getUncaughtExceptionHandler().uncaughtException(currentThread, error);
                    return null;
                });
    }

    public static <U> SafeFuture<U> completedFuture(U value) {
        SafeFuture<U> future = new SafeFuture<>();
        future.complete(value);
        return future;
    }

    public static <U> SafeFuture<U> failedFuture(Throwable ex) {
        SafeFuture<U> future = new SafeFuture<>();
        future.completeExceptionally(ex);
        return future;
    }

    public static <U> SafeFuture<U> of(final CompletionStage<U> stage) {
        if (stage instanceof SafeFuture) {
            return (SafeFuture<U>) stage;
        }
        final SafeFuture<U> safeFuture = new SafeFuture<>();
        propagateResult(stage, safeFuture);
        return safeFuture;
    }
    public static SafeFuture<Void> fromRunnable(final ExceptionThrowingRunnable action) {
        try {
            action.run();
            return SafeFuture.COMPLETE;
        } catch (Throwable t) {
            return SafeFuture.failedFuture(t);
        }
    }
    public static <U> SafeFuture<U> of(final ExceptionThrowingFutureSupplier<U> futureSupplier) {
        try {
            return SafeFuture.of(futureSupplier.get());
        } catch (Throwable e) {
            return SafeFuture.failedFuture(e);
        }
    }

    public static <U> SafeFuture<U> of(final ExceptionThrowingSupplier<U> supplier) {
        try {
            return SafeFuture.completedFuture(supplier.get());
        } catch (final Throwable e) {
            return SafeFuture.failedFuture(e);
        }
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    static <U> void propagateResult(final CompletionStage<U> stage, final SafeFuture<U> safeFuture) {
        stage.whenComplete(
                (result, error) -> {
                    if (error != null) {
                        safeFuture.completeExceptionally(error);
                    } else {
                        safeFuture.complete(result);
                    }
                });
    }

    public static SafeFuture<Void> allOf(final SafeFuture<?>... futures) {
        return of(CompletableFuture.allOf(futures))
                .catchAndRethrow(completionException -> addSuppressedErrors(completionException, futures));
    }

    /**
     * Adds the {@link Throwable} from each future as a suppressed exception to completionException
     * unless it is already set as the cause.
     *
     * <p>This ensures that when futures are combined with {@link #allOf(SafeFuture[])} that all
     * failures are reported, not just the first one.
     *
     * @param completionException the exception reported by {@link
     *     CompletableFuture#allOf(CompletableFuture[])}
     * @param futures the futures passed to allOf
     */
    @SuppressWarnings("FutureReturnValueIgnored")
    public static void addSuppressedErrors(
            final Throwable completionException, final SafeFuture<?>[] futures) {
        Stream.of(futures)
                .forEach(
                        future ->
                                future.exceptionally(
                                        error -> {
                                            if (completionException.getCause() != error) {
                                                completionException.addSuppressed(error);
                                            }
                                            return null;
                                        }));
    }

    public static SafeFuture<Object> anyOf(final SafeFuture<?>... futures) {
        return of(CompletableFuture.anyOf(futures));
    }

    @Override
    public <U> SafeFuture<U> newIncompleteFuture() {
        return new SafeFuture<>();
    }

    public void reportExceptions() {
        reportExceptions(this);
    }

    public void finish(final Runnable onSuccess, final Consumer<Throwable> onError) {
        finish(result -> onSuccess.run(), onError);
    }

    /**
     * Run final logic on success or error
     *
     * @param onFinished Task to run when future completes successfully or exceptionally
     */
    public void always(final Runnable onFinished) {
        finish(res -> onFinished.run(), err -> onFinished.run());
    }

    public SafeFuture<T> alwaysRun(final Runnable action) {
        return exceptionallyCompose(
                error -> {
                    action.run();
                    return failedFuture(error);
                })
                .thenPeek(value -> action.run());
    }

    public void finish(final Consumer<T> onSuccess, final Consumer<Throwable> onError) {
        handle(
                (result, error) -> {
                    if (error != null) {
                        onError.accept(error);
                    } else {
                        onSuccess.accept(result);
                    }
                    return null;
                })
                .reportExceptions();
    }

    public void finish(final Consumer<Throwable> onError) {
        handle(
                (result, error) -> {
                    if (error != null) {
                        onError.accept(error);
                    }
                    return null;
                })
                .reportExceptions();
    }

    /**
     * Returns a new CompletionStage that, when the provided stage completes exceptionally, is
     * executed with the provided stage's exception as the argument to the supplied function.
     * Otherwise the returned stage completes successfully with the same value as the provided stage.
     *
     * <p>This is the exceptional equivalent to {@link CompletionStage#thenCompose(Function)}
     *
     * @param errorHandler the function returning a new CompletionStage
     * @return the SafeFuture
     */
    @SuppressWarnings({"FutureReturnValueIgnored", "MissingOverride"})
    public SafeFuture<T> exceptionallyCompose(
            final Function<Throwable, ? extends CompletionStage<T>> errorHandler) {
        final SafeFuture<T> result = new SafeFuture<>();
        whenComplete(
                (value, error) -> {
                    try {
                        final CompletionStage<T> nextStep =
                                error != null ? errorHandler.apply(error) : completedFuture(value);
                        propagateResult(nextStep, result);
                    } catch (final Throwable t) {
                        result.completeExceptionally(t);
                    }
                });
        return result;
    }

    /**
     * Returns a new CompletionStage that, when the this stage completes exceptionally, executes the
     * provided Consumer with the exception as the argument. The returned stage will be exceptionally
     * completed with the same exception.
     *
     * <p>This is equivalent to a catch block that performs some action and then rethrows the original
     * exception.
     *
     * @param onError the function to executor when this stage completes exceptionally.
     * @return a new SafeFuture which completes with the same result (successful or exceptionally) as
     *     this stage.
     */
    public SafeFuture<T> catchAndRethrow(final Consumer<Throwable> onError) {
        return exceptionallyCompose(
                error -> {
                    onError.accept(error);
                    return failedFuture(error);
                });
    }

    @SuppressWarnings("unchecked")
    @Override
    public <U> SafeFuture<U> thenApply(final Function<? super T, ? extends U> fn) {
        return (SafeFuture<U>) super.thenApply(fn);
    }

    /** Shortcut to process the value when complete and return the same future */
    public SafeFuture<T> thenPeek(Consumer<T> fn) {
        return thenApply(
                v -> {
                    fn.accept(v);
                    return v;
                });
    }

    @Override
    public SafeFuture<Void> thenRun(final Runnable action) {
        return (SafeFuture<Void>) super.thenRun(action);
    }

    @Override
    public SafeFuture<Void> thenRunAsync(final Runnable action, final Executor executor) {
        return (SafeFuture<Void>) super.thenRunAsync(action, executor);
    }

    @Override
    public SafeFuture<Void> thenAccept(final Consumer<? super T> action) {
        return (SafeFuture<Void>) super.thenAccept(action);
    }

    @Override
    public SafeFuture<Void> thenAcceptAsync(
            final Consumer<? super T> action, final Executor executor) {
        return (SafeFuture<Void>) super.thenAcceptAsync(action, executor);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <U, V> SafeFuture<V> thenCombine(
            final CompletionStage<? extends U> other,
            final BiFunction<? super T, ? super U, ? extends V> fn) {
        return (SafeFuture<V>) super.thenCombine(other, fn);
    }

    @Override
    public <U> SafeFuture<U> thenCompose(final Function<? super T, ? extends CompletionStage<U>> fn) {
        return (SafeFuture<U>) super.thenCompose(fn);
    }

    @Override
    public <U> SafeFuture<U> thenComposeAsync(
            final Function<? super T, ? extends CompletionStage<U>> fn, final Executor executor) {
        return (SafeFuture<U>) super.thenComposeAsync(fn, executor);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <U, V> SafeFuture<V> thenCombineAsync(
            final CompletionStage<? extends U> other,
            final BiFunction<? super T, ? super U, ? extends V> fn,
            final Executor executor) {
        return (SafeFuture<V>) super.thenCombineAsync(other, fn, executor);
    }

    @Override
    public SafeFuture<T> exceptionally(final Function<Throwable, ? extends T> fn) {
        return (SafeFuture<T>) super.exceptionally(fn);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <U> SafeFuture<U> handle(final BiFunction<? super T, Throwable, ? extends U> fn) {
        return (SafeFuture<U>) super.handle(fn);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <U> SafeFuture<U> handleAsync(
            final BiFunction<? super T, Throwable, ? extends U> fn, final Executor executor) {
        return (SafeFuture<U>) super.handleAsync(fn, executor);
    }

    @Override
    public SafeFuture<T> whenComplete(final BiConsumer<? super T, ? super Throwable> action) {
        return (SafeFuture<T>) super.whenComplete(action);
    }

    @Override
    public SafeFuture<T> orTimeout(final long timeout, final TimeUnit unit) {
        return (SafeFuture<T>) super.orTimeout(timeout, unit);
    }

    @SafeVarargs
    @SuppressWarnings("unchecked")
    public final SafeFuture<T> or(SafeFuture<T>... others) {
        SafeFuture<T>[] futures = Arrays.copyOf(others, others.length + 1);
        futures[others.length] = this;
        return anyOf(futures).thenApply(o -> (T) o);
    }

}

