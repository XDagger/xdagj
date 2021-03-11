package io.xdag.utils.discoveryutils.cryto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.ethereum.crypto.cryptohash.Keccak256;
import io.xdag.utils.discoveryutils.BouncyCastleMessageDigestFactory;
import io.xdag.utils.discoveryutils.bytes.Bytes32;
import io.xdag.utils.discoveryutils.bytes.BytesValue;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;

public abstract class Hash {
    private Hash() {}

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static final String KECCAK256_ALG = "KECCAK-256";


    private static byte[] digestUsingAlgorithm(final BytesValue input, final String alg) {
        final MessageDigest digest;
        try {
            digest = BouncyCastleMessageDigestFactory.create(alg);
            input.update(digest);
            return digest.digest();
        } catch (final NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }


    public static Bytes32 keccak256(final BytesValue input) {
        byte[] tmp = input.extractArray();
        Keccak256 digest =  new Keccak256();
        digest.update(tmp);
        return Bytes32.wrap(digestUsingAlgorithm(input, KECCAK256_ALG));

    }


}