package io.xdag.discovery.cryto;


import io.libp2p.core.crypto.KEY_TYPE;
import io.libp2p.core.crypto.KeyKt;
import io.libp2p.core.crypto.PrivKey;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.Test;

import java.math.BigInteger;

public class SECP256K1Test {
    @Test
    public void wen() throws DecoderException {
        final SECP256K1.PrivateKey privateKey =
                SECP256K1.PrivateKey.create(
                        new BigInteger("c85ef7d79691fe79573b1a7064c19c1a9819ebdbd1faaab1a8ec92344438aaf4", 16));
        SECP256K1.KeyPair keyPair = SECP256K1.KeyPair.create(privateKey);
        System.out.println(Hex.encodeHex(keyPair.getPublicKey().getEncoded()));
        PrivKey privKey = KeyKt.generateKeyPair(KEY_TYPE.SECP256K1).component1();
        System.out.println(Hex.encodeHex(privKey.publicKey().bytes()));
        String id = "0947751e3022ecf3016be03ec77ab0ce3c2662b4843898cb068d74f698ccc8ad75aa17564ae80a20bb044ee7a6d903e8e8df624b089c95d66a0570f051e5a05b";
        byte [] peerid= Hex.decodeHex(id);
        System.out.println(peerid.length);
    }
}