package io.xdag.utils.discoveryutils.bytes;

import java.nio.ByteBuffer;

import static com.google.common.base.Preconditions.*;

public class MutableByteBufferWrappingBytesValue extends AbstractBytesValue
        implements MutableBytesValue {

    protected final ByteBuffer bytes;
    protected final int offset;
    protected final int size;

    /**
     * Wraps a ByteBuffer given absolute values for offset.
     *
     * @param bytes the source byte buffer
     * @param offset the absolute offset where this value should begin
     * @param size the number of bytes to include in this value
     */
    MutableByteBufferWrappingBytesValue(final ByteBuffer bytes, final int offset, final int size) {
        int bytesSize = bytes.capacity();
        checkNotNull(bytes, "Invalid 'null' byte buffer provided");
        checkArgument(size >= 0, "Invalid negative length provided");
        if (size > 0) {
            checkElementIndex(offset, bytesSize);
        }
        checkArgument(
                offset + size <= bytesSize,
                "Provided length %s is too big: the value has only %s bytes from offset %s",
                size,
                bytesSize - offset,
                offset);

        this.bytes = bytes;
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
        return bytes.get(offset + i);
    }

    @Override
    public BytesValue slice(final int index, final int length) {
        if (index == 0 && length == size()) {
            return this;
        }
        if (length == 0) {
            return BytesValue.EMPTY;
        }

        checkElementIndex(index, size());
        checkArgument(
                index + length <= size(),
                "Provided length %s is too big: the value has size %s and has only %s bytes from %s",
                length,
                size(),
                size() - index,
                index);

        return new MutableByteBufferWrappingBytesValue(bytes, offset + index, length);
    }

    @Override
    public void set(final int i, final byte b) {
        checkElementIndex(i, size());
        bytes.put(offset + i, b);
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

        return new MutableByteBufferWrappingBytesValue(bytes, offset + index, length);
    }

    @Override
    public byte[] getArrayUnsafe() {
        if (bytes.hasArray() && offset == 0 && size == bytes.capacity() && bytes.arrayOffset() == 0) {
            return bytes.array();
        }

        return super.getArrayUnsafe();
    }
}
