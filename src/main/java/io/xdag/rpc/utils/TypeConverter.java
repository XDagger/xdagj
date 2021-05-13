package io.xdag.rpc.utils;

import org.bouncycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public class TypeConverter {
    private static final Pattern LEADING_ZEROS_PATTERN = Pattern.compile("0x(0)+");

    private TypeConverter() {
        throw new IllegalAccessError("Utility class");
    }

    public static BigInteger stringNumberAsBigInt(String input) {
        if (input.startsWith("0x")) {
            return TypeConverter.stringHexToBigInteger(input);
        } else {
            return TypeConverter.stringDecimalToBigInteger(input);
        }
    }

    public static BigInteger stringHexToBigInteger(String input) {
        if(!input.startsWith("0x")) {
            throw new NumberFormatException("Invalid hex number, expected 0x prefix");
        }
        String hexa = input.substring(2);
        return new BigInteger(hexa, 16);
    }

    private static BigInteger stringDecimalToBigInteger(String input) {
        return new BigInteger(input);
    }

    public static byte[] stringHexToByteArray(String x) {
        String result = x;
        if (x.startsWith("0x")) {
            result = x.substring(2);
        }
        if (result.length() % 2 != 0) {
            result = "0" + result;
        }
        return Hex.decode(result);
    }

    public static byte[] stringToByteArray(String input) {
        return input.getBytes(StandardCharsets.UTF_8);
    }

    public static String toJsonHex(byte[] x) {
        String result = toUnformattedJsonHex(x);

        if ("0x".equals(result)) {
            return "0x00";
        }

        return result;
    }

//    public static String toJsonHex(Coin x) {
//        return x != null ? x.asBigInteger().toString() : "" ;
//    }

    public static String toJsonHex(String x) {
        return "0x"+x;
    }

    /**
     * @return A Hex representation of n WITHOUT leading zeroes
     */
    public static String toQuantityJsonHex(long n) {
        return "0x" + Long.toHexString(n);
    }

    public static String toQuantityJsonHex(double n) {
        return "" + n;
    }

    /**
     * @return A Hex representation of n WITHOUT leading zeroes
     */
    public static String toQuantityJsonHex(BigInteger n) {
        return "0x"+ n.toString(16);
    }

    /**
     * Converts a byte array to a string according to ethereum json-rpc specifications, null and empty
     * convert to 0x.
     *
     * @param x An unformatted byte array
     * @return A hex representation of the input with two hex digits per byte
     */
    public static String toUnformattedJsonHex(byte[] x) {
        return "0x" + (x == null ? "" : Hex.toHexString(x));
    }

    /**
     * Converts a byte array representing a quantity according to ethereum json-rpc specifications.
     *
     * <p>
     * 0x000AEF -> 0x2AEF
     * <p>
     * 0x00 -> 0x0
     * @param x A hex string with or without leading zeroes ("0x00AEF"). If null, it is considered as zero.
     * @return A hex string without leading zeroes ("0xAEF")
     */
    public static String toQuantityJsonHex(byte[] x) {
        String withoutLeading = LEADING_ZEROS_PATTERN.matcher(toJsonHex(x)).replaceFirst("0x");
        if ("0x".equals(withoutLeading)) {
            return "0x0";
        }

        return withoutLeading;
    }

    /**
     * Converts "0x876AF" to "876AF"
     */
    public static String removeZeroX(String str) {
        return str.substring(2);
    }

    public static long JSonHexToLong(String x) {
        if (!x.startsWith("0x")) {
            throw new NumberFormatException("Incorrect hex syntax");
        }
        x = x.substring(2);
        return Long.parseLong(x, 16);
    }
}

