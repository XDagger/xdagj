package io.xdag.discovery.Utils.bytes.uint;

import io.xdag.discovery.Utils.bytes.Bytes32;

import java.math.BigInteger;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;

public abstract class BaseUInt256Value<T extends UInt256Value<T>> extends AbstractUInt256Value<T> {

    protected BaseUInt256Value(final Bytes32 bytes, final Supplier<Counter<T>> mutableCtor) {
        super(bytes, mutableCtor);
    }

    protected BaseUInt256Value(final long v, final Supplier<Counter<T>> mutableCtor) {
        this(UInt256Bytes.of(v), mutableCtor);
        checkArgument(v >= 0, "Invalid negative value %s for an unsigned scalar", v);
    }

    protected BaseUInt256Value(final BigInteger v, final Supplier<Counter<T>> mutableCtor) {
        this(UInt256Bytes.of(v), mutableCtor);
        checkArgument(v.signum() >= 0, "Invalid negative value %s for an unsigned scalar", v);
    }

    protected BaseUInt256Value(final String hexString, final Supplier<Counter<T>> mutableCtor) {
        this(Bytes32.fromHexStringLenient(hexString), mutableCtor);
    }

    public T times(final UInt256 value) {
        return binaryOp(value, UInt256Bytes::multiply);
    }

    public T mod(final UInt256 value) {
        return binaryOp(value, UInt256Bytes::modulo);
    }

    public int compareTo(final UInt256 other) {
        return UInt256Bytes.compareUnsigned(this.bytes, other.getBytes());
    }

    @Override
    public UInt256 asUInt256() {
        return new DefaultUInt256(bytes);
    }
}
