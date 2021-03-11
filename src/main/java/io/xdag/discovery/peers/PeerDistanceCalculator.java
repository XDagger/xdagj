package io.xdag.discovery.peers;

import io.xdag.utils.discoveryutils.bytes.BytesValue;

import java.util.Arrays;

public class PeerDistanceCalculator {

    /**
     * Calculates the XOR distance between two values.
     *
     * @param v1 the first value
     * @param v2 the second value
     * @return the distance
     */
    static int distance(final BytesValue v1, final BytesValue v2) {
        assert (v1.size() == v2.size());
        final byte[] v1b = v1.extractArray();
        final byte[] v2b = v2.extractArray();
        if (Arrays.equals(v1b, v2b)) {
            return 0;
        }
        int distance = v1b.length * 8;
        for (int i = 0; i < v1b.length; i++) {
            final byte xor = (byte) (0xff & (v1b[i] ^ v2b[i]));
            if (xor == 0) {
                distance -= 8;
            } else {
                int p = 7;
                while (((xor >> p--) & 0x01) == 0) {
                    distance--;
                }
                break;
            }
        }
        return distance;
    }
}
