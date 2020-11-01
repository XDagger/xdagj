package io.xdag.crypto.bip39;

import io.xdag.crypto.bip32.DeterministicSeed;
import org.junit.Test;

public class DeterministicSeedTest {

    @Test
    public  void testSeed() {
        String seedCode = "yard impulse luxury drive today throw farm pepper survey wreck glass federal";
        String passphrase = "";
        Long creationtime = 1409478661L;

        DeterministicSeed seed = new DeterministicSeed(seedCode, null, passphrase, creationtime);
        System.out.println(seed.getMnemonicString());
    }
}
