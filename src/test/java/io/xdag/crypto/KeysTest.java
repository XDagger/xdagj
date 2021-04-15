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
package io.xdag.crypto;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;

import io.xdag.utils.Numeric;
import io.xdag.utils.Strings;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;


public class KeysTest {

    private static final byte[] ENCODED;

    static {
        byte[] privateKey = Numeric.hexStringToByteArray(SampleKeys.PRIVATE_KEY_STRING);
        byte[] publicKey = Numeric.hexStringToByteArray(SampleKeys.PUBLIC_KEY_STRING);
        ENCODED = Arrays.copyOf(privateKey, privateKey.length + publicKey.length);
        System.arraycopy(publicKey, 0, ENCODED, privateKey.length, publicKey.length);
    }

    @Test
    public void testCreateSecp256k1KeyPair() throws Exception {
        KeyPair keyPair = Keys.createSecp256k1KeyPair();
        PrivateKey privateKey = keyPair.getPrivate();
        PublicKey publicKey = keyPair.getPublic();

        assertNotNull(privateKey);
        assertNotNull(publicKey);

        assertEquals(privateKey.getEncoded().length, (144));
        assertEquals(publicKey.getEncoded().length, (88));
    }

    @Test
    public void testCreateEcKeyPair() throws Exception {
        ECKeyPair ecKeyPair = Keys.createEcKeyPair();
        assertEquals(ecKeyPair.getPublicKey().signum(), (1));
        assertEquals(ecKeyPair.getPrivateKey().signum(), (1));
    }

    @Test
    public void testGetAddressString() {
        assertEquals(Keys.getAddress(SampleKeys.PUBLIC_KEY_STRING), (SampleKeys.ADDRESS_NO_PREFIX));
    }

    @Test
    public void testGetAddressZeroPaddedAddress() {
        String publicKey =
                "0xa1b31be4d58a7ddd24b135db0da56a90fb5382077ae26b250e1dc9cd6232ce22"
                        + "70f4c995428bc76aa78e522316e95d7834d725efc9ca754d043233af6ca90113";
        assertEquals(Keys.getAddress(publicKey), ("9bc8beec6d9aed67d23218941ac5fb60a1960a0c"));
    }

    @Test
    public void testGetAddressBigInteger() {
        assertEquals(Keys.getAddress(SampleKeys.PUBLIC_KEY), (SampleKeys.ADDRESS_NO_PREFIX));
    }

    @Test
    public void testGetAddressSmallPublicKey() {
        byte[] address =
                Keys.getAddress(
                        Numeric.toBytesPadded(BigInteger.valueOf(0x1234), Keys.PUBLIC_KEY_SIZE));
        String expected = Numeric.toHexStringNoPrefix(address);

        assertEquals(Keys.getAddress("0x1234"), (expected));
    }

    @Test
    public void testGetAddressZeroPadded() {
        byte[] address =
                Keys.getAddress(
                        Numeric.toBytesPadded(BigInteger.valueOf(0x1234), Keys.PUBLIC_KEY_SIZE));
        String expected = Numeric.toHexStringNoPrefix(address);

        String value = "1234";
        assertEquals(
                Keys.getAddress(
                        "0x"
                                + Strings.zeros(Keys.PUBLIC_KEY_LENGTH_IN_HEX - value.length())
                                + value),
                (expected));
    }

    @Test
    public void testSerializeECKey() {
        assertArrayEquals(Keys.serialize(SampleKeys.KEY_PAIR), (ENCODED));
    }

    @Test
    public void testDeserializeECKey() {
        assertEquals(Keys.deserialize(ENCODED), (SampleKeys.KEY_PAIR));
    }

    @Test
    public void testDeserializeInvalidKey() {
        assertThrows(RuntimeException.class, () -> Keys.deserialize(new byte[0]));
    }

}
