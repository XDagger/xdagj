package io.xdag.utils;

public interface Cancellable {

    void cancel();

    boolean isCancelled();
}
