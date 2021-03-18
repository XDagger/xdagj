package io.xdag.utils;

import io.xdag.utils.SafeFuture;

import java.util.concurrent.atomic.AtomicReference;

public abstract class Service {
    enum State {
        IDLE,
        RUNNING,
        STOPPED
    }

    private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);

    public SafeFuture<?> start() {
        if (!state.compareAndSet(State.IDLE, State.RUNNING)) {
            return SafeFuture.failedFuture(
                    new IllegalStateException("Attempt to start an already started service."));
        }
        return doStart();
    }

    protected abstract SafeFuture<?> doStart();

    public SafeFuture<?> stop() {
        if (state.compareAndSet(State.RUNNING, State.STOPPED)) {
            return doStop();
        } else {
            // Return a successful future if there's nothing to do at this point
            return SafeFuture.COMPLETE;
        }
    }

    protected abstract SafeFuture<?> doStop();

    public boolean isRunning() {
        return state.get() == State.RUNNING;
    }
}
