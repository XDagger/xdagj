/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020-2030 The XdagJ Developers
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.xdag.evm;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * DataWord is the 32-byte array representation of a 256-bit number.
 *
 * @ImplNote DataWord objects are immutable.
 */
public class DataWord implements Comparable<DataWord> {

    public static final BigInteger TWO_POW_256 = BigInteger.valueOf(2).pow(256);
    public static final BigInteger MAX_VALUE = TWO_POW_256.subtract(BigInteger.ONE);

    public static final DataWord ZERO = of(0);
    public static final DataWord ONE = of(1);

    public static final int SIZE = 32;

    private final Bytes data;

    public static DataWord of(byte num) {
        MutableBytes bb = MutableBytes.create(SIZE);
        bb.set(31, num);
        return new DataWord(bb, false);
    }

    public static DataWord of(int num) {
        return new DataWord(Bytes.wrap(ByteBuffer.allocate(Integer.BYTES).putInt(num).array()), false);
    }

    public static DataWord of(long num) {
        return new DataWord(Bytes.wrap(ByteBuffer.allocate(Long.BYTES).putLong(num).array()), false);
    }

    public static DataWord of(BigInteger num) {
        if (num.signum() < 0 || num.compareTo(MAX_VALUE) > 0) {
            throw new IllegalArgumentException("Input BigInt can't be negative or larger than MAX_VALUE");
        }

        // NOTE: a 33 bytes array may be produced
        byte[] bytes = num.toByteArray();
        int copyOffset = Math.max(bytes.length - SIZE, 0);
        int copyLength = bytes.length - copyOffset;

//        byte[] data = new byte[SIZE];
        MutableBytes data = MutableBytes.create(SIZE);
//        System.arraycopy(bytes, copyOffset, data, SIZE - copyLength, copyLength);
        data.set(SIZE - copyLength, Bytes.wrap(bytes).slice(copyOffset, copyLength));
        return new DataWord(data, false);
    }

    public static DataWord of(String hex) {
//        return new DataWord(BytesUtils.fromHexString(hex), false);
        return new DataWord(Bytes.fromHexString(hex), false);
    }

    public static DataWord of(Bytes bytes) {
        return new DataWord(bytes, false);
    }

    /**
     * Creates a DataWord instance from byte array.
     *
     * @param data
     *            an byte array
     * @param unsafe
     *            whether the data is safe to refer
     */
    protected DataWord(Bytes data, boolean unsafe) {
        if (data == null) {
            throw new IllegalArgumentException("Input data can't be NULL");
        }

        if (data.size() == SIZE) {
            this.data = unsafe ? data.copy() : data;
        } else {
//            this.data = new byte[32];
            this.data = MutableBytes.create(32);
//            System.arraycopy(data, 0, this.data, SIZE - data.length, data.length);
            ((MutableBytes)this.data).set(SIZE - data.size(), data);
        }
    }

    /**
     * Returns a clone of the underlying byte array.
     *
     * @return a byte array
     */
    public Bytes getData() {
        return data.copy();
    }

    /**
     * Returns the last 20 bytes.
     *
     * @return
     */
    public Bytes getLast20Bytes() {
        MutableBytes last20Bytes = MutableBytes.create(20);
        last20Bytes.set(0, this.data.slice(SIZE - 20));
        return last20Bytes;
    }

    /**
     * Returns the n-th byte.
     *
     * @param index
     * @return
     */
    public byte getByte(int index) {
        return this.data.get(index);
    }

    public BigInteger value() {
        return new BigInteger(1, data.toArray());
    }

    public BigInteger sValue() {
        return new BigInteger(data.toArray());
    }

    /**
     * Converts this DataWord to an integer, checking for lost information. If this
     * DataWord is out of the possible range, then an ArithmeticException is thrown.
     *
     * @return an integer
     * @throws ArithmeticException
     *             if this value is larger than {@link Integer#MAX_VALUE}
     */
    public int intValue() throws ArithmeticException {
        return intValue(false);
    }

    /**
     * Returns {@link Integer#MAX_VALUE} in case of overflow
     */
    public int intValueSafe() {
        return intValue(true);
    }

    /**
     * Converts this DataWord to a long integer, checking for lost information. If
     * this DataWord is out of the possible range, then an ArithmeticException is
     * thrown.
     *
     * @return a long integer
     * @throws ArithmeticException
     *             if this value is larger than {@link Long#MAX_VALUE}
     */
    public long longValue() {
        return longValue(false);
    }

    /**
     * Returns {@link Long#MAX_VALUE} in case of overflow
     */
    public long longValueSafe() {
        return longValue(true);
    }

    public boolean isZero() {
        byte[] bb = data.toArray();
        for (byte b : bb) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    public boolean isNegative() {
        return (data.get(0) & 0x80) != 0;
    }

    public DataWord and(DataWord w2) {
//        byte[] buffer = new byte[SIZE];
        MutableBytes buffer = MutableBytes.create(SIZE);
        for (int i = 0; i < this.data.size(); ++i) {
//            buffer[i] = (byte) (this.data[i] & w2.data[i]);
            buffer.set(i, (byte) (this.data.get(i) & w2.data.get(i)));
        }
        return new DataWord(buffer, false);
    }

    public DataWord or(DataWord w2) {
//        byte[] buffer = new byte[SIZE];
        MutableBytes buffer = MutableBytes.create(SIZE);
        for (int i = 0; i < this.data.size(); ++i) {
//            buffer[i] = (byte) (this.data[i] | w2.data[i]);
            buffer.set(i, (byte) (this.data.get(i) | w2.data.get(i)));
        }
        return new DataWord(buffer, false);
    }

    public DataWord xor(DataWord w2) {
//        byte[] buffer = new byte[SIZE];
        MutableBytes buffer = MutableBytes.create(SIZE);
        for (int i = 0; i < this.data.size(); ++i) {
//            buffer[i] = (byte) (this.data[i] ^ w2.data[i]);
            buffer.set(i, (byte) (this.data.get(i) ^ w2.data.get(i)));
        }
        return new DataWord(buffer, false);
    }

    public DataWord negate() {
        return isZero() ? ZERO : bnot().add(DataWord.ONE);
    }

    // bitwise not
    public DataWord bnot() {
//        byte[] buffer = new byte[SIZE];
        MutableBytes buffer = MutableBytes.create(SIZE);
        for (int i = 0; i < this.data.size(); ++i) {
//            buffer[i] = (byte) (~this.data[i]);
            buffer.set(i, (byte) (~this.data.get(i)));
        }
        return new DataWord(buffer, false);
    }

    // Credit -> http://stackoverflow.com/a/24023466/459349
    public DataWord add(DataWord word) {
//        byte[] buffer = new byte[SIZE];
        MutableBytes buffer = MutableBytes.create(SIZE);
        for (int i = 31, overflow = 0; i >= 0; i--) {
            int v = (this.data.get(i) & 0xff) + (word.data.get(i) & 0xff) + overflow;
//            buffer[i] = (byte) v;
            buffer.set(i, (byte) v);
            overflow = v >>> 8;
        }
        return new DataWord(buffer, false);
    }

    public DataWord mul(DataWord word) {
        BigInteger result = value().multiply(word.value());

        return of(result.and(MAX_VALUE));
    }

    public DataWord div(DataWord word) {
        if (word.isZero()) {
            return ZERO;
        } else {
            BigInteger result = value().divide(word.value());
            return of(result.and(MAX_VALUE));
        }
    }

    public DataWord sDiv(DataWord word) {
        if (word.isZero()) {
            return ZERO;
        } else {
            BigInteger result = sValue().divide(word.sValue());
            return of(result.and(MAX_VALUE));
        }
    }

    public DataWord sub(DataWord word) {
        BigInteger result = value().subtract(word.value());
        return of(result.and(MAX_VALUE));
    }

    public DataWord exp(DataWord word) {
        BigInteger result = value().modPow(word.value(), TWO_POW_256);
        return of(result);
    }

    public DataWord mod(DataWord word) {
        if (word.isZero()) {
            return ZERO;
        } else {
            BigInteger result = value().mod(word.value());
            return of(result.and(MAX_VALUE));
        }
    }

    public DataWord sMod(DataWord word) {
        if (word.isZero()) {
            return ZERO;
        } else {
            BigInteger result = sValue().abs().mod(word.sValue().abs());
            result = (sValue().signum() == -1) ? result.negate() : result;

            return of(result.and(MAX_VALUE));
        }
    }

    public DataWord addmod(DataWord word1, DataWord word2) {
        if (word2.isZero()) {
            return ZERO;
        } else {
            BigInteger result = value().add(word1.value()).mod(word2.value());
            return of(result.and(MAX_VALUE));
        }
    }

    public DataWord mulmod(DataWord word1, DataWord word2) {
        if (this.isZero() || word1.isZero() || word2.isZero()) {
            return ZERO;
        } else {
            BigInteger result = value().multiply(word1.value()).mod(word2.value());
            return of(result.and(MAX_VALUE));
        }
    }

    public DataWord signExtend(byte k) {
        if (0 > k || k > 31) {
            throw new IndexOutOfBoundsException();
        }

//        byte[] buffer = data.clone();
        MutableBytes buffer = data.mutableCopy();
        byte mask = this.sValue().testBit((k * 8) + 7) ? (byte) 0xff : 0;
        for (int i = 31; i > k; i--) {
//            buffer[31 - i] = mask;
            buffer.set(31 - i, mask);
        }

        return new DataWord(buffer, false);
    }

    public int bytesOccupied() {
        for (int i = 0; i < SIZE; i++) {
            if (data.get(i) != 0) {
                return SIZE - i;
            }
        }

        return 0;
    }

    /**
     * Shift left, both this and input arg are treated as unsigned
     *
     * @param arg
     * @return this << arg
     */
    public DataWord shiftLeft(DataWord arg) {
        if (arg.value().compareTo(BigInteger.valueOf(SIZE * 8)) > 0) {
            return ZERO;
        } else {
            return DataWord.of(value().shiftLeft(arg.intValue()).and(MAX_VALUE));
        }
    }

    /**
     * Shift right, both this and input arg are treated as unsigned
     *
     * @param arg
     * @return this >>> arg
     */
    public DataWord shiftRight(DataWord arg) {
        if (arg.value().compareTo(BigInteger.valueOf(SIZE * 8)) > 0) {
            return ZERO;
        } else {
            return DataWord.of(value().shiftRight(arg.intValue()).and(MAX_VALUE));
        }
    }

    /**
     * Shift right, this is signed, while input arg is treated as unsigned
     *
     * @param arg
     * @return this >> arg
     */
    public DataWord shiftRightSigned(DataWord arg) {
        if (arg.value().compareTo(BigInteger.valueOf(SIZE * 8)) > 0) {
            if (this.isNegative()) {
                return DataWord.ONE.negate();
            } else {
                return DataWord.ZERO;
            }
        } else {
            return DataWord.of(sValue().shiftRight(arg.intValue()).and(MAX_VALUE));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return data.equals(((DataWord)o).data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data.toArray());
    }

    @Override
    public int compareTo(DataWord o) {
        return org.bouncycastle.util.Arrays.compareUnsigned(this.data.toArray(), o.data.toArray());
    }

    @Override
    public String toString() {
        return data.toUnprefixedHexString();
    }

    private int intValue(boolean safe) {
        if (bytesOccupied() > 4 || (data.get(SIZE - 4) & 0x80) != 0) {
            if (safe) {
                return Integer.MAX_VALUE;
            } else {
                throw new ArithmeticException();
            }
        }

        int value = 0;
        for (int i = 0; i < 4; i++) {
            value = (value << 8) + (0xff & data.get(SIZE - 4 + i));
        }

        return value;
    }

    private long longValue(boolean safe) {
        if (bytesOccupied() > 8 || (data.get(SIZE - 8) & 0x80) != 0) {
            if (safe) {
                return Long.MAX_VALUE;
            } else {
                throw new ArithmeticException();
            }
        }

        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) + (0xff & data.get(SIZE - 8 + i));
        }

        return value;
    }
}

