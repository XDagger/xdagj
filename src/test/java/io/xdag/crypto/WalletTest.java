package io.xdag.crypto;

import org.spongycastle.util.encoders.Hex;

import io.xdag.crypto.jni.Native;

public class WalletTest {
    public static void main(String[] args) throws Exception {
        Native.init();
        if (Native.dnet_crypt_init() < 0) {
            throw new Exception("dnet crypt init failed");
        }
        byte[] array = new byte[8];
        byte[] random = Native.generate_random_bytes(array, 8);
        System.out.println(Hex.toHexString(random));
    }
}
