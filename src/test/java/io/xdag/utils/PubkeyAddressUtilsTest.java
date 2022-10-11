package io.xdag.utils;

import io.xdag.crypto.Keys;
import io.xdag.utils.exception.AddressFormatException;
import org.hyperledger.besu.crypto.KeyPair;
import org.junit.Test;

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

import static org.junit.Assert.assertArrayEquals;

public class PubkeyAddressUtilsTest {
    @Test
    public void pulkeyAddressTest()
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        KeyPair key = Keys.createEcKeyPair();
        byte[] hash160 = Keys.toBytesAddress(key);
        String base58 = PubkeyAddressUtils.toBase58(hash160);
        System.out.println(base58);
        assertArrayEquals(PubkeyAddressUtils.formBase58(base58), hash160);
    }

    @Test(expected = AddressFormatException.class)
    public void testAddressFormatException() {
        //the correct base58 = "7pWm5FZaNVV61wb4vQapqVixPaLC7Dh2C"
        String base58 = "7pWm5FZaNVV61wb4vQapqVixPaLC7Dh2a";
        PubkeyAddressUtils.formBase58(base58);
    }
}
