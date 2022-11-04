package io.xdag.utils;

import io.xdag.crypto.Keys;
import io.xdag.utils.exception.AddressFormatException;
import org.hyperledger.besu.crypto.KeyPair;
import org.junit.Test;

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class PubkeyAddressUtilsTest {
    @Test
    public void pulkeyAddressTest()
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        KeyPair key = Keys.createEcKeyPair();
        byte[] hash160 = Keys.toBytesAddress(key);
        String base58 = PubkeyAddressUtils.toBase58(hash160);
        assertArrayEquals(PubkeyAddressUtils.fromBase58(base58), hash160);
    }

    @Test(expected = AddressFormatException.class)
    public void testAddressFormatException() {
        //the correct base58 = "7pWm5FZaNVV61wb4vQapqVixPaLC7Dh2C"
        String base58 = "7pWm5FZaNVV61wb4vQapqVixPaLC7Dh2a";
        PubkeyAddressUtils.fromBase58(base58);
    }
    @Test
    public void testCheckAddress() {
        assertEquals(true, PubkeyAddressUtils.checkAddress("7pWm5FZaNVV61wb4vQapqVixPaLC7Dh2C"));
        assertEquals(false, PubkeyAddressUtils.checkAddress("7pWm5FZaNVV61wb4vQapqVixPaLC7Dh2a"));
    }
}
