package io.xdag.new_libp2p.utils;

public interface ExceptionThrowingSupplier<O> {
    O get() throws Throwable;
}

