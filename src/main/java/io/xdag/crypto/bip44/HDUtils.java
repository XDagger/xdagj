package io.xdag.crypto.bip44;

import io.xdag.crypto.ECKey;
import org.bouncycastle.crypto.digests.SHA512Digest;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.KeyParameter;

import javax.annotation.Nonnull;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

public class HDUtils {

    static HMac createHmacSha512Digest(byte[] key) {
        SHA512Digest digest = new SHA512Digest();
        HMac hMac = new HMac(digest);
        hMac.init(new KeyParameter(key));
        return hMac;
    }

    static byte[] hmacSha512(HMac hmacSha512, byte[] input) {
        hmacSha512.reset();
        hmacSha512.update(input, 0, input.length);
        byte[] out = new byte[64];
        hmacSha512.doFinal(out, 0);
        return out;
    }

    public static byte[] hmacSha512(byte[] key, byte[] data) {
        return hmacSha512(createHmacSha512Digest(key), data);
    }

    static byte[] toCompressed(byte[] uncompressedPoint) {
        return ECKey.CURVE.getCurve().decodePoint(uncompressedPoint).getEncoded(true);
    }

    static byte[] longTo4ByteArray(long n) {
        byte[] bytes = Arrays.copyOfRange(ByteBuffer.allocate(8).putLong(n).array(), 4, 8);
        assert bytes.length == 4 : bytes.length;
        return bytes;
    }

    /**
     * Append a derivation level to an existing path
     *
     * @deprecated Use {@code HDPath#extend}
     */
    @Deprecated
    public static HDPath append(List<ChildNumber> path, ChildNumber childNumber) {
        return new HDPath(path).extend(childNumber);
    }

    /**
     * Concatenate two derivation paths
     *
     * @deprecated Use {@code HDPath#extend}
     */
    @Deprecated
    public static HDPath concat(List<ChildNumber> path, List<ChildNumber> path2) {
        return new HDPath(path).extend(path2);
    }

    /**
     * Convert to a string path, starting with "M/"
     *
     * @deprecated Use {@code HDPath#toString}
     */
    @Deprecated
    public static String formatPath(List<ChildNumber> path) {
        return HDPath.M(path).toString();
    }

    /**
     * Create an HDPath from a path string.
     *
     * @deprecated Use {@code HDPath.parsePath}
     */
    @Deprecated
    public static HDPath parsePath(@Nonnull String path) {
        return HDPath.parsePath(path);
    }

}
