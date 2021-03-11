package io.xdag.utils.discoveryutils.bytes;

import static com.google.common.base.Preconditions.checkArgument;

public class WrappingBytes32 extends AbstractBytesValue implements Bytes32 {

    private final BytesValue value;

    public WrappingBytes32(final BytesValue value) {
        checkArgument(
                value.size() == SIZE, "Expected value to be %s bytes, but is %s bytes", SIZE, value.size());
        this.value = value;
    }

    @Override
    public byte get(final int i) {
        return value.get(i);
    }

    @Override
    public BytesValue slice(final int index, final int length) {
        return value.slice(index, length);
    }

    @Override
    public MutableBytes32 mutableCopy() {
        final MutableBytes32 copy = MutableBytes32.create();
        value.copyTo(copy);
        return copy;
    }

    @Override
    public Bytes32 copy() {
        return mutableCopy();
    }

    @Override
    public byte[] getArrayUnsafe() {
        return value.getArrayUnsafe();
    }

    @Override
    public int size() {
        return value.size();
    }
}
