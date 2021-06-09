package io.xdag.crypto;

import io.libp2p.core.crypto.PrivKey;
import io.libp2p.core.crypto.PubKey;
import io.libp2p.crypto.keys.Secp256k1Kt;
import io.xdag.utils.Numeric;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class Libp2pCryptoTest {

    private PrivKey libp2pPrivKey;
    private PubKey libp2pPubKey;

    @Before
    public void setUp() {
        libp2pPrivKey = Secp256k1Kt.unmarshalSecp256k1PrivateKey(Numeric.hexStringToByteArray(SampleKeys.PRIVATE_KEY_STRING));
        libp2pPubKey = Secp256k1Kt.unmarshalSecp256k1PublicKey(Numeric.hexStringToByteArray(SampleKeys.PUBLIC_KEY_COMPRESS_STRING));
    }

    @Test
    public void testUnmarshalSecp256k1PrivateKey() {
        assertArrayEquals(libp2pPrivKey.raw(), SampleKeys.KEY_PAIR.getPrivateKey().toByteArray());
    }

    @Test
    public void testUnmarshalSecp256k1PublicKey() {
        assertArrayEquals(libp2pPubKey.raw(), SampleKeys.KEY_PAIR.getCompressPubKeyBytes());
    }
}