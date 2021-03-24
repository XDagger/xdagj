/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020-2030 The XdagJ Developers
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
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
