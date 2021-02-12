package io.xdag.discovery.Utils.bytes;

public class BytesValueRLPOutput extends AbstractRLPOutput {
    /**
     * Computes the final encoded data.
     *
     * @return A value containing the data written to this output RLP-encoded.
     */
    public BytesValue encoded() {
        final int size = encodedSize();
        if (size == 0) {
            return BytesValue.EMPTY;
        }

        final MutableBytesValue output = MutableBytesValue.create(size);
        writeEncoded(output);
        return output;
    }
}
