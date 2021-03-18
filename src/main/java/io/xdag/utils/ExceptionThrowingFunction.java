package io.xdag.utils;

public interface ExceptionThrowingFunction<I, O> {
    O apply(I value) throws Throwable;
}

