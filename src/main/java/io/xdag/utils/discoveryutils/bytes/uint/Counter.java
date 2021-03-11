package io.xdag.utils.discoveryutils.bytes.uint;

import io.xdag.utils.discoveryutils.bytes.Bytes32;
import io.xdag.utils.discoveryutils.bytes.MutableBytes32;

import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;

public class Counter<T extends UInt256Value<T>> {

    private final MutableBytes32 bytes;
    private final T value;

    // Kept around for copy()
    private final Function<Bytes32, T> wrapFct;

    protected Counter(final Function<Bytes32, T> wrapFct) {
        this(MutableBytes32.create(), wrapFct);
    }

    protected Counter(final MutableBytes32 bytes, final Function<Bytes32, T> wrapFct) {
        this.bytes = bytes;
        this.value = wrapFct.apply(bytes);
        this.wrapFct = wrapFct;
    }

    public T get() {
        return value;
    }

    public MutableBytes32 getBytes() {
        return bytes;
    }

    public Counter<T> copy() {
        return new Counter<>(bytes.mutableCopy(), wrapFct);
    }

    public void increment() {
        increment(1);
    }

    public void increment(final long increment) {
        checkArgument(increment >= 0, "Invalid negative increment %s", increment);
        UInt256Bytes.add(bytes, increment, bytes);
    }

    public void increment(final T increment) {
        UInt256Bytes.add(bytes, increment.getBytes(), bytes);
    }

    public void decrement() {
        decrement(1);
    }

    public void decrement(final long decrement) {
        checkArgument(decrement >= 0, "Invalid negative decrement %s", decrement);
        UInt256Bytes.subtract(bytes, decrement, bytes);
    }

    public void decrement(final T decrement) {
        UInt256Bytes.subtract(bytes, decrement.getBytes(), bytes);
    }

    public void set(final T value) {
        value.getBytes().copyTo(bytes);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
