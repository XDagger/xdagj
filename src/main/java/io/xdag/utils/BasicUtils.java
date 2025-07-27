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

import com.google.common.primitives.UnsignedLong;
import io.xdag.core.XAmount;
import io.xdag.crypto.encoding.Base58;
import io.xdag.crypto.exception.AddressFormatException;
import io.xdag.crypto.keys.ECKeyPair;
import io.xdag.utils.exception.XdagOverFlowException;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.bytes.MutableBytes32;
import org.apache.tuweni.units.bigints.UInt64;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

import static io.xdag.config.Constants.HASH_RATE_LAST_MAX_TIME;
import static io.xdag.crypto.keys.AddressUtils.toBytesAddress;
import static io.xdag.utils.BytesUtils.equalBytes;
import static io.xdag.utils.BytesUtils.long2UnsignedLong;

/**
 * Utility class containing basic operations for XDAG
 */
public class BasicUtils {

    /**
     * Calculate difficulty from hash by shifting right 32 bits (4 bytes)
     * @param hash Input hash value
     * @return Calculated difficulty as BigInteger
     */
    public static BigInteger getDiffByHash(Bytes32 hash) {
        MutableBytes data = MutableBytes.create(16);
        data.set(4, hash.slice(0, 12));
        BigInteger res = new BigInteger(data.toUnprefixedHexString(), 16);
        BigInteger max = new BigInteger("ffffffffffffffffffffffffffffffff", 16);
        return max.divide(res);
    }

    /**
     * Convert hex string address to Bytes32 hash
     * @param address Hex string address
     * @return Bytes32 hash or null if address is null
     */
    public static Bytes32 getHash(String address) {
        Bytes32 hash = null;
        if (address != null) {
            hash = Bytes32.fromHexString(address);
        }
        return hash;
    }

    /**
     * Convert string to double with 2 decimal places
     * @param value String number value
     * @return Double value rounded to 2 decimal places
     */
    public static double getDouble(String value) {
        double num = Double.parseDouble(value);
        BigDecimal bigDecimal = new BigDecimal(num);
        return bigDecimal.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Convert hash to base64 address
     * @param hash Input hash
     * @return Base64 encoded address
     */
    public static String hash2Address(Bytes32 hash) {
        return hash.reverse().slice(0, 24).toBase64String();
    }

    /**
     * Convert hash to public address
     * @param hash Input hash
     * @return Base58 encoded public address
     */
    public static String hash2PubAddress(Bytes32 hash) {
       return Base58.encodeCheck(hash2byte(hash.mutableCopy()));
    }

    /**
     * Convert hex public address to hash low
     * @param hexPubAddress Hex string public address
     * @return Hash low as MutableBytes32
     */
    public static MutableBytes32 hexPubAddress2Hashlow(String hexPubAddress){
        Bytes hash = Bytes.fromHexString(hexPubAddress);
        MutableBytes32 hashLow = MutableBytes32.create();
        hashLow.set(8, hash);
        return hashLow;
    }

    /**
     * Convert base64 address to hash
     * @param address Base64 encoded address
     * @return Hash as Bytes32
     */
    public static Bytes32 address2Hash(String address) {
        Bytes ret = Bytes.fromBase64String(address);
        MutableBytes32 res = MutableBytes32.create();
        res.set(8, ret.reverse().slice(0, 24));
        return res;
    }

    /**
     * Convert public address to hash
     * @param address Base58 encoded public address
     * @return Hash as Bytes32
     */
    public static Bytes32 pubAddress2Hash(String address) throws AddressFormatException {
        Bytes ret = Bytes.wrap(WalletUtils.fromBase58(address));
        MutableBytes32 res = MutableBytes32.create();
        res.set(8, ret);
        return res;
    }

    /**
     * Convert key pair to hash
     * @param keyPair Input key pair
     * @return Hash as Bytes32
     */
    public static Bytes32 keyPair2Hash(ECKeyPair keyPair) {
        Bytes ret = Bytes.wrap(toBytesAddress(keyPair));
        MutableBytes32 res = MutableBytes32.create();
        res.set(8, ret);
        return res;
    }

    /**
     * Extract bytes from hash
     * @param hash Input hash as MutableBytes32
     * @return Byte array
     */
    public static Bytes hash2byte(MutableBytes32 hash){
        return hash.slice(8,20);
    }

    /**
     * Extract bytes from hash
     * @param hash Input hash as Bytes32
     * @return Byte array
     */
    public static byte[] hash2byte(Bytes32 hash){
        Bytes bytes = hash.slice(8,20);
        return bytes.toArray();
    }

    /**
     * Convert XDAG amount to internal representation
     * @param input XDAG amount as double
     * @return Internal amount as UInt64
     * @throws XdagOverFlowException if input is negative
     */
    public static UInt64 xdag2amount(double input) {
        if (input < 0) {
            throw new XdagOverFlowException();
        }
        long amount = (long) Math.floor(input);

        UInt64 res = UInt64.valueOf(amount).shiftLeft(32);
        input -= amount; // Decimal part
        input = input * Math.pow(2, 32);
        long tmp = (long) Math.ceil(input);
        return res.add(tmp);
    }

    /**
     * Convert internal amount to XDAG
     * @param xdag Internal amount as long
     * @return XDAG amount as double
     * @throws XdagOverFlowException if input is negative
     */
    public static double amount2xdag(long xdag) {
        if (xdag < 0) {
            throw new XdagOverFlowException();
        }
        long first = xdag >>> 32;
        long temp = xdag - (first << 32);
        double tem = temp / Math.pow(2, 32);
        BigDecimal bigDecimal = new BigDecimal(first + tem);
        return bigDecimal.setScale(12, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Convert internal amount to XDAG
     * @param xdag Internal amount as UInt64
     * @return XDAG amount as double
     */
    public static double amount2xdag(UInt64 xdag) {
        UInt64 first = xdag.shiftRight(32);
        UInt64 temp = xdag.subtract(first.shiftLeft(32));
        double tem = 1.0*temp.toLong()/Math.pow(2, 32);
        BigDecimal bigDecimal = new BigDecimal(first.toLong() + tem);
        return bigDecimal.setScale(12, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Verify CRC32 checksum
     * @param src Source data
     * @param crc Expected CRC value
     * @return true if checksum matches
     */
    public static boolean crc32Verify(byte[] src, int crc) {
        CRC32 crc32 = new CRC32();
        crc32.update(src, 0, 512);
        return equalBytes(
                BytesUtils.intToBytes((int) crc32.getValue(), true), BytesUtils.intToBytes(crc, true));
    }

    /**
     * Calculate logarithm of difficulty
     * @param diff Input difficulty
     * @return Logarithm value or 0 if diff <= 0
     */
    public static double xdag_diff2log(BigInteger diff) {
        if (diff.compareTo(BigInteger.ZERO) > 0) {
            return Math.log(diff.doubleValue());
        } else {
            return 0.0;
        }
    }

    /**
     * Calculate hash rate from difficulties
     * @param diffs Array of difficulties
     * @return Calculated hash rate
     */
    public static double xdagHashRate(BigInteger[] diffs){
        double sum = 0;
        for (int i = 0; i < HASH_RATE_LAST_MAX_TIME; i++) {
            sum += xdag_diff2log(diffs[i]);
        }
        sum /= HASH_RATE_LAST_MAX_TIME;
        return Math.exp(sum) * Math.pow(2, -48);
    }

    /**
     * Compare two amounts
     * @param amount1 First amount
     * @param amount2 Second amount
     * @return Comparison result
     */
    public static int compareAmountTo(long amount1, long amount2) {
        return long2UnsignedLong(amount1).compareTo(long2UnsignedLong(amount2));
    }

    /**
     * Compare two XAmounts
     */
    public static int compareAmountTo(XAmount amount1, XAmount amount2) {
        return amount1.compareTo(amount2);
    }

    /**
     * Compare two UInt64 amounts
     */
    public static int compareAmountTo(UInt64 amount1, UInt64 amount2) {
        return amount1.compareTo(amount2);
    }

    /**
     * Compare two UnsignedLong amounts
     */
    public static int compareAmountTo(UnsignedLong amount1, UnsignedLong amount2) {
        return amount1.compareTo(amount2);
    }

    /**
     * Convert internal amount to XDAG with BigDecimal precision
     * @param xdag Internal amount
     * @return XDAG amount as BigDecimal
     * @throws XdagOverFlowException if input is negative
     */
    public static BigDecimal amount2xdagNew(long xdag) {
        if(xdag < 0) throw new XdagOverFlowException();
        long first = xdag >> 32;
        long temp = xdag - (first << 32);
        double tem = temp / Math.pow(2, 32);
        return new BigDecimal(first + tem);
    }

    /**
     * Perform division with specified scale
     * @param v1 dividend
     * @param v2 divisor
     * @param scale decimal places to round to
     * @return Division result rounded to specified scale
     * @throws IllegalArgumentException if scale is negative
     */
    public static double div(double v1, double v2, int scale) {
        if (scale < 0) {
            throw new IllegalArgumentException("The scale must be a positive integer or zero");
        }
        BigDecimal b1 = BigDecimal.valueOf(v1);
        BigDecimal b2 = BigDecimal.valueOf(v2);
        return b1.divide(b2, scale, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Extract IP address from address:port string
     * @param ipAddressAndPort String in format "/ip:port"
     * @return Extracted IP address or null if not found
     */
    public static String extractIpAddress(String ipAddressAndPort) {
        String pattern = "/(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}):\\d+";
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(ipAddressAndPort);
        if (m.find()) {
            return m.group(1);
        } else {
            return null;
        }
    }
}
