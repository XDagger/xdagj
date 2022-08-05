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

package io.xdag.crypto;

import io.xdag.config.Config;
import io.xdag.config.DevnetConfig;
import io.xdag.crypto.jni.Native;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.util.Objects;

import static org.junit.Assert.assertEquals;

public class DnetKeysTest {

    public final String cWord = "cdeee61f60df6185f5b90a95b00f4fe70dd2675df2150d777778780025f6947a3581f329cfd393ab042080e5e7f1bd75cd5877477550bebee9e7d09b92766b9c9a266793a646ee9fe0a6f1b66f36b88bd386ff302abe8492b8b63d2806db22bcc2cf420e7a5087374827fdb9cddae4d6581b11d60640a029dce9ff5050b46a9a991f6621ef9c3c1ff7c57800175f95f9c703f0c00ac123c88400bdc0c7d9060630b2919e49e3531e993fbb87b644a8013ded2ee60020c457b97270169e0de67a0279ad8de6230d5b455d95fd4997b3c2e65d681a8a7cc4c11d25bb159894556556c876f633619d21347a1c6458753033c5432670a6810e79b46bb549f48f733d7864e4c2cd7eac5a180b6823028658f4cce95c009578e86f4cb04bbf915a695138487784cc36922e676fa9ccad9dcaf2275e4a7255965fad83c49c4d6401119b70015e9baee5b68eed0cfb99a7214d150feac1c7ad81d7dca5d314c4fffeef9a090afed569f87a1d32512aa35d0ef6133f0e9eb1d3746bf5c16931bdf85fe85292eb934bff3dd6a506bae61d54bfac90e2b07bab4ad1dba15e2478a6ccfd5fd6e096c4be6ec47a117a99d9e051621d83f2def5ae90a6a437867a6225fd53570a4d3f0511730f2e2c3306c6d0b055bad55804f61f3ff2d4a38f00d31251187534903c2227183c787e3f42bc592b36f84ebe7a406b03d497765c15b9d2646244b8";
    public final String cPwd = "08d599aafccb4e64a97d31cc2e8204ac";
    public byte[] dnetKeys;
    public Config config;

    @Before
    public void setUp() throws Exception {
        config = new DevnetConfig();
        config.initKeys();
        String absolutePath = Objects.requireNonNull(Native.class.getClassLoader().getResource("dnet_keys.bin")).getPath();
        File keyFile = new File(absolutePath);

        dnetKeys = new byte[3072];

        IOUtils.read(new FileInputStream(keyFile), dnetKeys);

        byte[] prvKey = new byte[1024];
        byte[] pubKey = new byte[1024];
        byte[] encodedWord = new byte[512];
        byte[] word = new byte[512];

        System.arraycopy(dnetKeys, 0, prvKey, 0, 1024);
        System.arraycopy(dnetKeys, 1024, pubKey, 0, 1024);
        System.arraycopy(dnetKeys, 2048, encodedWord, 0, 512);
        System.arraycopy(dnetKeys, 2560, word, 0, 512);
    }

    @Test
    public void testInitKeys() {

        for (int i = 0; i < 3072; i++) {
            if (i < 1024) {
                assertEquals(dnetKeys[i], config.getNodeSpec().getXKeys().prv[i]);
            } else if (i < 2048) {
                assertEquals(dnetKeys[i], config.getNodeSpec().getXKeys().pub[i - 1024]);
            } else if (i < 2560) {
                assertEquals(dnetKeys[i], config.getNodeSpec().getXKeys().sect0_encoded[i - 2048]);
            } else {
                assertEquals(dnetKeys[i], config.getNodeSpec().getXKeys().sect0[i - 2560]);
            }
        }
    }
}
