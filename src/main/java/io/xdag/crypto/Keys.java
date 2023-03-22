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

import static io.xdag.crypto.SecureRandomUtils.secureRandom;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;

import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPPublicKey;
import org.hyperledger.besu.crypto.SignatureAlgorithm;

/**
 * Crypto key utilities.
 */
public class Keys {

    public static final String ALGORITHM = SignatureAlgorithm.ALGORITHM;
    public static final String PROVIDER = BouncyCastleProvider.PROVIDER_NAME;
    public static final String CURVE_NAME = "secp256k1";

    public static final int PUBLIC_KEY_SIZE = 64;
    static final int PUBLIC_KEY_LENGTH_IN_HEX = PUBLIC_KEY_SIZE << 1;

    static {
        if (Security.getProvider(PROVIDER) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private Keys() {
    }

    /**
     * Create a keypair using SECP-256k1 curve.
     *
     * <p>Private keypairs are encoded using PKCS8
     *
     * <p>Private keys are encoded using X.509
     */
    static KeyPair createSecp256k1KeyPair()
            throws NoSuchProviderException, NoSuchAlgorithmException,
            InvalidAlgorithmParameterException {
        return createSecp256k1KeyPair(secureRandom());
    }

    static KeyPair createSecp256k1KeyPair(SecureRandom random)
            throws NoSuchProviderException, NoSuchAlgorithmException,
            InvalidAlgorithmParameterException {

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM, PROVIDER);
        ECGenParameterSpec ecGenParameterSpec = new ECGenParameterSpec(CURVE_NAME);
        if (random != null) {
            keyPairGenerator.initialize(ecGenParameterSpec, random);
        } else {
            keyPairGenerator.initialize(ecGenParameterSpec);
        }
        return KeyPair.generate(keyPairGenerator, ALGORITHM);
    }

    public static KeyPair createEcKeyPair()
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM, PROVIDER);
        ECGenParameterSpec ecGenParameterSpec = new ECGenParameterSpec(CURVE_NAME);
        keyPairGenerator.initialize(ecGenParameterSpec);
        return KeyPair.generate(keyPairGenerator, ALGORITHM);
    }

    public static byte[] toBytesAddress(KeyPair key) {
        return Hash.sha256hash160(Bytes.wrap(key.getPublicKey().asEcPoint(Sign.CURVE).getEncoded(true)));
    }

    public static byte[] Pub2Byte(SECPPublicKey publicKey){
        return Hash.sha256hash160(Bytes.wrap(publicKey.asEcPoint(Sign.CURVE).getEncoded(true)));
    }

}

