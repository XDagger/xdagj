package io.xdag.discovery.Utils;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public class BouncyCastleMessageDigestFactory {
    private static final BouncyCastleProvider securityProvider = new BouncyCastleProvider();

    @SuppressWarnings("DoNotInvokeMessageDigestDirectly")
    public static MessageDigest create(final String algorithm) throws NoSuchAlgorithmException {
        return MessageDigest.getInstance(algorithm, securityProvider);
    }
}
