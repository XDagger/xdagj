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
package io.xdag.evm.chainspec;

import io.xdag.evm.DataWord;
import io.xdag.evm.crypto.zksnark.*;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;

import static io.xdag.utils.BytesUtils.parseWord;
import static io.xdag.utils.Numeric.toBI;

public class ByzantiumPrecompiledContracts extends BasePrecompiledContracts {

    private static final BN128Addition altBN128Add = new BN128Addition();
    private static final BN128Multiplication altBN128Mul = new BN128Multiplication();
    private static final BN128Pairing altBN128Pairing = new BN128Pairing();

    private static final DataWord altBN128AddAddr = DataWord.of(6);
    private static final DataWord altBN128MulAddr = DataWord.of(7);
    private static final DataWord altBN128PairingAddr = DataWord.of(8);

    @Override
    public PrecompiledContract getContractForAddress(DataWord address) {

        if (address.equals(altBN128AddAddr)) {
            return altBN128Add;
        } else if (address.equals(altBN128MulAddr)) {
            return altBN128Mul;
        } else if (address.equals(altBN128PairingAddr)) {
            return altBN128Pairing;
        }

        return super.getContractForAddress(address);
    }

    /**
     * Computes point addition on Barreto–Naehrig curve. See {@link BN128Fp} for
     * details<br/>
     * <br/>
     *
     * input data[]:<br/>
     * two points encoded as (x, y), where x and y are 32-byte left-padded
     * integers,<br/>
     * if input is shorter than expected, it's assumed to be right-padded with zero
     * bytes<br/>
     * <br/>
     *
     * output:<br/>
     * resulting point (x', y'), where x and y encoded as 32-byte left-padded
     * integers<br/>
     */
    public static class BN128Addition implements PrecompiledContract {

        @Override
        public long getGasForData(Bytes data) {
            return 500;
        }

        @Override
        public Pair<Boolean, Bytes> execute(PrecompiledContractContext context) {
            Bytes data = context.getInternalTransaction().getData();
            if (data == null) {
                data = Bytes.EMPTY;
            }

            byte[] x1 = parseWord(data.toArray(), 0);
            byte[] y1 = parseWord(data.toArray(), 1);

            byte[] x2 = parseWord(data.toArray(), 2);
            byte[] y2 = parseWord(data.toArray(), 3);

            BN128<Fp> p1 = BN128Fp.create(x1, y1);
            if (p1 == null)
                return Pair.of(false, Bytes.EMPTY);

            BN128<Fp> p2 = BN128Fp.create(x2, y2);
            if (p2 == null)
                return Pair.of(false, Bytes.EMPTY);

            BN128<Fp> res = p1.add(p2).toEthNotation();

            return Pair.of(true, Bytes.wrap(encodeRes(res.x().bytes(), res.y().bytes())));
        }
    }

    /**
     * Computes multiplication of scalar value on a point belonging to
     * Barreto–Naehrig curve. See {@link BN128Fp} for details<br/>
     * <br/>
     *
     * input data[]:<br/>
     * point encoded as (x, y) is followed by scalar s, where x, y and s are 32-byte
     * left-padded integers,<br/>
     * if input is shorter than expected, it's assumed to be right-padded with zero
     * bytes<br/>
     * <br/>
     *
     * output:<br/>
     * resulting point (x', y'), where x and y encoded as 32-byte left-padded
     * integers<br/>
     */
    public static class BN128Multiplication implements PrecompiledContract {

        @Override
        public long getGasForData(Bytes data) {
            return 40000;
        }

        @Override
        public Pair<Boolean, Bytes> execute(PrecompiledContractContext context) {
            Bytes data = context.getInternalTransaction().getData();
            if (data == null) {
                data = Bytes.EMPTY;
            }

            byte[] x = parseWord(data.toArray(), 0);
            byte[] y = parseWord(data.toArray(), 1);

            byte[] s = parseWord(data.toArray(), 2);

            BN128<Fp> p = BN128Fp.create(x, y);
            if (p == null)
                return Pair.of(false, Bytes.EMPTY);

            BN128<Fp> res = p.mul(toBI(s)).toEthNotation();

            return Pair.of(true, Bytes.wrap(encodeRes(res.x().bytes(), res.y().bytes())));
        }
    }

    /**
     * Computes pairing check. <br/>
     * See {@link PairingCheck} for details.<br/>
     * <br/>
     *
     * Input data[]: <br/>
     * an array of points (a1, b1, ... , ak, bk), <br/>
     * where "ai" is a point of {@link BN128Fp} curve and encoded as two 32-byte
     * left-padded integers (x; y) <br/>
     * "bi" is a point of {@link BN128G2} curve and encoded as four 32-byte
     * left-padded integers {@code (ai + b; ci + d)}, each coordinate of the point
     * is a big-endian {@link Fp2} number, so {@code b} precedes {@code a} in the
     * encoding: {@code (b, a; d, c)} <br/>
     * thus each pair (ai, bi) has 192 bytes length, if 192 is not a multiple of
     * {@code data.length} then execution fails <br/>
     * the number of pairs is derived from input length by dividing it by 192 (the
     * length of a pair) <br/>
     * <br/>
     *
     * output: <br/>
     * pairing product which is either 0 or 1, encoded as 32-byte left-padded
     * integer <br/>
     */
    public static class BN128Pairing implements PrecompiledContract {

        private static final int PAIR_SIZE = 192;

        @Override
        public long getGasForData(Bytes data) {
            if (data == null) {
                return 100000;
            }

            return 80000L * (data.size() / PAIR_SIZE) + 100000;
        }

        @Override
        public Pair<Boolean, Bytes> execute(PrecompiledContractContext context) {
            Bytes data = context.getInternalTransaction().getData();
            if (data == null) {
                data = Bytes.EMPTY;
            }

            // fail if input len is not a multiple of PAIR_SIZE
            if (data.size() % PAIR_SIZE > 0)
                return Pair.of(false, Bytes.EMPTY);

            PairingCheck check = PairingCheck.create();

            // iterating over all pairs
            for (int offset = 0; offset < data.size(); offset += PAIR_SIZE) {

                Pair<BN128G1, BN128G2> pair = decodePair(data.toArray(), offset);

                // fail if decoding has failed
                if (pair == null)
                    return Pair.of(false, Bytes.EMPTY);

                check.addPair(pair.getLeft(), pair.getRight());
            }

            check.run();
            int result = check.result();

            return Pair.of(true, DataWord.of(result).getData());
        }

        private Pair<BN128G1, BN128G2> decodePair(byte[] in, int offset) {
            byte[] x = parseWord(in, offset, 0);
            byte[] y = parseWord(in, offset, 1);

            BN128G1 p1 = BN128G1.create(x, y);

            // fail if point is invalid
            if (p1 == null)
                return null;

            // (b, a)
            byte[] b = parseWord(in, offset, 2);
            byte[] a = parseWord(in, offset, 3);

            // (d, c)
            byte[] d = parseWord(in, offset, 4);
            byte[] c = parseWord(in, offset, 5);

            BN128G2 p2 = BN128G2.create(a, b, c, d);

            // fail if point is invalid
            if (p2 == null)
                return null;

            return Pair.of(p1, p2);
        }
    }
}

