package io.xdag.utils;

import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.simple.RandomSource;

public class XdagRandomUtils {

    private final static UniformRandomProvider rng = RandomSource.XO_RO_SHI_RO_128_PP.create();

    public static int nextInt() {
        return rng.nextInt();
    }

    public static int nextInt(int n) {
        return rng.nextInt(n);
    }

    public static long nextLong() {
        return rng.nextLong();
    }

    public static long nextLong(long n) {
        return rng.nextLong(n);
    }

    public static void nextBytes(byte[] bytes) {
        rng.nextBytes(bytes);
    }

    public static void nextBytes(byte[] bytes, int start, int len) {
        rng.nextBytes(bytes, start, len);
    }

    public static byte[] nextNewBytes(int count) {
        final byte[] result = new byte[count];
        rng.nextBytes(result);
        return result;
    }
}
