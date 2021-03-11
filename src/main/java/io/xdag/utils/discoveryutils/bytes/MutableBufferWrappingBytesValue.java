package io.xdag.utils.discoveryutils.bytes;

import io.vertx.core.buffer.Buffer;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkElementIndex;

class MutableBufferWrappingBytesValue extends AbstractBytesValue implements MutableBytesValue {

    private final Buffer buffer;
    private final int offset;
    private final int size;

    MutableBufferWrappingBytesValue(final Buffer buffer, final int offset, final int size) {
        checkArgument(size >= 0, "Invalid negative length provided");
        if (size > 0) {
            checkElementIndex(offset, buffer.length());
        }
        checkArgument(
                offset + size <= buffer.length(),
                "Provided length %s is too big: the buffer has size %s and has only %s bytes from %s",
                size,
                buffer.length(),
                buffer.length() - offset,
                offset);

        this.buffer = buffer;
        this.offset = offset;
        this.size = size;
    }



    @Override
    public int size() {
        return size;
    }

    @Override
    public byte get(final int i) {
        checkElementIndex(i, size());
        return buffer.getByte(offset + i);
    }

    @Override
    public void set(final int i, final byte b) {
        checkElementIndex(i, size());
        buffer.setByte(offset + i, b);
    }

    @Override
    public MutableBytesValue mutableSlice(final int index, final int length) {
        if (index == 0 && length == size()) {
            return this;
        }
        if (length == 0) {
            return MutableBytesValue.EMPTY;
        }

        checkElementIndex(index, size());
        checkArgument(
                index + length <= size(),
                "Provided length %s is too big: the value has size %s and has only %s bytes from %s",
                length,
                size(),
                size() - index,
                index);

        return new MutableBufferWrappingBytesValue(buffer, offset + index, length);
    }

    @Override
    public BytesValue slice(final int index, final int length) {
        return mutableSlice(index, length);
    }
}
