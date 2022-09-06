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

import static io.xdag.config.Constants.HASH_RATE_LAST_MAX_TIME;
import static io.xdag.utils.BytesUtils.equalBytes;

import io.xdag.utils.exception.XdagOverFlowException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.bytes.MutableBytes32;

public class BasicUtils {

    public static BigInteger getDiffByHash(Bytes32 hash) {
//        byte[] data = new byte[16];
        MutableBytes data = MutableBytes.create(16);
        // 实现了右移32位 4个字节
//        System.arraycopy(hash, 0, data, 4, 12);
        data.set(4, hash.slice(0, 12));
        BigInteger res = new BigInteger(data.toUnprefixedHexString(), 16);
//        BigInteger res = data.toUnsignedBigInteger();
        // max是2的128次方减1 这样效率高吗
        BigInteger max = new BigInteger("ffffffffffffffffffffffffffffffff", 16);
        return max.divide(res);
    }

    public static Bytes32 getHash(String address) {
        Bytes32 hash = null;
        if (address != null) {
//            hash = Hex.decode(address);
            hash = Bytes32.fromHexString(address);
        }
        return hash;
    }

    public static double getDouble(String value) {
        double num = Double.parseDouble(value);
        BigDecimal bigDecimal = new BigDecimal(num);
        return bigDecimal.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public static String hash2Address(Bytes32 hash) {
        return hash.reverse().slice(0, 24).toBase64String();
    }

    public static Bytes32 address2Hash(String address) {
//        byte[] ret = Base64.decode(address);
        Bytes ret = Bytes.fromBase64String(address);
//        byte[] res = new byte[32];
        MutableBytes32 res = MutableBytes32.create();
//        System.arraycopy(Arrays.reverse(ret),0,res,8,24);
        res.set(8, ret.reverse().slice(0, 24));
        return res;
    }

    public static byte[] getHashlowByHash(byte[] hash) {
        byte[] hashLow = new byte[32];
        System.arraycopy(hash, 8, hashLow, 8, 24);
        return hashLow;
    }

    // convert xdag to cheato
    public static long xdag2amount(double input) {
        if (input < 0) {
            throw new XdagOverFlowException();
        }
        double amount = Math.floor(input);
        long res = (long) amount << 32;
        input -= amount; // 小数部分
        input = input * Math.pow(2, 32);
        double tmp = Math.ceil(input);
        long result =  (long) (res + tmp);
        if (result < 0) {
            throw new XdagOverFlowException();
        }
        return result;
    }

    public static String formatDouble(double d) {
        NumberFormat nf = NumberFormat.getInstance();
        nf.setMaximumFractionDigits(9);
        nf.setGroupingUsed(false);
        return nf.format(d);
    }

    /**
     * Xfer:transferred   44796588980   10.430000000 XDAG to the address 0000002f28322e9d817fd94a1357e51a. 10.43
     * Xfer:transferred   42949672960   10.000000000 XDAG to the address 0000002f28322e9d817fd94a1357e51a. 10
     * Xfer:transferred 4398046511104 1024.000000000 XDAG to the address 0000002f28322e9d817fd94a1357e51a. 1024
     */
    public static double amount2xdag(long xdag) {
        if (xdag < 0) {
            throw new XdagOverFlowException();
        }
        long first = xdag >> 32;
        long temp = xdag - (first << 32);
        double tem = temp / Math.pow(2, 32);
        BigDecimal bigDecimal = new BigDecimal(first + tem);
        return bigDecimal.setScale(12, RoundingMode.HALF_UP).doubleValue();
    }

    public static boolean crc32Verify(byte[] src, int crc) {
        CRC32 crc32 = new CRC32();
        crc32.update(src, 0, 512);
        return equalBytes(
                BytesUtils.intToBytes((int) crc32.getValue(), true), BytesUtils.intToBytes(crc, true));
    }

    public static double xdag_diff2log(BigInteger diff) {
        if (res.compareTo(BigInteger.ZERO) > 0) {
            return Math.log(res.doubleValue());
        } else {
            return 0.0;
        }
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
        } else {
            number = number.substring(2);
            number = number.length() % 2 != 0 ? "0".concat(number) : number;
            byte[] numberBytes = Bytes.fromHexString(number).toArray();
            return (new BigInteger(1, numberBytes));
        }
    }

    public static byte[] readDnetDat(File file) throws IOException {
        FileInputStream inputStream = new FileInputStream(file);
        byte[] buffer = new byte[2048];
        try {
            while (true) {
                int len = inputStream.read(buffer);
                if (len == -1) {
                    break;
                }
            }
        } finally {
            inputStream.close();
        }
        return buffer;
    }
    public static double xdagHashRate(BigInteger[] diffs){
        double sum = 0;
        for (int i = 0; i < HASH_RATE_LAST_MAX_TIME; i++) {
            sum += xdag_diff2log(diffs[i]);
        }
        sum /= HASH_RATE_LAST_MAX_TIME;
        return Math.exp(sum) * Math.pow(2, -48);
    }
}
