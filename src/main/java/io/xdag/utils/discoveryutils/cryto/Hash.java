package io.xdag.utils.discoveryutils.cryto;

import io.xdag.utils.XdagSha256Digest;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import io.xdag.utils.discoveryutils.BouncyCastleMessageDigestFactory;
import io.xdag.utils.discoveryutils.bytes.Bytes32;
import io.xdag.utils.discoveryutils.bytes.BytesValue;

import java.io.IOException;
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


    public static Bytes32 keccak256(final BytesValue input) throws IOException {
        byte[] tmp = input.extractArray();
        XdagSha256Digest xdagSha256Digest = new XdagSha256Digest();
        return Bytes32.wrap(xdagSha256Digest.sha256Final(tmp));
    }
}