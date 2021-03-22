package io.xdag.crypto.bip32;

import io.xdag.config.Config;
import io.xdag.crypto.bip44.HDKeyDerivation;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import static org.junit.Assert.*;

public class Bip32Test {

    @Test
    public void testMasterPrivateKey() {
        String seedCode = "yard impulse luxury drive today throw farm pepper survey wreck glass federal";
        String passphrase = "";
        Long creationtime = 1409478661L;
        DeterministicSeed seed = new DeterministicSeed(seedCode, null, passphrase, creationtime);

        DeterministicKey masterPrivateKey = HDKeyDerivation.createMasterPrivateKey(seed.getSeedBytes());
        Config config = new Config();
        assertTrue(StringUtils.equals(masterPrivateKey.serializePrivB58(config), "xprv9s21ZrQH143K4Wc4BbtEduyjFx9h42F6YwKPu2XLaqoAxcPy38zWXn4trwZG8LWorTGkyMYGrejDp9eyXoqg2q5NAPUUpCdqu9RYgRMqPAW"));
        assertTrue(StringUtils.equals(masterPrivateKey.serializePubB58(config), "xpub661MyMwAqRbcGzgXHdRF13vToyzBTUxwvAEzhQvx9BL9qQj7agJm5aPNiCidxw1ccYAQdRVDvmBVPvTMxt4nMY6F4cdHAaby3ntmd5ccJAc"));
    }

}
