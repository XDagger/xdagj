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

import org.apache.commons.codec.binary.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

public class ECKeyTest {

    @Test
    public void generKeyTest() {
        String prvkey = "93c930b7a7260f4f896561417fbe54ff16b9e49112fb41a69f230c179f2048d0";
        String pubkey = "04ee41b7d17d8b8cfd8a8a6d7bb25f575cc627da758be3cabcbd7909619cd64a453d1da60294c1037322f5fbf572725d89af4a7cf1dfa223b04cc4d85c611f5b1f";
        String pubkeyCompress = "03ee41b7d17d8b8cfd8a8a6d7bb25f575cc627da758be3cabcbd7909619cd64a45";
        ECKey ecKey = ECKey.fromPrivate(Hex.decode(prvkey));
        Assert.assertTrue(StringUtils.equals(Hex.toHexString(ecKey.getPubKey()), pubkey));
        Assert.assertTrue(StringUtils.equals(Hex.toHexString(ecKey.getPrivKeyBytes()), prvkey));
        Assert.assertTrue(StringUtils.equals(Hex.toHexString(ecKey.getPubKeybyCompress()), pubkeyCompress));
    }
}
