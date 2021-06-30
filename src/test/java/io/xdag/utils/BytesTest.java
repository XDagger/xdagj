package io.xdag.utils;

import io.xdag.crypto.Hash;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes32;
import static org.junit.Assert.*;
import org.junit.Test;

public class BytesTest {

    @Test
    public void testSetMutableBytes32() {
        MutableBytes32 hashlow = MutableBytes32.create();
        Bytes32 hash = Hash.hashTwice(Bytes.wrap("123".getBytes()));
        assertEquals("0x0000000000000000000000000000000000000000000000000000000000000000", hashlow.toHexString());
        assertEquals("0x5a77d1e9612d350b3734f6282259b7ff0a3f87d62cfef5f35e91a5604c0490a3", hash.toHexString());
        hashlow.set(8, hash.slice(8, 24));
        assertEquals("0x00000000000000003734f6282259b7ff0a3f87d62cfef5f35e91a5604c0490a3", hashlow.toHexString());
    }

}
