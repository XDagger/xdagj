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

import io.xdag.crypto.Sign;
import io.xdag.utils.HashUtils;
import io.xdag.crypto.Keys;
import io.xdag.evm.DataWord;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.crypto.Hash;

import java.math.BigInteger;

import static io.xdag.utils.BytesUtils.*;
import static io.xdag.utils.EVMUtils.getSizeInWords;
import static io.xdag.utils.Numeric.*;

public class BasePrecompiledContracts implements PrecompiledContracts {

    private static final ECRecover ecRecover = new ECRecover();
    private static final Sha256 sha256 = new Sha256();
    private static final Ripempd160 ripempd160 = new Ripempd160();
    private static final Identity identity = new Identity();
    private static final ModExp modExp = new ModExp();

    private static final DataWord ecRecoverAddr = DataWord.of(1);
    private static final DataWord sha256Addr = DataWord.of(2);
    private static final DataWord ripempd160Addr = DataWord.of(3);
    private static final DataWord identityAddr = DataWord.of(4);
    private static final DataWord modExpAddr = DataWord.of(5);

    @Override
    public PrecompiledContract getContractForAddress(DataWord address) {

        if (address.equals(ecRecoverAddr)) {
            return ecRecover;
        } else if (address.equals(sha256Addr)) {
            return sha256;
        } else if (address.equals(ripempd160Addr)) {
            return ripempd160;
        } else if (address.equals(identityAddr)) {
            return identity;
        } else if (address.equals(modExpAddr)) {
            return modExp;
        }

        return null;
    }

    public static byte[] encodeRes(byte[] w1, byte[] w2) {
        byte[] res = new byte[64];

        w1 = stripLeadingZeroes(w1);
        w2 = stripLeadingZeroes(w2);

        System.arraycopy(w1, 0, res, 32 - w1.length, w1.length);
        System.arraycopy(w2, 0, res, 64 - w2.length, w2.length);

        return res;
    }

    public static class Identity implements PrecompiledContract {

        public Identity() {
        }

        @Override
        public long getGasForData(Bytes data) {
            // gas charge for the execution:
            // minimum 1 and additional 1 for each 32 bytes word (round up)
            if (data == null)
                return 15;
            return 15 + getSizeInWords(data.size()) * 3;
        }

        @Override
        public Pair<Boolean, Bytes> execute(PrecompiledContractContext context) {
            return Pair.of(true, context.getInternalTransaction().getData());
        }
    }

    public static class Sha256 implements PrecompiledContract {
        @Override
        public long getGasForData(Bytes data) {
            // gas charge for the execution:
            // minimum 50 and additional 50 for each 32 bytes word (round up)
            if (data == null) {
                return 60;
            }

            return 60 + getSizeInWords(data.size()) * 12;
        }

        @Override
        public Pair<Boolean, Bytes> execute(PrecompiledContractContext context) {
            Bytes data = context.getInternalTransaction().getData();
            return Pair.of(true, Hash.sha2_256(data == null ? Bytes.EMPTY : data));
        }
    }

    public static class Ripempd160 implements PrecompiledContract {
        @Override
        public long getGasForData(Bytes data) {
            // gas charge for the execution:
            // minimum 50 and additional 50 for each 32 bytes word (round up)
            if (data == null) {
                return 600;
            }

            return 600 + getSizeInWords(data.size()) * 120;
        }

        @Override
        public Pair<Boolean, Bytes> execute(PrecompiledContractContext context) {
            Bytes data = context.getInternalTransaction().getData();
            Bytes result;
            if (data == null) {
                result = Bytes.wrap(HashUtils.ripemd160(EMPTY_BYTE_ARRAY));
            } else {
                result = Bytes.wrap(HashUtils.ripemd160(data.toArray()));
            }

            return Pair.of(true, DataWord.of(result).getData());
        }
    }

//    public static class ContractSign {
//        public static final BigInteger SECP256K1N = new BigInteger(
//                "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141", 16);
//        public byte v;
//        public BigInteger r;
//        public BigInteger s;
//
//        public ContractSign(BigInteger r, BigInteger s) {
//            this.r = r;
//            this.s = s;
//        }
//
//
//
//        public boolean validateComponents() {
//            if (v != 27 && v != 28)
//                return false;
//
//            if (isLessThan(r, BigInteger.ONE) || !isLessThan(r, SECP256K1N)) {
//                return false;
//            }
//
//            return !isLessThan(s, BigInteger.ONE) && isLessThan(s, SECP256K1N);
//        }
//    }

    public static class ECRecover implements PrecompiledContract {

        @Override
        public long getGasForData(Bytes data) {
            return 3000;
        }

        @Override
        public Pair<Boolean, Bytes> execute(PrecompiledContractContext context) {
            Bytes data = context.getInternalTransaction().getData();

            byte[] h = new byte[32];
            byte[] v = new byte[32];
            byte[] r = new byte[32];
            byte[] s = new byte[32];

            DataWord out = null;

            try {
                System.arraycopy(data.toArray(), 0, h, 0, 32);
                System.arraycopy(data.toArray(), 32, v, 0, 32);
                System.arraycopy(data.toArray(), 64, r, 0, 32);

                int sLength = data.size() < 128 ? data.size() - 96 : 32;
                System.arraycopy(data.toArray(), 96, s, 0, sLength);

                //v[31]
                Sign.ECDSASignature sig = Sign.ECDSASignature.fromComponents(r, s, v[31]);
                if (validateV(v) && sig.validateComponents() ) {
                    out = DataWord.of(Bytes.wrap(Keys.signatureToAddress(h, sig)));
                }
            } catch (Throwable any) {
                any.printStackTrace();
            }

            return Pair.of(true, Bytes.wrap(out == null ? EMPTY_BYTE_ARRAY : out.getData().toArray()));
        }

        private static boolean validateV(byte[] v) {
            for (int i = 0; i < v.length - 1; i++) {
                if (v[i] != 0) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Computes modular exponentiation on big numbers
     *
     * format of data[] array: [length_of_BASE] [length_of_EXPONENT]
     * [length_of_MODULUS] [BASE] [EXPONENT] [MODULUS] where every length is a
     * 32-byte left-padded integer representing the number of bytes. Call data is
     * assumed to be infinitely right-padded with zero bytes.
     *
     * Returns an output as a byte array with the same length as the modulus
     */
    public static class ModExp implements PrecompiledContract {

        private static final BigInteger GQUAD_DIVISOR = BigInteger.valueOf(20);

        private static final int ARGS_OFFSET = 32 * 3; // addresses length part

        @Override
        public long getGasForData(Bytes data) {
            if (data == null) {
                data = Bytes.EMPTY;
            }

            int baseLen = parseLen(data.toArray(), 0);
            int expLen = parseLen(data.toArray(), 1);
            int modLen = parseLen(data.toArray(), 2);

            byte[] expHighBytes = parseBytes(data.toArray(), addSafely(ARGS_OFFSET, baseLen), Math.min(expLen, 32));

            long multComplexity = getMultComplexity(Math.max(baseLen, modLen));
            long adjExpLen = getAdjustedExponentLength(expHighBytes, expLen);

            // use big numbers to stay safe in case of overflow
            BigInteger gas = BigInteger.valueOf(multComplexity)
                    .multiply(BigInteger.valueOf(Math.max(adjExpLen, 1)))
                    .divide(GQUAD_DIVISOR);

            return isLessThan(gas, BigInteger.valueOf(Long.MAX_VALUE)) ? gas.longValue() : Long.MAX_VALUE;
        }

        @Override
        public Pair<Boolean, Bytes> execute(PrecompiledContractContext context) {
            Bytes data = context.getInternalTransaction().getData();
            if (data == null) {
                return Pair.of(true, Bytes.EMPTY);
            }

            int baseLen = parseLen(data.toArray(), 0);
            int expLen = parseLen(data.toArray(), 1);
            int modLen = parseLen(data.toArray(), 2);

            BigInteger base = parseArg(data.toArray(), ARGS_OFFSET, baseLen);
            BigInteger exp = parseArg(data.toArray(), addSafely(ARGS_OFFSET, baseLen), expLen);
            BigInteger mod = parseArg(data.toArray(), addSafely(addSafely(ARGS_OFFSET, baseLen), expLen), modLen);

            // check if modulus is zero
            if (isZero(mod))
                return Pair.of(true, Bytes.wrap(new byte[modLen])); // should keep length of the result

            byte[] res = stripLeadingZeroes(base.modPow(exp, mod).toByteArray());

            // adjust result to the same length as the modulus has
            if (res.length < modLen) {

                byte[] adjRes = new byte[modLen];

                System.arraycopy(res, 0, adjRes, modLen - res.length, res.length);
                return Pair.of(true, Bytes.wrap(adjRes));

            } else {
                return Pair.of(true, Bytes.wrap(res));
            }
        }

        private long getMultComplexity(long x) {
            long x2 = x * x;

            if (x <= 64)
                return x2;
            if (x <= 1024)
                return x2 / 4 + 96 * x - 3072;

            return x2 / 16 + 480 * x - 199680;
        }

        private long getAdjustedExponentLength(byte[] expHighBytes, long expLen) {
            int leadingZeros = numberOfLeadingZeros(expHighBytes);
            int highestBit = 8 * expHighBytes.length - leadingZeros;

            // set index basement to zero
            if (highestBit > 0)
                highestBit--;

            if (expLen <= 32) {
                return highestBit;
            } else {
                return 8 * (expLen - 32) + highestBit;
            }
        }

        private int parseLen(byte[] data, int idx) {
            Bytes bytes = Bytes.wrap(parseBytes(data, 32 * idx, 32));
            return DataWord.of(bytes).intValueSafe();
        }

        private BigInteger parseArg(byte[] data, int offset, int len) {
            Bytes bytes = Bytes.wrap(parseBytes(data, offset, len));
            return bytesToBigInteger(bytes.toArray());
        }
    }
}

