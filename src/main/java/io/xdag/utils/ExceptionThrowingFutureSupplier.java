package io.xdag.utils;

import java.util.concurrent.CompletionStage;

public interface ExceptionThrowingFutureSupplier<O> {
    CompletionStage<O> get() throws Throwable;
}

