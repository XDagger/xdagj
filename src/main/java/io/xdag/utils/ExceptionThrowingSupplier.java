package io.xdag.utils;

public interface ExceptionThrowingSupplier<O> {
    O get() throws Throwable;
}

