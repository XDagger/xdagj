package io.xdag.crypto;

import io.xdag.utils.RSAUtils;
import org.junit.Test;

import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Map;

public class RSATest {

    @Test
    public void test() throws Exception {
        Map<String, String> keyPairMap = RSAUtils.createKeys(1024);
        System.out.println("-----public key----\n" + keyPairMap.get("publicKey"));
        System.out.println("-----private key----\n" + keyPairMap.get("privateKey"));

        String data = "abc122";

        // 1.use public key encrypt
        String encode = RSAUtils.publicEncrypt(data, RSAUtils.getPublicKey(keyPairMap.get("publicKey")));

        System.out.println("-----encrypt result----\n" + encode);
        // 1.use private key decrypt
        String decodeResult = RSAUtils.privateDecrypt(encode, RSAUtils.getPrivateKey(keyPairMap.get("privateKey")));
        System.out.println("-----decrypt result----\n" + decodeResult);
    }

    public static void main(String[] args) throws NoSuchAlgorithmException, InvalidKeySpecException {
        Map<String, String> keyPairMap = RSAUtils.createKeys(1024);
        RSAPublicKey pub = RSAUtils.getPublicKey(keyPairMap.get("publicKey"));

        System.out.println("getModulus length:" + pub.getModulus().bitLength() + " bits");
    }
}
