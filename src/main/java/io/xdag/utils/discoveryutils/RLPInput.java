package io.xdag.utils.discoveryutils;


import io.xdag.utils.discoveryutils.bytes.BytesValue;
import java.math.BigInteger;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public interface RLPInput {

    boolean isDone();

    boolean nextIsList();

    boolean nextIsNull();

    boolean isEndOfCurrentList();

    void skipNext();


    int enterList();

    void leaveList();


    void leaveList(boolean ignoreRest);

    long readLongScalar();

    BigInteger readBigIntegerScalar();

    short readShort();

    default int readUnsignedShort() {
        return readShort() & 0xFFFF;
    }

    InetAddress readInetAddress();

    BytesValue readBytesValue();


    BytesValue raw();

    void reset();

    default <T> List<T> readList(final Function<RLPInput, T> valueReader) {
        final int size = enterList();
        final List<T> res = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            try {
                res.add(valueReader.apply(this));
            } catch (final Exception ignored) {

            }
        }
        leaveList();
        return res;
    }
}
