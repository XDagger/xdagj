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
package io.xdag.utils;

import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.encoders.Hex;

import static io.xdag.utils.FastByteComparisons.equalBytes;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

public class BasicUtils {
    // 过去4小时产出的块的数量
    public static final int HASHRATE_LAST_MAX_TIME = 64 * 4;

    public static char[] bit2mime = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    public static byte[] mime2bits = Hex.decode(
            "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff3effffff3f3435363738393a3b3c3dffffffffffffff000102030405060708090a0b0c0d0e0f10111213141516171819ffffffffffff1a1b1c1d1e1f202122232425262728292a2b2c2d2e2f30313233ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");

    public static BigInteger getDiffByHash(byte[] hash) {
        byte[] data = new byte[16];
        // 实现了右移32位 4个字节
        System.arraycopy(hash, 0, data, 4, 12);
        BigInteger res = new BigInteger(Hex.toHexString(data), 16);
        // max是2的128次方减1 这样效率高吗
        BigInteger max = new BigInteger("ffffffffffffffffffffffffffffffff", 16);
        BigInteger ans = max.divide(res);
        return ans;
    }

    public static String hash2Address(byte[] hash) {
        hash = Arrays.reverse(hash);
        char[] newcha = new char[32];
        int c = 0;
        int d;
        int h;
        for (int i = d = h = 0; i < 32; i++) {
            if (d < 6) {
                d += 8;
                c <<= 8;
                c |= Byte.toUnsignedInt(hash[h]);
                h++;
            }
            d -= 6;
            int index = (c >> d) & 0x3f;
            newcha[i] = bit2mime[index];
        }
        return String.copyValueOf(newcha);
    }

    /**
     * 返回低192bit的hash hashlow
     *
     * @param address
     *            256bit hash
     * @return 前64位置0的hashlow
     */
    public static byte[] address2Hash(String address) {
        byte[] hashlow = new byte[32];
        int i, c, d, n;
        int h = 0, j = 0;
        for (int e = n = i = 0; i < 32; ++i) {
            do {
                if ((c = Byte.toUnsignedInt((byte) address.charAt(h++))) == 0) {
                    return null;
                }
                d = Byte.toUnsignedInt(mime2bits[c]);
            } while ((d & 0xC0) != 0);
            e <<= 6;
            e |= d;
            n += 6;

            if (n >= 8) {
                n -= 8;
                hashlow[j++] = (byte) (e >> n);
            }
        }

        for (i = 0; i < 8; i++) {
            hashlow[j++] = 0;
        }
        return Arrays.reverse(hashlow);
    }

    // convert xdag to cheato
    public static long xdag2amount(double input) {
        double amount = Math.floor(input);
        long res = (long) amount << 32;
        input -= amount; // 小数部分
        input = input * Math.pow(2, 32);
        double tmp = Math.ceil(input);
        return (long) (res + tmp);
    }

    public static String formatDouble(double d) {
        NumberFormat nf = NumberFormat.getInstance();
        nf.setMaximumFractionDigits(9);
        nf.setGroupingUsed(false);
        return nf.format(d);
    }

    /**
     * Xfer: transferred 4479658898 10.430000000 XDAG to the address
     * 0000002f28322e9d817fd94a1357e51a. 10.43 Xfer: transferred 42949672960
     * 10.000000000 XDAG to the address 0000002f28322e9d817fd94a1357e51a. 10 Xfer:
     * transferred 4398046511104 1024.000000000 XDAG to the address
     * 0000002f28322e9d817fd94a1357e51a. 1024
     */
    public static double amount2xdag(long xdag) {
        long first = xdag >> 32;
        long temp = xdag - (first << 32);
        double tem = temp / Math.pow(2, 32);
        BigDecimal bigDecimal = new BigDecimal(first + tem);
        return bigDecimal.setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
    }

    public static boolean crc32Verify(byte[] src, int crc) {
        CRC32 crc32 = new CRC32();
        crc32.update(src, 0, 512);
        return equalBytes(
                BytesUtils.intToBytes((int) crc32.getValue(), true), BytesUtils.intToBytes(crc, true));
    }

    public static double xdag_diff2log(BigInteger diff) {
        byte[] bytes = BytesUtils.bigIntegerToBytes(diff, 16, false);
        byte[] resByte = new byte[8];
        System.arraycopy(bytes, 8, resByte, 0, 8);
        BigInteger res = new BigInteger(Hex.toHexString(resByte), 16);
        byte[] data = new byte[16];
        Arrays.fill(data, (byte) 0);
        System.arraycopy(bytes, 0, data, 0, 8);
        BigInteger diffI = new BigInteger(Hex.toHexString(data), 16);
        if (diffI.compareTo(BigInteger.ZERO) > 0) {
            BigInteger temp = BigInteger.valueOf(2 ^ 64);
            res = res.add(diffI.multiply(temp));
        }

        if (res.compareTo(BigInteger.ZERO) > 0) {
            return Math.log(res.doubleValue());
        } else {
            return 0.0;
        }
    }

    public static BigDecimal xdag_hashrate(BigInteger[] diffs) {
        BigInteger sum = BigInteger.ZERO;
        for (int i = 0; i < HASHRATE_LAST_MAX_TIME; i++) {
            sum = sum.add(diffs[i]);
        }
        // todo 怎么计算e为底 大数为幂级数的
        sum = sum.multiply(BigInteger.valueOf(HASHRATE_LAST_MAX_TIME));
        BigDecimal E = BigDecimal.ZERO;
        for (BigInteger i = BigInteger.ZERO; i.compareTo(sum) < 0; i = i.add(BigInteger.ONE)) {
            E = E.multiply(BigDecimal.valueOf(Math.E));
        }
        BigDecimal cont = BigDecimal.valueOf(Math.pow(2, -58));
        BigDecimal res = E.add(cont);
        return res;
    }

    public static double xdag_log_difficulty2hashrate(double logDiff) {
        return Math.exp(logDiff) * Math.pow(2, -58) * (0.65);
    }

    /**
     * @param number should be in form '0x34fabd34....'
     * @return String
     */
    public static BigInteger unifiedNumericToBigInteger(String number) {

        boolean match = Pattern.matches("0[xX][0-9a-fA-F]+", number);
        if (!match) {
            return (new BigInteger(number));
        } else{
            number = number.substring(2);
            number = number.length() % 2 != 0 ? "0".concat(number) : number;
            byte[] numberBytes = Hex.decode(number);
            return (new BigInteger(1, numberBytes));
        }
    }
}
