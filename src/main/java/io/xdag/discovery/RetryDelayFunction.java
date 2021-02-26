package io.xdag.discovery;

import java.util.function.UnaryOperator;

interface RetryDelayFunction extends UnaryOperator<Long> {

    static RetryDelayFunction linear(final double multiplier, final long min, final long max) {
        return (prev) -> Math.min(Math.max((long) Math.ceil(prev * multiplier), min), max);
    }
}
