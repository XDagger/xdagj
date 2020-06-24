package io.xdag.wallet;

import io.xdag.crypto.jni.Native;
import io.xdag.utils.RSAUtils;
import org.spongycastle.util.encoders.Hex;

import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Map;



public class XdagWalletTest {
    public static void main(String[] args) throws Exception {
        Native.init();
        if(Native.dnet_crypt_init() < 0){
            throw new Exception("dnet crypt init failed");
        }
        XdagWallet xdagWallet = new XdagWallet();
        xdagWallet.init();


        byte[] array = new byte[8];
        byte[] random = Native.generate_random_bytes(array,8);
        System.out.println(Hex.toHexString(random));
    }

    public static void main1(String[] args) throws NoSuchAlgorithmException, InvalidKeySpecException {
        Map<String, String> keyPairMap = RSAUtils.createKeys(1024);
        RSAPublicKey pub = RSAUtils.getPublicKey(keyPairMap.get("publicKey"));

        System.out.println("getModulus length:" + pub.getModulus().bitLength() + " bits");

    }

}
