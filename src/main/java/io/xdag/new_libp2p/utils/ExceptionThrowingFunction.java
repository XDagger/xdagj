package io.xdag.new_libp2p.utils;

public interface ExceptionThrowingFunction<I, O> {
    O apply(I value) throws Throwable;
}

