package io.xdag.libp2p.utils;

public interface ExceptionThrowingFunction<I, O> {
    O apply(I value) throws Throwable;
}

